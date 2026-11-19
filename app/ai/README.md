# app/ai —— AI 定级服务（Python + FastAPI）

只做一件事：给一段学生写的描述，回答"是哪类故障、多急、凭什么"。

**它不认识工单，不连数据库，不改状态。** 状态怎么流转由 Java 那边的状态机管。AI 判错了顶多定级不准，不会让单走错。

## 跑起来

```powershell
# 用已经建好的 venv（别用系统那个 3.14）
D:\develop\python-envs\dorm-ai\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8001
```

或双击 `start-ai.bat`。

真模型配置放在 `app/ai/.env.local`（已被 Git 忽略）：

```text
AI_MODE=llm
AI_BASE_URL=http://127.0.0.1:3065/v1
AI_MODEL=raccoon/sn-deepseek-v4-1-flash-1017
AI_API_KEY=sk-...
AI_TIMEOUT_S=20
AI_CACHE_TTL_S=3600
```

模型限流、超时或非法 JSON 时，服务自动退回规则结果，不阻断建单。

装依赖（只在换机器时需要）：

```powershell
$env:UV_DEFAULT_INDEX = 'https://mirrors.aliyun.com/pypi/simple'
D:\develop\uv\uv.exe pip install --python 'D:\develop\python-envs\dorm-ai\Scripts\python.exe' -r requirements.txt
```

> 本机 pip 只能走阿里云源：清华源在这台机器上 TLS 握手直接 EOF。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/health` | `{"status":"UP","rules":5,...}`，Java 的 `/api/v1/ai/health` 就是打它 |
| POST | `/classify` | 入参 `{"desc":"..."}`，出 `category / level / confidence / basis / fallback` |
| GET | `/rules` | 词表预览，写文档时引用它，别抄源码 |

`/classify` 返回示例：

```json
{"category":"给排水","level":"紧急","confidence":0.94,
 "basis":"命中「软管/滴水」，归给排水；描述提到「快接满了」，按浸水风险算安全类，直接定紧急",
 "fallback":false}
```

## 为什么是规则不是模型

本机没有能跑推理的显卡，答辩现场也不允许联网下权重。规则版本可解释、可测、断网能跑，接口形状跟真模型一致——将来换模型只改 `rules.classify()` 一个函数，Java 那边一行不用动。

规则优先级：

1. **安全类词**（漏电/触电/打火/冒烟/起火/焦味/煤气味）命中 → 直接「紧急」，不再往下判
2. **定类型**：五类关键词表，命中词越多置信度越高（最多 0.99）
3. **定紧急**：给排水里出现浸水风险词（快接满/接满了/淹没/积水/全是水）→ 直接「紧急」；出现影响使用词（没法用/洗不了澡/影响上课）→ 「较急」；否则「普通」
4. **置信度 < 0.70** → 保守升一档，并把这步写进依据

## 怎么证明它对

```powershell
D:\develop\python-envs\dorm-ai\Scripts\python.exe -m pytest -q
```

期望 `29 passed`。覆盖规则定级、分类器、LLM JSON 解析、供应商故障码与断线兜底。

端到端（含"杀掉 AI 后建单仍成功"）在 `../server/tests/m3-ai.ps1`，20 项。

## 文件

```
app/ai/
├── app.py             FastAPI 接口
├── rules.py           规则本体 + Verdict
├── classifier.py      快速分类入口
├── llm.py             可选模型复核与 JSON 解析
├── test_*.py          29 条 pytest
├── requirements.txt   钉版本
└── start-ai.bat       双击起服务
```
