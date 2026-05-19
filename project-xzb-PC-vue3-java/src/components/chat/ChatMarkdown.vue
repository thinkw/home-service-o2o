<!-- Markdown 渲染组件 — cherry-markdown 纯预览渲染 -->
<template>
  <div class="chat-markdown-wrapper">
    <!-- Cherry 编辑器挂载点（隐藏） -->
    <div ref="cherryMountRef" class="cherry-mount"></div>
    <!-- 实际渲染内容 -->
    <div class="chat-markdown" v-html="renderedHtml"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps<{
  content: string
}>()

const renderedHtml = ref('')
const cherryMountRef = ref<HTMLDivElement>()
let cherryInstance: any = null

/** 初始化 Cherry 实例 */
const initCherry = async () => {
  if (!cherryMountRef.value) return

  try {
    // 动态导入 cherry-markdown 主包 ESM
    const Cherry = (await import('cherry-markdown')).default

    // 在隐藏的 DOM 上初始化 Cherry，关闭工具栏和编辑区
    cherryInstance = new Cherry({
      el: cherryMountRef.value,
      defaultToolbar: false,
      isPreviewOnly: true,
      engine: {
        global: {
          flowSessionContext: true,
        },
        syntax: {
          mathBlock: {
            engine: 'katex',
            katexConfig: {
              trust: true,
              strict: false,
              throwOnError: false,
            },
          },
          inlineMath: {
            engine: 'katex',
            katexConfig: {
              trust: true,
              strict: false,
              throwOnError: false,
            },
          },
          codeBlock: {
            wrap: false,
            lineNumber: false,
            copyCode: true,
          },
        },
      },
    })

    // 使用引擎做纯 Markdown → HTML 转换
    renderContent()
  } catch (e: any) {
    console.error('[ChatMarkdown] Cherry init error:', e?.message || e)
    // 降级：直接显示原始内容
    renderedHtml.value = props.content ? props.content.replace(/\n/g, '<br>') : ''
  }
}

/** 渲染当前内容 */
const renderContent = () => {
  if (!cherryInstance) {
    renderedHtml.value = props.content || ''
    return
  }
  if (!props.content) {
    renderedHtml.value = ''
    return
  }
  try {
    // 使用 engine.makeHtml() 做纯渲染
    renderedHtml.value = cherryInstance.engine.makeHtml(props.content)
  } catch (e: any) {
    console.error('[ChatMarkdown] makeHtml error:', e?.message || e)
    renderedHtml.value = props.content.replace(/\n/g, '<br>')
  }
}

onMounted(() => {
  initCherry()
})

watch(() => props.content, () => {
  renderContent()
})

onBeforeUnmount(() => {
  if (cherryInstance) {
    cherryInstance.destroy()
    cherryInstance = null
  }
})
</script>

<style lang="less">
.chat-markdown-wrapper {
  position: relative;
}

/* 隐藏 Cherry 编辑器自身产生的所有 DOM（工具栏、编辑区、预览区等） */
.cherry-mount {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
  pointer-events: none;
  opacity: 0;

  // 确保内部所有子元素都不可见
  * {
    display: none !important;
  }
}

.chat-markdown {
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  overflow-wrap: break-word;
  color: #333;

  img {
    max-width: 100%;
    border-radius: 4px;
    margin: 8px 0;
  }

  pre {
    background: #f6f8fa;
    border-radius: 4px;
    padding: 12px;
    overflow-x: auto;
  }

  code {
    background: #f0f0f0;
    border-radius: 3px;
    padding: 2px 5px;
    font-size: 13px;
  }

  pre code {
    background: none;
    padding: 0;
  }

  table {
    border-collapse: collapse;
    width: 100%;
    margin: 10px 0;

    th, td {
      border: 1px solid #e8e8e8;
      padding: 8px 12px;
      text-align: left;
    }

    th {
      background: #fafafa;
      font-weight: 600;
    }
  }

  blockquote {
    border-left: 4px solid #d9d9d9;
    padding: 8px 16px;
    margin: 10px 0;
    color: #666;
    background: #fafafa;
  }

  h1, h2, h3, h4, h5, h6 {
    margin-top: 16px;
    margin-bottom: 8px;
    line-height: 1.4;
    font-weight: 600;
  }

  p { margin: 8px 0; }

  ul, ol { padding-left: 20px; margin: 8px 0; }

  li { margin: 4px 0; }

  strong { font-weight: 600; }

  a {
    color: #0066cc;
    text-decoration: none;
    &:hover { text-decoration: underline; }
  }
}
</style>
