"""知识库 REST API — 文档 CRUD + 向量检索"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.rag import (
    add_document,
    delete_document,
    update_document,
    get_document,
    list_documents,
    search_knowledge,
)
from app.utils.logger import get_logger

logger = get_logger(__name__)
router = APIRouter(prefix="/knowledge", tags=["知识库"])


# ── Request/Response Models ──

class DocumentCreateRequest(BaseModel):
    title: str = Field(..., description="文档标题")
    content: str = Field(..., description="文档正文")
    category: str = Field(default="", description="分类")
    source: str = Field(default="", description="来源")


class DocumentUpdateRequest(BaseModel):
    title: str = Field(..., description="文档标题")
    content: str = Field(..., description="文档正文")
    category: str = Field(default="", description="分类")
    source: str = Field(default="", description="来源")


class SearchRequest(BaseModel):
    query: str = Field(..., description="搜索查询文本")
    top_k: int = Field(default=3, ge=1, le=10, description="返回数量")


# ── CRUD Endpoints ──

@router.post("/documents")
async def create_document(req: DocumentCreateRequest):
    """添加文档 (自动切分 + 嵌入 + 入库)"""
    try:
        result = add_document(
            title=req.title,
            content=req.content,
            category=req.category,
            source=req.source,
        )
        return result
    except Exception as e:
        logger.error("添加文档失败: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/documents/{parent_doc_id}")
async def read_document(parent_doc_id: str):
    """查询单个文档"""
    doc = get_document(parent_doc_id)
    if doc is None:
        raise HTTPException(status_code=404, detail="文档不存在")
    return doc


@router.put("/documents/{parent_doc_id}")
async def update_document_endpoint(parent_doc_id: str, req: DocumentUpdateRequest):
    """更新文档 (删除旧 chunk + 重新切分嵌入入库)"""
    try:
        result = update_document(
            parent_doc_id=parent_doc_id,
            title=req.title,
            content=req.content,
            category=req.category,
            source=req.source,
        )
        return result
    except Exception as e:
        logger.error("更新文档失败: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


@router.delete("/documents/{parent_doc_id}")
async def delete_document_endpoint(parent_doc_id: str):
    """删除文档的所有 chunk"""
    count = delete_document(parent_doc_id)
    return {"deleted": count}


@router.get("/documents")
async def list_documents_endpoint(page: int = 1, size: int = 20, category: str = ""):
    """分页列出文档"""
    return list_documents(page=page, size=size, category=category)


@router.post("/search")
async def search_endpoint(req: SearchRequest):
    """向量检索"""
    try:
        results = search_knowledge(query=req.query, top_k=req.top_k)
        return {"results": results}
    except Exception as e:
        logger.error("向量检索失败: %s", e)
        raise HTTPException(status_code=500, detail=str(e))
