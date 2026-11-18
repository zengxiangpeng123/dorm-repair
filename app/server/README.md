# app/server —— 正式项目后端（Spring Boot 4）

当前已完成：状态机内核、21 表 MySQL 仓储（20 张业务表 + 1 张迁移记录表）、三角色 Bearer 鉴权、按楼栋派工、T1/T2、返修审查、Vue 12 页和端到端验收。AI 快速定级超时 1500ms，AI 挂了照样能建单。

## 怎么跑

```bash
cd app/server
mvn -q -DskipTests package
java -jar target/dorm-repair-server-1.0.0.jar
```

- 端口 **8082**（`server.port`，改它别改代码）
- 默认连本机 MySQL：`dorm_repair` 库，`root/123456`，可用环境变量改：`DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD`
- **`APP_STORE=mysql`（默认）走数据库，`APP_STORE=memory` 走内存**（内存只用来跑单测和临时演示，重启就没了）
- 环境：JDK 17、Maven、Spring Boot 4.0.9、Spring Framework 7.0.10、MyBatis-Plus 3.5.18.1、MySQL 8.0.41

## 怎么证明它对

```bash
mvn -q test                                    # 110 项：状态机、API、鉴权、返修审查、文件治理、AI 兜底
pwsh -NoProfile -File tests/m1-chain.ps1       # 13 项：主链、非法边、乐观锁和权限
pwsh -NoProfile -File tests/m2-mysql.ps1       # 16 项：真库落盘与重启
pwsh -NoProfile -File tests/m3-ai.ps1          # 20 项：AI 接上与断线兜底
pwsh -NoProfile -File tests/m4-rework.ps1      # 26 项：兼容返修 + 新审查链 R1/R2
pwsh -NoProfile -File tests/m5-auth.ps1        # 36 项：真 Bearer Token 和角色权限
```

期望：`Tests run: 110, Failures: 0`；M1–M5 分别为 `13/16/20/26/36 通过，0 失败`。

## 数据库（M2）

建库建表 + 灌演示数据：

```powershell
$tmp = Join-Path $env:TEMP 'dormdb'; New-Item -ItemType Directory -Force $tmp | Out-Null
Copy-Item src\main\resources\db\schema.sql $tmp
Copy-Item src\main\resources\db\data.sql  $tmp
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -p123456 --default-character-set=utf8mb4 -e "source $tmp/schema.sql"
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -p123456 --default-character-set=utf8mb4 -e "source $tmp/data.sql"
```

**坑：`source` 打不开中文路径**（`Failed to open file ... error: 42`），所以先复制到 `%TEMP%` 这种纯英文路径再 source。

21 张表：`t_student` / `t_worker` / `t_admin` / `t_worker_building` / `t_auth_session` / `t_login_attempt` / `t_building` / `t_building_floor` / `t_work_order` / `t_work_order_event` / `t_work_order_photo` / `t_file` / `t_ai_review_task` / `t_part` / `t_part_request` / `t_rework_review` / `t_exception_record` / `t_config` / `t_idempotency_record` / `t_seq` / `t_schema_migration`。兼容视图为 `v_user_account`。

想推倒重来就 `POST /api/v1/demo/reset`：清表 + 重灌 11 张单，返回 `{"deleted":N,"seeded":11,"orders":11}`。

## 接口（M1 已经有）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/orders` | 建单，照片≥1 张、描述≥4 字，返回 201 |
| POST | `/api/v1/orders/{no}/assign` | 派工（system / admin） |
| POST | `/api/v1/orders/{no}/arrive` | 到门打卡（worker，必须是本单师傅） |
| POST | `/api/v1/orders/{no}/no-show` | 到访未遇，回待派工 |
| POST | `/api/v1/orders/{no}/finish` | 完工，完工照≥1 张 |
| POST | `/api/v1/orders/{no}/need-parts` | 登记缺件（名称 + 金额 + 到货日） |
| POST | `/api/v1/orders/{no}/parts-arrived` | 配件到货，回队列，定级不降 |
| POST | `/api/v1/orders/{no}/accept` | 验收通过，签字人必填 |
| POST | `/api/v1/orders/{no}/rework-request` | 学生申请返修，202，状态不变 |
| POST | `/api/v1/orders/{no}/rework-review` | 管理员批准/驳回返修审查 |
| POST | `/api/v1/orders/{no}/reject` | 兼容旧路径：学生直接生成返修单 |
| POST | `/api/v1/orders/{no}/cancel` | 撤销：待派工、已派工未打卡可以，进楼后 409 |
| POST | `/api/v1/orders/{no}/cancel-request` | 申请撤销，只留记录不改状态，返回 202 |
| GET | `/api/v1/orders?status=ACTIVE\|DONE\|ALL\|<状态>` | 列表 |
| GET | `/api/v1/orders/{no}` | 详情（含 allowed 下一个能推的事件） |
| GET | `/api/v1/orders/{no}/events` | 全链路事件 |
| GET | `/api/v1/ai/health` | AI 在不在、延迟多少毫秒 |
| GET | `/api/v1/meta` | 9 状态、13 事件、阈值、五类故障、四时段 |
| GET | `/api/v1/state/matrix` | 10 × 13 = 140 种组合，合法 14 |
| GET | `/api/v1/state/transitions` | 14 条合法转移清单 |
| GET | `/api/v1/admin/rework-reviews` | 管理员待审返修队列 |
| POST | `/api/v1/demo/reset` | 清空 + 重灌 11 张演示单 |

正式身份使用 `Authorization: Bearer <token>`。仅测试脚本可临时开启 `X-Actor-Type` / `X-Actor-Id`，生产必须关闭。

## 错误码怎么分

- **409 `STATE_CONFLICT`** 状态机里没这条边，带 `from` / `event` / `allowed`
- **409 `VERSION_CONFLICT`** 版本号对不上，message「状态已变化，请刷新」，带 `expectVersion` / `gotVersion`
- **422 `PRECONDITION_FAILED`** 有这条边但前置不满足（照片没传、原因没写），带 `reason`
- **403** 身份不对（学生派工、别的师傅打卡）
- **404** 单号不存在

## 代码在哪

```
src/main/java/com/zxp/dormrepair/
├── statemachine/   OrderStatus / OrderEvent / ActorType / TransitionTable / StateMachine
├── domain/         WorkOrder / OrderEventLog / Actor
├── auth/           登录、PBKDF2、TokenStore、AuthFilter、三角色用户仓库
├── store/          OrderRepository / ReworkReviewRepository / ExceptionRecordRepository；内存与 MySQL 双实现
├── service/        OrderStateService（唯一改状态）/ OrderService / TimerService / QueryService / AiReviewService
├── controller/     AuthController / OrderController / QueryController / MetaController
├── dto/            命令 DTO、返修 DTO、OrderView
└── common/         BizException / ApiResponses / GlobalExceptionHandler / TimeUtil
```

铁律：**只有 `OrderStateService.transit()` 能改 `status`**；转移表只在 `TransitionTable`；别在 service 里写 `if (status == ...)`。

## 当前完成状态

- 三角色分表、Bearer 登录、按楼栋派工、返修审查 R1/R2、反复返修例外、T1/T2 和 12 页 Vue UI 已验收。
- 旧 `/reject` 只保留兼容；新 UI 使用 `/rework-request` 与 `/rework-review`。
- 后续增强：真实 S3/MinIO 部署验证、生产容量与生命周期演练。
- M5：管理端 + 自检台 + 测试报告与 CG01–CG14 文档
- Bearer 登录、分角色人员表和会话撤销已实现；`REPAIRING→CANCELLED` 已作为管理员撤销决定接口实现，要求先有学生撤销申请。
