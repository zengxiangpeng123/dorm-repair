# 宿舍维修系统（V5.0）

一个能自己跑完的宿舍报修闭环：学生拍照提交 → 系统定级派工 → 师傅进楼维修 → 学生验收 → 归档。
目标不是"做得像大厂系统"，而是**证明正常维修链不需要人催、返修必须人工审查、每步都留痕**。

---

## 现在到哪一步了（2026-11-18）

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M1 | Spring Boot 4 骨架 + 状态机内核 + 一条链 | ✅ JUnit 38 项 + M1 脚本验收 |
| M2 | MySQL 8 + MyBatis-Plus，迁移期 16 张表、最终 15 张表 | ✅ 角色分表、迁移与 16 项脚本验收 |
| M3 | Python AI 定级服务，1500ms 快速兜底，AI 挂了照样能建单 | ✅ 20 项脚本验收 + 22 项 pytest |
| M4 | Vue3 + Element Plus 前端 12 页（含登录与学生/管理返修审查） | ✅ 全部接真接口，15 张截图在 `app\screens\vue\` |
| M5 | Bearer Token、返修审查、迁移验收、测试报告、CG01–CG14 | ✅ 已完成并验证 |

**界面现在看哪儿**：
- 正式前端（三个独立 Vue 应用，接真接口）：学生 `http://localhost:5173/`、师傅 `http://localhost:5174/`、管理端 `http://localhost:5175/`；需要先起后端 8082。
- 旧共享前端 `app\web` 仅保留为兼容与原型参考，不再作为三端正式入口。
- 原型（零依赖假数据，双击就开）：`prototype\index.html` —— 视觉定稿的依据，留着当"改过不止一版"的证据。

`app\screens\vue\` 是 Vue 版的 15 张截图（1440 桌面宽 / 430 手机宽），覆盖登录、学生申请返修、管理端待审与审查弹层。`app\screens\` 里那 11 张是已退役的原生页面，只作视觉参照。

---

## 怎么跑起来

五个进程：AI（Python 8001）、后端（Java 8082）、学生端（5173）、师傅端（5174）、管理端（5175）；三个前端均将 `/api` 代理到 8082。

**Windows：双击 `start.bat`**，它会开两个窗口（AI + 后端）并打开 `/api/v1/health`。

手工等价：

```powershell
# 1 AI 定级服务
cd app\ai
D:\develop\python-envs\dorm-ai\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8001

# 2 后端（先建库，见下一节）
cd app\server
mvn -q -DskipTests package
java -jar target\dorm-repair-server-1.0.0.jar
```

| 想改什么 | 怎么改 |
|---|---|
| 端口 | `server.port`（后端 8082）、`--port`（AI 8001）、`PORT`（前端） |
| 数据库 | 环境变量 `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD`（默认 `localhost:3306/dorm_repair`，`root/123456`） |
| 存储换内存 | `APP_STORE=memory`（只用于跑单测与临时演示，重启就没了） |
| AI 不在本机 | `AI_SERVICE_URL`（默认 `http://127.0.0.1:8001`）、`AI_TIMEOUT_MS`（默认 1500）、`AI_REVIEW_TIMEOUT_MS`（默认 30000） |

### 建库（只需一次）

```powershell
$tmp = Join-Path $env:TEMP 'dormdb'; New-Item -ItemType Directory -Force $tmp | Out-Null
Copy-Item app\server\src\main\resources\db\schema.sql $tmp
Copy-Item app\server\src\main\resources\db\data.sql  $tmp
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -p123456 --default-character-set=utf8mb4 -e "source $tmp/schema.sql"
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -p123456 --default-character-set=utf8mb4 -e "source $tmp/data.sql"
```

> `source` 打不开中文路径，所以先复制到 `%TEMP%` 再 source。

重置演示数据：`POST /api/v1/demo/reset` → 清表 + 重灌 11 张样本单（含一条 `-R1`/`-R2` 返修链）。

---

### 前端（三个独立 Vue 应用）怎么起

分别开三个终端，首次各自执行 `npm install`：

```powershell
cd app\student-web; npm run dev   # http://localhost:5173
cd app\worker-web;  npm run dev   # http://localhost:5174
cd app\admin-web;   npm run dev   # http://localhost:5175
```

三端使用不同 session key，可同时登录；共享的只有 `app\web\packages\contract` 中的状态/事件展示契约。构建前把 `$env:TEMP` 指到各应用 `.tmp`，再执行 `npm run build`。三端均设置 `strictPort`，端口被占用会直接失败，不会静默换端口。


## 怎么证明它是对的

| 要证明的事 | 一条命令 | 期望 |
|---|---|---|
| 9 状态 14 转移，117 种非法组合全拒，矩阵 10×14=140 | `cd app\server; mvn -q test` | `Tests run: 38, Failures: 0` |
| 一条链真跑通（打真服务，不是 mock） | `pwsh -NoProfile -File app\server\tests\m1-chain.ps1` | `13 通过 / 0 失败` |
| 重启之后数据还在（真 MySQL） | `pwsh -NoProfile -File app\server\tests\m2-mysql.ps1` | `16 通过 / 0 失败` |
| AI 接上了；**杀掉 AI 之后建单仍成功**走兜底 | `pwsh -NoProfile -File app\server\tests\m3-ai.ps1` | `20 通过 / 0 失败` |
| 新审查链 R1/R2、反复返修例外、最终关闭整条链 | `pwsh -NoProfile -File app\server\tests\m4-rework.ps1` | `26 通过 / 0 失败` |
| 真 Bearer Token、三角色权限、system 令牌与越权 | `pwsh -NoProfile -File app\server\tests\m5-auth.ps1` | `36 通过 / 0 失败` |
| AI 规则、分类器与 LLM 兜底 | `cd app\ai; <venv>\python.exe -m pytest -q` | `22 passed` |
| 前端状态词、五步进度与倒计时 | `cd app\web; npm run test:unit` | `8 passed` |
| 原型状态机与后端一致（离线也算得对） | `node prototype/tests/selftest.js prototype` | `34 通过 / 0 失败` |

看接口而不是跑脚本：

- `GET /api/v1/state/matrix` —— 140 种组合哪些合法
- `GET /api/v1/ai/health` —— AI 在不在、多少毫秒
- `POST /api/v1/orders` + `POST /api/v1/orders/{no}/finish` —— 故意用错状态，看是不是 409 且 `allowed` 列出来

---

## 目录

```
宿舍维修系统\
├─ README.md                     本文件
├─ start.bat                     双击起 AI + 后端
├─ docs\                         文档（只放文档）
│  ├─ README.md                  文档索引：每份各管什么、冲突信谁
│  ├─ 01_需求\                   需求报告 / 闭环大纲 xmind / 口径统一说明
│  ├─ 02_流程与状态\             流程图、状态机图（html 看细节，png 贴 PPT）
│  ├─ 03_系统设计\               SYS-003 接口契约（语言中立，Java 照它实现）
│  ├─ 04_项目管理\               流程计划 / 原型评审 / 代码实施计划 / 答辩脚本
│  └─ 99_存档\                   上一版原件 + 旧 Node 实现 zip（只读）
├─ prototype\                    ★ 原型（离线可跑，假数据，只当视觉与交互标准）
│  ├─ index.html                 三端入口（桌面页）
│  ├─ lab.html                   状态机自检台：140 种组合自己点
│  ├─ student\ worker\ admin\    11 页
│  └─ reference\stitch-12屏\     原始 AI 生成稿（只读，别改）
├─ app\                          ★ 正式项目
│  ├─ server\                    Spring Boot 4 + MyBatis-Plus + MySQL
│  ├─ ai\                        Python + FastAPI 定级服务
│  ├─ student-web\               学生独立应用 :5173
│  ├─ worker-web\                师傅独立应用 :5174
│  ├─ admin-web\                 管理端独立应用 :5175
│  ├─ web\packages\contract\     只读共享契约
│  └─ screens\                   上一版界面截图（只作参照）；vue\ 是上一版共享界面
└─ ..\rules\                     AI 开发规则 7 篇（技术栈 / 目录 / 口径 / 代码 / 测试 / 协作）
```

---

## 三端各跑在哪（别搞错）

| 端点 | 设备 | 谁用 | 在哪儿用 |
|---|---|---|---|
| 学生端 | **手机**（430 宽竖屏） | 报修的学生 | 宿舍里、走廊上，扫码就进 |
| 师傅端 | **手机**（430 宽竖屏） | 跑楼的维修师傅 | 站着、走着、在门口，一只手操作 |
| 管理端 | **电脑**（桌面 1440） | 后勤维修部的督察/主任 | 办公室，看台账和例外 |

学生端和师傅端固定 430 宽、底部浮动导航、按钮大一号；管理端是左侧深色导航 + 密集表格 + 鼠标 hover，**不要做成手机壳**。

---

## 三条铁律

1. **`prototype/` 只读物证。** 原型零依赖可双击跑，是"改过不止一版"的证据；新功能只进 `app/`，不许把原型的逻辑复制过去。
2. **原型用假数据，`app/` 用真数据库，两者不混。**
3. **文档只放 `docs/`，代码只放 `prototype/` 和 `app/`。** 交作业时能单独打包。

---

## 口径标准

工单怎么流转，冲突时按这个顺序取：

1. `SYS-004 状态机设计`（课程目录里的外部标准，**9 状态 / 13 转移**）
2. [闭环大纲 xmind](docs/01_需求/宿舍维修%20·%20项目完整闭环-V5.0.xmind)（业务闭环）
3. [状态机图](docs/02_流程与状态/工单状态机图-V5.0.html) / [流程图](docs/02_流程与状态/宿舍维修流程图-V5.0.html)（图化，可能有笔误，不是源头）

代码里对这张表的唯一实现是 `app/server/.../statemachine/TransitionTable.java`；
原型 `prototype/assets/app.js` 有一份等价实现，只用于离线演示。
`status` 只能由 `OrderStateService.transit()` 改，service 里不许出现 `setStatus(...)`。

---

## 这套东西能证明什么 / 不能证明什么

能证明（测试 + 手点都验过）：

- 一条链自己走完：提交 → 派工 → 到门打卡 → 完工 → 验收 → 归档；
- 非法转移**当场**被拒（409），且**被拒后状态一个字没变**（`updateCas` 受影响行数≠1 → 409，测试里逐条断言）；
- 版本冲突（两个人同时点）→ `409 VERSION_CONFLICT`「状态已变化，请刷新」；
- AI 挂了不影响学生报修：1500ms 超时 → 兜底档 + `levelSource=FALLBACK`，且兜底也认安全类词；
- 数据真落 MySQL：杀进程重启，单子、事件、照片都还在（`m2-mysql.ps1` 第 11-13 项）；
- 例外清单只出约定那四类；超预算那笔账**花过就留着**，配件到货后仍在清单里直到归档。

仍不能证明（诚实写在前面）：

- 真实并发压力：只覆盖单请求级乐观锁，没做压测；
- 真模型：已接入 `raccoon/sn-deepseek-v4-1-flash-1017`，模型限流/超时/非法输出时自动退回规则；
- 真实上传：照片是 `demo://` 占位串，没接对象存储；
- 多楼栋组织树：样本只有 B7/B8 两栋；
- 界面：学生、师傅、管理端已拆成三个独立 Vue 应用，各自拥有入口、路由、会话、API 与构建；旧 `app\web` 不再作为正式入口。
- 照片仍是占位地址，真实上传、对象存储、部署监控与生产压测尚未完成，不能据此宣称生产就绪。

---

## 三处与 SYS-004 的差异（都写在代码注释里）

- **G-01** 返修单验收通过时原单 `返修中 → 已关闭`，14 条转移里没有这条边 → 按**级联副作用**处理（事件 `ORIGIN_CLOSED`，不占名额）。M1 还没实现（返修单生成 `-R1` 也还欠着）。
- **G-02** 师傅进楼后学生申请撤销、管理员批准作废，需要 `维修中 → 已取消`，而这条在禁止清单里 → 需**变更单 #001**；未批准前 `POST /orders/{no}/admin-cancel` 一律 `501`（`changeRequest: CR-001`）。
- **G-03** 零人工完成率有两套口径：宽松（这张单没有人类"推进状态"的事件）/ 严格（全部事件 `manual=0`，结构性恒 0）。待评审定稿。
