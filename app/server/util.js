'use strict';
/** 时间与单号：服务端时区固定 Asia/Shanghai，客户端传来的时间一律忽略 */

const TZ_OFFSET = 8 * 3600 * 1000; // 东八区，无夏令时
const HOUR = 3600 * 1000;
const DAY = 24 * HOUR;

function pad(n, w = 2) { return String(n).padStart(w, '0'); }

/** ms → 'yyyy-MM-dd HH:mm:ss' */
function fmt(ms = Date.now()) {
  const d = new Date(ms + TZ_OFFSET);
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())} ` +
         `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())}`;
}

/** ms → 'yyyyMMdd' */
function fmtDay(ms = Date.now()) {
  const d = new Date(ms + TZ_OFFSET);
  return `${d.getUTCFullYear()}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}`;
}

/** ms → 'yyyy-MM-dd' */
function fmtDate(ms = Date.now()) {
  return fmt(ms).slice(0, 10);
}

/** 'yyyy-MM-dd HH:mm:ss' → ms；解析不了返回 null */
function parse(s) {
  if (!s) return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2})(?::(\d{2}))?)?$/.exec(String(s).trim());
  if (!m) return null;
  const [, y, mo, d, h = '0', mi = '0', se = '0'] = m;
  return Date.UTC(+y, +mo - 1, +d, +h, +mi, +se) - TZ_OFFSET;
}

/** 毫秒 → 人话，给前端倒计时兜底用 */
function human(ms) {
  if (ms == null) return '';
  if (ms <= 0) return '已超时';
  const d = Math.floor(ms / DAY);
  const h = Math.floor((ms % DAY) / HOUR);
  const m = Math.floor((ms % HOUR) / 60000);
  if (d > 0) return `${d} 天 ${h} 小时`;
  if (h > 0) return `${h} 小时 ${m} 分`;
  return `${m} 分`;
}

/** 单号：BX + 8 位日期 + 3 位当日序号 */
function makeNo(day, n) {
  return `BX${day}${pad(n, 3)}`;
}

/** 返修单号：原单 + -R{n} */
function makeReworkNo(baseNo, index) {
  return `${baseNo}-R${index}`;
}

/** 一串 JSON 存进 TEXT 列；读时安全解析 */
function toJson(v) { return v == null ? null : JSON.stringify(v); }
function fromJson(s, fallback) {
  if (s == null || s === '') return fallback;
  try { return JSON.parse(s); } catch (e) { return fallback; }
}

module.exports = {
  TZ_OFFSET, HOUR, DAY, fmt, fmtDay, fmtDate, parse, human, makeNo, makeReworkNo, toJson, fromJson, pad
};
