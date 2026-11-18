"""规则 ∧ 模型 的合并逻辑单测。全程离线，不打网络。

跑法：D:\\develop\\python-envs\\dorm-ai\\Scripts\\python.exe -m pytest -q
"""

import classifier
import llm


def _fake(monkeypatch, result):
    """把 llm.classify 换掉，并记下有没有被调用。"""
    calls = []

    def stub(desc):
        calls.append(desc)
        return result

    monkeypatch.setattr(llm, "enabled", lambda: True)
    monkeypatch.setattr(llm, "classify", stub)
    classifier._CACHE.clear()
    return calls


def _ok(category, level, confidence=0.9, basis="模型理由"):
    return {"category": category, "level": level, "confidence": confidence,
            "basis": basis, "latencyMs": 3400, "model": "stub", "invalid": False}


def test_safety_gate_never_calls_model(monkeypatch):
    """打火/焦味是闸门，模型再准也不许参与——省一次调用，也不给不确定性留口子。"""
    calls = _fake(monkeypatch, _ok("电气", "普通"))
    out = classifier.classify("插座打火，有焦味，不敢用了")
    assert out["level"] == "紧急"
    assert out["source"] == "rule-gate"
    assert calls == [], "安全类命中时不该调模型"


def test_water_risk_never_calls_model(monkeypatch):
    calls = _fake(monkeypatch, _ok("给排水", "普通"))
    out = classifier.classify("洗手盆软管一直滴水，水桶快接满了")
    assert out["level"] == "紧急"
    assert out["source"] == "rule-gate"
    assert calls == []


def test_model_can_raise_level(monkeypatch):
    """规则看不出的严重性，允许模型往上抬。"""
    _fake(monkeypatch, _ok("给排水", "紧急"))
    out = classifier.classify("洗手盆下面有点水渍")
    assert out["level"] == "紧急"
    assert out["source"] == "llm"


def test_model_cannot_lower_level(monkeypatch):
    """模型说普通、规则说较急 → 取较急。宁可多跑一趟，也别把单压到明天。"""
    _fake(monkeypatch, _ok("电器", "普通"))
    out = classifier.classify("空调坏了，洗不了澡")
    assert out["level"] == "较急"
    assert "取高的一档" in out["basis"]


def test_category_mismatch_discounts_confidence(monkeypatch):
    # 规则按命中词数判「给排水」（堵/地漏），模型读完整句认为主诉是门 → 两边不一致
    _fake(monkeypatch, _ok("门窗家具", "普通", confidence=0.9))
    out = classifier.classify("卫生间地漏堵了，门也关不上，没法用")
    assert out["category"] == "门窗家具", "类型以模型为准"
    assert out["confidence"] == 0.63  # 0.9 * 0.7
    assert "打折" in out["basis"]
    assert out["level"] == "较急", "模型给普通，规则给较急 → 取高"


def test_model_down_falls_back_to_rules(monkeypatch):
    """超时/连不上/额度用完，都当模型没说话。"""
    _fake(monkeypatch, {"category": None, "level": None, "confidence": 0.0,
                        "basis": "模型不可用：TimeoutException", "latencyMs": 20000,
                        "model": "stub", "invalid": True})
    out = classifier.classify("衣柜门关不上")
    assert out["source"] == "rule-fallback"
    assert out["fallback"] is True
    assert out["category"] == "门窗家具"
    assert "TimeoutException" in out["basis"]


def test_rules_only_mode(monkeypatch):
    monkeypatch.setattr(llm, "enabled", lambda: False)
    out = classifier.classify("衣柜门关不上")
    assert out["source"] == "rule-only"
    assert out["level"] in ("普通", "较急", "紧急")


def test_cache_hits_once(monkeypatch):
    """同一段描述第二次直接命中缓存，不重复烧额度。"""
    calls = _fake(monkeypatch, _ok("网络", "普通"))
    classifier.classify("网口没反应")
    classifier.classify("网口没反应")
    assert len(calls) == 1
