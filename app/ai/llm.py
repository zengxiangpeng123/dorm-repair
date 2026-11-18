"""真模型的调用口：OpenAI 兼容的 /chat/completions。

设计上刻意跟 rules.py 长得一样——都是"给一段描述，回答是哪类、多急、凭什么"。
区别只有三点：慢（秒级）、不稳定（同一段话两次可能不同）、要钱。
所以它永远只是 rules.py 的**补丁**，不是替代：见 classifier.py 里的合并规则。

配置全走环境变量，密钥不进仓库：
    AI_MODE=llm
    AI_BASE_URL=http://127.0.0.1:3065/v1
    AI_MODEL=workbuddy/deepseek-v4.1-flash
    AI_API_KEY=sk-...
    AI_TIMEOUT_S=8
"""

from __future__ import annotations

import json
import os
import re
import time
from typing import Any

import httpx

from rules import CATEGORIES, LEVELS

DEFAULT_BASE_URL = "http://127.0.0.1:3065/v1"
DEFAULT_MODEL = "workbuddy/deepseek-v4.1-flash"

SYSTEM_PROMPT = f"""你是宿舍维修系统的故障定级助手。只输出一行 JSON，不要解释，不要代码块。

字段：
- category：只能是 {"、".join(CATEGORIES)} 之一
- level：只能是 {"、".join(LEVELS)} 之一
- confidence：0 到 1 之间的小数
- basis：一句中文理由，说明命中了描述里的哪个词或哪件事，不超过 40 字

判定原则：
1. 有漏电、触电、打火、冒烟、起火、焦味、煤气味 → level 必须是「紧急」
2. 给排水故障出现积水、快接满、漫出来、泡水 → level 必须是「紧急」
3. 影响正常生活（没法洗澡、用不了、出不了水）但不涉及安全的 → 「较急」
4. 不影响基本生活的 → 「普通」
5. 描述里看不出来 → category 选最接近的，confidence 给 0.5 以下

示例输入：洗手盆下面的软管一直滴水，水桶快接满了
示例输出：{{"category":"给排水","level":"紧急","confidence":0.95,"basis":"软管滴水且快接满，有溢水风险"}}"""

_JSON_RE = re.compile(r"\{.*\}", re.S)


def _cfg() -> dict[str, Any]:
    return {
        "base_url": os.getenv("AI_BASE_URL", DEFAULT_BASE_URL).rstrip("/"),
        "api_key": os.getenv("AI_API_KEY", "").strip(),
        "model": os.getenv("AI_MODEL", DEFAULT_MODEL),
        "timeout_s": float(os.getenv("AI_TIMEOUT_S", "8")),
    }


def mode() -> str:
    """llm = 真模型（需要 AI_API_KEY）；其余一律按 rules 处理。"""
    return os.getenv("AI_MODE", "rules").strip().lower()


def enabled() -> bool:
    return mode() == "llm" and bool(_cfg()["api_key"])


def _extract(content: str) -> dict[str, Any] | None:
    """模型有时会裹 ```json 或加一句废话，抠出第一个 JSON 对象。"""
    if not content:
        return None
    text = content.strip()
    if text.startswith("```"):
        text = re.sub(r"^```[a-zA-Z]*\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    m = _JSON_RE.search(text)
    if not m:
        return None
    try:
        obj = json.loads(m.group(0))
    except json.JSONDecodeError:
        return None
    return obj if isinstance(obj, dict) else None


def _validate(obj: dict[str, Any]) -> dict[str, Any] | None:
    """模型说的话不合规就当没说——绝不让它把类型写成"其他"这种词污染状态机外的字段。"""
    category = str(obj.get("category", "")).strip()
    level = str(obj.get("level", "")).strip()
    if category not in CATEGORIES or level not in LEVELS:
        return None
    try:
        confidence = float(obj.get("confidence", 0))
    except (TypeError, ValueError):
        confidence = 0.0
    return {
        "category": category,
        "level": level,
        "confidence": max(0.0, min(1.0, confidence)),
        "basis": str(obj.get("basis", "")).strip()[:120] or "模型未给理由",
    }


REPAIR_PROMPT = """上一次的输出不是合法 JSON。现在只输出一个 JSON 对象，形如
{"category":"给排水","level":"普通","confidence":0.6,"basis":"理由"}
不要解释，不要代码块，不要多余文字。"""


def _post(cfg: dict[str, Any], messages: list[dict[str, str]]) -> tuple[str, str]:
    """发一次请求，返回 (content, model)。

    先带 response_format={"type":"json_object"}——这是让模型**从概率上**少说废话的
    正规手段；如果服务端不认这个字段（400/404/422/501），再不带它重发一次。
    """
    payload = {
        "model": cfg["model"],
        "messages": messages,
        "temperature": 0,
        "max_tokens": 400,
    }
    url = f"{cfg['base_url']}/chat/completions"
    headers = {
        "Authorization": f"Bearer {cfg['api_key']}",
        "Content-Type": "application/json",
    }
    # trust_env=False 是必须的：本机 NO_PROXY 里写了裸 ::1，
    # httpx 解析环境代理时会抛 InvalidURL，连请求都发不出去。
    # 而且 127.0.0.1 的模型流量本来也不该走系统代理。
    with httpx.Client(timeout=cfg["timeout_s"], trust_env=False) as client:
        r = client.post(url, headers=headers, json={**payload, "response_format": {"type": "json_object"}})
        if r.status_code in (400, 404, 422, 501):
            r = client.post(url, headers=headers, json=payload)
        r.raise_for_status()
        body = r.json()
    msg = body["choices"][0]["message"]
    # 推理模型偶尔把答案全塞进 reasoning_content，content 为空——退一步读它。
    content = (msg.get("content") or "").strip() or (msg.get("reasoning_content") or "").strip()
    return content, body.get("model", cfg["model"])


def classify(desc: str) -> dict[str, Any] | None:
    """调模型。成功返回四字段 + 延迟；任何失败返回 None（调用方走规则）。

    合规性靠三道栅栏：response_format → 抠 JSON → 不合规就带纠正提示重试一次。
    还是不吐 JSON 才认输（此时 source 会写成 rule-fallback）。
    """
    if not enabled():
        return None
    cfg = _cfg()
    t0 = time.time()
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": (desc or "").strip()[:800]},
    ]
    content = ""
    model = cfg["model"]
    try:
        for attempt in (1, 2):
            content, model = _post(cfg, messages)
            verdict = _validate(_extract(content) or {})
            if verdict is not None:
                verdict.update({
                    "latencyMs": int((time.time() - t0) * 1000),
                    "model": model,
                    "invalid": False,
                    "attempts": attempt,
                })
                return verdict
            if attempt == 1:
                messages = messages + [{"role": "user", "content": REPAIR_PROMPT}]
        return {
            "category": None,
            "level": None,
            "confidence": 0.0,
            "basis": f"模型连试两次都不合规：{content[:80]}",
            "latencyMs": int((time.time() - t0) * 1000),
            "model": model,
            "invalid": True,
            "attempts": 2,
        }
    except Exception as e:  # 超时、连不上、401、额度用完，一律当"没说话"
        return {
            "category": None,
            "level": None,
            "confidence": 0.0,
            "basis": f"模型不可用：{type(e).__name__}",
            "latencyMs": int((time.time() - t0) * 1000),
            "model": cfg["model"],
            "invalid": True,
            "error": str(e)[:200],
        }


def warmup() -> dict[str, Any]:
    """/health 和启动时用：先打一发小请求，把冷启动的十几秒挪到没人看的时刻。"""
    t0 = time.time()
    out = classify("插座没电了")
    return {
        "ok": bool(out and not out.get("invalid")),
        "latencyMs": int((time.time() - t0) * 1000),
        "detail": out,
    }