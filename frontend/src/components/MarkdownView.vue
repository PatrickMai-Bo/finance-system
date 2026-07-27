<template>
  <div class="md" v-html="html"></div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  source: { type: String, default: '' }
})

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function inline(s) {
  let h = escapeHtml(s)
  // 行内加粗 → 重点(红色+浅黄底)
  h = h.replace(/\*\*([^*\n]+?)\*\*/g, '<strong class="md-bold">$1</strong>')
  // 行内代码
  h = h.replace(/`([^`\n]+?)`/g, '<code class="md-code">$1</code>')
  // 常见 emoji 单独高亮
  h = h.replace(/(✅|❌|💡|⚠️|🎯|📌|🚀|⚡|🔍|📊|💰|🏦|📈|📉|🟢|🔵|🟡)/g, '<span class="md-emoji">$1</span>')
  return h
}

function parse(src) {
  if (!src) return ''
  const lines = String(src).replace(/\r\n/g, '\n').split('\n')
  const out = []
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    const t = line.trim()
    if (!t) { i++; continue }
    if (/^-{3,}$/.test(t) || /^\*{3,}$/.test(t)) { out.push('<hr/>'); i++; continue }
    const hMatch = t.match(/^(#{1,3})\s+(.*)$/)
    if (hMatch) {
      const lvl = hMatch[1].length
      const cls = lvl === 1 ? 'md-h1' : lvl === 2 ? 'md-h2' : 'md-h3'
      out.push(`<div class="${cls}">${inline(hMatch[2])}</div>`)
      i++; continue
    }
    if (/^[-*]\s+/.test(t)) {
      const items = []
      while (i < lines.length && /^[-*]\s+/.test(lines[i].trim())) {
        items.push(lines[i].trim().replace(/^[-*]\s+/, ''))
        i++
      }
      out.push('<ul class="md-ul">' + items.map(it => `<li>${inline(it)}</li>`).join('') + '</ul>')
      continue
    }
    if (/^\d+[.)]\s+/.test(t)) {
      const items = []
      while (i < lines.length && /^\d+[.)]\s+/.test(lines[i].trim())) {
        items.push(lines[i].trim().replace(/^\d+[.)]\s+/, ''))
        i++
      }
      out.push('<ol class="md-ol">' + items.map(it => `<li>${inline(it)}</li>`).join('') + '</ol>')
      continue
    }
    // 警告行: !!! 开头 → 红色大字
    if (/^!!!\s+/.test(t)) {
      const warnText = t.replace(/^!!!\s+/, '')
      out.push(`<div class="md-warn">${inline(warnText)}</div>`)
      i++
      continue
    }
    // 段落:累积连续非空非列表行
    const para = [t]
    i++
    while (i < lines.length) {
      const nt = lines[i].trim()
      if (!nt || /^!!!\s+/.test(nt) || /^#{1,3}\s+/.test(nt) || /^[-*]\s+/.test(nt) || /^\d+[.)]\s+/.test(nt) || /^-{3,}$/.test(nt)) break
      para.push(nt)
      i++
    }
    out.push(`<p class="md-p">${inline(para.join(' '))}</p>`)
  }
  return out.join('\n')
}

const html = computed(() => parse(props.source))
</script>

<style scoped>
.md {
  font-size: 14px;
  line-height: 1.85;
  color: #303133;
  word-break: break-word;
}
/* 标题:次重点-蓝色左边框,字号适中 */
.md :deep(.md-h1) {
  font-size: 20px;
  font-weight: 800;
  color: #1f3a68;
  margin: 18px 0 12px;
  padding: 0 0 8px 12px;
  border-left: 5px solid #2b6cb0;
  background: linear-gradient(90deg, #f0f6ff, transparent);
}
.md :deep(.md-h2) {
  font-size: 17px;
  font-weight: 700;
  color: #2b4a8b;
  margin: 16px 0 10px;
  padding: 4px 0 4px 12px;
  border-left: 4px solid #2b6cb0;
  background: #f5f9ff;
  border-radius: 0 6px 6px 0;
}
.md :deep(.md-h3) {
  font-size: 15px;
  font-weight: 700;
  color: #2b4a8b;
  margin: 14px 0 8px;
  padding-left: 10px;
  border-left: 3px solid #409eff;
}
/* 段落:舒松行高 */
.md :deep(.md-p) {
  margin: 10px 0;
  color: #303133;
}
/* 无序列表:蓝色三角 */
.md :deep(.md-ul) {
  padding-left: 0;
  margin: 10px 0;
  list-style: none;
}
.md :deep(.md-ul li) {
  position: relative;
  padding: 5px 0 5px 24px;
  font-size: 14px;
  line-height: 1.85;
  border-bottom: 1px dashed #f0f2f5;
}
.md :deep(.md-ul li:last-child) { border-bottom: none; }
.md :deep(.md-ul li::before) {
  content: '▸';
  color: #409eff;
  position: absolute;
  left: 6px;
  top: 5px;
  font-weight: 700;
}
/* 有序列表:蓝色数字 */
.md :deep(.md-ol) {
  padding-left: 8px;
  margin: 10px 0;
  counter-reset: md-ol;
  list-style: none;
}
.md :deep(.md-ol li) {
  position: relative;
  padding: 5px 0 5px 30px;
  font-size: 14px;
  line-height: 1.85;
  counter-increment: md-ol;
}
.md :deep(.md-ol li::before) {
  content: counter(md-ol);
  position: absolute;
  left: 0;
  top: 5px;
  width: 22px;
  height: 22px;
  background: #2b6cb0;
  color: #fff;
  border-radius: 50%;
  text-align: center;
  font-size: 12px;
  font-weight: 700;
  line-height: 22px;
}
/* 重点加粗:红色+浅黄底,最醒目 */
.md :deep(.md-bold) {
  color: #c0392b;
  font-weight: 700;
  background: #fff7e6;
  padding: 1px 5px;
  border-radius: 3px;
  border: 1px solid #ffe7ba;
}
/* 行内代码 */
.md :deep(.md-code) {
  background: #f0f2f5;
  color: #d63384;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: Consolas, monospace;
}
/* emoji */
.md :deep(.md-emoji) {
  display: inline-block;
  margin: 0 2px;
  font-size: 15px;
  vertical-align: middle;
}
/* 分隔线 */
.md :deep(hr) {
  border: none;
  border-top: 1px dashed #dcdfe6;
  margin: 16px 0;
}
/* 链接(若 AI 输出 markdown 链接) */
.md :deep(a) {
  color: #2b6cb0;
  text-decoration: none;
  border-bottom: 1px dotted #2b6cb0;
}
.md :deep(a:hover) { color: #f56c6c; }
/* 警告行:超大红色字体 + 闪眼底色 */
.md :deep(.md-warn) {
  font-size: 28px;
  font-weight: 900;
  color: #c0392b;
  background: #fff0f0;
  padding: 14px 18px;
  margin: 14px 0;
  border-left: 6px solid #e74c3c;
  border-radius: 0 8px 8px 0;
  line-height: 1.4;
}
.md :deep(.md-warn .md-bold) {
  color: #c0392b;
  background: none;
  border: none;
  padding: 0;
}
</style>