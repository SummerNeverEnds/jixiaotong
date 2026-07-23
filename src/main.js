import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import App from './App.vue'
import router from './router/index.js'

const app = createApp(App)

axios.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
  if (userInfo.id) {
    config.headers['X-User-Id'] = userInfo.id
  }
  return config
})

axios.interceptors.response.use(
  (response) => {
    if (response.data && response.data.code && response.data.code !== 200) {
      if (response.data.code === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        if (window.location.pathname !== '/login') {
          ElMessage.error(response.data.message || '登录已失效，请重新登录')
          window.location.href = '/login'
        }
      }
      return Promise.reject({ response })
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      if (window.location.pathname !== '/login') {
        ElMessage.error(error.response?.data?.message || '登录已失效，请重新登录')
        window.location.href = '/login'
      }
    } else if (error.response?.status === 429) {
      ElMessage.warning(error.response?.data?.message || '请求过于频繁，请稍后再试')
    }
    return Promise.reject(error)
  }
)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(ElementPlus)
app.use(router)

app.mount('#app')
