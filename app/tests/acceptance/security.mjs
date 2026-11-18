#!/usr/bin/env node
import fs from 'node:fs'
import path from 'node:path'
const base=(process.env.ACCEPTANCE_BASE_URL||'http://127.0.0.1:8082/api/v1').replace(/\/$/,'')
const report={startedAt:new Date().toISOString(),checks:[],passed:0,failed:0}
function check(name,ok,detail=''){report.checks.push({name,ok,detail});if(ok)report.passed++;else report.failed++;console.log((ok?'PASS ':'FAIL ')+name+(detail?' - '+detail:''))}
async function req(method,url,{token,body,headers={},expected=[200]}={}){const h={...headers};if(token)h.Authorization='Bearer '+token;if(body!==undefined)h['Content-Type']=h['Content-Type']||'application/json';const r=await fetch(url,{method,headers:h,body:body===undefined?undefined:(Buffer.isBuffer(body)?body:JSON.stringify(body))});const text=await r.text();let json;try{json=text?JSON.parse(text):null}catch{json=null}if(!expected.includes(r.status))throw new Error(method+' '+url+' expected '+expected+' got '+r.status+': '+text.slice(0,200));return{response:r,json,text}}
async function login(account,password){const r=await req('POST',base+'/auth/login',{body:{account,password}});return r.json.data.token}
const student=await login('S001','dorm@2026'),admin=await login('A001','dorm@2026'),worker=await login('W0428','dorm@2026')
let r=await req('GET',base+'/orders',{expected:[401]});check('未登录访问被拒绝',r.response.status===401)
r=await req('GET',base+'/orders',{token:student,expected:[403]});check('学生不能读取全员工单',r.response.status===403)
r=await req('GET',base+'/admin/ai-reviews',{token:worker,expected:[403]});check('师傅不能读取AI审计',r.response.status===403)
r=await req('POST',base+'/orders/NOT-EXIST/reject',{token:student,body:{version:1,reason:'test'},expected:[410,404]});check('旧 reject 旁路关闭',r.response.status===410,JSON.stringify(r.json))
const bytes=fs.readFileSync(path.resolve('app/screens/vue/student-home.png'));const boundary='----security'+Date.now();const head=Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="fake.txt"\r\nContent-Type: text/plain\r\n\r\n`);const tail=Buffer.from(`\r\n--${boundary}--\r\n`);r=await req('POST',base+'/files?purpose=BEFORE',{token:student,body:Buffer.concat([head,bytes,tail]),headers:{'Content-Type':'multipart/form-data; boundary='+boundary},expected:[400,415,422]});check('上传类型不匹配被拒绝',r.response.status>=400,JSON.stringify(r.json))
const health=await fetch(base+'/health');check('健康端点无敏感细节',health.ok && !(await health.text()).includes('password'))
report.finishedAt=new Date().toISOString();report.ok=report.failed===0;const out=path.resolve(process.env.SECURITY_REPORT||'app/tests/acceptance/security-report.json');fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(report,null,2));console.log('SECURITY '+(report.ok?'PASS':'FAIL')+' '+report.passed+'/'+report.checks.length);if(!report.ok)process.exitCode=1
