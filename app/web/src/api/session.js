/**
 * 演示用的身份。正式版这里会换成登录拿 token（SYS-003 的 Bearer）。
 *
 * 现在后端读三个请求头：X-Actor-Type / X-Actor-Id / X-Actor-Name。
 * 中文姓名不能进请求头（只支持 ASCII），所以姓名由后端按 id 补默认值 —— 这里只传 type 和 id。
 */
const KEY = 'dorm.actor'
const SESSION_KEY = 'dorm.session'

/** 四类演示身份，跟 data.sql 里的账号对齐 */
export const ACTORS = {
  student: [
    { id: 'S001', name: '张同学', building: 'B7', room: '402' },
    { id: 'S002', name: '李同学', building: 'B7', room: '508' },
    { id: 'S003', name: '王同学', building: 'B8', room: '205' }
  ],
  worker: [
    { id: 'W0428', name: '李建国', team: '水电班', building: 'B7' },
    { id: 'W0512', name: '王海涛', team: '综合班', building: 'B8' }
  ],
  admin: [{ id: 'A001', name: '王主任' }],
  system: [{ id: 'system', name: '系统' }]
}

const DEFAULT = { student: 'S001', worker: 'W0428', admin: 'A001', system: 'system' }

function readAll () {
  try {
    return JSON.parse(localStorage.getItem(KEY) || '{}')
  } catch {
    return {}
  }
}

/** 当前某端用的身份 */
export function actorOf (type) {
  const all = readAll()
  const id = all[type] || DEFAULT[type]
  const found = (ACTORS[type] || []).find((a) => a.id === id) || (ACTORS[type] || [])[0]
  return { type, id: found.id, name: found.name, ...found }
}

/** 换人（演示用：学生换同学、师傅换班次、管理端换主任） */
export function setActor (type, id) {
  const all = readAll()
  all[type] = id
  localStorage.setItem(KEY, JSON.stringify(all))
  return actorOf(type)
}

export function saveSession (payload) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(payload))
  return payload
}

export function sessionOf () {
  try {
    return JSON.parse(localStorage.getItem(SESSION_KEY) || 'null')
  } catch {
    return null
  }
}

export function tokenOf () {
  return sessionOf()?.token || ''
}

export function userOf () {
  return sessionOf()?.user || null
}

export function clearSession () {
  localStorage.removeItem(SESSION_KEY)
}

/** 当前页面属于哪一端：靠路由前缀判，别在页面里写死 */
export function roleOfPath (path) {
  if (path.startsWith('/worker')) return 'worker'
  if (path.startsWith('/admin')) return 'admin'
  return 'student'
}
