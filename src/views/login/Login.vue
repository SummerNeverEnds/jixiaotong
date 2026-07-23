<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <img :src="logoUrl" alt="绩效通" class="login-logo" />
        <p class="subtitle">员工绩效与技能评测系统 V1.0</p>
      </div>

      <el-form :model="loginForm" :rules="rules" ref="loginFormRef" class="login-form">
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            prefix-icon="User"
            placeholder="请输入工号"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            prefix-icon="Lock"
            type="password"
            placeholder="请输入密码"
            show-password
            size="large"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-button type="primary" class="login-btn" size="large" :loading="loading" @click="handleLogin">
          立即登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import axios from 'axios';
import logoUrl from '../../assets/logo.png';

const loginFormRef = ref(null);
const loading = ref(false);

const loginForm = ref({
  username: '',
  password: ''
});

const rules = {
  username: [{ required: true, message: '工号不能为空', trigger: 'blur' }],
  password: [{ required: true, message: '密码不能为空', trigger: 'blur' }]
};

const handleLogin = async () => {
  if (!loginFormRef.value) return;

  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return;
    loading.value = true;
    try {
      const res = await axios.post('/api/auth/login', loginForm.value);
      const loginData = res.data.data;
      if (!loginData?.token || !loginData?.user) {
        ElMessage.error('登录响应异常，请稍后重试');
        return;
      }
      localStorage.setItem('token', loginData.token);
      localStorage.setItem('userInfo', JSON.stringify(loginData.user));
      ElMessage.success(`欢迎回来，${loginData.user.realName}`);

      if (loginData.user.role === 'ADMIN') {
        window.location.href = '/admin/users';
      } else if (loginData.user.role === 'MANAGER') {
        window.location.href = '/manager/dashboard';
      } else {
        window.location.href = '/employee/study';
      }
    } catch (error) {
      ElMessage.error(error.response?.data?.message || '登录失败，请检查账号密码');
    } finally {
      loading.value = false;
    }
  });
};
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #2b3643;
  background-image: linear-gradient(135deg, #2b3643 0%, #1a232c 100%);
}
.login-box {
  width: 400px;
  background: #fff;
  border-radius: 8px;
  padding: 40px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}
.login-header {
  text-align: center;
  margin-bottom: 30px;
}
.login-logo {
  width: 180px;
  max-width: 80%;
  display: block;
  margin: 0 auto;
}
.subtitle {
  margin: 10px 0 0 0;
  font-size: 14px;
  color: #909399;
}
.login-btn {
  width: 100%;
  margin-top: 10px;
}
</style>
