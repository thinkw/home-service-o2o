<!-- Markdown 渲染组件 — 零依赖自包含解析 -->
<template>
  <div class="chat-markdown" v-html="renderedHtml"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  content: string
}>()

/**
 * 轻量 Markdown → HTML 解析器
 * 覆盖 AI 总结常用语法：标题/粗体/斜体/行内代码/代码块/列表/表格/引用/分割线/删除线
 */
function parseMd(md: string): string {
  if (!md) return ''

  let html = md

  // 0. XSS 防护：转义 HTML 标签（在 markdown 解析之前）
  html = html
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // 1. 代码块 ```lang\n...\n```
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, (_m, _lang, code) => {
    return `<pre><code>${code.trimEnd()}</code></pre>`
  })

  // 2. 行内代码 `code`
  html = html.replace(/`([^`\n]+)`/g, '<code>$1</code>')

  // 3. 标题 ## ~ ######
  html = html.replace(/^######\s+(.+)$/gm, '<h6>$1</h6>')
  html = html.replace(/^#####\s+(.+)$/gm, '<h5>$1</h5>')
  html = html.replace(/^####\s+(.+)$/gm, '<h4>$1</h4>')
  html = html.replace(/^###\s+(.+)$/gm, '<h3>$1</h3>')
  html = html.replace(/^##\s+(.+)$/gm, '<h2>$1</h2>')
  html = html.replace(/^#\s+(.+)$/gm, '<h1>$1</h1>')

  // 4. 引用块 > （支持多行）
  html = html.replace(/^(?:>\s*.+)(?:\n>\s*.+)*/g, (block) => {
    const lines = block.split('\n').map((l) => l.replace(/^>\s?/, ''))
    return `<blockquote>${lines.join('<br>')}</blockquote>`
  })

  // 5. 分割线 --- 或 *** 或 ___
  html = html.replace(/^(?:---|\*\*\*|___)\s*$/gm, '<hr>')

  // 6. 无序列表 - 或 *
  html = html.replace(/^[\s]*[-*]\s+(.+)$/gm, '<li>$1</li>')
  // 将连续的 <li> 包裹为 <ul>
  html = html.replace(/((?:<li>.*<\/li>(?:<br>)?)+)/g, '<ul>$1</ul>')

  // 7. 有序列表 1.
  html = html.replace(/^[\s]*\d+\.\s+(.+)$/gm, '<oli>$1</oli>')
  html = html.replace(/((?:<oli>.*<\/oli>(?:<br>)?)+)/g, (_m, content) => {
    return '<ol>' + content.replace(/<(\/?)oli>/g, '<$1li>') + '</ol>'
  })

  // 8. 表格 | ... |
  const tableRegex = /^\|.+\|$\n(\|[-:\s|]+\|\n)?((?:\|.+\|$\n?)+)/gm
  html = html.replace(tableRegex, (_match, _separatorRow, body) => {
    const rows = body.trim().split('\n').filter(Boolean)
    if (rows.length === 0) return _match

    const parseCells = (row: string): string =>
      row
        .split('|')
        .filter((c) => c.trim() !== '')
        .map((c) => c.trim())
        .map((c) => `<td>${c}</td>`)
        .join('')

    const trs = rows
      .map((row, i) => {
        const tag = i === 0 ? 'th' : 'td'
        const cells = row
          .split('|')
          .filter((c) => c.trim() !== '')
          .map((c) => c.trim())
          .map((c) => `<${tag}>${c}</${tag}>`)
          .join('')
        return `<tr>${cells}</tr>`
      })
      .join('')

    return `<table>${trs}</table>`
  })

  // 9. 粗体 **text**
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')

  // 10. 斜体 *text*
  html = html.replace(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g, '<em>$1</em>')

  // 11. 删除线 ~~text~~
  html = html.replace(/~~(.+?)~~/g, '<del>$1</del>')

  // 12. 段落处理：将连续的非标签文本包裹为 <p>
  // 先按双换行分段
  const segments = html.split(/\n{2,}/)
  html = segments
    .map((seg) => {
      const trimmed = seg.trim()
      if (!trimmed) return ''
      // 已被块级元素包裹的不再包 <p>
      if (/^<(h[1-6]|ul|ol|li|blockquote|pre|table|hr|div)/.test(trimmed)) {
        return trimmed
      }
      // 单行内联内容包 <p>
      return `<p>${trimmed.replace(/\n/g, '<br>')}</p>`
    })
    .join('\n')

  // 13. 清理多余空行
  html = html.replace(/\n{3,}/g, '\n\n')

  return html.trim()
}

const renderedHtml = computed(() => parseMd(props.content))
</script>

<style lang="less">
.chat-markdown {
  font-size: 14px;
  line-height: 1.75;
  word-break: break-word;
  overflow-wrap: break-word;
  color: #333;

  h1, h2, h3, h4, h5, h6 {
    margin-top: 16px;
    margin-bottom: 8px;
    line-height: 1.4;
    font-weight: 600;

    &:first-child { margin-top: 0; }
  }
  h1 { font-size: 22px; border-bottom: 1px solid #e8e8e8; padding-bottom: 8px; }
  h2 { font-size: 19px; border-bottom: 1px solid #eee; padding-bottom: 6px; }
  h3 { font-size: 17px; }
  h4 { font-size: 16px; }
  h5 { font-size: 15px; }
  h6 { font-size: 14px; color: #666; }

  p { margin: 8px 0; }

  strong { font-weight: 600; color: #1a1a1a; }
  em { font-style: italic; }

  code {
    background: rgba(175, 184, 193, 0.2);
    border-radius: 3px;
    padding: 2px 5px;
    font-size: 13px;
    font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
    color: #e34d59;
  }

  pre {
    background: #f6f8fa;
    border-radius: 6px;
    padding: 12px 16px;
    overflow-x: auto;
    margin: 10px 0;
    border: 1px solid #e8e8e8;

    code {
      background: none;
      padding: 0;
      color: #24292f;
      font-size: 13px;
    }
  }

  blockquote {
    border-left: 4px solid #d9d9d9;
    padding: 8px 16px;
    margin: 10px 0;
    color: #555;
    background: #fafafa;
    border-radius: 0 4px 4px 0;
  }

  ul, ol {
    padding-left: 22px;
    margin: 8px 0;

    ul, ol { margin: 4px 0; }
  }
  ul { list-style-type: disc; }
  ol { list-style-type: decimal; }

  li {
    margin: 4px 0;
    line-height: 1.65;
  }

  table {
    border-collapse: collapse;
    max-width: 100%;
    margin: 10px 0;
    display: table; /* 修复: display:block 会摧毁表格布局,导致竖线消失 */

    th, td {
      border: 1px solid #ddd;
      padding: 8px 12px;
      text-align: left;
      white-space: nowrap;
    }

    th {
      background: #f5f5f5;
      font-weight: 600;
      font-size: 13px;
    }

    td { font-size: 13px; }

    tr:nth-child(even) td { background: #fafafa; }
  }

  hr {
    border: none;
    border-top: 1px solid #e8e8e8;
    margin: 16px 0;
  }

  a { color: #0066cc; text-decoration: none; &:hover { text-decoration: underline; } }

  img { max-width: 100%; border-radius: 4px; margin: 8px 0; }

  del { color: #999; text-decoration: line-through; }
}
</style>
