<template>
  <div class="login">
    <div class="brand">
      <van-icon name="balance-o" size="40" color="#2b6cb0" />
      <h1>理财投资决策系统</h1>
      <p>智能筛选 · 财务分析 · AI 决策建议</p>
    </div>

    <van-form @submit="onLogin">
      <van-cell-group inset>
        <van-field v-model="form.username" label="账号" placeholder="请输入账号" :rules="[{ required: true, message: '请填写账号' }]" />
        <van-field v-model="form.password" type="password" label="密码" placeholder="请输入密码" :rules="[{ required: true, message: '请填写密码' }]" />
      </van-cell-group>
      <div class="actions">
        <van-button round block type="primary" native-type="submit" :loading="loading">登 录</van-button>
        <van-button round block plain type="primary" @click="showForgot = true">忘记密码</van-button>
      </div>
    </van-form>

    <!-- 忘记密码弹框 -->
    <van-popup v-model:show="showForgot" position="bottom" round :style="{ height: '70%' }">
      <div class="forgot">
        <van-nav-bar :title="step === 0 ? '验证手机' : '设置新密码'" left-text="关闭" left-arrow @click-left="showForgot = false" />
        <div class="forgot-body">
          <template v-if="step === 0">
            <p class="m-sub">请输入绑定的手机号码以验证身份</p>
            <van-field v-model="phone" label="手机号" placeholder="请输入绑定手机号" maxlength="11" />
            <van-button round block type="primary" :loading="verifyLoading" @click="onVerify">验证</van-button>
          </template>
          <template v-else>
            <van-field v-model="newPwd" type="password" label="新密码" placeholder="至少 6 位" />
            <van-field v-model="confirmPwd" type="password" label="确认密码" placeholder="再次输入新密码" />
            <van-button round block type="primary" :loading="resetLoading" @click="onReset">确认修改</van-button>
          </template>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { authApi } from '../api'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const form = reactive({ username: 'admin', password: '' })
const loading = ref(false)

const showForgot = ref(false)
const step = ref(0)
const phone = ref('')
const newPwd = ref('')
const confirmPwd = ref('')
const verifyLoading = ref(false)
const resetLoading = ref(false)
const resetToken = ref('')

async function onLogin() {
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    showSuccessToast('登录成功')
    router.replace('/home')
  } catch (e) { /* toast by interceptor */ } finally {
    loading.value = false
  }
}

async function onVerify() {
  if (!phone.value || phone.value.length < 11) { showToast('请输入正确的手机号码'); return }
  verifyLoading.value = true
  try {
    const res = await authApi.forgotPassword(phone.value)
    resetToken.value = res.data.resetToken
    showSuccessToast(res.data.msg || '验证通过')
    step.value = 1
  } catch (e) { /* toast by interceptor */ } finally {
    verifyLoading.value = false
  }
}

async function onReset() {
  if (!newPwd.value || newPwd.value.length < 6) { showToast('新密码至少 6 位'); return }
  if (newPwd.value !== confirmPwd.value) { showToast('两次输入的密码不一致'); return }
  resetLoading.value = true
  try {
    await authApi.resetPassword(resetToken.value, newPwd.value)
    showSuccessToast('密码修改成功,请用新密码登录')
    showForgot.value = false
    step.value = 0
    phone.value = ''
    newPwd.value = ''
    confirmPwd.value = ''
    form.password = ''
  } catch (e) { /* toast by interceptor */ } finally {
    resetLoading.value = false
  }
}
</script>

<style scoped>
.login { min-height: 100vh; background: linear-gradient(135deg, #1b3a5c, #2b6cb0); display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 24px; }
.brand { text-align: center; color: #fff; margin-bottom: 28px; }
.brand h1 { font-size: 20px; margin: 12px 0 4px; }
.brand p { font-size: 12px; opacity: 0.85; margin: 0; }
.login :deep(.van-cell-group) { width: 100%; max-width: 360px; }
.actions { width: 100%; max-width: 360px; margin: 18px auto 0; display: flex; flex-direction: column; gap: 12px; }
.forgot-body { padding: 16px; }
</style>
