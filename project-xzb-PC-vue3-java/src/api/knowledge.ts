/**
 * RAG 知识库接口 - 对接 Java 后端 (转发到 Python AI Engine)
 */
import proxy from '@/config/proxy'
import { TOKEN_NAME } from '@/config/global'

const env = import.meta.env.MODE || 'development'
const baseHost = env === 'mock' || !proxy.isRequestProxy ? '' : proxy[env].host

export interface KnowledgeDoc {
  parent_doc_id: string  // 文档ID，用于删除/更新
  title: string
  content?: string
  category: string
  source: string
  chunkCount?: number
  create_time?: number
}

export interface SearchResult {
  parent_doc_id: string
  title: string
  content: string
  score?: number
}

export interface PageResult {
  total: number
  page: number
  size: number
  list: KnowledgeDoc[]
}

/** 获取知识库文档列表 */
export async function listKnowledgeDocs(page = 1, size = 20, category = ''): Promise<KnowledgeDoc[]> {
  const token = localStorage.getItem(TOKEN_NAME)
  const prefix = baseHost ? '' : '/api'
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (category) params.append('category', category)

  const response = await fetch(`${baseHost}${prefix}/ai/consumer/knowledge/documents?${params}`, {
    headers: { Authorization: token || '' },
  })
  if (!response.ok) return []
  const json = await response.json()
  const result: PageResult = json.data || { total: 0, page: 1, size: 20, list: [] }
  return result.list || []
}

/** 添加文档到知识库 */
export async function addDocument(params: {
  title: string
  content: string
  category?: string
  source?: string
}): Promise<{ success: boolean; message?: string }> {
  const token = localStorage.getItem(TOKEN_NAME)
  const prefix = baseHost ? '' : '/api'
  const response = await fetch(`${baseHost}${prefix}/ai/consumer/knowledge/documents`, {
    method: 'POST',
    headers: {
      Authorization: token || '',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(params),
  })

  const json = await response.json()
  return {
    success: response.ok,
    message: json.message || (response.ok ? '添加成功' : '添加失败'),
  }
}

/** 更新知识库文档 */
export async function updateDocument(
  parentDocId: string,
  params: { title: string; content: string; category?: string; source?: string }
): Promise<{ success: boolean; message?: string }> {
  const token = localStorage.getItem(TOKEN_NAME)
  const prefix = baseHost ? '' : '/api'
  const response = await fetch(`${baseHost}${prefix}/ai/consumer/knowledge/documents/${encodeURIComponent(parentDocId)}`, {
    method: 'PUT',
    headers: {
      Authorization: token || '',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(params),
  })

  const json = await response.json()
  return {
    success: response.ok,
    message: json.message || (response.ok ? '更新成功' : '更新失败'),
  }
}

/** 删除指定文档 (使用 parent_doc_id) */
export async function deleteDocument(parentDocId: string): Promise<{ success: boolean; message?: string }> {
  const token = localStorage.getItem(TOKEN_NAME)
  const prefix = baseHost ? '' : '/api'
  const response = await fetch(`${baseHost}${prefix}/ai/consumer/knowledge/documents/${encodeURIComponent(parentDocId)}`, {
    method: 'DELETE',
    headers: { Authorization: token || '' },
  })
  const json = await response.json()
  return {
    success: response.ok,
    message: json.message || (response.ok ? '删除成功' : '删除失败'),
  }
}

/** 清空整个知识库 */
export async function clearKnowledge(): Promise<{ success: boolean; message?: string }> {
  const token = localStorage.getItem(TOKEN_NAME)
  const prefix = baseHost ? '' : '/api'
  const response = await fetch(`${baseHost}${prefix}/ai/consumer/knowledge/documents`, {
    method: 'DELETE',
    headers: { Authorization: token || '' },
  })
  const json = await response.json()
  return {
    success: response.ok,
    message: json.message || (response.ok ? '清空成功' : '清空失败'),
  }
}

/** 语义搜索知识库 */
export async function searchKnowledge(query: string, topK = 3): Promise<SearchResult[]> {
  const token = localStorage.getItem(TOKEN_NAME)
  const prefix = baseHost ? '' : '/api'
  const response = await fetch(`${baseHost}${prefix}/ai/consumer/knowledge/search`, {
    method: 'POST',
    headers: {
      Authorization: token || '',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query, top_k: topK }),
  })
  if (!response.ok) return []
  const json = await response.json()
  return json.results || []
}

/** 上传文件到知识库 (读取文件内容后以 JSON 发送) */
export async function uploadDocument(file: File): Promise<{ success: boolean; message?: string }> {
  const token = localStorage.getItem(TOKEN_NAME)
  const prefix = baseHost ? '' : '/api'

  // 读取文件内容
  const content = await file.text()

  // 发送到后端
  const response = await fetch(`${baseHost}${prefix}/ai/consumer/knowledge/documents`, {
    method: 'POST',
    headers: {
      Authorization: token || '',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      title: file.name,
      content: content,
      category: 'default',
      source: file.name,
    }),
  })

  const json = await response.json()
  return {
    success: response.ok,
    message: json.message || (response.ok ? '上传成功' : '上传失败'),
  }
}
