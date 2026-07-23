<template>
  <div class="material-manager">
    <div class="header-actions">
      <h2>学习资料与SOP发布中心</h2>
      <el-button type="primary" icon="Plus" @click="openDialog(null)">上传/发布新资料</el-button>
    </div>

    <el-table :data="tableData" border style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="资料标题" min-width="180" />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.type === 'VIDEO' ? 'danger' : 'primary'">{{ scope.row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'OFF_SHELF' ? 'info' : 'success'">
            {{ scope.row.status === 'OFF_SHELF' ? '已下架' : '上架中' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deadline" label="学习截止日期" width="170">
        <template #default="scope">{{ formatDate(scope.row.deadline) }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="发布时间" width="170">
        <template #default="scope">{{ formatDate(scope.row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="scope">
          <el-button size="small" @click="openDialog(scope.row)" :disabled="scope.row.status === 'OFF_SHELF'">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
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

    <el-dialog :title="form.id ? '编辑资料' : '发布资料'" v-model="dialogVisible" width="600px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="资料标题" required>
          <el-input v-model="form.title" placeholder="请输入课程或SOP标题" />
        </el-form-item>
        <el-form-item label="资料类型" required>
          <el-radio-group v-model="form.type">
            <el-radio label="VIDEO">视频课程</el-radio>
            <el-radio label="DOC">文档/课件</el-radio>
            <el-radio label="LINK">外部链接</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="资源链接" required>
          <el-input v-model="form.url" placeholder="http://..." />
        </el-form-item>
        <el-form-item label="学习截止日期" required>
          <el-date-picker
            v-model="form.deadline"
            type="datetime"
            placeholder="选择学习截止时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled-date="disablePastDate"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="课程简介">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import axios from 'axios';

const loading = ref(false);
const dialogVisible = ref(false);
const tableData = ref([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);
const form = ref({ id: null, title: '', type: 'DOC', url: '', description: '', deadline: null });

const formatDate = (dateStr) => {
  if (!dateStr) return '-';
  return String(dateStr).replace('T', ' ').substring(0, 16);
};

const disablePastDate = (date) => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return date.getTime() < today.getTime();
};

const fetchList = async () => {
  loading.value = true;
  try {
    const res = await axios.get('/api/material/page', {
      params: { current: currentPage.value, size: pageSize.value }
    });
    tableData.value = res.data.data.records || [];
    total.value = res.data.data.total || 0;
  } catch (error) {
    ElMessage.error('加载资料失败');
  } finally {
    loading.value = false;
  }
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

const openDialog = (row) => {
  if (row) {
    form.value = {
      ...row,
      deadline: row.deadline ? String(row.deadline).replace('T', ' ').substring(0, 19) : null
    };
  } else {
    form.value = { id: null, title: '', type: 'DOC', url: '', description: '', deadline: null };
  }
  dialogVisible.value = true;
};

const submitForm = async () => {
  if (!form.value.title) {
    ElMessage.warning('请填写资料标题');
    return;
  }
  if (!form.value.url) {
    ElMessage.warning('请填写资源链接');
    return;
  }
  if (!form.value.deadline) {
    ElMessage.warning('请选择学习截止日期');
    return;
  }
  try {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}');
    form.value.creatorId = userInfo.id || 1;

    if (form.value.id) {
      await axios.put('/api/material/update', form.value);
    } else {
      await axios.post('/api/material/add', form.value);
    }
    ElMessage.success('操作成功');
    dialogVisible.value = false;
    fetchList();
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败');
  }
};

const handleDelete = (row) => {
  ElMessageBox.confirm(`确认删除资料 [${row.title}] 吗？`, '警告', { type: 'warning' }).then(async () => {
    await axios.delete(`/api/material/${row.id}`);
    ElMessage.success('删除成功');
    if (tableData.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1;
    }
    fetchList();
  }).catch(() => {});
};

onMounted(() => {
  fetchList();
});
</script>

<style scoped>
.material-manager { padding: 20px; }
.header-actions { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.pagination-box { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
