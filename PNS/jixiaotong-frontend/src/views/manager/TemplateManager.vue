<template>
  <div class="template-manager">
    <div class="header-actions">
      <h2>考核与试卷发布</h2>
      <el-button type="primary" icon="Promotion" @click="openDialog()">组卷与发布</el-button>
    </div>

    <el-table :data="tableData" border style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="试卷ID" width="80" />
      <el-table-column prop="name" label="考核名称" min-width="180" />
      <el-table-column prop="cycleName" label="考核周期" width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'PUBLISHED' ? 'success' : 'info'">
            {{ scope.row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="durationMinutes" label="考试时长(分钟)" width="130" />
      <el-table-column prop="deadline" label="截止日期" width="170">
        <template #default="scope">{{ formatDate(scope.row.deadline) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="scope">
          <template v-if="scope.row.status !== 'PUBLISHED'">
            <el-button size="small" type="success" :loading="publishingId === scope.row.id" @click="handlePublish(scope.row)">发布</el-button>
            <el-button size="small" @click="openDialog(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
          </template>
          <el-button v-else size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
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

    <el-dialog :title="form.id ? '编辑考核试卷' : '新建考核试卷'" v-model="dialogVisible" width="560px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="试卷名称" required>
          <el-input v-model="form.name" placeholder="请输入考核或试卷名称" />
        </el-form-item>
        <el-form-item label="考核周期">
          <el-tag type="info">{{ currentCycleName }}</el-tag>
        </el-form-item>
        <el-form-item label="考试时长(分钟)" required>
          <el-input-number v-model="form.durationMinutes" :min="10" :max="300" />
        </el-form-item>
        <el-form-item label="考核截止日期" required>
          <el-date-picker
            v-model="form.deadline"
            type="datetime"
            placeholder="选择考试截止时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled-date="disablePastDate"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="客观题数量" required>
          <el-input-number v-model="form.objectiveCount" :min="0" :max="objectiveTotal" />
        </el-form-item>
        <el-form-item label="主观题数量" required>
          <el-input-number v-model="form.subjectiveCount" :min="0" :max="subjectiveTotal" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">保存试卷配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import axios from 'axios';

const loading = ref(false);
const dialogVisible = ref(false);
const publishingId = ref(null);
const tableData = ref([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);
const indicatorList = ref([]);
const form = ref({ id: null, name: '', durationMinutes: 120, deadline: null, status: 'UNPUBLISHED', objectiveCount: 2, subjectiveCount: 2 });

const levels = ['P1', 'P2', 'P3', 'P4'];
const countByTypeAndLevel = (type, level) => indicatorList.value.filter(item => item.type === type && item.jobLevel === level).length;
const minCountByType = (type) => Math.min(...levels.map(level => countByTypeAndLevel(type, level)));

const objectiveTotal = computed(() => {
  const n = minCountByType('OBJECTIVE');
  return Number.isFinite(n) ? n : 0;
});
const subjectiveTotal = computed(() => {
  const n = minCountByType('SUBJECTIVE');
  return Number.isFinite(n) ? n : 0;
});
const currentCycleName = computed(() => {
  const now = new Date();
  const quarter = Math.floor(now.getMonth() / 3) + 1;
  return `${now.getFullYear()}-Q${quarter}`;
});

const getCurrentUser = () => JSON.parse(localStorage.getItem('userInfo') || '{}');

const formatDate = (dateStr) => {
  if (!dateStr) return '-';
  return String(dateStr).replace('T', ' ').substring(0, 16);
};

const disablePastDate = (date) => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return date.getTime() < today.getTime();
};

const emptyForm = () => ({
  id: null,
  name: '',
  durationMinutes: 120,
  deadline: null,
  status: 'UNPUBLISHED',
  objectiveCount: 2,
  subjectiveCount: 2
});

const fetchList = async () => {
  loading.value = true;
  try {
    const res = await axios.get('/api/template/page', {
      params: {
        current: currentPage.value,
        size: pageSize.value,
        managerId: getCurrentUser().id
      }
    });
    tableData.value = res.data.data.records || [];
    total.value = res.data.data.total || 0;
  } catch (error) {
    ElMessage.error('加载试卷列表失败');
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

const fetchIndicators = async () => {
  try {
    const res = await axios.get('/api/indicator/list');
    indicatorList.value = res.data.data || [];
  } catch (error) {
    console.error('加载题库失败', error);
  }
};

const openDialog = (row) => {
  if (row) {
    if (row.status === 'PUBLISHED') {
      ElMessage.warning('已发布试卷不可编辑');
      return;
    }
    form.value = {
      id: row.id,
      name: row.name,
      durationMinutes: row.durationMinutes || 120,
      deadline: row.deadline ? String(row.deadline).replace('T', ' ').substring(0, 19) : null,
      status: 'UNPUBLISHED',
      objectiveCount: row.objectiveCount ?? 2,
      subjectiveCount: row.subjectiveCount ?? 2
    };
  } else {
    form.value = emptyForm();
  }
  dialogVisible.value = true;
};

const submitForm = async () => {
  if (!form.value.name) {
    ElMessage.warning('请填写试卷名称');
    return;
  }
  if (!form.value.deadline) {
    ElMessage.warning('请选择考核截止日期');
    return;
  }
  if ((form.value.objectiveCount || 0) + (form.value.subjectiveCount || 0) <= 0) {
    ElMessage.warning('请至少抽取一道题目');
    return;
  }
  try {
    const userInfo = getCurrentUser();
    form.value.managerId = userInfo.id || 1;
    form.value.status = 'UNPUBLISHED';
    await axios.post('/api/template/save', form.value);
    ElMessage.success('试卷配置保存成功！');
    dialogVisible.value = false;
    fetchList();
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败');
  }
};

const handlePublish = async (row) => {
  if (!row.deadline) {
    ElMessage.warning('请先编辑试卷并设置考核截止日期');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认发布试卷「${row.name}」？发布后本部门全部员工将收到考核，题目按各人职级从题库随机抽取。`,
      '发布确认',
      { type: 'warning', confirmButtonText: '确认发布', cancelButtonText: '取消' }
    );
  } catch {
    return;
  }
  publishingId.value = row.id;
  try {
    const res = await axios.post('/api/template/publish', {
      templateId: row.id,
      managerId: getCurrentUser().id
    });
    const result = res.data.data || {};
    const created = result.createdCount || 0;
    const skipped = result.skippedCount || 0;
    if (skipped > 0) {
      ElMessage.success(`成功下发 ${created} 人，跳过已下发 ${skipped} 人：${(result.skippedNames || []).join('、')}`);
    } else {
      ElMessage.success(`已向本部门 ${created} 名员工下发考核`);
    }
    fetchList();
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '发布失败');
  } finally {
    publishingId.value = null;
  }
};

const handleDelete = (row) => {
  ElMessageBox.confirm(`确认删除试卷 [${row.name}] 吗？`, '警告', { type: 'warning' }).then(async () => {
    await axios.delete(`/api/template/${row.id}`);
    ElMessage.success('删除成功！');
    fetchList();
  }).catch(() => {});
};

onMounted(() => {
  fetchList();
  fetchIndicators();
});
</script>

<style scoped>
.template-manager { padding: 20px; }
.header-actions { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.pagination-box { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
