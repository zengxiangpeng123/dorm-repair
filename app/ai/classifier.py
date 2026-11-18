"""把规则和模型拼成一个答案。这是对外唯一的入口。

三条分工，答辩被问就照这个说：

1. **规则是闸门**。命中安全类词（漏电/起火/冒烟）或浸水风险（快接满/积水）时，
   直接定紧急并返回，**根本不调模型**——这类判断不能交给一个每次答案都不一样的模型，
   而且省一次调用。
2. **模型是理解器**。其余情况才问模型，它更会读"混着说"的描述。
3. **合并取保守**。紧急程度取两边高的那个：规则说较急、模型说普通，就按较急走，
   宁可让师傅多跑一趟，也别把漏水的单压到明天。类型不合规就退回规则。

模型慢、连不上、返回不是 JSON、类型写成"其他"——全都当它没说话，返回规则结果，
并在 basis 里记下原因，事后能查这一单是怎么定的。
"""

from __future__ import annotations

from typing import Any

import llm
import rules
from rules import LEVELS

_CACHE: dict[str, dict[str, Any]] = {}
_CACHE_MAX = 200


def _cache_get(key: str) -> dict[str, Any] | None:
    hit = _CACHE.get(key)
    if hit is None:
        return None
    out = dict(hit)
    out["source"] = "cache"
    return out


def _cache_put(key: str, value: dict[str, Any]) -> None:
    if len(_CACHE) >= _CACHE_MAX:
        _CACHE.pop(next(iter(_CACHE)))
    _CACHE[key] = dict(value)


def _higher(a: str, b: str) -> str:
    return a if LEVELS.index(a) >= LEVELS.index(b) else b


def _gate_hit(rule_verdict: rules.Verdict) -> bool:
    """规则是不是靠"安全类/浸水风险"定到紧急的——是就说明闸门已经拦下来了。"""
    return rule_verdict.level == "紧急" and (
        "安全类词" in rule_verdict.basis or "浸水风险" in rule_verdict.basis
    )


def classify(desc: str, fallback_category: str | None = None,
             force_rules: bool = False) -> dict[str, Any]:
    text = (desc or "").strip()
    rule_verdict = rules.classify(text, fallback_category)

    # 1 闸门：不调模型
    if _gate_hit(rule_verdict):
        out = rule_verdict.to_dict()
        out.update({"source": "rule-gate", "model": None, "latencyMs": 0})
        _cache_put(f"{fallback_category}|{text}", out)
        return out

    # 1.5 有人要求"先给个快的"：学生提交这一步不能等模型，模型另开一路复核
    if force_rules:
        out = rule_verdict.to_dict()
        out.update({"source": "rule-fast", "model": None, "latencyMs": 0})
        return out

    # 2 模型
    if not llm.enabled():
        out = rule_verdict.to_dict()
        out.update({"source": "rule-only", "model": None, "latencyMs": 0})
        return out

    key = f"{fallback_category}|{text}"
    cached = _cache_get(key)
    if cached is not None:
        return cached

    res = llm.classify(text)
    if not res or res.get("invalid"):
        out = rule_verdict.to_dict()
        reason = (res or {}).get("basis", "模型未启用")
        out["fallback"] = True
        out["basis"] = f"{out['basis']}；（模型未采用：{reason}，按规则结果）"
        out.update({
            "source": "rule-fallback",
            "model": (res or {}).get("model"),
            "latencyMs": (res or {}).get("latencyMs", 0),
        })
        _cache_put(key, out)
        return out

    # 3 合并：类型用模型的，紧急取高的
    category = res["category"]
    level = _higher(rule_verdict.level, res["level"])
    confidence = res["confidence"]
    notes = [f"模型：{res['basis']}"]
    if category != rule_verdict.category:
        confidence = round(confidence * 0.7, 2)
        notes.append(f"类型与规则不一致（规则判{rule_verdict.category}），置信度打折")
    if level != res["level"]:
        notes.append(f"模型定{res['level']}，规则定{rule_verdict.level}，取高的一档")

    out = {
        "category": category,
        "level": level,
        "confidence": confidence,
        "basis": "；".join(notes),
        "fallback": False,
        "source": "llm",
        "model": res.get("model"),
        "latencyMs": res.get("latencyMs", 0),
    }
    _cache_put(key, out)
    return out


def stats() -> dict[str, Any]:
    return {
        "mode": llm.mode(),
        "llmEnabled": llm.enabled(),
        "model": llm._cfg()["model"] if llm.enabled() else None,
        "baseUrl": llm._cfg()["base_url"] if llm.enabled() else None,
        "cached": len(_CACHE),
    }