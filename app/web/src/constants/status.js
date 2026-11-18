/** 9 个状态与 12 个事件的中文名。页面里不许自己写状态中文，只能从这里取。 */
export const STATUS = {
  PENDING: '待派工',
  ASSIGNED: '已派工',
  REPAIRING: '维修中',
  PARTS: '待配件',
  VERIFYING: '待验收',
  REWORKING: '返修中',
  DONE: '已完成',
  CLOSED: '已关闭',
  CANCELLED: '已取消'
}

export const STATUS_ORDER = Object.keys(STATUS)

export const EVENT = {
  SUBMIT: '提交报修',
  ASSIGN: '派工',
  ARRIVE: '到门打卡',
  NO_SHOW: '到访未遇',
  FINISH: '完工提交',
  NEED_PARTS: '登记缺件',
  PARTS_ARRIVED: '配件到货',
  ACCEPT: '验收通过',
  REJECT: '验收不通过',
  AUTO_ACCEPT: '超时自动验收',
  CANCEL: '学生撤销',
  CANCEL_REQUEST: '申请撤销',
  ORIGIN_CLOSED: '原单关闭（级联）'
}

/** 谁在跑这单（不参与状态机，只用于界面分组） */
export const ACTIVE_STATUS = ['PENDING', 'ASSIGNED', 'REPAIRING', 'PARTS', 'VERIFYING', 'REWORKING']

/** 师傅要上楼的状态。验收及以后不用上去 —— 这条在原型里是踩过的坑，别再把验收单塞进上楼清单 */
export const WALK_STATUS = ['PENDING', 'ASSIGNED', 'REPAIRING', 'PARTS']

/** 学生端的五步进度。只借用状态，不新增词。 */
export function steps (o) {
  const i = { PENDING: 1, ASSIGNED: 2, REPAIRING: 3, PARTS: 3, VERIFYING: 4, REWORKING: 3, DONE: 5, CLOSED: 5, CANCELLED: 0 }[o.status] ?? 0
  const labels = ['提交报修', '派工', '上门维修', '验收', '完成']
  return labels.map((label, idx) => ({
    label,
    state: o.status === 'CANCELLED' ? 'pending' : idx + 1 < i ? 'done' : idx + 1 === i ? 'now' : 'pending'
  }))
}

export function shortWindow (w) {
  return String(w || '').split(/[（(]/)[0].trim()
}

/** 还剩多久：6d14h / 4h12m / 已超时 */
export function shortLeft (ms) {
  if (ms === null || ms === undefined) return ''
  if (ms <= 0) return '已超时'
  const m = Math.floor(ms / 60000)
  const d = Math.floor(m / 1440)
  const h = Math.floor((m - d * 1440) / 60)
  const mm = m - d * 1440 - h * 60
  if (d > 0) return `${d}d${h}h`
  if (h > 0) return `${h}h${mm}m`
  return `${mm}m`
}

export function human (ms) {
  if (ms === null || ms === undefined) return ''
  if (ms <= 0) return '已超时'
  const m = Math.round(ms / 60000)
  if (m < 60) return `${m} 分钟`
  const h = Math.floor(m / 60)
  const rest = m % 60
  if (h < 24) return rest ? `${h} 小时 ${rest} 分` : `${h} 小时`
  const d = Math.floor(h / 24)
  return `${d} 天 ${h - d * 24} 小时`
}

/** 计时器：t1 = 验收倒计时（起点 finishAt），t2 = 归档倒计时（起点 signAt）。接口里叫"验收超时"。 */
export function timerLabel (t) {
  if (!t) return ''
  if (t.label === '验收超时' || t.kind === 'T1') return '验收倒计时'
  if (t.label === '归档倒计时' || t.kind === 'T2') return '归档倒计时'
  return t.label || ''
}
