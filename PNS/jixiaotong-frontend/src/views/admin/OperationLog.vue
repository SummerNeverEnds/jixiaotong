<template>
  <div class="operation-log-page">
    <div class="header-actions">
      <h2>用户历史操作日志</h2>
    </div>

    <el-card shadow="never" class="mb-20">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="用户ID">
          <el-input
            v-model="userId"
            placeholder="可选，精确筛选"
            clearable
            style="width: 140px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="keyword"
            placeholder="工号 / 姓名 / 操作 / 模块"
            clearable
            style="width: 260px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table :data="tableData" border style="width: 100%" v-loading="loading">
      <el-table-column prop="createTime" label="操作时间" width="180" sortable />
      <el-table-column prop="username" label="工号" width="120" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="role" label="角色" width="110">
        <template #default="scope">
          <el-tag size="small" :type="roleTag(scope.row.role)">{{ roleName(scope.row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column prop="action" label="操作内容" min-width="200" />
      <el-table-column prop="status" label="结果" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
            {{ scope.row.status === 'SUCCESS' ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-box">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import axios from 'axios';

const route = useRoute();
const loading = ref(false);
const tableData = ref([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);
const userId = ref('');
const keyword = ref('');

const roleName = (role) => {
  if (role === 'ADMIN') return '管理员';
  if (role === 'MANAGER') return '部门经理';
  if (role === 'EMPLOYEE') return '普通员工';
  return role || '-';
};

const roleTag = (role) => {
  if (role === 'ADMIN') return 'danger';
  if (role === 'MANAGER') return 'warning';
  return 'success';
};

const fetchList = async () => {
  loading.value = true;
  try {
    const res = await axios.get('/api/operation-log/page', {
      params: {
        current: currentPage.value,
        size: pageSize.value,
        userId: userId.value ? Number(userId.value) : undefined,
        keyword: keyword.value || undefined
      }
    });
    tableData.value = res.data.data.records || [];
    total.value = res.data.data.total || 0;
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载操作日志失败');
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  currentPage.value = 1;
  fetchList();
};

const handleReset = () => {
  userId.value = '';
  keyword.value = '';
  currentPage.value = 1;
  fetchList();
};

const handleSizeChange = (val) => {
  pageSize.value = val;
  currentPage.value = 1;
  fetchList();
};

const handleCurrentChange = (val) => {
  currentPage.value = val;
  fetchList();
};

const applyRouteQuery = () => {
  if (route.query.userId) {
    userId.value = String(route.query.userId);
  }
  if (route.query.keyword) {
    keyword.value = String(route.query.keyword);
  }
};

watch(() => route.query, () => {
  applyRouteQuery();
  currentPage.value = 1;
  fetchList();
});

onMounted(() => {
  applyRouteQuery();
  fetchList();
});
</script>

<style scoped>
.operation-log-page { padding: 20px; }
.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.pagination-box {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.mb-20 { margin-bottom: 20px; }
</style>
