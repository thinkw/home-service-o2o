<!-- 知识库管理 -->
<template>
  <div class="base-up-wapper bgTable min-h">
    <!-- 操作区 -->
    <div class="formBox bg-wt">
      <t-row :gutter="16">
        <t-col :span="12">
          <div class="btn-wrap">
            <t-button variant="outline" theme="primary" @click="triggerUpload">
              <template #icon>
                <UploadIcon />
              </template>
              上传文档
            </t-button>
            <input
              ref="fileInputRef"
              type="file"
              accept=".pdf,.md,.txt,.docx"
              :multiple="true"
              style="display: none"
              @change="handleFileChange"
            />
            <span class="file-type-hint">支持 .pdf .md .txt 格式</span>
            <t-button variant="outline" theme="danger" @click="handleClearAll" class="btn-split-left">
              清空知识库
            </t-button>
          </div>
        </t-col>
        <t-col :span="12">
          <div class="search-wrap">
            <t-input
              v-model="searchQuery"
              placeholder="输入关键词进行语义搜索..."
              clearable
              @enter="handleSearch"
            />
            <t-button theme="primary" @click="handleSearch" class="btn-search">
              搜索
            </t-button>
          </div>
        </t-col>
      </t-row>
    </div>

    <!-- 表格区 -->
    <div class="baseList bg-wt min-h">
      <div class="tableBoxs">
        <t-table
          :data="listData"
          :columns="columns"
          row-key="parent_doc_id"
          vertical-align="middle"
          :hover="true"
          :loading="dataLoading"
          table-layout="fixed"
        >
          <!-- 空页面 -->
          <template #empty>
            <no-data content="暂无知识库文档，请上传文档" />
          </template>
          <!-- 序号 -->
          <template #index="{ rowIndex }">
            <span>{{ rowIndex + 1 }}</span>
          </template>
          <!-- 分块数 -->
          <template #chunkCount="{ row }">
            <span class="chunk-count">{{ row.chunkCount }} 块</span>
          </template>
          <!-- 操作栏 -->
          <template #op="{ row }">
            <a class="font-bt line" @click="handleDelete(row)">删除</a>
          </template>
        </t-table>
      </div>
    </div>

    <!-- 删除确认弹窗 -->
    <Delete
      :title="'确认删除'"
      :dialog-delete-visible="dialogDeleteVisible"
      :delete-text="`确认删除文档「${deleteSource}」吗？删除后无法恢复。`"
      @handle-delete="confirmDelete"
      @handle-close="dialogDeleteVisible = false"
    />

    <!-- 清空确认弹窗 -->
    <Delete
      :title="'确认清空'"
      :dialog-delete-visible="dialogClearVisible"
      :delete-text="'确认清空整个知识库吗？所有文档将被删除，且无法恢复！'"
      @handle-delete="confirmClear"
      @handle-close="dialogClearVisible = false"
    />

    <!-- 搜索结果弹窗 -->
    <t-dialog
      v-model:visible="searchVisible"
      header="搜索结果"
      width="700px"
      :footer="false"
    >
      <div v-if="searchLoading" class="summary-loading">
        <t-loading text="正在搜索..." />
      </div>
      <div v-else-if="searchResults.length === 0" class="summary-empty">
        未找到匹配结果
      </div>
      <div v-else class="search-results-list">
        <div
          v-for="(item, index) in searchResults"
          :key="index"
          class="search-result-item"
        >
          <div class="search-result-source">
            来源: {{ item.source }}
          </div>
          <div class="search-result-content">
            {{ item.content }}
          </div>
        </div>
      </div>
      <div class="dialog-footer">
        <t-button variant="outline" @click="searchVisible = false">关闭</t-button>
      </div>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import { UploadIcon } from 'tdesign-icons-vue-next'
import { listKnowledgeDocs, uploadDocument, deleteDocument, clearKnowledge, searchKnowledge, type KnowledgeDoc, type SearchResult } from '@/api/knowledge'
import Delete from '@/components/Delete/index.vue'
import NoData from '@/components/noData/index.vue'
import './index.less'

const listData = ref<KnowledgeDoc[]>([])
const dataLoading = ref(false)
const searchQuery = ref('')
const dialogDeleteVisible = ref(false)
const dialogClearVisible = ref(false)
const deleteDocId = ref('')
const deleteSource = ref('')
const fileInputRef = ref<HTMLInputElement | null>(null)

// 搜索相关
const searchVisible = ref(false)
const searchLoading = ref(false)
const searchResults = ref<SearchResult[]>([])

// 表格列配置
const columns = [
  { colKey: 'index', title: '序号', width: '80' },
  { colKey: 'source', title: '文档名称', width: '400' },
  { colKey: 'chunkCount', title: '分块数', width: '120' },
  { colKey: 'op', title: '操作', width: '120', align: 'center' },
]

// 加载文档列表
const loadList = async () => {
  dataLoading.value = true
  try {
    const data = await listKnowledgeDocs()
    listData.value = data
  } catch (error) {
    MessagePlugin.error('加载文档列表失败')
  } finally {
    dataLoading.value = false
  }
}

// 触发文件选择
const triggerUpload = () => {
  fileInputRef.value?.click()
}

// 处理文件选择
const handleFileChange = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const files = target.files
  if (!files || files.length === 0) return

  for (let i = 0; i < files.length; i++) {
    const file = files[i]
    const ext = file.name.split('.').pop()?.toLowerCase()
    if (!['pdf', 'md', 'txt', 'docx'].includes(ext || '')) {
      MessagePlugin.error(`文件「${file.name}」格式不支持，仅支持 PDF、Markdown、TXT`)
      continue
    }

    try {
      const result = await uploadDocument(file)
      if (result.success) {
        MessagePlugin.success(`「${file.name}」上传成功`)
      } else {
        MessagePlugin.error(result.message || `「${file.name}」上传失败`)
      }
    } catch {
      MessagePlugin.error(`「${file.name}」上传失败`)
    }
  }

  // 清空 input 以便重复选择同一文件
  if (target) target.value = ''
  setTimeout(() => loadList(), 1000)
}

// 删除文档
const handleDelete = (row: KnowledgeDoc) => {
  deleteDocId.value = row.parent_doc_id
  deleteSource.value = row.source
  dialogDeleteVisible.value = true
}

// 确认删除
const confirmDelete = async () => {
  try {
    // 使用 parent_doc_id 删除
    const result = await deleteDocument(deleteDocId.value)
    if (result.success) {
      MessagePlugin.success('删除成功')
      dialogDeleteVisible.value = false
      loadList()
    } else {
      MessagePlugin.error(result.message || '删除失败')
    }
  } catch (error) {
    MessagePlugin.error('删除失败')
  }
}

// 清空知识库
const handleClearAll = () => {
  dialogClearVisible.value = true
}

// 确认清空
const confirmClear = async () => {
  try {
    const result = await clearKnowledge()
    if (result.success) {
      MessagePlugin.success('知识库已清空')
      dialogClearVisible.value = false
      loadList()
    } else {
      MessagePlugin.error(result.message || '清空失败')
    }
  } catch (error) {
    MessagePlugin.error('清空失败')
  }
}

// 搜索
const handleSearch = async () => {
  if (!searchQuery.value.trim()) {
    MessagePlugin.warning('请输入搜索关键词')
    return
  }
  searchVisible.value = true
  searchLoading.value = true
  try {
    const results = await searchKnowledge(searchQuery.value)
    searchResults.value = results
  } catch (error) {
    MessagePlugin.error('搜索失败')
  } finally {
    searchLoading.value = false
  }
}

onMounted(() => {
  loadList()
})
</script>
