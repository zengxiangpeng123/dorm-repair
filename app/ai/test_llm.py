"""llm.py 的离线测试：不碰网络，只验"不合规怎么办"这条最难复现的路径。

为什么单独测这一层：模型偶尔不吐合法 JSON（实测 BX20261118024 复核定级
5405ms 后拿回一句人话，只能走 rule-fallback）。抓 JSON、纠正重试、认输三种
结局必须都在测试里钉死，否则线上只能靠运气。
"""

from __future__ import annotations

import llm


def _enable(monkeypatch):
    monkeypatch.setenv("AI_MODE", "llm")
    monkeypatch.setenv("AI_API_KEY", "sk-test")


def test_extract_plain_json():
    assert llm._extract('{"category":"给排水","level":"普通","confidence":0.6,"basis":"滴水"}') == {
        "category": "给排水",
        "level": "普通",
        "confidence": 0.6,
        "basis": "滴水",
    }


def test_extract_fenced_json():
    text = '好的：\n```json\n{"category":"电气","level":"紧急","confidence":0.9,"basis":"打火"}\n```'
    assert llm._extract(text)["category"] == "电气"


def test_extract_no_json():
    assert llm._extract("抱歉，我无法判断。") is None


def test_validate_rejects_unknown_category():
    assert llm._validate({"category": "其他", "level": "普通", "confidence": 0.9}) is None
    assert llm._validate({"category": "电气", "level": "很急", "confidence": 0.9}) is None


def test_retry_once_then_success(monkeypatch):
    """第一次吐人话，第二次吐 JSON —— 必须救回来，且标记 attempts=2。"""
    _enable(monkeypatch)
    calls: list[list[dict[str, str]]] = []

    def fake_post(cfg, messages):
        calls.append(list(messages))
        if len(calls) == 1:
            return "这个问题看起来是给排水问题。", "fake-model"
        return '{"category":"给排水","level":"较急","confidence":0.8,"basis":"软管漏水"}', "fake-model"

    monkeypatch.setattr(llm, "_post", fake_post)
    out = llm.classify("洗手盆下面软管一直滴水")

    assert out["invalid"] is False
    assert out["attempts"] == 2
    assert out["category"] == "给排水"
    # 第二次请求必须带上纠正提示，否则等于原样重试
    assert calls[1][-1]["content"] == llm.REPAIR_PROMPT


def test_two_bad_answers_give_up(monkeypatch):
    """两次都不吐 JSON —— 认输，标 invalid，让 classifier 走 rule-fallback。"""
    _enable(monkeypatch)
    monkeypatch.setattr(llm, "_post", lambda cfg, messages: ("我判断不了。", "fake-model"))

    out = llm.classify("天花板有点脏")

    assert out["invalid"] is True
    assert out["attempts"] == 2
    assert out["category"] is None
    assert "不合规" in out["basis"]


def test_disabled_without_key(monkeypatch):
    monkeypatch.setenv("AI_MODE", "llm")
    monkeypatch.delenv("AI_API_KEY", raising=False)
    assert llm.classify("插座没电") is None
