import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/login/Login.vue'
import EmployeeReview from '../views/employee/EmployeeReview.vue'
import EmployeePerformance from '../views/employee/EmployeePerformance.vue'
import MaterialStudy from '../views/employee/MaterialStudy.vue'
import IndicatorManager from '../views/admin/IndicatorManager.vue'
import ManagerDashboard from '../views/manager/ManagerDashboard.vue'
import UserManager from '../views/admin/UserManager.vue'
import AdminPerformance from '../views/admin/AdminPerformance.vue'
import MaterialManager from '../views/manager/MaterialManager.vue'
import TemplateManager from '../views/manager/TemplateManager.vue'
import ArchiveSearch from '../views/manager/ArchiveSearch.vue'
import OperationLog from '../views/admin/OperationLog.vue'

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { title: '用户登录', isPublic: true }
  },
  {
    path: '/admin/users',
    name: 'UserManager',
    component: UserManager,
    meta: { title: '用户与组织管理', roles: ['ADMIN'] }
  },
  {
    path: '/admin/operation-logs',
    name: 'OperationLog',
    component: OperationLog,
    meta: { title: '用户历史操作日志', roles: ['ADMIN'] }
  },
  {
    path: '/manager/materials',
    name: 'MaterialManager',
    component: MaterialManager,
    meta: { title: '资料与课程发布', roles: ['MANAGER'] }
  },
  {
    path: '/manager/templates',
    name: 'TemplateManager',
    component: TemplateManager,
    meta: { title: '发布考核与试卷', roles: ['MANAGER'] }
  },
  {
    path: '/manager/search',
    name: 'ArchiveSearch',
    component: ArchiveSearch,
    meta: { title: '历年绩效 ES 检索', roles: ['MANAGER'] }
  },
  {
    path: '/employee/study',
    name: 'MaterialStudy',
    component: MaterialStudy,
    meta: { title: '我的课程资料', roles: ['EMPLOYEE'] }
  },
  {
    path: '/employee/review',
    name: 'EmployeeReview',
    component: EmployeeReview,
    meta: { title: '我的考试与自评', roles: ['EMPLOYEE'] }
  },
  {
    path: '/employee/performance',
    name: 'EmployeePerformance',
    component: EmployeePerformance,
    meta: { title: '个人绩效面板', roles: ['EMPLOYEE'] }
  },
  {
    path: '/manager/dashboard',
    name: 'ManagerDashboard',
    component: ManagerDashboard,
    meta: { title: '批阅与统计看板', roles: ['MANAGER'] }
  },
  {
    path: '/admin/indicator',
    name: 'IndicatorManager',
    component: IndicatorManager,
    meta: { title: '题库与指标管理', roles: ['ADMIN'] }
  },
  {
    path: '/admin/performance',
    name: 'AdminPerformance',
    component: AdminPerformance,
    meta: { title: '公司员工绩效', roles: ['ADMIN'] }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const roleHomeMap = {
  ADMIN: '/admin/users',
  MANAGER: '/manager/dashboard',
  EMPLOYEE: '/employee/study'
}

router.beforeEach((to) => {
  if (to.meta.isPublic) return true

  const token = localStorage.getItem('token')
  const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
  if (!token || !userInfo.id || !userInfo.role) {
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    return '/login'
  }

  const roles = to.meta.roles
  if (roles && !roles.includes(userInfo.role)) {
    return roleHomeMap[userInfo.role] || '/login'
  }

  return true
})

export default router

