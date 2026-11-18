import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const appRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const roots = {
  'student-web': join(appRoot, 'student-web', 'src'),
  'worker-web': join(appRoot, 'worker-web', 'src'),
  'admin-web': join(appRoot, 'admin-web', 'src')
}
const forbidden = {
  'student-web': ['/worker/', '/admin/', 'X-Actor', "role: 'system'", 'studentApi.assign', '/demo/', 'advance(', 'resetDemo', 'parts-arrived'],
  'worker-web': ['/student/', '/admin/', 'X-Actor', 'workerApi.assign', '/demo/', 'parts-arrived', "role: 'admin'"],
  'admin-web': ['/student/', '/worker/', 'X-Actor', 'adminApi.assign', '/demo/', 'advance(', 'resetDemo']
}
let failed = false
for (const [app, root] of Object.entries(roots)) {
  const files = []
  const walk = (d) => {
    for (const e of readdirSync(d)) {
      const p = join(d, e)
      if (statSync(p).isDirectory()) walk(p)
      else if (/\.(js|vue)$/.test(e)) files.push(p)
    }
  }
  walk(root)
  const hits = []
  for (const p of files) {
    const s = readFileSync(p, 'utf8')
    for (const token of forbidden[app]) {
      if (s.includes(token)) hits.push(relative(root, p) + ' -> ' + token)
    }
  }
  console.log(app, hits.length ? 'FAIL' : 'OK', hits.join('; '))
  failed ||= hits.length > 0
}
const builtApps = {
  'student-web': { session: 'dorm.student.session', route: '/student/' },
  'worker-web': { session: 'dorm.worker.session', route: '/worker/' },
  'admin-web': { session: 'dorm.admin.session', route: '/admin/' }
}
const allSessions = Object.values(builtApps).map((v) => v.session)
for (const [app, own] of Object.entries(builtApps)) {
  const dist = join(appRoot, app, 'dist')
  const index = join(dist, 'index.html')
  if (!existsSync(index)) {
    console.log(app + ' DIST FAIL -> dist/index.html missing')
    failed = true
    continue
  }
  const html = readFileSync(index, 'utf8')
  const refs = [...html.matchAll(/(?:src|href)="([^"]+)"/g)]
    .map((m) => m[1])
    .filter((url) => url.startsWith('/') || url.startsWith('./'))
  const badBase = refs.filter((url) => url.startsWith('/') && !url.startsWith(own.route))
  const missing = refs.filter((url) => {
    const local = url.startsWith(own.route) ? url.slice(own.route.length) : url.replace(/^\.?\//, '')
    return !existsSync(join(dist, local.split('?')[0]))
  })
  const mainRef = html.match(/src="([^"]+\.js)"/)?.[1]
  const mainLocal = mainRef ? (mainRef.startsWith(own.route) ? mainRef.slice(own.route.length) : mainRef.replace(/^\.?\//, '')) : null
  const mainFile = mainLocal ? join(dist, mainLocal) : null
  const mainText = mainFile && existsSync(mainFile) ? readFileSync(mainFile, 'utf8') : ''
  const wrongSessions = allSessions.filter((key) => key !== own.session && mainText.includes(key))
  const crossRoutes = ['/student/', '/worker/', '/admin/']
    .filter((prefix) => prefix !== own.route && mainText.includes(prefix))
  if (!mainText || badBase.length || missing.length || wrongSessions.length || crossRoutes.length) {
    console.log(app + ' DIST FAIL',
      'main=' + (mainText ? 'OK' : 'MISSING') + ';',
      'badBase=' + badBase.join(',') + ';',
      'missing=' + missing.join(',') + ';',
      'wrongSessions=' + wrongSessions.join(',') + ';',
      'crossRoutes=' + crossRoutes.join(','))
    failed = true
  } else {
    console.log(app + ' DIST OK')
  }
}
if (failed) process.exit(1)