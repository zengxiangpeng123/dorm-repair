"""故障类型与紧急程度的判断规则。

一条铁律：这份规则只回答"是哪类、多急、凭什么"，不碰工单状态、不写数据库。
工单怎么流转由 Java 那边的状态机管，AI 说错了顶多定级不准，不会让单走错。

为什么是规则不是模型：本机没有 GPU 推理环境，答辩现场也不允许联网下载权重。
规则版本可解释、可测、断网能跑；接口形状跟真模型一致，将来换模型只改 classify() 实现。
"""

from __future__ import annotations

import re
from dataclasses import dataclass, asdict

CATEGORIES = ["给排水", "电气", "电器", "门窗家具", "网络"]
LEVELS = ["普通", "较急", "紧急"]

# 安全类关键词：命中就不再看别的，直接定紧急
SAFETY = ["漏电", "触电", "打火", "冒烟", "起火", "着火", "焦味", "糊味", "煤气味"]

# 每条规则：(故障类型, 关键词, 基础置信度)
RULES: list[tuple[str, list[str], float]] = [
    ("给排水", ["漏水", "滴水", "渗水", "堵", "下水", "反味", "马桶", "水龙头", "花洒",
               "洗手盆", "水管", "软管", "阀", "地漏", "水压", "停水"], 0.92),
    ("电气", ["插座", "跳闸", "断电", "没电", "开关", "灯", "灯泡", "灯管", "线路",
             "电闸", "短路", "电压", "电表"], 0.90),
    ("电器", ["空调", "热水器", "冰箱", "洗衣机", "风扇", "电视", "微波炉", "饮水机",
             "吹风机", "不制冷", "不加热"], 0.90),
    ("门窗家具", ["门", "窗", "锁", "把手", "合页", "床", "桌", "椅", "柜", "抽屉",
                 "衣柜", "书架", "关不严", "关不上"], 0.88),
    ("网络", ["网", "wifi", "Wi-Fi", "网络", "网口", "路由器", "宽带", "断网", "没信号"], 0.86),
]

# 影响使用/影响上课的词：抬到较急
IMPACT = ["没法用", "不能用", "用不了", "影响上课", "影响考试", "洗不了澡", "没法洗澡",
          "出不了水", "没热水", "通宵", "熬夜", "急着"]

# 浸水风险：给排水 + 这些词 = 安全类
WATER_RISK = ["快接满", "接满了", "快满", "漫出来", "淹", "积水", "全是水", "泡水", "泡了", "溢水"]


@dataclass
class Verdict:
    category: str
    level: str
    confidence: float
    basis: str
    fallback: bool = False

    def to_dict(self) -> dict:
        return asdict(self)


def _hits(desc: str, words: list[str]) -> list[str]:
    return [w for w in words if w in desc]


def _raise_one(level: str) -> str:
    order = LEVELS
    i = order.index(level)
    return order[min(i + 1, len(order) - 1)]


def classify(desc: str, fallback_category: str | None = None) -> Verdict:
    """按关键词判类型与紧急程度。判不出来给保守档，绝不停单。"""
    text = (desc or "").strip()
    basis: list[str] = []

    if not text:
        cat = fallback_category or "给排水"
        return Verdict(cat, "较急", 0.0, "描述为空，按保守档处理（较急，人工确认）")

    # 1 安全类：先看整体描述，命中最严重的词直接定紧急
    safety = _hits(text, SAFETY)
    if safety:
        cat = _pick_category(text, fallback_category)
        return Verdict(cat, "紧急", 0.95,
                       f"命中安全类词「{'/'.join(safety)}」，有起火/触电风险，直接定紧急，不再往下判")

    # 2 定类型
    cat, conf, hits = _pick_category_with_basis(text, fallback_category)

    # 3 定紧急程度
    level = "普通"
    if cat == "给排水":
        risk = _hits(text, WATER_RISK)
        if risk:
            level = "紧急"
            basis.append(f"描述提到「{'/'.join(risk)}」，按浸水风险算安全类，直接定紧急")
            return Verdict(cat, level, max(conf, 0.94),
                           f"命中「{'/'.join(hits)}」，归{cat}；" + "；".join(basis))

    impact = _hits(text, IMPACT)
    if impact:
        level = "较急"
        basis.append(f"提到「{'/'.join(impact)}」，影响正常使用，抬一档到较急")
    else:
        basis.append("不影响基本生活，定普通")

    # 4 置信度低就保守升一档，但照样往下走
    if conf < 0.70:
        raised = _raise_one(level)
        basis.append(f"识别依据少、置信度 {conf:.2f} 偏低，保守升一档（{level}→{raised}）")
        level = raised

    head = f"命中「{'/'.join(hits)}」，归{cat}" if hits else "没识别到明确故障词"
    return Verdict(cat, level, round(conf, 2), head + "；" + "；".join(basis))


def _pick_category(text: str, fallback_category: str | None) -> str:
    return _pick_category_with_basis(text, fallback_category)[0]


def _pick_category_with_basis(text: str, fallback_category: str | None) -> tuple[str, float, list[str]]:
    best: tuple[str, float, list[str]] | None = None
    for cat, words, conf in RULES:
        hits = _hits(text, words)
        if not hits:
            continue
        # 命中词越多越可信，最多 +0.06
        score = min(0.99, conf + 0.02 * (len(hits) - 1))
        if best is None or score > best[1]:
            best = (cat, round(score, 2), hits)
    if best is not None:
        return best
    cat = fallback_category or "给排水"
    return cat, 0.55, []


def keyword_preview() -> dict:
    """给前端/文档用的词表预览，别让人去翻源码。"""
    return {
        "categories": CATEGORIES,
        "levels": LEVELS,
        "safety": SAFETY,
        "waterRisk": WATER_RISK,
        "impact": IMPACT,
        "rules": {cat: words for cat, words, _ in RULES},
    }
