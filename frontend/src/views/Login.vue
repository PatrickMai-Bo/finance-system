<template>
  <div class="login-wrap">
    <div class="login-box">
      <div class="brand">
        <el-icon :size="34"><TrendCharts /></el-icon>
        <h1>理财投资决策系统</h1>
        <p>智能筛选 · 财务分析 · AI 决策建议</p>
      </div>
      <el-form :model="form" @submit.prevent="onLogin">
        <el-form-item>
          <el-input v-model="form.username" size="large" placeholder="账号 (admin)" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" size="large" type="password" show-password placeholder="密码 (admin123)" :prefix-icon="Lock" @keyup.enter="onLogin" />
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="onLogin">登 录</el-button>
      </el-form>
      <p class="tip">单用户自用 · 非投资建议 · 数据仅供参考</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const form = ref({ username: 'admin', password: 'admin123' })
const loading = ref(false)

async function onLogin() {
  loading.value = true
  try {
    await auth.login(form.value.username, form.value.password)
    ElMessage.success('登录成功')
    router.push('/home')
  } catch (e) { /* handled */ } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap { height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1b3a5c 0%, #2b6cb0 100%); }
.login-box { width: 380px; background: #fff; border-radius: 16px; padding: 40px 34px; box-shadow: 0 20px 60px rgba(0,0,0,0.25); }
.brand { text-align: center; margin-bottom: 24px; color: #2b6cb0; }
.brand h1 { font-size: 20px; margin: 8px 0 6px; color: #1f2d3d; }
.brand p { font-size: 12px; color: #909399; margin: 0; }
.tip { text-align: center; font-size: 12px; color: #c0c4cc; margin-top: 18px; }
</style>
