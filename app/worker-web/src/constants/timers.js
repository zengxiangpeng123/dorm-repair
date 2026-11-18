/**
 * 两颗计时器：T1 从 finishAt 起算 verifyHours 小时，T2 从 signAt 起算 archiveDays 天。
 *
 * 服务端也会在工单查询里给一份 timers，这里再算一遍是为了
 * 详情页能秒级刷新——只要起始时间对，两边数字一定一致（同一个公式，同一个阈值）。
 */
export function parseAt (s) {
  if (!s) return null
  const m = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?$/.exec(String(s).trim())
  if (!m) return null
  return new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +(m[6] || 0)).getTime()
}

export function timersOf (o, meta) {
  const out = { t1: null, t2: null, stayedMs: null }
  if (!o) return out
  const now = Date.now()
  if (o.status === 'VERIFYING' && o.finishAt) {
    const from = parseAt(o.finishAt)
    const total = (meta?.verifyHours ?? 168) * 3600 * 1000
    if (from) out.t1 = { from: o.finishAt, deadline: from + total, leftMs: from + total - now, total, label: '验收倒计时' }
  }
  if (o.status === 'DONE' && o.signAt) {
    const from = parseAt(o.signAt)
    const total = (meta?.archiveDays ?? 7) * 24 * 3600 * 1000
    if (from) out.t2 = { from: o.signAt, deadline: from + total, leftMs: from + total - now, total, label: '归档倒计时' }
  }
  const base = parseAt(o.statusAt || o.createdAt)
  if (base) out.stayedMs = now - base
  return out
}

export function toneOf (leftMs, total) {
  if (leftMs === null || leftMs === undefined) return ''
  if (leftMs <= 0) return 'bad'
  if (total && leftMs < total * 0.25) return 'warn'
  return ''
}
