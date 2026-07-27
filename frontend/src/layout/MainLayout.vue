<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="logo">
        <el-icon><TrendCharts /></el-icon>
        <span>理财投资决策系统</span>
      </div>
      <el-menu
        mode="horizontal"
        :default-active="activeMenu"
        class="nav"
        router
        :ellipsis="false"
        background-color="transparent"
        text-color="#dfe6f0"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/home"><el-icon><HomeFilled /></el-icon>首页</el-menu-item>
        <el-menu-item index="/finance"><el-icon><Wallet /></el-icon>个人财务</el-menu-item>
        <el-menu-item index="/fund"><el-icon><Coin /></el-icon>基金筛选</el-menu-item>
        <el-menu-item index="/stock"><el-icon><Histogram /></el-icon>股票筛选</el-menu-item>
        <el-menu-item index="/decision"><el-icon><Compass /></el-icon>决策思维</el-menu-item>
        <el-menu-item index="/ai"><el-icon><MagicStick /></el-icon>AI 设置</el-menu-item>
      </el-menu>
      <el-dropdown @command="onCommand">
        <span class="user">
          <el-icon><UserFilled /></el-icon>{{ auth.nickname || '投资者' }}
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </el-header>
    <el-main class="main">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </el-main>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const activeMenu = computed(() => route.path)

function onCommand(cmd) {
  if (cmd === 'logout') {
    auth.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout { min-height: 100vh; }
.header {
  display: flex; align-items: center;
  background: linear-gradient(90deg, #1b3a5c, #2b6cb0);
  color: #fff; padding: 0 20px; height: 60px;
}
.logo { display: flex; align-items: center; gap: 8px; font-weight: 700; font-size: 22px; margin-right: 30px; white-space: nowrap; }
.nav { flex: 1; border-bottom: none; }
.user { display: flex; align-items: center; gap: 4px; color: #fff; cursor: pointer; white-space: nowrap; }
.main { background: #f5f7fa; padding: 0; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
:deep(.el-menu--horizontal > .el-menu-item.is-active) { border-bottom: 3px solid #fff; }
</style>
