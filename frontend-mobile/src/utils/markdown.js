// 从桌面端 MarkdownView.vue 移植的轻量 Markdown 渲染器(适配移动端)
export function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function inline(s) {
  let h = escapeHtml(s)
  h = h.replace(/\*\*([^*\n]+?)\*\*/g, '<strong>$1</strong>')
  h = h.replace(/`([^`\n]+?)`/g, '<code style="background:#f0f2f5;color:#d63384;padding:1px 6px;border-radius:4px;font-size:13px">$1</code>')
  h = h.replace(/(✅|❌|💡|⚠️|🎯|📌|🚀|⚡|🔍|📊|💰|🏦|📈|📉|🟢|🔵|🟡)/g, '<span style="margin:0 2px;font-size:15px;vertical-align:middle">$1</span>')
  return h
}

export function parseMarkdown(src) {
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
      const tag = lvl === 1 ? 'h1' : lvl === 2 ? 'h2' : 'h3'
      out.push(`<${tag}>${inline(hMatch[2])}</${tag}>`)
      i++; continue
    }
    if (/^[-*]\s+/.test(t)) {
      const items = []
      while (i < lines.length && /^[-*]\s+/.test(lines[i].trim())) {
        items.push(lines[i].trim().replace(/^[-*]\s+/, ''))
        i++
      }
      out.push('<ul>' + items.map((it) => `<li>${inline(it)}</li>`).join('') + '</ul>')
      continue
    }
    if (/^\d+[.)]\s+/.test(t)) {
      const items = []
      while (i < lines.length && /^\d+[.)]\s+/.test(lines[i].trim())) {
        items.push(lines[i].trim().replace(/^\d+[.)]\s+/, ''))
        i++
      }
      out.push('<ol>' + items.map((it) => `<li>${inline(it)}</li>`).join('') + '</ol>')
      continue
    }
    if (/^!!!\s+/.test(t)) {
      out.push(`<div style="background:#fef5e7;border-left:4px solid #e6a23c;padding:8px 12px;border-radius:6px;margin:8px 0;font-size:14px;line-height:1.8">${inline(t.replace(/^!!!\s+/, ''))}</div>`)
      i++; continue
    }
    const para = [t]
    i++
    while (i < lines.length) {
      const nt = lines[i].trim()
      if (!nt || /^!!!\s+/.test(nt) || /^#{1,3}\s+/.test(nt) || /^[-*]\s+/.test(nt) || /^\d+[.)]\s+/.test(nt) || /^-{3,}$/.test(nt)) break
      para.push(nt)
      i++
    }
    out.push(`<p>${inline(para.join(' '))}</p>`)
  }
  return out.join('\n')
}
