<template>
  <router-view v-if="$route.meta.isPublic"></router-view>

  <el-container v-else class="layout-container">
    <el-aside width="200px">
      <div class="logo">
        <img :src="logoUrl" alt="绩效通" class="logo-img" />
        <span>绩效通</span>
      </div>
      <el-menu
        :default-active="$route.path"
        router
        class="el-menu-vertical"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item-group title="系统管理层 (Admin)" v-if="userRole === 'ADMIN'">
          <el-menu-item index="/admin/users">
            <el-icon><User /></el-icon>
            <span>用户与组织管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/operation-logs">
            <el-icon><Notebook /></el-icon>
            <span>用户操作日志</span>
          </el-menu-item>
          <el-menu-item index="/admin/indicator">
            <el-icon><Tickets /></el-icon>
            <span>系统总题库管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/performance">
            <el-icon><DataAnalysis /></el-icon>
            <span>公司员工绩效</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="经理层 (Manager)" v-if="userRole === 'MANAGER'">
          <el-menu-item index="/manager/materials">
            <el-icon><FolderAdd /></el-icon>
            <span>课程资料发布</span>
          </el-menu-item>
          <el-menu-item index="/manager/templates">
            <el-icon><Promotion /></el-icon>
            <span>发布考试试卷</span>
          </el-menu-item>
          <el-menu-item index="/manager/dashboard">
            <el-icon><DataBoard /></el-icon>
            <span>批阅与统计看板</span>
          </el-menu-item>
          <el-menu-item index="/manager/search">
            <el-icon><Search /></el-icon>
            <span>ES 全文检索查询</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="员工层 (Employee)" v-if="userRole === 'EMPLOYEE'">
          <el-menu-item index="/employee/study">
            <el-icon><Reading /></el-icon>
            <span>我的课程资料</span>
          </el-menu-item>
          <el-menu-item index="/employee/review">
            <el-icon><DocumentChecked /></el-icon>
            <span>我的考试自评</span>
          </el-menu-item>
          <el-menu-item index="/employee/performance">
            <el-icon><DataAnalysis /></el-icon>
            <span>个人绩效面板</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header>
        <div class="header-breadcrumb">
          <h3>{{ $route?.meta?.title || '绩效通' }}</h3>
        </div>
        <div class="header-user">
          <el-popover
            v-if="userRole === 'EMPLOYEE' || userRole === 'MANAGER'"
            placement="bottom-end"
            :width="360"
            trigger="click"
            @show="loadNotifications"
          >
            <template #reference>
              <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notify-badge">
                <el-button circle text>
                  <el-icon :size="18"><Bell /></el-icon>
                </el-button>
              </el-badge>
            </template>
            <div class="notify-panel">
              <div class="notify-header">
                <span>消息通知</span>
                <el-button link type="primary" size="small" @click="markAllRead" :disabled="unreadCount === 0">全部已读</el-button>
              </div>
              <el-scrollbar max-height="320px">
                <div v-if="notifications.length === 0" class="notify-empty">暂无消息</div>
                <div
                  v-for="item in notifications"
                  :key="item.id"
                  class="notify-item"
                  :class="{ unread: !item.isRead }"
                  @click="openNotification(item)"
                >
                  <div class="notify-title">{{ item.title }}</div>
                  <div class="notify-content">{{ item.content }}</div>
                  <div class="notify-time">{{ formatNotifyTime(item.createTime) }}</div>
                </div>
              </el-scrollbar>
            </div>
          </el-popover>
          <el-avatar size="small" src="https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png" />
          <el-dropdown @command="handleCommand">
            <span class="el-dropdown-link" style="margin-left: 8px; cursor: pointer;">
              {{ realName }} <el-icon class="el-icon--right"><arrow-down /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="changePassword">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main>
        <router-view></router-view>
      </el-main>
    </el-container>
  </el-container>

  <el-dialog title="修改密码" v-model="passwordDialogVisible" width="420px">
    <el-form :model="passwordForm" label-width="90px">
      <el-form-item label="原密码" required>
        <el-input v-model="passwordForm.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码" required>
        <el-input v-model="passwordForm.newPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="确认密码" required>
        <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="passwordDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="passwordSubmitting" @click="submitPasswordChange">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowDown, Bell } from '@element-plus/icons-vue';
import axios from 'axios';
import logoUrl from './assets/logo.png';

const router = useRouter();
const userInfo = computed(() => {
  const info = localStorage.getItem('userInfo');
  return info ? JSON.parse(info) : { role: 'EMPLOYEE', realName: '未登录' };
});

const userRole = computed(() => userInfo.value.role);
const realName = computed(() => userInfo.value.realName);
const passwordDialogVisible = ref(false);
const passwordSubmitting = ref(false);
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});
const unreadCount = ref(0);
const notifications = ref([]);
let notifyTimer = null;

const formatNotifyTime = (dateStr) => {
  if (!dateStr) return '';
  return String(dateStr).replace('T', ' ').substring(0, 16);
};

const fetchUnreadCount = async () => {
  if (userRole.value !== 'EMPLOYEE' && userRole.value !== 'MANAGER') {
    unreadCount.value = 0;
    return;
  }
  try {
    const res = await axios.get('/api/notification/unread-count');
    unreadCount.value = res.data.data?.count || 0;
  } catch (e) {
    unreadCount.value = 0;
  }
};

const loadNotifications = async () => {
  try {
    const res = await axios.get('/api/notification/page', {
      params: { current: 1, size: 30 }
    });
    notifications.value = res.data.data?.records || [];
    await fetchUnreadCount();
  } catch (e) {
    notifications.value = [];
  }
};

const markAllRead = async () => {
  try {
    await axios.put('/api/notification/read-all');
    notifications.value = notifications.value.map(item => ({ ...item, isRead: true }));
    unreadCount.value = 0;
  } catch (e) {
    ElMessage.error('标记已读失败');
  }
};

const openNotification = async (item) => {
  try {
    if (!item.isRead) {
      await axios.put(`/api/notification/${item.id}/read`);
      item.isRead = true;
      unreadCount.value = Math.max(0, unreadCount.value - 1);
    }
  } catch (e) {
  }
  if (item.link) {
    router.push(item.link);
  }
};

const handleCommand = async (command) => {
  if (command === 'changePassword') {
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' };
    passwordDialogVisible.value = true;
    return;
  }
  if (command === 'logout') {
    try {
      await axios.post('/api/auth/logout');
    } catch (e) {
    }
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
    window.location.href = '/login';
  }
};

const submitPasswordChange = async () => {
  if (!passwordForm.value.oldPassword || !passwordForm.value.newPassword) {
    ElMessage.warning('请填写原密码和新密码');
    return;
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致');
    return;
  }
  passwordSubmitting.value = true;
  try {
    await axios.post('/api/auth/change-password', {
      userId: userInfo.value.id,
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    });
    ElMessage.success('密码修改成功，请重新登录');
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
    window.location.href = '/login';
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '修改密码失败');
  } finally {
    passwordSubmitting.value = false;
  }
};

const startNotifyPolling = () => {
  if (notifyTimer) {
    clearInterval(notifyTimer);
  }
  fetchUnreadCount();
  notifyTimer = setInterval(fetchUnreadCount, 30000);
};

watch(userRole, (role) => {
  if (role === 'EMPLOYEE' || role === 'MANAGER') {
    startNotifyPolling();
  } else if (notifyTimer) {
    clearInterval(notifyTimer);
    notifyTimer = null;
  }
});

onMounted(() => {
  if (userRole.value === 'EMPLOYEE' || userRole.value === 'MANAGER') {
    startNotifyPolling();
  }
});

onUnmounted(() => {
  if (notifyTimer) {
    clearInterval(notifyTimer);
  }
});
</script>

<style>
body {
  margin: 0;
  padding: 0;
  font-family: "Helvetica Neue", Helvetica, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "微软雅黑", Arial, sans-serif;
  background-color: #f0f2f5;
}
.layout-container {
  height: 100vh;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  background-color: #2b3643;
}
.logo-img {
  width: 34px;
  height: 34px;
  object-fit: contain;
  border-radius: 6px;
}
.el-menu-vertical {
  height: calc(100vh - 60px);
  border-right: none;
}
.el-header {
  background-color: #fff;
  color: #333;
  line-height: 60px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 1px 4px rgba(0,21,41,.08);
  padding: 0 20px;
}
.header-breadcrumb h3 {
  margin: 0;
  font-size: 18px;
}
.header-user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.notify-badge {
  margin-right: 4px;
  line-height: 1;
}
.notify-panel {
  padding: 4px 0;
}
.notify-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 600;
}
.notify-empty {
  text-align: center;
  color: #909399;
  padding: 24px 0;
}
.notify-item {
  padding: 10px 8px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
}
.notify-item:hover {
  background: #f5f7fa;
}
.notify-item.unread {
  background: #ecf5ff;
}
.notify-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.notify-content {
  margin-top: 4px;
  font-size: 13px;
  color: #606266;
  line-height: 1.4;
}
.notify-time {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.el-main {
  background-color: #f0f2f5;
  padding: 20px;
}
</style>
