# app/server —— 正式项目后端（Spring Boot 4）

M1 已完成：状态机内核 + 一条链 + 13 条命令骨架。M2 接 MySQL，M3 接 Python AI，M4 配 Vue 前端。

## 怎么跑

```bash
cd app/server
mvn -q -DskipTests package
java -jar target/dorm-repair-server-1.0.0.jar
```

- 端口 **8080**（`server.port`，改它别改代码）
- 现在数据在内存里，**重启就清空**（M2 换成 MySQL 后不再清）
- 环境：JDK 17、Maven、Spring Boot 4.0.9、Spring Framework 7.0.10

## 怎么证明它对

```bash
mvn -q test                                    # 14 项：7 项白盒状态机 + 7 项黑盒接口
pwsh -NoProfile -File tests/m1-chain.ps1       # 13 项：打真服务，跑一条链 + 该拒的都拒
```

期望：`Tests run: 14, Failures: 0` 与 `结果：13 通过 / 0 失败`。

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
| POST | `/api/v1/orders/{no}/reject` | 验收不通过，原因≥2 字 |
| POST | `/api/v1/orders/{no}/cancel` | 撤销：待派工、已派工未打卡可以，进楼后 409 |
| POST | `/api/v1/orders/{no}/cancel-request` | 申请撤销，只留记录不改状态，返回 202 |
| GET | `/api/v1/orders?status=ACTIVE\|DONE\|ALL\|<状态>` | 列表 |
| GET | `/api/v1/orders/{no}` | 详情（含 allowed 下一个能推的事件） |
| GET | `/api/v1/orders/{no}/events` | 全链路事件 |
| GET | `/api/v1/meta` | 9 状态、13 事件、阈值、五类故障、四时段 |
| GET | `/api/v1/state/matrix` | 10 × 13 = 130 种组合，合法 13 |
| GET | `/api/v1/state/transitions` | 13 条合法转移清单 |
| POST | `/api/v1/demo/reset` | 清空内存数据 |

身份暂时走三个请求头（M4 换 token）：`X-Actor-Type: student|worker|admin|system`、`X-Actor-Id`、`X-Actor-Name`。请求头只能放 ASCII，姓名等中文留给登录后由服务端补。

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
├── store/          OrderStore（内存 + 版本 CAS，M2 换 Mapper）
├── service/        OrderStateService（唯一改状态的地方）/ OrderService（12 条命令）
├── controller/     OrderController / MetaController
├── dto/            SubmitRequest / TransitRequest / OrderView
└── common/         BizException / ApiResponses / GlobalExceptionHandler / TimeUtil
```

铁律：**只有 `OrderStateService.transit()` 能改 `status`**；转移表只在 `TransitionTable`；别在 service 里写 `if (status == ...)`。

## 还没做（按里程碑往下排）

- M2：MySQL 8 + MyBatis-Plus，7 张表，`schema.sql` / `data.sql`，重启数据还在
- M3：Python AI 服务 `/classify`，800ms 超时兜底，AI 挂了照样能建单
- M4：Vue3 + Element Plus 11 页
- M5：管理端 + 自检台 + 测试报告与 CG01–CG14 文档
- 一直欠着：`-R1` 返修单生成（现在验收不通过只把原单转返修中）、两颗 7 天计时器的定时任务、登录鉴权、`REPAIRING→CANCELLED`（变更单 #001 未批，将返回 501）
