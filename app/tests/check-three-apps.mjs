import { readdirSync, readFileSync, statSync } from 'node:fs'
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
if (failed) process.exit(1)
