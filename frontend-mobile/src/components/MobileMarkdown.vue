<template>
  <div class="md" v-html="html"></div>
</template>

<script setup>
import { computed } from 'vue'
import { parseMarkdown } from '../utils/markdown'

const props = defineProps({
  source: { type: String, default: '' }
})
const html = computed(() => parseMarkdown(props.source))
</script>

<style>
.md { word-break: break-word; overflow-wrap: break-word; }
.md h3 { font-size: 16px; color: #2b4a8b; border-left: 3px solid #2b6cb0; padding-left: 10px; margin: 14px 0 8px; }
.md b, .md strong { color: #c0392b; background: #fef9e7; padding: 1px 4px; border-radius: 3px; font-weight: 700; }
.md li { font-size: 14px; line-height: 1.85; margin: 4px 0; }
.md p { font-size: 14px; line-height: 1.85; margin: 6px 0; }
.md ul, .md ol { padding-left: 20px; margin: 4px 0; }
.md code { background: #f4f4f5; padding: 2px 6px; border-radius: 4px; font-size: 12px; color: #e74c3c; }
.md pre { background: #2d2d2d; color: #f8f8f2; padding: 12px; border-radius: 8px; font-size: 12px; overflow-x: auto; white-space: pre-wrap; }
.md table { width: 100%; border-collapse: collapse; margin: 8px 0; font-size: 12px; }
.md th, .md td { border: 1px solid #dcdfe6; padding: 6px 8px; text-align: left; }
.md th { background: #f5f7fa; font-weight: 600; }
.md img { max-width: 100%; height: auto; border-radius: 8px; }
</style>
