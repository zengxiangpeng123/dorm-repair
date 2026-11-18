export const STATUS = {
  PENDING: '待派工', ASSIGNED: '已派工', REPAIRING: '维修中', PARTS: '待配件',
  VERIFYING: '待验收', REWORKING: '返修中', DONE: '已完成', CLOSED: '已关闭', CANCELLED: '已取消'
}

export const ACTIVE_STATUS = ['PENDING', 'ASSIGNED', 'REPAIRING', 'PARTS', 'VERIFYING', 'REWORKING']
export const WALK_STATUS = ['PENDING', 'ASSIGNED', 'REPAIRING', 'PARTS']

export const EVENT = {
  SUBMIT: '提交报修', ASSIGN: '派工', ARRIVE: '到门打卡', NO_SHOW: '到访未遇',
  FINISH: '完工提交', NEED_PARTS: '登记缺件', PARTS_ARRIVED: '配件到货',
  ACCEPT: '验收通过', REJECT: '验收不通过', AUTO_ACCEPT: '超时自动验收',
  CANCEL: '学生撤销', CANCEL_REQUEST: '申请撤销', ORIGIN_CLOSED: '原单关闭（级联）',
  REWORK_REQUEST: '申请返修', REWORK_REJECTED: '驳回返修申请'
}

export function shortWindow (w) {
  return String(w || '').split(/[（(]/)[0].trim()
}

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

export function describeError (e) {
  const d = e.detail || {}
  if (e.status === 409) {
    if (d.reason === 'VERSION_CONFLICT') return '这条单刚被别人动过，刷新一下再试'
    const allowed = (d.allowed || []).join(' / ')
    const now = d.from ? `这单现在是「${STATUS[d.from] || d.from}」` : ''
    return [now, allowed ? `只能做：${allowed}` : '', d.message || ''].filter(Boolean).join('，')
  }
  if (e.status === 422) return `${e.message}${d.reason ? `（${d.reason}）` : ''}`
  if (e.status === 403) return '这个身份不能做这件事'
  if (e.status === 404) return '找不到这张单'
  return e.message || '出错了'
}
