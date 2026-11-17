'use strict';
/**
 * 演示用"AI 定级"：五类故障 × 三档紧急。
 *
 * 这不是模型，是一张规则表 —— 但规则本身就是需求：
 *   1. 命中安全类词 → 直接定「紧急」，不再往下判（漏水的单不能被判成"普通"）
 *   2. 其余按类目关键词判类型，再按影响面判级别
 *   3. 一个都没命中 → 置信度低于 0.7 → 保守升一档，并写进依据（人看得见为什么）
 *   4. 任何情况下都不拒单（SYS-003 3.1）
 */

const CATEGORIES = ['给排水', '电气', '电器', '门窗家具', '网络'];
const LEVELS = ['普通', '较急', '紧急'];

/** 顺序即优先级：安全类在前 */
const RULES = [
  {
    category: '给排水',
    safety: ['爆管', '喷水', '漏水到楼下', '水漫', '快接满', '接满了', '快满', '漫出来', '淹', '漏电',
             '积水', '全是水', '泡水', '泡了', '溢水'],
    words: ['漏水', '滴水', '堵', '下水', '反味', '水龙头', '软管', '马桶', '洗手盆', '地漏', '热水器', '水管', '水压']
  },
  {
    category: '电气',
    safety: ['冒烟', '起火', '烧焦', '跳闸', '电火花', '麻手', '裸露'],
    words: ['灯', '插座', '开关', '断电', '没电', '跳闸', '电表', '线路', '灯管', '空开', '漏保']
  },
  {
    category: '电器',
    safety: ['冒烟', '起火', '烧焦', '烫手'],
    words: ['空调', '不制冷', '洗衣机', '冰箱', '热水器', '风扇', '微波炉', '饮水机', '遥控']
  },
  {
    category: '门窗家具',
    safety: ['玻璃裂', '掉下来', '门锁坏关不上', '阳台门脱落'],
    words: ['门', '窗', '锁', '合页', '床', '柜', '桌椅', '抽屉', '晾衣杆', '纱窗', '窗帘']
  },
  {
    category: '网络',
    safety: [],
    words: ['网', '网口', '路由器', '断网', 'wifi', 'Wi-Fi', 'WIFI', '网线', '信号', '没反应']
  }
];

/** 影响面：这些词说明基本生活受影响，抬到「较急」 */
const IMPACT_WORDS = ['没法', '不能用', '用不了', '影响', '上课', '作业', '复习', '考试', '晚上', '冷', '热', '臭', '吵', '着急', '快'];

function countHits(text, words) {
  let n = 0;
  const hit = [];
  for (const w of words) {
    if (text.includes(w)) { n++; hit.push(w); }
  }
  return { n, hit };
}

function upLevel(level) {
  const i = LEVELS.indexOf(level);
  return LEVELS[Math.min(i + 1, LEVELS.length - 1)];
}

/**
 * @param {string} desc 学生描述
 * @param {string} [fallbackCategory] 兜底类目（学生没写时用）
 * @returns {{category, level, confidence, basis, fallback}}
 */
function classify(desc, fallbackCategory) {
  const text = String(desc || '');
  if (text.trim().length < 2) {
    return {
      category: fallbackCategory || '门窗家具',
      level: '普通',
      confidence: 0.4,
      basis: '描述太短，无法判断，按普通入队（不拒单）',
      fallback: true
    };
  }

  let best = { category: null, score: 0, hit: [], safety: [] };
  for (const rule of RULES) {
    const safety = countHits(text, rule.safety);
    const normal = countHits(text, rule.words);
    const score = safety.n * 10 + normal.n;
    if (score > best.score) {
      best = { category: rule.category, score, hit: normal.hit, safety: safety.hit };
    }
  }

  if (!best.category) {
    const cat = fallbackCategory || '门窗家具';
    return {
      category: cat,
      level: '较急',
      confidence: 0.55,
      basis: `没命中任何关键词，归「${cat}」；置信度低，保守升一档定「较急」`,
      fallback: true
    };
  }

  const cat = best.category;
  if (best.safety.length) {
    return {
      category: cat,
      level: '紧急',
      confidence: 0.94,
      basis: `命中「${best.hit.concat(best.safety).join('/')}」，归${cat}；提到「${best.safety[0]}」，按安全类直接定紧急，不再往下判`,
      fallback: false
    };
  }

  let level = '普通';
  const impact = countHits(text, IMPACT_WORDS);
  if (impact.n > 0) level = '较急';

  const hits = best.hit.join('/');
  let basis = `命中「${hits}」，归${cat}；`;
  if (level === '较急') basis += `提到「${impact.hit[0]}」，影响基本使用，定较急`;
  else basis += '不影响基本生活，定普通';

  let confidence = Math.min(0.72 + best.score * 0.04, 0.96);
  if (confidence < 0.7) {
    const before = level;
    level = upLevel(level);
    basis += `；置信度 ${confidence.toFixed(2)} 偏低，保守升一档（${before}→${level}）`;
  }

  return { category: cat, level, confidence: +confidence.toFixed(2), basis, fallback: false };
}

module.exports = { classify, CATEGORIES, LEVELS, RULES };
