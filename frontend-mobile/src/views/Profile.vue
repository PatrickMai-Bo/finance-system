<template>
  <div class="page">
    <div class="m-card profile">
      <van-icon name="manager-o" size="44" color="#2b6cb0" />
      <div class="pinfo">
        <div class="pn">{{ auth.nickname || '投资者' }}</div>
        <div class="muted">理财投资决策系统 · 手机版</div>
      </div>
    </div>

    <van-cell-group inset>
      <van-cell title="决策思维系统" icon="completed" is-link @click="go('/decision')" />
      <van-cell title="AI 模型设置" icon="smile-o" is-link @click="go('/ai')" />
      <van-cell title="自选股管理" icon="star-o" is-link @click="go('/stock')" />
      <van-cell title="自选基金管理" icon="gold-coin-o" is-link @click="go('/fund')" />
    </van-cell-group>

    <van-cell-group inset style="margin-top:10px">
      <van-cell title="退出登录" icon="down" @click="logout" />
    </van-cell-group>

    <p class="muted foot">电脑访问自动使用桌面版 · 手机访问自动使用本手机版</p>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
function go(p) { router.push(p) }
async function logout() {
  await showConfirmDialog({ title: '提示', message: '确认退出登录?' })
  auth.logout()
  router.replace('/login')
}
onMounted(() => auth.fetchMe())
</script>

<style scoped>
.page { padding-bottom: 12px; }
.profile { display: flex; align-items: center; gap: 14px; }
.pinfo .pn { font-size: 18px; font-weight: 700; }
.foot { text-align: center; font-size: 12px; margin: 16px 14px; }
</style>
