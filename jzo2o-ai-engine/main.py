"""jzo2o-ai-engine — AI 引擎微服务入口
职责: Prompt编排、LangChain运行、工具调用决策、LLM适配
严禁: 直接操作生产DB、执行扣款/改订单状态等业务逻辑
"""
import warnings
from contextlib import asynccontextmanager

warnings.filterwarnings("ignore", message=".*urllib3.*")
try:
    from langchain_core._api.deprecation import LangChainPendingDeprecationWarning
except ImportError:
    LangChainPendingDeprecationWarning = DeprecationWarning
warnings.filterwarnings("ignore", category=LangChainPendingDeprecationWarning)

import uvicorn
from fastapi import FastAPI
from app.api.chat import router as chat_router
from app.api.ws_chat import router as ws_chat_router
from app.api.knowledge import router as knowledge_router
from app.core.config import settings
from app.utils.logger import get_logger

logger = get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    logger.info("jzo2o-ai-engine 启动中...")
    yield
    # 关闭时清理资源
    from app.agent import shutdown_checkpointer
    await shutdown_checkpointer()
    logger.info("jzo2o-ai-engine 已关闭")


app = FastAPI(
    title="jzo2o-ai-engine",
    description="云岚到家 AI 引擎 — Prompt编排 + LLM适配",
    version="0.1.0",
    lifespan=lifespan,
)

# 注册路由
app.include_router(chat_router, prefix="/chat", tags=["聊天"])
app.include_router(ws_chat_router, tags=["WebSocket"])
app.include_router(knowledge_router, tags=["知识库"])


@app.get("/health")
async def health():
    """健康检查接口"""
    return {"status": "ok", "service": "jzo2o-ai-engine"}


if __name__ == "__main__":
    logger.info("jzo2o-ai-engine 启动中...")
    uvicorn.run(
        app,
        host=settings.server_host,
        port=settings.server_port,
        log_level="info",
    )
