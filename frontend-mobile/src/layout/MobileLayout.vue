<template>
  <div class="layout">
    <van-nav-bar :title="title" fixed placeholder>
      <template #right>
        <van-icon name="manager-o" @click="goProfile" />
      </template>
    </van-nav-bar>

    <div class="content">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </div>

    <van-tabbar route fixed placeholder>
      <van-tabbar-item to="/home" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item to="/finance" icon="balance-o">财务</van-tabbar-item>
      <van-tabbar-item to="/stock" icon="chart-trending-o">股票</van-tabbar-item>
      <van-tabbar-item to="/fund" icon="gold-coin-o">基金</van-tabbar-item>
      <van-tabbar-item to="/profile" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const titleMap = {
  home: '总览看板', finance: '个人财务', stock: '股票筛选', fund: '基金筛选',
  decision: '决策思维', ai: 'AI 设置', profile: '我的'
}
const title = computed(() => titleMap[route.name] || '理财投资决策系统')
function goProfile() { router.push('/profile') }
</script>

<style scoped>
.layout { min-height: 100vh; display: flex; flex-direction: column; }
.content { flex: 1; overflow-y: auto; padding-bottom: 8px; }
</style>
