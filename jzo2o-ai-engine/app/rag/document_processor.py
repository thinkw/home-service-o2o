"""文档预处理 — 清洗 + 切分, 入库前将长文档拆分为语义 chunk"""
import re
import uuid
import logging
from langchain_text_splitters import RecursiveCharacterTextSplitter

logger = logging.getLogger(__name__)

# 中文优先的分隔符
_CHINESE_SEPARATORS = [
    "\n\n", "\n", "。", "；", "，", "、", " ", ""
]


def clean_text(text: str) -> str:
    """清洗文本: 去除 HTML 标签、多余空白、特殊字符"""
    import re
    # 去除 HTML 标签
    text = re.sub(r'<[^>]+>', '', text)
    # 去除多余空白行
    text = re.sub(r'\n{3,}', '\n\n', text)
    # 去除行首行尾空白
    text = '\n'.join(line.strip() for line in text.split('\n'))
    # 合并连续空白
    text = re.sub(r'[ \t]{2,}', ' ', text)
    return text.strip()


# 全局单例 splitter
_splitter: RecursiveCharacterTextSplitter | None = None


def _get_splitter() -> RecursiveCharacterTextSplitter:
    global _splitter
    if _splitter is None:
        _splitter = RecursiveCharacterTextSplitter(
            chunk_size=500,
            chunk_overlap=50,
            separators=_CHINESE_SEPARATORS,
            keep_separator=True,
        )
    return _splitter


def split_document(title: str, content: str, category: str = "",
                   source: str = "", parent_doc_id: str = "") -> list[dict]:
    """将文档切分为 chunk 列表, 每个 chunk 继承元数据

    Args:
        title: 文档标题
        content: 文档正文
        category: 分类标签
        source: 来源
        parent_doc_id: 父文档 ID, 为空则自动生成

    Returns:
        [{parent_doc_id, chunk_index, title, content, category, source}, ...]
    """
    if not parent_doc_id:
        parent_doc_id = uuid.uuid4().hex[:16]

    # 1. 清洗
    cleaned = clean_text(content)
    if not cleaned:
        return []

    # 2. 切分
    splitter = _get_splitter()
    chunks = splitter.split_text(cleaned)

    # 3. 组装
    results = []
    for i, chunk_text in enumerate(chunks):
        results.append({
            "parent_doc_id": parent_doc_id,
            "chunk_index": i,
            "title": title,
            "content": chunk_text,
            "category": category,
            "source": source,
        })

    logger.info("文档切分完成: title=%s, chunks=%d", title, len(results))
    return results
