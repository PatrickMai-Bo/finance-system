import { defineStore } from 'pinia'
import { authApi } from '../api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    nickname: localStorage.getItem('nickname') || ''
  }),
  getters: {
    isLogin: (s) => !!s.token
  },
  actions: {
    async login(username, password) {
      const res = await authApi.login({ username, password })
      this.token = res.data.token
      this.nickname = res.data.nickname
      localStorage.setItem('token', this.token)
      localStorage.setItem('nickname', this.nickname)
      return res
    },
    logout() {
      this.token = ''
      this.nickname = ''
      localStorage.removeItem('token')
      localStorage.removeItem('nickname')
    }
  }
})
