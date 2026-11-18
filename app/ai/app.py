"""AI 定级服务。只做一件事：给一段描述，返回故障类型 / 紧急程度 / 置信度 / 依据。

跑起来：
    D:\\develop\\python-envs\\dorm-ai\\Scripts\\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8000

接口：
    GET  /health   -> {"status":"UP","rules":5}
    POST /classify {"desc":"...", "fallbackCategory":"给排水"}
                   -> {"category":"给排水","level":"紧急","confidence":0.94,"basis":"...","fallback":false}
    GET  /rules    -> 词表预览，文档里引用它，别抄源码

它不认识工单、不连数据库、不改状态。Java 那边 800ms 拿不到就自己按保守档兜底。
"""

from __future__ import annotations

import time
from typing import Optional

from fastapi import FastAPI
from pydantic import BaseModel, Field

from rules import classify, keyword_preview, CATEGORIES, LEVELS

app = FastAPI(title="宿舍维修 AI 定级服务", version="1.0.0")

STARTED_AT = time.time()


class ClassifyRequest(BaseModel):
    desc: str = Field(default="", description="学生写的故障描述")
    fallbackCategory: Optional[str] = Field(default=None, description="判不出来时用的类型")


class ClassifyResponse(BaseModel):
    category: str
    level: str
    confidence: float
    basis: str
    fallback: bool = False


@app.get("/health")
def health() -> dict:
    return {
        "status": "UP",
        "service": "dorm-repair-ai",
        "version": "1.0.0",
        "rules": len(CATEGORIES),
        "levels": len(LEVELS),
        "uptimeSeconds": round(time.time() - STARTED_AT, 1),
    }


@app.post("/classify", response_model=ClassifyResponse)
def do_classify(req: ClassifyRequest) -> ClassifyResponse:
    v = classify(req.desc, req.fallbackCategory)
    return ClassifyResponse(**v.to_dict())


@app.get("/rules")
def rules() -> dict:
    return keyword_preview()
