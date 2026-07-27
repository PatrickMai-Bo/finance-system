import { defineStore } from 'pinia'
import { authApi } from '../api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    nickname: '投资者'
  }),
  actions: {
    async login(username, password) {
      const res = await authApi.login({ username, password })
      this.token = res.data.token
      this.nickname = res.data.nickname || '投资者'
      localStorage.setItem('token', this.token)
    },
    async fetchMe() {
      try {
        const res = await authApi.me()
        this.nickname = res.data.nickname || '投资者'
      } catch (e) { /* ignore */ }
    },
    logout() {
      this.token = ''
      localStorage.removeItem('token')
    }
  }
})
