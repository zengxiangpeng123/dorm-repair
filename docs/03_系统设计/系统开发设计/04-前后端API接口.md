# 前后端 API 接口

| 项 | 内容 |
|---|---|
| 文档编号 | DEV-04 |
| 版本 | V1.3 |
| 状态 | ✅ 已实现并完成联调、鉴权与回归 |
| 更新日期 | 2026-11-18 |
| 基础路径 | `/api/v1` |
| 服务地址 | `http://localhost:8082` |
| 上游依据 | `SYS-003_接口契约.md`、`SYS-005_系统设计说明书.md`（V1.2 审查轮次基线） |

> 本文件是前后端字段的唯一开发契约。字段名按 Java JSON 实际输出使用 `camelCase`，不得由前端自行改名或造字段。`reworkReview`、申请接口和管理员审查接口均已实现。

## 一、通用规则

- JSON 使用 UTF-8；所有日期时间均为字符串 `yyyy-MM-dd HH:mm:ss`，日期使用 `yyyy-MM-dd`。
- 服务端时区固定 `Asia/Shanghai`；客户端不得传业务时间。
- 正式鉴权：`Authorization: Bearer <token>`。
- 演示鉴权：`X-Actor-Type`、`X-Actor-Id`；仅测试/演示使用。
- Jackson 配置为 `NON_NULL`，**值为 null 的响应字段可能直接缺失**；前端读取时应按 `field ?? null` 处理。
- 写接口请求中的 `version` 目标上必须传；当前 DTO 允许省略，但省略会跳过版本校验，只适用于演示。
- 金额用 JSON number，时间、编号、状态、角色用 string。数组字段永远按数组处理，不用逗号分隔字符串。

### 1.1 枚举值

| 类型 | 允许值 |
|---|---|
| 角色 `actorType` | `STUDENT`、`WORKER`、`ADMIN`、`SYSTEM` |
| 工单状态 `status` | `PENDING`、`ASSIGNED`、`REPAIRING`、`PARTS`、`VERIFYING`、`REWORKING`、`DONE`、`CLOSED`、`CANCELLED` |
| 状态事件 `event` | `SUBMIT`、`ASSIGN`、`ARRIVE`、`NO_SHOW`、`FINISH`、`NEED_PARTS`、`PARTS_ARRIVED`、`ACCEPT`、`REJECT`、`AUTO_ACCEPT`、`ARCHIVE`、`ADMIN_CANCEL`、`CANCEL_REQUEST`、`ORIGIN_CLOSED` |
| 审计事件 | `REWORK_REQUEST`、`REWORK_REJECTED`；审查通过沿用 `REJECT` |
| 故障类型 `category` | `给排水`、`电气`、`电器`、`门窗家具`、`网络` |
| 紧急程度 `level` | `紧急`、`较急`、`普通` |
| 级别来源 `levelSource` | `AI`、`FALLBACK`、`MANUAL`、`REWORK` |
| 例外类型 `kind` | `MISMATCH`、`OVER_BUDGET`、`REPEAT_REWORK`、`CANCEL_REQUEST` |
| 返修审查决定 `decision` | `APPROVE`、`REJECT` |

## 二、统一响应

### 2.1 成功响应

```json
{ "ok": true, "data": { } }
```

### 2.2 失败响应

```json
{
  "ok": false,
  "error": {
    "code": "STATE_CONFLICT",
    "message": "状态「待派工」不接受事件「完工提交」",
    "reason": "NOT_ALLOWED",
    "from": "PENDING",
    "event": "FINISH",
    "allowed": ["ASSIGN", "CANCEL"]
  }
}
```

`error.code` 是**字符串业务码**，不是 HTTP 数字。`allowed` 是当前状态可执行的事件代码数组。

## 三、共享数据结构

### 3.1 `OrderView`

工单详情、命令成功结果和多数列表项都使用该对象。

| 字段 | 类型 | 可空 | 说明 |
|---|---|---:|---|
| `no` | string | 否 | 工单号，返修单带 `-R{n}` |
| `status` | enum string | 否 | 当前状态代码 |
| `statusLabel` | string | 否 | 当前状态中文名 |
| `version` | integer | 否 | 乐观锁版本 |
| `building` | string | 否 | 楼栋代码，如 `B7` |
| `floor` | integer | 否 | 楼层 |
| `room` | string | 否 | 房间号 |
| `location` | string | 否 | 展示用位置，如 `B7 4F-402` |
| `studentName` | string | 否 | 学生姓名快照 |
| `desc` | string | 否 | 故障描述 |
| `window` | string | 否 | 可上门时段 |
| `photosBefore` | string[] | 否 | 现场照片受权读取 URL；旧演示数据可为 demo 协议 |
| `photosAfter` | string[] | 否 | 完工照片受权读取 URL，未完工时为空数组 |
| `category` | string | 否 | 故障类型 |
| `level` | string | 否 | 紧急程度 |
| `levelSource` | string | 否 | AI 结果来源 |
| `aiBasis` | string | 是 | AI/兜底判断依据 |
| `workerId` | string | 是 | 当前师傅工号 |
| `workerName` | string | 是 | 当前师傅姓名 |
| `partsName` | string | 是 | 缺件名称 |
| `partsAmount` | number | 是 | 缺件金额，两位小数语义 |
| `partsEta` | string | 是 | 预计到货日 `yyyy-MM-dd` |
| `createdAt` | string | 否 | 建单时间 |
| `assignedAt` | string | 是 | 派工时间 |
| `arriveAt` | string | 是 | 到门打卡时间 |
| `finishAt` | string | 是 | 完工时间，T1 起点 |
| `signAt` | string | 是 | 验收时间，T2 起点 |
| `closedAt` | string | 是 | 归档时间 |
| `cancelledAt` | string | 是 | 取消时间 |
| `statusAt` | string | 是 | 进入当前状态的时间 |
| `reworkOf` | string | 是 | 原单号 |
| `reworkIndex` | integer | 是 | 第几次返修，从 1 开始 |
| `reworkCount` | integer | 否 | 原单累计返修次数 |
| `allowed` | string[] | 否 | 当前状态允许的事件代码 |
| `events` | `OrderEvent[]` | 否 | 完整时间线 |
| `cancelRequest` | `CancelRequest` | 是 | 最近一次撤销申请 |
| `reworkReview` | `ReworkReviewView` | 是 | 待审/已审返修信息 |

### 3.2 `OrderEvent`

| 字段 | 类型 | 可空 | 说明 |
|---|---|---:|---|
| `seq` | integer | 否 | 事件自增序号 |
| `at` | string | 否 | 事件时间 |
| `event` | string | 否 | 事件代码 |
| `actorType` | string | 否 | 操作人角色 |
| `actorId` | string | 否 | 操作人账号 |
| `actorName` | string | 否 | 操作人姓名 |
| `manual` | integer | 否 | 系统 0，人工 1 |
| `note` | string | 是 | 业务说明、原因或判断依据 |

### 3.3 `CancelRequest`

| 字段 | 类型 | 可空 | 说明 |
|---|---|---:|---|
| `at` | string | 否 | 申请时间 |
| `by` | string | 否 | 申请人姓名 |
| `reason` | string | 否 | 撤销原因 |

### 3.4 `ReworkReviewView`

| 字段 | 类型 | 可空 | 说明 |
|---|---|---:|---|
| `attemptNo` | integer | 否 | 同一原单的申请轮次，从 1 开始 |
| `status` | string | 否 | `PENDING` / `APPROVED` / `REJECTED` |
| `reworkIndex` | integer | 是 | 批准后第几次返修，申请时为 null |
| `requestedAt` | string | 否 | 学生申请时间 |
| `requestedBy` | string | 否 | 学生姓名 |
| `reason` | string | 否 | 学生返修原因 |
| `decidedAt` | string | 是 | 管理员决定时间 |
| `decidedBy` | string | 是 | 管理员姓名 |
| `decisionReason` | string | 是 | 审查意见 |

### 3.5 Timer 与列表包装

`Timer`：`from` string、`deadline` string、`leftMs` integer、`total` integer、`label` string。

列表统一为 `{ "items": [], "total": 0 }`；`GET /my/orders`、`GET /orders` 和事件列表均使用该结构。

## 三、写接口幂等

所有需要防重复的 `POST/PUT/PATCH/DELETE` 请求应携带：

```http
Idempotency-Key: <客户端生成的唯一字符串，建议 UUID>
```

- 唯一范围：当前身份 + HTTP 方法 + 路径 + key。
- 相同 key、相同请求体：处理中返回 `409 IDEMPOTENCY_IN_PROGRESS`，完成后返回首次成功响应，不重复执行。
- 相同 key、不同请求体：返回 `409 IDEMPOTENCY_KEY_REUSED`。
- 只缓存成功响应；校验失败、权限失败和业务冲突允许修正后用同一 key 重试。
- 上传接口不进入该缓存，文件去重另行处理。

## 四、请求 DTO

### 4.1 `SubmitRequest`

| 字段 | 类型 | 必填 | 当前行为/约束 |
|---|---|---:|---|
| `building` | string | 建议 | 为空默认 `B7` |
| `floor` | integer | 建议 | 为空默认 `4` |
| `room` | string | 建议 | 为空默认 `402` |
| `desc` | string | 是 | 去除首尾空格后至少 4 字，否则 422 `DESC_TOO_SHORT` |
| `window` | string | 否 | 为空默认全天 |
| `photoIdsBefore` | string[] | 是 | 上传接口返回的文件 ID；正式环境至少 1 个 |
| `category` | string | 否 | 传值时跳过 AI 分类；必须是五类之一 |
| `level` | string | 否 | 仅在传 `category` 时使用；为空默认普通 |
| `studentId` | string | 否 | **当前不参与建单，身份以 Token 为准** |
| `studentName` | string | 否 | **当前不参与建单，身份以 Token 为准** |

### 4.2 `TransitRequest`

所有命令共用的请求对象，只使用该命令需要的字段。

| 字段 | 类型 | 说明 |
|---|---|---|
| `version` | integer | 工单版本；目标上必填 |
| `note` | string | 完工说明等自由文本 |
| `reason` | string | 未遇、驳回、撤销原因或固定原因代码 |
| `signerName` | string | 验收签字人，`accept` 必填 |
| `photoIdsAfter` | string[] | 完工照片文件 ID，`finish` 至少 1 张 |
| `partsName` | string | 缺件名称，`need-parts` 必填 |
| `partsAmount` | number | 缺件金额，必须大于 0 |
| `partsEta` | string | 到货日期 `yyyy-MM-dd`，必填 |
| `workerId` | string | 派工师傅工号 |
| `workerName` | string | 派工师傅姓名 |

### 4.3 登录与配置

`LoginRequest`：`account` string 必填，`password` string 必填。

`ConfigPatch`：`budgetLine`、`verifyHours`、`archiveDays` 均可选；值为正整数。未识别字段会被忽略。

### 4.4 目标返修请求

`ReworkRequestCreate`：`version` integer 必填，`reason` string 必填 2–200 字。

`ReworkReviewDecision`：`version` integer 必填，`decision` enum 必填，`reason` string 必填 2–200 字。

## 五、鉴权接口

| 方法 | 路径 | 请求字段 | `data` 响应字段 |
|---|---|---|---|
| POST | `/auth/login` | `account,password` | `token,expiresAt,ttlSeconds,user` |
| POST | `/auth/logout` | Header Token | `logout:boolean` |
| GET | `/auth/me` | Header Token | `user,expiresAt` |
| POST | `/auth/system-token` | 管理员（仅演示模式） | `token,expiresAt,user,actor,ttlSeconds` |
| POST | `/files` | 已登录角色 | multipart `file,purpose=BEFORE/AFTER` | `id,url,mime,byteSize,sha256` |
| GET | `/files/{id}` | 上传者 / 本单学生 / 本单师傅 / 管理员 | 无 | 图片字节，带安全响应头 |

`user` 字段：`id`、`role`、`name`，可选 `team`、`building`、`room`。`login` 的 `user` 不包含密码。

## 六、工单命令接口

所有成功响应的 `data` 默认是 `OrderView`，下表特别说明的除外。

| 方法 | 路径 | 调用方 | 请求字段 | 响应/状态 |
|---|---|---|---|---|
| POST | `/orders` | 学生 | `SubmitRequest` | `201`，`OrderView` |
| POST | `/orders/{no}/assign` | 管理员/系统 | `version,workerId,workerName` | `OrderView` |
| POST | `/orders/{no}/arrive` | 师傅 | `version` | `OrderView` |
| POST | `/buildings/{id}/arrive` | 师傅/管理员 | 无 body | 批量结果对象 |
| POST | `/orders/{no}/no-show` | 师傅 | `version,reason` | `OrderView`，`reason` 默认 `KNOCK_NO_ANSWER` |
| POST | `/orders/{no}/finish` | 师傅 | `version,photoIdsAfter,photosAfter,note` | `OrderView` |
| POST | `/orders/{no}/need-parts` | 师傅 | `version,partsName,partsAmount,partsEta` | `OrderView` |
| POST | `/orders/{no}/parts-arrived` | 管理员/系统 | `version` | `OrderView` |
| POST | `/orders/{no}/accept` | 学生 | `version,signerName` | `OrderView` |
| POST | `/orders/{no}/reject` | 学生（当前旧口径） | `version,reason` | `OrderView`，但返回的是**新建的返修单** |
| POST | `/orders/{no}/cancel` | 学生 | `version,reason` | `OrderView`，`reason` 为空默认“不需要修了” |
| POST | `/orders/{no}/cancel-request` | 学生 | `version,reason` | `202`，`OrderView`，状态不变 |
| POST | `/orders/{no}/auto-accept` | 系统 | 无 | `OrderView`，未到 T1 返回 422 |
| POST | `/orders/{no}/archive` | 系统 | 无 | `OrderView`，未到 T2 返回 422 |
| POST | `/orders/{no}/admin-cancel` | 管理员 | `{version,reason}` | `OrderView`；仅允许维修中且已有学生撤销申请的工单；事件为 `ADMIN_CANCEL` |

### 6.1 楼栋批量打卡响应

```json
{
  "buildingId": "B7",
  "arrivedAt": "2026-11-18 15:30:00",
  "ok": ["BX20261118001"],
  "skipped": ["BX20261118002"],
  "failed": []
}
```

`orders` 只处理当前师傅名下的 `ASSIGNED` 单；已在 `REPAIRING` 的进入 `skipped`。

### 6.2 旧版驳回接口（已退场）

当前 `/orders/{no}/reject` 成功后：
1. 原单进入 `REWORKING`；
2. 系统自动创建并保存 `-R1`；
3. **接口响应返回新返修单的 `OrderView`，不是原单**。

前端不得把 `response.no` 当作原单号。新 UI 只调用 `/rework-request` 与 `/rework-review`，旧接口仅保留兼容。

### 6.3 返修审查接口

| 方法 | 路径 | 调用方 | 请求 | `data` 响应 |
|---|---|---|---|---|
| POST | `/orders/{no}/rework-request` | 学生 | `ReworkRequestCreate` | `202`，`{order:OrderView,review:ReworkReviewView}` |
| POST | `/orders/{no}/rework-review` | 管理员 | `ReworkReviewDecision` | `{original:OrderView,reworkOrder:OrderView or null}` |

- `APPROVE`：原单 `REWORKING`，`reworkOrder` 为 `PENDING` 新单，`review.status=APPROVED`。
- `REJECT`：原单仍 `VERIFYING`，`reworkOrder=null`，`review.status=REJECTED`，T1 从决定时刻重新计时。

## 七、查询接口

### 7.1 工单列表

`GET /orders?status=`（管理员）：`status` 可为 `ALL`、`ACTIVE`、`DONE` 或状态代码；响应 `data={items:OrderView[],total:number}`。

`GET /my/orders?status=`（学生/师傅/管理员）：按当前身份过滤；响应同列表结构。

### 7.2 工单详情与事件

`GET /orders/{no}`：`data=OrderView`。

`GET /orders/{no}/events`：`data={items:OrderEvent[],total:number}`。

### 7.2.1 管理员返修审查队列

`GET /admin/rework-reviews`（管理员）：返回 `data={items[],total}`。每项包含 `id,no,attemptNo,status,requestedBy,requestedAt,reason,reworkCount,building,floor,room,location,workerName,version,order:OrderView`。

### 7.2.2 管理员 AI 复核审计

`GET /admin/ai-reviews?limit=50`（管理员，只读）：返回 `data={items:AiReviewTaskView[],total:number}`，`limit` 范围 1–100。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 复核任务 ID |
| `orderNo` | string | 被复核工单号 |
| `orderVersion` | integer | 发起复核时的工单版本 |
| `inputHash` | string | 描述文本 SHA-256，避免复制敏感原文 |
| `model` | string | 实际模型名 |
| `provider` | string | 模型供应方标识 |
| `status` | string | `PENDING/RUNNING/SUCCEEDED/FALLBACK/FAILED/SUPERSEDED` |
| `attempts` | integer | 模型调用次数 |
| `requestedAt` | string | 任务创建时间 |
| `completedAt` | string/null | 任务结束时间 |
| `errorCode` | string/null | 稳定错误码 |
| `errorMessage` | string/null | 截断后的错误摘要 |
| `category` | string/null | 模型返回或兜底类别 |
| `level` | string/null | 模型返回或兜底级别 |
| `confidence` | number/null | 模型置信度 |
| `basis` | string/null | 判断依据 |
| `source` | string/null | 结果来源 |
| `latencyMs` | integer | 模型调用耗时 |
| `applied` | boolean | 是否写回工单 |
| `appliedAt` | string/null | 写回时间 |

审计接口只供管理端排查模型失败、兜底、晚到结果与版本冲突，不得用于推进工单状态。

### 7.3 师傅待办 `GET /worker/todo`

| 字段 | 类型 | 说明 |
|---|---|---|
| `buildings` | array | 楼栋分组 |
| `buildings[].buildingId` | string | 楼栋代码 |
| `buildings[].buildingName` | string | 楼栋名称 |
| `buildings[].arrived` | boolean | 是否已有到楼记录 |
| `buildings[].orders` | OrderView[] | 需要上楼的工单 |
| `buildings[].floors` | array | `{floor:number,count:number}`，楼层降序 |
| `buildings[].progress` | object | `{done:number,total:number}` |
| `summary.todo` | number | 待派工 + 已派工 |
| `summary.repairing` | number | 维修中 |
| `summary.parts` | number | 待配件 |

### 7.4 楼栋看板 `GET /buildings/{id}/board`

查询参数 `floors` 可重复或逗号分隔，如 `?floors=5&floors=4`。响应：`buildingId`、`buildingName`、`floors[{floor,orders:OrderView[]}]`、`emptyFloors:number[]`。

### 7.5 例外清单 `GET /admin/exceptions`

响应：`items[]`、`counts`、`total`。`items[]` 字段：`id,kind,kindLabel,no,building,room,floor,location,status,statusLabel,detail,version`。

`counts`：`MISMATCH`、`OVER_BUDGET`、`REPEAT_REWORK`、`CANCEL_REQUEST` 四个整数计数。

### 7.6 看板 `GET /admin/dashboard`

| 字段 | 类型 | 说明 |
|---|---|---|
| `active` | number | 在办工单数 |
| `pendingArchive` | number | 已完成待归档数 |
| `exceptionTotal` | number | 例外总数 |
| `avgFixMinutes` | number | 平均修复分钟数 |
| `reworkRate` | number | 返修占比，0–1 |
| `zeroManual` | object | `{zero,total,rate,basis}` 宽松口径 |
| `strictZero` | object | `{zero,total,rate,basis}` 严格口径 |
| `byBuilding` | array | `{buildingId,buildingName,active,done,exceptions}` |
| `timers` | array | `{no,room,building,status,t1?,t2?,stayedMs}` |
| `config` | object | 当前配置，见 7.7 |

### 7.7 配置 `GET/PUT /admin/config`

`GET data`：`budgetLine`、`verifyHours`、`archiveDays`、`source`。

`PUT data`：更新后的同结构；请求 `ConfigPatch` 只接受三个阈值键。

## 八、元数据与系统接口

| 接口 | `data` 字段 |
|---|---|
| `GET /health` | `status,orders` |
| `GET /meta` | `statuses,events,transitions,categories,levels,windows,budgetLine,verifyHours,archiveDays,brand,version,buildings,noShowReasons,finishTags,cancelReasons,exceptionKinds` |
| `GET /state/matrix` | `rows,rowCount,eventCount,total,legal` |
| `GET /state/transitions` | `items,total`；item 为 `{from,event,eventLabel,to,trigger[]}` |
| `GET /ai/health` | `baseUrl,timeoutMs,reviewTimeoutMs,up,latencyMs,remote?` 或 `error?` |
| `POST /demo/advance?hours=` | `at,autoAccepted[],archived[],failed[],advancedHours` |
| `POST /demo/reset` | `deleted,seeded,orders` |

`/meta` 的 `statuses[]` 为 `{code,label,terminal}`；`events[]` 为 `{code,label,trigger[]}`；`buildings[]` 为 `{id,name,floors[]}`；`exceptionKinds[]` 为 `{code,label}`。

`/state/matrix` 的 `rows[]` 为 `{from,cells[]}`，`cells[]` 为 `{event,to,legal}`。

## 九、权限与错误映射

| 场景 | HTTP | `error.code` / 额外字段 |
|---|---:|---|
| 未登录/Token 过期 | 401 | `UNAUTHORIZED` |
| 角色不对 | 403 | `FORBIDDEN` |
| 非本单师傅/学生 | 403 | `NOT_OWNER` |
| 工单不存在 | 404 | `NOT_FOUND` |
| 非法状态 | 409 | `STATE_CONFLICT` + `from/event/allowed/reason=NOT_ALLOWED` |
| 版本冲突 | 409 | `VERSION_CONFLICT` + `expectVersion/gotVersion/allowed` |
| 照片/原因/金额/日期缺失 | 422 | `PRECONDITION_FAILED` + `reason` |
| 未批准变更单 | 501 | `NOT_IMPLEMENTED` + `changeRequest=CR-001` |
| 服务端异常 | 500 | `INTERNAL_ERROR` |

常见 422 `reason`：`PHOTO_REQUIRED`、`DESC_TOO_SHORT`、`PHOTO_AFTER_REQUIRED`、`PARTS_NAME_REQUIRED`、`PARTS_AMOUNT_REQUIRED`、`PARTS_ETA_REQUIRED`、`SIGNER_REQUIRED`、`REASON_REQUIRED`、`CANCEL_REQUEST_ONLY_IN_REPAIRING`、`T1_NOT_DUE`、`T2_NOT_DUE`、`NO_WORKER_AVAILABLE`。

## 十、前端落地检查

- 只使用本文件定义的对象和枚举；字段缺失按 null 处理。
- 所有命令按钮必须同时检查角色、`allowed` 和 `version`。
- `/reject` 迁移到返修审查前，必须按“返回新返修单”处理；迁移后改用 `/rework-request` 与 `/rework-review`。
- 返修审查 UI 只读取 `review`/`reworkReview`，不得从前端自行推断审查状态。
- 修改后端 DTO 或 Map 返回字段时，必须同步更新本文件、前端接口封装和接口测试。
- 新照片只能提交 `/files` 返回的 ID；不得再生成 demo 协议占位地址。
