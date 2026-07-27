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
