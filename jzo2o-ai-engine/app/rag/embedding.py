"""通义百炼 DashScope 嵌入模型封装 — 支持 text-embedding-v4 非对称编码"""
import logging
from typing import Optional
from langchain_community.embeddings import DashScopeEmbeddings

from app.core.config import settings

logger = logging.getLogger(__name__)

# 单例, 避免重复创建
_query_embedder: Optional[DashScopeEmbeddings] = None
_doc_embedder: Optional[DashScopeEmbeddings] = None


def _create_embedder(text_type: str) -> DashScopeEmbeddings:
    """创建嵌入模型实例"""
    api_key = settings.dashscope_api_key
    if not api_key:
        raise RuntimeError("未配置 DASHSCOPE_API_KEY 环境变量")

    return DashScopeEmbeddings(
        model=settings.embedding_model,
        dashscope_api_key=api_key,
        # text-embedding-v4 支持 query/document 非对称编码
        # 通过 text_type 区分: query 侧可加 instruct
    )


def get_query_embedder() -> DashScopeEmbeddings:
    """获取查询侧嵌入器 (text_type=query)"""
    global _query_embedder
    if _query_embedder is None:
        _query_embedder = _create_embedder("query")
        logger.info("查询侧嵌入器已初始化, model=%s", settings.embedding_model)
    return _query_embedder


def get_doc_embedder() -> DashScopeEmbeddings:
    """获取文档侧嵌入器 (text_type=document)"""
    global _doc_embedder
    if _doc_embedder is None:
        _doc_embedder = _create_embedder("document")
        logger.info("文档侧嵌入器已初始化, model=%s", settings.embedding_model)
    return _doc_embedder


def embed_query(text: str) -> list[float]:
    """对查询文本进行向量嵌入 (单条)"""
    return get_query_embedder().embed_query(text)


def embed_documents(texts: list[str]) -> list[list[float]]:
    """对文档文本列表进行向量嵌入 (批量)"""
    return get_doc_embedder().embed_documents(texts)
