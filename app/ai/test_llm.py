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


def test_default_model_is_production_model(monkeypatch):
    """漏配 AI_MODEL 时仍必须使用经联调的正式模型，避免静默漂移。"""
    monkeypatch.delenv("AI_MODEL", raising=False)
    assert llm.DEFAULT_MODEL == "raccoon/sn-deepseek-v4-1-flash-1017"
    assert llm._cfg()["model"] == "raccoon/sn-deepseek-v4-1-flash-1017"

def test_provider_status_maps_to_stable_error_code():
    assert llm.classify_error(RuntimeError("auth"), 401) == "AI_AUTH_FAILED"
    assert llm.classify_error(RuntimeError("forbidden"), 403) == "AI_AUTH_FAILED"
    assert llm.classify_error(RuntimeError("quota"), 429) == "AI_RATE_LIMITED"
    assert llm.classify_error(RuntimeError("upstream"), 503) == "AI_UPSTREAM_ERROR"


def test_transport_errors_map_to_timeout_and_connect_codes():
    assert llm.classify_error(TimeoutError("timed out")) == "AI_TIMEOUT"
    assert llm.classify_error(ConnectionError("connect refused")) == "AI_CONNECT_FAILED"
    assert llm.classify_error(RuntimeError("other")) == "AI_REQUEST_FAILED"


def test_http_429_is_invalid_and_falls_back(monkeypatch):
    """供应商限流必须落稳定错误码，不能把异常抛到分类器。"""
    _enable(monkeypatch)
    import httpx
    request = httpx.Request("POST", "http://model.local/chat/completions")
    response = httpx.Response(429, request=request)
    error = httpx.HTTPStatusError("too many requests", request=request, response=response)
    monkeypatch.setattr(llm, "_post", lambda cfg, messages: (_ for _ in ()).throw(error))

    out = llm.classify("洗手台下面有点渗水")

    assert out["invalid"] is True
    assert out["errorCode"] == "AI_RATE_LIMITED"
    assert out["category"] is None


def test_http_500_is_invalid_and_falls_back(monkeypatch):
    _enable(monkeypatch)
    import httpx
    request = httpx.Request("POST", "http://model.local/chat/completions")
    response = httpx.Response(503, request=request)
    error = httpx.HTTPStatusError("upstream down", request=request, response=response)
    monkeypatch.setattr(llm, "_post", lambda cfg, messages: (_ for _ in ()).throw(error))

    out = llm.classify("窗户关不严")

    assert out["invalid"] is True
    assert out["errorCode"] == "AI_UPSTREAM_ERROR"


def test_classifier_keeps_rule_result_when_provider_is_rate_limited(monkeypatch):
    """分类器对外必须继续可用：模型限流只影响来源，不阻断报修。"""
    import classifier
    _enable(monkeypatch)
    classifier._CACHE.clear()
    monkeypatch.setattr(llm, "classify", lambda desc: {
        "category": None, "level": None, "confidence": 0.0,
        "basis": "模型不可用：限流", "invalid": True, "attempts": 1,
        "model": "fake-model", "latencyMs": 10, "errorCode": "AI_RATE_LIMITED",
        "errorMessage": "429",
    })

    out = classifier.classify("窗户关不严，晚上漏风")

    assert out["fallback"] is True
    assert out["source"] == "rule-fallback"
    assert out["category"]
    assert out["level"]
    assert out["errorCode"] == "AI_RATE_LIMITED"
