# API 与前后端联调计划

| 项 | 内容 |
|---|---|
| 文档编号 | DPLAN-04 |
| 版本 | V1.2 |
| 状态 | ✅ 已完成（2026-11-18 验收回填） |
| 上游依据 | `04-前后端API接口.md` V1.1、`SYS-005` V1.1 |

## 一、冻结内容

联调前冻结 `OrderView`、`OrderEvent`、`CancelRequest`、`ReworkReviewView`、`SubmitRequest`、`TransitRequest`、错误对象和枚举值。

## 二、状态机与审计事件

1. `TransitionTable.TRIGGER`：`REJECT` 允许 `ADMIN`；旧学生路径迁移期单独兼容。
2. `OrderEvent`：增加 `REWORK_REQUEST`、`REWORK_REJECTED`。
3. `OrderEvent.changesStatus()`：两项返回 `false`。
4. `TransitionTable.TRIGGER`：增加 `REWORK_REQUEST -> STUDENT`、`REWORK_REJECTED -> ADMIN`。否则 `logOnly()` 的 `assertCanTrigger()` 会返回 403。
5. `TransitionTable.EVENTS` 仍只保留 13 个状态事件；审计事件放在独立集合，`/state/matrix` 仍为 10×13=130。

## 三、返修旧/新路径

- 旧 `/reject`：保留兼容，响应仍是新建返修单，不再作为新 UI 主路径。
- 新 `/rework-review`：返回 `{original,reworkOrder}`。
- `OrderService.reject()` 拆为两个实现：
  - `rejectLegacy()`：旧路径保持不变；
  - `reviewRework()`：新审查路径，负责审查表、触发方和子单创建。
- 批准时：
  - 原单记录管理员 `REJECT` 事件；
  - 审查行写 `APPROVED` 和 `rework_index`；
  - 子单 `SUBMIT` 事件使用原学生身份，避免把学生提交记成管理员；
  - 或改用独立 `REWORK_CREATED` 事件，但必须新增设计与契约，不能静默绕过触发方。

## 四、接口迁移顺序

| 阶段 | 后端 | 前端 | 门禁 |
|---|---|---|---|
| A | 认证接入分角色表和写路径 | 登录使用 Bearer Token | 三角色登录/建号/改口令 |
| B | 现有工单命令字段对齐 | 按契约校验请求 | 建单/派工/打卡/完工 |
| C | 新审查接口、管理员 REJECT、审查表 | 学生申请、管理端审查 | 审查表 PENDING/APPROVED/REJECTED |
| D | 旧 `/reject` 兼容 | 不再新增旧调用 | 旧路径行为可解释 |
| E | 查询字段补齐 | 列表、详情、看板、例外 | 无 undefined |
| F | 错误码与版本适配 | 409/422/403 统一处理 | 错误场景可操作 |

## 五、端口迁移清单

T07 必须检查并替换以下位置，目标后端 8082、AI 8001：

- `app/server/src/main/resources/application.yml`
- `app/web/vite.config.js`
- `start.bat`
- `app/ai/start-ai.bat`
- `app/server/tests/m1-chain.ps1`
- `app/server/tests/m2-mysql.ps1`
- `app/server/tests/m3-ai.ps1`
- `app/server/tests/m4-rework.ps1`
- `app/server/tests/m5-auth.ps1`
- `AGENTS.md`
- `README.md`、`app/server/README.md`、`app/ai/README.md`
- `docs/tools/build_docs.py`

完成后执行 `rg -n '8080|8000'`，剩余项只能是“当前代码端口”说明，不能出现在启动命令或测试脚本中。

## 六、联调检查

- 409 返回 `from/event/allowed`。
- 审查响应包含 `original`、`reworkOrder`、`review`。
- 驳回时 `reworkOrder` 为 null；原单保持 `VERIFYING`。
- 管理端队列按 `t_rework_review.status=PENDING` 查询。
- `/meta` 可返回审计事件元数据，但不能混入 13 条状态矩阵。
- 无可用师傅时 `/assign` 返回 `422 NO_WORKER_AVAILABLE`，状态不变。

## 七、契约测试

- DTO 和 `OrderView` 字段与 DEV-04 一致。
- 管理员可推 REJECT；学生调用新审查接口返回 403。
- `REWORK_REQUEST/REWORK_REJECTED` 只写日志，不改变状态。
- `eventCount=13`、`total=130` 不变。
- `reworkReview` 三态和空值正确。

## 八、联调出口

API 文档、DTO、前端封装和测试字段一致；旧/新返修路径、矩阵口径、派遣失败和错误码全部通过。
