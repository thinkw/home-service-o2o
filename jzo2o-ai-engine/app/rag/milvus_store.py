"""Milvus 向量存储 — collection 管理、chunk CRUD、向量检索（Docker Standalone）"""
import json
import logging
import time
from typing import Optional

from pymilvus import (
    MilvusClient,
    DataType,
    connections,
)
from pymilvus.milvus_client.index import IndexParams

from app.core.config import settings
from .embedding import embed_query, embed_documents
from .document_processor import split_document

logger = logging.getLogger(__name__)

COLLECTION_NAME = "platform_knowledge"
EMBEDDING_DIM = 1024

_client: Optional[MilvusClient] = None


def _get_client() -> MilvusClient:
    """获取 Milvus 客户端 (惰性初始化, 连接 Docker Standalone)"""
    global _client
    if _client is not None:
        return _client

    uri = settings.milvus_uri
    token = settings.milvus_token or None

    logger.info("连接 Milvus Standalone, uri=%s", uri)
    _client = MilvusClient(uri=uri, token=token)
    _init_collection()
    return _client


def _init_collection():
    """创建 collection 和索引 (如已存在则跳过)"""
    client = _client

    if client.has_collection(COLLECTION_NAME):
        logger.info("Collection 已存在: %s", COLLECTION_NAME)
        client.load_collection(COLLECTION_NAME)
        return

    # 创建 collection, 定义 schema
    client.create_collection(
        collection_name=COLLECTION_NAME,
        auto_id=True,
        dimension=EMBEDDING_DIM,
        metric_type="IP",  # 内积, 等价于归一化后的余弦相似度
        # 向量字段默认, 额外标量字段通过 enable_dynamic_field=True 动态存储
        enable_dynamic_field=True,
    )

    # 创建 HNSW 索引 (pymilvus >= 2.5 使用 IndexParams)
    index_params = IndexParams()
    index_params.add_index(
        field_name="vector",
        index_type="HNSW",
        index_name="idx_vector_hnsw",
        params={"M": 16, "efConstruction": 200},
        metric_type="IP",
    )
    client.create_index(
        collection_name=COLLECTION_NAME,
        index_params=index_params,
    )

    client.load_collection(COLLECTION_NAME)
    logger.info("Collection 已创建: %s", COLLECTION_NAME)


# ── 文档 CRUD ──

def add_document(title: str, content: str, category: str = "",
                 source: str = "") -> dict:
    """添加文档: 切分 → 嵌入 → 存入 Milvus

    Returns:
        {parent_doc_id, chunk_count}
    """
    # 1. 切分
    chunks = split_document(title, content, category, source)
    if not chunks:
        return {"parent_doc_id": "", "chunk_count": 0}

    parent_doc_id = chunks[0]["parent_doc_id"]

    # 2. 批量嵌入
    chunk_texts = [c["content"] for c in chunks]
    vectors = embed_documents(chunk_texts)

    # 3. 组装插入数据
    now = int(time.time())
    insert_data = []
    for i, chunk in enumerate(chunks):
        insert_data.append({
            "vector": vectors[i],
            "parent_doc_id": parent_doc_id,
            "chunk_index": chunk["chunk_index"],
            "title": chunk["title"],
            "content": chunk["content"],
            "category": chunk["category"],
            "source": chunk["source"],
            "create_time": now,
        })

    client = _get_client()
    result = client.insert(collection_name=COLLECTION_NAME, data=insert_data)
    logger.info("文档入库完成: parent_doc_id=%s, chunks=%d, insert_count=%d",
                parent_doc_id, len(chunks), result.get("insert_count", 0))
    return {"parent_doc_id": parent_doc_id, "chunk_count": len(chunks)}


def delete_document(parent_doc_id: str) -> int:
    """删除文档的所有 chunk"""
    client = _get_client()
    result = client.delete(
        collection_name=COLLECTION_NAME,
        filter=f'parent_doc_id == "{parent_doc_id}"',
    )
    count = result.get("delete_count", 0) if isinstance(result, dict) else 0
    logger.info("文档删除完成: parent_doc_id=%s, deleted=%d", parent_doc_id, count)
    return count


def update_document(parent_doc_id: str, title: str, content: str,
                    category: str = "", source: str = "") -> dict:
    """更新文档: 先删旧 chunk, 再切分+嵌入+入库新 chunk"""
    delete_document(parent_doc_id)
    result = add_document(title, content, category, source)
    result["parent_doc_id"] = parent_doc_id
    logger.info("文档更新完成: parent_doc_id=%s", parent_doc_id)
    return result


def get_document(parent_doc_id: str) -> Optional[dict]:
    """查询文档详情 (聚合所有 chunk)"""
    client = _get_client()
    results = client.query(
        collection_name=COLLECTION_NAME,
        filter=f'parent_doc_id == "{parent_doc_id}"',
        output_fields=["title", "content", "chunk_index", "category", "source", "create_time"],
        limit=100,
    )
    if not results:
        return None
    # 按 chunk_index 排序, 拼接 content
    results.sort(key=lambda r: r.get("chunk_index", 0))
    first = results[0]
    return {
        "parent_doc_id": parent_doc_id,
        "title": first.get("title", ""),
        "content": "\n\n".join(r.get("content", "") for r in results),
        "category": first.get("category", ""),
        "source": first.get("source", ""),
        "chunk_count": len(results),
        "create_time": first.get("create_time", 0),
    }


def list_documents(page: int = 1, size: int = 20, category: str = "") -> dict:
    """分页列出文档 (按 parent_doc_id 去重)"""
    client = _get_client()
    filter_expr = None
    if category:
        filter_expr = f'category == "{category}"'

    # 查询所有 chunk, 按 parent_doc_id 去重
    results = client.query(
        collection_name=COLLECTION_NAME,
        filter=filter_expr,
        output_fields=["parent_doc_id", "title", "chunk_index", "category", "source", "create_time"],
        limit=1000,
    )

    # 手动去重: 每个 parent_doc_id 只保留 chunk_index == 0 的那条
    seen = {}
    for r in results:
        pid = r.get("parent_doc_id", "")
        ci = r.get("chunk_index", 0)
        if pid not in seen or ci < seen[pid].get("chunk_index", 999):
            seen[pid] = r

    docs = list(seen.values())
    docs.sort(key=lambda r: r.get("create_time", 0), reverse=True)

    total = len(docs)
    start = (page - 1) * size
    page_docs = docs[start:start + size]

    return {
        "total": total,
        "page": page,
        "size": size,
        "list": [
            {
                "parent_doc_id": d.get("parent_doc_id", ""),
                "title": d.get("title", ""),
                "category": d.get("category", ""),
                "source": d.get("source", ""),
                "create_time": d.get("create_time", 0),
            }
            for d in page_docs
        ],
    }


# ── 向量检索 ──

def search_knowledge(query: str, top_k: int = 3) -> list[dict]:
    """向量检索: query 嵌入 → Milvus search → 按 parent_doc_id 去重"""
    # 1. 嵌入查询
    vector = embed_query(query)

    # 2. 检索 (多取一些, 用于去重后仍有 top_k 条)
    search_top_k = top_k * 3
    client = _get_client()
    results = client.search(
        collection_name=COLLECTION_NAME,
        data=[vector],
        limit=search_top_k,
        output_fields=["parent_doc_id", "title", "content", "chunk_index"],
    )

    if not results or not results[0]:
        return []

    hits = results[0]

    # 3. 按 parent_doc_id 去重: 同文档只保留得分最高的 chunk
    seen = {}
    for hit in hits:
        entity = hit.get("entity", {})
        pid = entity.get("parent_doc_id", "")
        score = hit.get("distance", 0)
        if pid not in seen or score > seen[pid]["score"]:
            seen[pid] = {
                "title": entity.get("title", ""),
                "content": entity.get("content", ""),
                "score": round(score, 4),
                "parent_doc_id": pid,
            }

    # 按得分降序, 取 top_k
    sorted_docs = sorted(seen.values(), key=lambda d: d["score"], reverse=True)
    return sorted_docs[:top_k]
