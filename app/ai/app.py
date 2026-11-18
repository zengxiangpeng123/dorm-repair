"""AI 定级服务。只做一件事：给一段描述，返回故障类型 / 紧急程度 / 置信度 / 依据。

跑起来：
    D:\\develop\\python-envs\\dorm-ai\\Scripts\\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8001

两种模式（环境变量 AI_MODE）：
    rules  只用规则，毫秒级，断网可跑（默认，答辩兜底就用它）
    llm    规则当闸门 + 真模型理解描述，秒级（需要 AI_API_KEY）

接口：
    GET  /health   -> 服务状态 + 当前模式
    POST /classify {"desc":"...", "fallbackCategory":"给排水"}
                   -> {"category":"给排水","level":"紧急","confidence":0.94,"basis":"...","fallback":false,"source":"llm"}
    GET  /rules    -> 词表预览，文档里引用它，别抄源码
    POST /warmup   -> 打一发小请求，把模型的冷启动挪到没人看的时刻

它不认识工单、不连数据库、不改状态。Java 那边拿不到就自己按保守档兜底。
"""

from __future__ import annotations

import time
import uuid
from typing import Optional

from fastapi import FastAPI, Response
from pydantic import BaseModel, Field

import classifier
import llm
from rules import CATEGORIES, LEVELS, keyword_preview

app = FastAPI(title="宿舍维修 AI 定级服务", version="1.2.0")

STARTED_AT = time.time()


class ClassifyRequest(BaseModel):
    desc: str = Field(default="", description="学生写的故障描述")
    fallbackCategory: Optional[str] = Field(default=None, description="判不出来时用的类型")
    fast: bool = Field(default=False, description="true=只跑规则立刻返回，学生提交这一步用它")


class ClassifyResponse(BaseModel):
    category: str
    level: str
    confidence: float
    basis: str
    fallback: bool = False
    # 下面三个是 1.1 新增的，Java 侧不读也不影响（只认前五个）
    source: str = "rule-only"
    model: Optional[str] = None
    latencyMs: int = 0
    attempts: int = 0
    errorCode: Optional[str] = None
    errorMessage: Optional[str] = None


@app.middleware("http")
async def request_observability(request, call_next):
    request_id = request.headers.get("X-Request-Id") or str(uuid.uuid4())
    started = time.perf_counter()
    response = await call_next(request)
    response.headers["X-Request-Id"] = request_id
    duration_ms = int((time.perf_counter() - started) * 1000)
    print(f"http request method={request.method} path={request.url.path} "
          f"status={response.status_code} durationMs={duration_ms} requestId={request_id}", flush=True)
    return response


@app.get("/health")
def health() -> dict:
    return {
        "status": "UP",
        "service": "dorm-repair-ai",
        "version": "1.2.0",
        "rules": len(CATEGORIES),
        "levels": len(LEVELS),
        "classifier": classifier.stats(),
        "uptimeSeconds": round(time.time() - STARTED_AT, 1),
    }


@app.get("/metrics")
def metrics() -> Response:
    stats = classifier.stats()
    body = (
        "# HELP dorm_ai_up AI service availability.\n"
        "# TYPE dorm_ai_up gauge\n"
        "dorm_ai_up 1\n"
        "# HELP dorm_ai_uptime_seconds AI service uptime.\n"
        "# TYPE dorm_ai_uptime_seconds gauge\n"
        f"dorm_ai_uptime_seconds {time.time() - STARTED_AT:.1f}\n"
        "# HELP dorm_ai_cache_entries Classifier cache entries.\n"
        "# TYPE dorm_ai_cache_entries gauge\n"
        f"dorm_ai_cache_entries {stats.get('cached', 0)}\n"
    )
    return Response(body, media_type="text/plain; version=0.0.4")


@app.post("/classify", response_model=ClassifyResponse)
def do_classify(req: ClassifyRequest) -> ClassifyResponse:
    return ClassifyResponse(**classifier.classify(req.desc, req.fallbackCategory, req.fast))


@app.get("/rules")
def rules() -> dict:
    return keyword_preview()


@app.post("/warmup")
def warmup() -> dict:
    if not llm.enabled():
        return {"ok": False, "reason": f"当前模式 {llm.mode()}，不需要预热"}
    return llm.warmup()