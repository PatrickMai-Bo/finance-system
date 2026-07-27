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
          <el-input v-model="form.username" size="large" placeholder="请输入账号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" size="large" type="password" show-password placeholder="请输入密码" :prefix-icon="Lock" @keyup.enter="onLogin" />
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="onLogin">登 录</el-button>
      </el-form>
      <div class="forgot-row">
        <el-button type="primary" link size="small" @click="showForgot = true">忘记密码</el-button>
      </div>
      <p class="tip">单用户自用 · 非投资建议 · 数据仅供参考</p>
    </div>

    <!-- 忘记密码弹框 -->
    <el-dialog v-model="showForgot" title="忘记密码" width="420px" :close-on-click-modal="false">
      <el-steps :active="forgotStep" simple style="margin-bottom: 20px">
        <el-step title="验证手机" />
        <el-step title="设置新密码" />
      </el-steps>

      <!-- 步骤 1: 输入手机号 -->
      <template v-if="forgotStep === 0">
        <el-form :model="forgotForm" label-width="80px">
          <el-form-item label="手机号码">
            <el-input v-model="forgotForm.phone" placeholder="请输入绑定的手机号码" maxlength="11" @keyup.enter="onVerifyPhone" />
          </el-form-item>
        </el-form>
        <div style="text-align: right; margin-top: 12px">
          <el-button @click="showForgot = false">取消</el-button>
          <el-button type="primary" :loading="verifyLoading" @click="onVerifyPhone">验证</el-button>
        </div>
      </template>

      <!-- 步骤 2: 设置新密码 -->
      <template v-else>
        <el-form :model="forgotForm" label-width="80px">
          <el-form-item label="新密码">
            <el-input v-model="forgotForm.newPassword" type="password" show-password placeholder="至少 6 位" @keyup.enter="onResetPassword" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="forgotForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" @keyup.enter="onResetPassword" />
          </el-form-item>
        </el-form>
        <div style="text-align: right; margin-top: 12px">
          <el-button @click="showForgot = false">取消</el-button>
          <el-button type="primary" :loading="resetLoading" @click="onResetPassword">确认修改</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import http from '../api'

const router = useRouter()
const auth = useAuthStore()
const form = ref({ username: 'admin', password: '' })
const loading = ref(false)

// 忘记密码
const showForgot = ref(false)
const forgotStep = ref(0)
const verifyLoading = ref(false)
const resetLoading = ref(false)
const resetToken = ref('')
const forgotForm = reactive({ phone: '', newPassword: '', confirmPassword: '' })

async function onLogin() {
  loading.value = true
  try {
    await auth.login(form.value.username, form.value.password)
    ElMessage.success('登录成功')
    router.push('/home')
  } catch (e) { /* handled by interceptor */ } finally {
    loading.value = false
  }
}

async function onVerifyPhone() {
  if (!forgotForm.phone || forgotForm.phone.length < 11) {
    ElMessage.warning('请输入正确的手机号码')
    return
  }
  verifyLoading.value = true
  try {
    const res = await http.post('/auth/forgot-password', { phone: forgotForm.phone })
    resetToken.value = res.data.resetToken
    ElMessage.success(res.data.msg || '验证通过')
    forgotStep.value = 1
  } catch (e) {
    // error handled by interceptor
  } finally {
    verifyLoading.value = false
  }
}

async function onResetPassword() {
  const pwd = forgotForm.newPassword
  if (!pwd || pwd.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (pwd !== forgotForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  resetLoading.value = true
  try {
    await http.post('/auth/reset-password', { resetToken: resetToken.value, newPassword: pwd })
    ElMessage.success('密码修改成功,请用新密码登录')
    showForgot.value = false
    forgotStep.value = 0
    forgotForm.phone = ''
    forgotForm.newPassword = ''
    forgotForm.confirmPassword = ''
    form.value.password = ''
  } catch (e) {
    // error handled by interceptor
  } finally {
    resetLoading.value = false
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
.forgot-row { text-align: right; margin-top: 6px; }
</style>
