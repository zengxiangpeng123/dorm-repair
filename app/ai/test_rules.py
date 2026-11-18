"""规则单测。跑法：D:\\develop\\python-envs\\dorm-ai\\Scripts\\python.exe -m pytest -q"""

from rules import classify


def test_safety_word_is_urgent():
    v = classify("插座打火，有焦味，不敢用了")
    assert v.level == "紧急"
    assert v.category == "电气"
    assert "安全类" in v.basis


def test_water_risk_is_safety():
    # SYS-004 里「快接满了」是浸水风险，必须算安全类
    v = classify("洗手盆软管一直滴水，水桶快接满了")
    assert v.category == "给排水"
    assert v.level == "紧急"
    assert "浸水风险" in v.basis


def test_plain_issue_is_normal():
    v = classify("窗户把手松了，关不严")
    assert v.category == "门窗家具"
    assert v.level == "普通"


def test_impact_raises_one_level():
    v = classify("空调坏了，没法洗澡，洗不了澡")
    assert v.category == "电器"
    assert v.level == "较急"


def test_unknown_falls_back_conservative():
    v = classify("asdfghjkl")
    assert v.confidence < 0.70
    assert v.level in ("较急", "紧急"), "判不出来要保守，不能给普通"
    assert "保守" in v.basis or "没识别" in v.basis


def test_empty_desc_never_blocks():
    v = classify("")
    assert v.category in ("给排水", "电气", "电器", "门窗家具", "网络")
    assert v.fallback is False
    assert v.confidence == 0.0


def test_all_five_categories_reachable():
    samples = {
        "给排水": "卫生间下水慢，怀疑堵了",
        "电气": "房间跳闸了，开关推不上去",
        "电器": "热水器不加热",
        "门窗家具": "衣柜门关不上",
        "网络": "网口没反应，连不上网",
    }
    for cat, text in samples.items():
        assert classify(text).category == cat, text
