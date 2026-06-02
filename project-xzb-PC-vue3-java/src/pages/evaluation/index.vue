<!-- 评价管理 -->
<template>
  <div class="base-up-wapper bgTable min-h">
    <!-- 搜索表单区域 -->
    <SearchForm
      @handleSearch="handleSearch"
      @handleReset="handleReset"
    />
    <!-- 表格 -->
    <div class="baseList bg-wt min-h">
      <div class="tableBoxs">
        <t-table
          :data="listData"
          :columns="COLUMNS"
          row-key="id"
          vertical-align="middle"
          :hover="true"
          :pagination="pagination.total <= 10 || !pagination.total ? false : pagination"
          :loading="dataLoading"
          :sort="sort"
          table-layout="fixed"
          @page-change="onPageChange"
          @sort-change="sortChange"
        >
          <!-- 空页面 -->
          <template #empty>
            <no-data content="暂无评价数据" />
          </template>
          <!-- 序号 -->
          <template #index="{ rowIndex }">
            <span>{{ (pagination.defaultCurrent - 1) * pagination.defaultPageSize + rowIndex + 1 }}</span>
          </template>
          <!-- 操作栏 -->
          <template #op="{ row }">
            <a class="btn-dl line btn-split-right" @click="handleClickDetail(row)">
              详情
            </a>
            <a class="btn-dl line btn-split-right" @click="handleClickSummary(row)">
              AI 总结
            </a>
            <a class="font-bt line" @click="handleClickDelete(row)">删除</a>
          </template>
        </t-table>
      </div>
    </div>
    <!-- 详情弹窗 -->
    <DetailDialog
      :visible="detailVisible"
      :detail="detailData"
      @handleClose="detailVisible = false"
    />
    <!-- 删除弹窗 -->
    <Delete
      :title="'确认删除'"
      :dialog-delete-visible="dialogDeleteVisible"
      :delete-text="'确认删除该评价吗？（一经删除无法恢复）'"
      @handle-delete="handleDelete"
      @handle-close="dialogDeleteVisible = false"
    />
    <!-- AI 总结弹窗 -->
    <t-dialog
      v-model:visible="summaryVisible"
      header="AI 评价总结"
      width="800px"
      :footer="false"
    >
      <div class="summary-target">
        评价对象：<strong>{{ summaryTargetName }}</strong>
      </div>
      <div v-if="summaryLoading" class="summary-loading">
        <t-loading text="正在查询 AI 总结..." />
      </div>
      <div v-else-if="summaryRefreshing || summaryFullLoading" class="summary-loading">
        <t-loading :text="summaryFullLoading ? 'AI 正在全量生成评价总结...' : 'AI 正在增量生成评价总结...'" />
      </div>
      <div v-else-if="summaryProcessing" class="summary-processing">
        AI 评价总结正在生成中, 请稍后点击下方【查看总结】按钮
      </div>
      <div v-else-if="summaryError" class="summary-error">
        {{ summaryError }}
      </div>
      <div v-else-if="summaryContent" class="summary-content">
        <ChatMarkdown :content="summaryContent" />
      </div>
      <div v-else class="summary-empty">暂无总结数据</div>
      <div class="summary-footer">
        <button class="bt-grey wt-60" @click="summaryVisible = false">关闭</button>
        <button
          v-if="summaryProcessing"
          class="bt wt-100"
          @click="handleClickSummary(currentSummaryRow)"
        >
          查看总结
        </button>
        <template v-else>
          <button
            class="bt wt-100"
            :disabled="summaryLoading || summaryRefreshing || summaryFullLoading"
            @click="handleRefreshSummary"
          >
            {{ summaryRefreshing ? '生成中...' : '增量总结' }}
          </button>
          <button
            class="bt wt-100"
            :disabled="summaryLoading || summaryRefreshing || summaryFullLoading"
            @click="handleFullSummary"
          >
            {{ summaryFullLoading ? '生成中...' : '全量总结' }}
          </button>
        </template>
      </div>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import { getEvaluationList, getEvaluationDetail, deleteEvaluation, getEvaluationSummary, refreshEvaluationSummary, summarizeEvaluationFull } from '@/api/service'
import { COLUMNS } from './constants'
import SearchForm from './components/SearchForm.vue'
import DetailDialog from './components/DetailDialog.vue'
import Delete from '@/components/Delete/index.vue'
import NoData from '@/components/noData/index.vue'
import ChatMarkdown from '@/components/chat/ChatMarkdown.vue'

const listData = ref([])
const dataLoading = ref(false)
const detailVisible = ref(false)
const detailData = ref(null)
const dialogDeleteVisible = ref(false)
const deleteId = ref('')

// AI 总结相关
const summaryVisible = ref(false)
const summaryLoading = ref(false)       // GET 查看 loading
const summaryRefreshing = ref(false)    // POST 增量 loading
const summaryFullLoading = ref(false)   // POST 全量 loading
const summaryProcessing = ref(false)    // PROCESSING 状态 (有人正在生成中)
const summaryContent = ref('')
const summaryError = ref('')
const summaryTargetName = ref('')
const currentSummaryRow = ref(null)

// 分页
const pagination = ref({
  defaultPageSize: 10,
  total: 0,
  defaultCurrent: 1
})
// 排序
const sort = ref([{ sortBy: 'createTime', descending: true }])
// 搜索参数
const searchParams = ref({
  scoreLevel: null,
  minEvaluationTime: null,
  maxEvaluationTime: null
})
// 请求参数
const requestData = ref({
  targetTypeId: '7',
  pageNo: 1,
  pageSize: 10,
  sortBy: 1,
  scoreLevel: null,
  minEvaluationTime: null,
  maxEvaluationTime: null
})

onMounted(() => {
  fetchData()
})

// 获取列表数据
const fetchData = async () => {
  dataLoading.value = true
  try {
    const res = await getEvaluationList(requestData.value)
    // 兼容两种响应格式:
    // 1. 经过 PackResultFilter 包装: { code: 200, data: { list, total } }
    // 2. 原始 Map 格式: { list, total }
    const isWrapped = typeof res?.code !== 'undefined'
    const payload = isWrapped ? res.data : res
    if (isWrapped ? res.code === 200 : true) {
      listData.value = payload?.list || []
      pagination.value.total = Number(payload?.total || 0)
    }
  } catch (err) {
    console.error('获取评价列表失败:', err)
  } finally {
    dataLoading.value = false
  }
}

// 搜索
const handleSearch = (val) => {
  if (val.scoreLevel) {
    requestData.value.scoreLevel = val.scoreLevel
  } else {
    requestData.value.scoreLevel = null
  }
  if (val.evaluationTime && val.evaluationTime.length === 2) {
    requestData.value.minEvaluationTime = val.evaluationTime[0]
    requestData.value.maxEvaluationTime = val.evaluationTime[1]
  } else {
    requestData.value.minEvaluationTime = null
    requestData.value.maxEvaluationTime = null
  }
  requestData.value.pageNo = 1
  pagination.value.defaultCurrent = 1
  fetchData()
}

// 重置
const handleReset = () => {
  requestData.value.scoreLevel = null
  requestData.value.minEvaluationTime = null
  requestData.value.maxEvaluationTime = null
  requestData.value.pageNo = 1
  pagination.value.defaultCurrent = 1
  fetchData()
}

// 翻页
const onPageChange = (val) => {
  requestData.value.pageNo = val.defaultCurrent || val.current
  pagination.value.defaultCurrent = val.defaultCurrent || val.current
  requestData.value.pageSize = val.defaultPageSize || val.pageSize
  pagination.value.defaultPageSize = val.defaultPageSize || val.pageSize
  fetchData()
}

// 排序
const sortChange = (val) => {
  sort.value = val
  if (val && val.length > 0) {
    requestData.value.sortBy = val[0].descending ? 1 : 1
  }
  fetchData()
}

// 查看详情
const handleClickDetail = async (row) => {
  try {
    const res = await getEvaluationDetail(row.id)
    if (res.code === 200) {
      detailData.value = res.data
      detailVisible.value = true
    }
  } catch (err) {
    console.error(err)
  }
}

// 点击删除
const handleClickDelete = (row) => {
  deleteId.value = row.id
  dialogDeleteVisible.value = true
}

// AI 总结 — 打开弹窗, GET 纯查库
const handleClickSummary = async (row) => {
  currentSummaryRow.value = row
  summaryTargetName.value = row.targetName || ''
  summaryVisible.value = true
  summaryError.value = ''
  summaryContent.value = ''
  summaryProcessing.value = false
  summaryLoading.value = true
  summaryRefreshing.value = false
  summaryFullLoading.value = false

  try {
    const res = await getEvaluationSummary(7, row.targetId)
    // 兼容两种返回格式:
    // 1. PackResultFilter 包装: { code: 200, data: { summary, status } }
    // 2. Feign 异常透传:    { code: 500, msg: "..." } (AxiosResponse 层)
    const payload = res?.data
    if (!payload) {
      summaryError.value = '查询总结服务暂不可用，请稍后重试'
      return
    }

    // Feign 异常透传: code !== 200
    if (payload.code && payload.code !== 200) {
      summaryError.value = payload.msg || '查询总结服务暂不可用，请稍后重试'
      return
    }

    // 正常响应
    if (payload.summary) {
      summaryContent.value = payload.summary
    } else if (payload.status === 'EMPTY') {
      // 后台明确返回无数据 → 引导用户生成
      summaryError.value = '暂无评价总结，可点击下方【增量总结】或【全量总结】生成'
    }
    // 其他情况: summary 为空且无明确状态 → UI 兜底显示「暂无总结数据」
  } catch (err) {
    // 网络/超时等异常
    summaryError.value = '查询总结服务暂不可用，请稍后重试'
    console.error('查询 AI 总结失败:', err)
  } finally {
    summaryLoading.value = false
  }
}

// AI 总结 — POST 触发增量生成
const handleRefreshSummary = async () => {
  const row = currentSummaryRow.value
  if (!row) return

  summaryError.value = ''
  summaryContent.value = ''
  summaryProcessing.value = false
  summaryRefreshing.value = true
  summaryFullLoading.value = false
  summaryLoading.value = false

  try {
    const res = await refreshEvaluationSummary(7, row.targetId)
    // 兼容两种返回格式:
    // 1. PackResultFilter 包装: { code: 200, data: { summary, status } }
    // 2. Feign 异常透传:    { code: 500, msg: "..." } (AxiosResponse 层)
    const payload = res?.data
    if (!payload) {
      summaryError.value = 'AI总结服务暂不可用，请稍后重试'
      MessagePlugin.error('AI总结服务暂不可用')
      return
    }

    // Feign 异常透传: code !== 200
    if (payload.code && payload.code !== 200) {
      summaryError.value = payload.msg || 'AI总结服务暂不可用，请稍后重试'
      MessagePlugin.error(summaryError.value)
      return
    }

    // 正常响应
    if (payload.status === 'SUCCESS' && payload.summary) {
      summaryContent.value = payload.summary
      MessagePlugin.success('增量总结已更新')
    } else if (payload.status === 'PROCESSING') {
      summaryProcessing.value = true
      MessagePlugin.info(payload.msg || '正在生成中, 请稍后查看')
    } else if (payload.status === 'ERROR') {
      summaryError.value = payload.msg || 'AI总结服务暂不可用，请稍后重试'
      MessagePlugin.error(summaryError.value)
    } else if (payload.summary) {
      // 兜底: 有 summary 但没有明确 status
      summaryContent.value = payload.summary
    } else {
      summaryError.value = '增量总结失败，请稍后重试'
      MessagePlugin.error(summaryError.value)
    }
  } catch (err) {
    // 网络超时 (180s) 或 fetch 失败
    summaryError.value = 'AI总结服务暂不可用，请稍后重试'
    MessagePlugin.error(summaryError.value)
  } finally {
    summaryRefreshing.value = false
  }
}

// AI 总结 — POST 触发全量生成（忽略历史游标）
const handleFullSummary = async () => {
  const row = currentSummaryRow.value
  if (!row) return

  summaryError.value = ''
  summaryContent.value = ''
  summaryProcessing.value = false
  summaryFullLoading.value = true
  summaryRefreshing.value = false
  summaryLoading.value = false

  try {
    const res = await summarizeEvaluationFull(7, row.targetId)
    // 兼容两种返回格式:
    // 1. PackResultFilter 包装: { code: 200, data: { summary, status } }
    // 2. Feign 异常透传:    { code: 500, msg: "..." } (AxiosResponse 层)
    const payload = res?.data
    if (!payload) {
      summaryError.value = 'AI总结服务暂不可用，请稍后重试'
      MessagePlugin.error('AI总结服务暂不可用')
      return
    }

    // Feign 异常透传: code !== 200
    if (payload.code && payload.code !== 200) {
      summaryError.value = payload.msg || 'AI总结服务暂不可用，请稍后重试'
      MessagePlugin.error(summaryError.value)
      return
    }

    // 正常响应
    if (payload.status === 'SUCCESS' && payload.summary) {
      summaryContent.value = payload.summary
      MessagePlugin.success('全量总结已更新')
    } else if (payload.status === 'PROCESSING') {
      summaryProcessing.value = true
      MessagePlugin.info(payload.msg || '正在生成中, 请稍后查看')
    } else if (payload.status === 'ERROR') {
      summaryError.value = payload.msg || 'AI总结服务暂不可用，请稍后重试'
      MessagePlugin.error(summaryError.value)
    } else if (payload.summary) {
      // 兜底: 有 summary 但没有明确 status
      summaryContent.value = payload.summary
    } else {
      summaryError.value = '全量总结失败，请稍后重试'
      MessagePlugin.error(summaryError.value)
    }
  } catch (err) {
    // 网络超时 (180s) 或 fetch 失败
    summaryError.value = 'AI总结服务暂不可用，请稍后重试'
    MessagePlugin.error(summaryError.value)
  } finally {
    summaryFullLoading.value = false
  }
}

// 确认删除
const handleDelete = async () => {
  try {
    const res = await deleteEvaluation(deleteId.value)
    if (res.code === 200) {
      dialogDeleteVisible.value = false
      MessagePlugin.success('删除成功')
      fetchData()
    } else {
      MessagePlugin.error(res.msg || '删除失败')
    }
  } catch (err) {
    console.error(err)
  }
}
</script>

<style lang="less" scoped src="./index.less"></style>
