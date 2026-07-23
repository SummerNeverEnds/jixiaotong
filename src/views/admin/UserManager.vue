<template>
  <div class="user-manager">
    <div class="header-actions">
      <h2>用户与组织结构管理</h2>
      <div>
        <el-button icon="Grid" @click="openBatchDialog">表格录入</el-button>
        <el-button type="primary" icon="Plus" @click="openDialog(null)">新增用户</el-button>
      </div>
    </div>

    <el-card shadow="never" class="mb-20">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="keyword"
            placeholder="工号 / 姓名"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="部门">
          <el-select
            v-model="filterDeptId"
            placeholder="全部部门"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="dept in deptList"
              :key="dept.id"
              :label="dept.name"
              :value="dept.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleSearch">查询</el-button>
          <el-button @click="handleResetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table :data="tableData" border style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="工号" width="120" />
      <el-table-column prop="realName" label="真实姓名" width="150" />
      <el-table-column prop="role" label="角色权限" width="150">
        <template #default="scope">
          <el-tag :type="getRoleTag(scope.row.role)">
            {{ getRoleName(scope.row.role) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="jobLevel" label="职级" width="100">
        <template #default="scope">
          <el-tag v-if="scope.row.role === 'EMPLOYEE'" type="info">{{ scope.row.jobLevel || 'P1' }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="所属部门" width="140">
        <template #default="scope">
          {{ getDeptName(scope.row.deptId) }}
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="联系方式" width="150" />
      <el-table-column label="账号状态" width="100">
        <template #default="scope">
          <el-tag :type="isDisabled(scope.row) ? 'danger' : 'success'" size="small">
            {{ isDisabled(scope.row) ? '已禁用' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" min-width="180" />
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="scope">
          <el-button size="small" @click="openDialog(scope.row)">编辑</el-button>
          <el-button size="small" type="primary" plain @click="viewOperationLogs(scope.row)">操作日志</el-button>
          <el-button size="small" type="warning" @click="handleResetPassword(scope.row)">重置密码</el-button>
          <el-button
            size="small"
            :type="isDisabled(scope.row) ? 'success' : 'info'"
            @click="handleToggleDisable(scope.row)"
            :disabled="scope.row.role === 'ADMIN'"
          >
            {{ isDisabled(scope.row) ? '启用' : '禁用' }}
          </el-button>
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

    <el-dialog :title="form.id ? '编辑用户' : '新增用户'" v-model="dialogVisible" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="工号" required>
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="真实姓名" required>
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="角色权限" required>
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="部门经理" value="MANAGER" />
            <el-option label="普通员工" value="EMPLOYEE" />
          </el-select>
        </el-form-item>
        <el-form-item label="员工职级" v-if="form.role === 'EMPLOYEE'" required>
          <el-select v-model="form.jobLevel" style="width: 100%">
            <el-option label="P1 初级" value="P1" />
            <el-option label="P2 中级" value="P2" />
            <el-option label="P3 高级" value="P3" />
            <el-option label="P4 专家" value="P4" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属部门" v-if="form.role === 'EMPLOYEE' || form.role === 'MANAGER'" required>
          <el-select v-model="form.deptId" style="width: 100%" placeholder="请选择部门">
            <el-option v-for="dept in deptList" :key="dept.id" :label="dept.name" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号码">
          <el-input v-model="form.phone" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定保存</el-button>
      </template>
    </el-dialog>

    <el-dialog title="表格录入用户" v-model="batchDialogVisible" width="960px">
      <el-alert title="请上传 Excel/CSV 表格文件，支持列名：工号、真实姓名、角色、职级、部门、手机号。" type="info" :closable="false" class="mb-12" />
      <el-upload
        drag
        action="#"
        accept=".xlsx,.xls,.csv"
        :auto-upload="false"
        :show-file-list="false"
        :on-change="handleBatchFileChange"
        class="mb-12"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽表格到这里，或 <em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">上传后会自动解析并录入；如校验失败，可在下方预览表修正后再批量保存。</div>
        </template>
      </el-upload>
      <el-table :data="batchRows" border style="width: 100%">
        <el-table-column label="工号" min-width="130">
          <template #default="scope">
            <el-input v-model="scope.row.username" placeholder="如 emp001" />
          </template>
        </el-table-column>
        <el-table-column label="真实姓名" min-width="130">
          <template #default="scope">
            <el-input v-model="scope.row.realName" placeholder="姓名" />
          </template>
        </el-table-column>
        <el-table-column label="角色" width="140">
          <template #default="scope">
            <el-select v-model="scope.row.role">
              <el-option label="管理员" value="ADMIN" />
              <el-option label="部门经理" value="MANAGER" />
              <el-option label="普通员工" value="EMPLOYEE" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="职级" width="120">
          <template #default="scope">
            <el-select v-model="scope.row.jobLevel" :disabled="scope.row.role !== 'EMPLOYEE'">
              <el-option label="P1" value="P1" />
              <el-option label="P2" value="P2" />
              <el-option label="P3" value="P3" />
              <el-option label="P4" value="P4" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="部门" width="140">
          <template #default="scope">
            <el-select v-model="scope.row.deptId" :disabled="scope.row.role === 'ADMIN'" placeholder="部门">
              <el-option v-for="dept in deptList" :key="dept.id" :label="dept.name" :value="dept.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="手机号" min-width="130">
          <template #default="scope">
            <el-input v-model="scope.row.phone" placeholder="可选" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="scope">
            <el-button type="danger" size="small" @click="removeBatchRow(scope.$index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="batch-actions">
        <el-button @click="addBatchRow">新增一行</el-button>
      </div>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="submitBatchRows">批量保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import axios from 'axios';
import * as XLSX from 'xlsx';

const router = useRouter();
const loading = ref(false);
const dialogVisible = ref(false);
const batchDialogVisible = ref(false);
const batchSubmitting = ref(false);
const tableData = ref([]);
const deptList = ref([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);
const keyword = ref('');
const filterDeptId = ref(null);
const form = ref({ id: null, username: '', realName: '', role: 'EMPLOYEE', jobLevel: 'P1', deptId: null, phone: '' });
const createBatchRow = () => ({ username: '', realName: '', role: 'EMPLOYEE', jobLevel: 'P1', deptId: 10, phone: '' });
const batchRows = ref([createBatchRow(), createBatchRow(), createBatchRow()]);

const getDeptName = (deptId) => {
  if (!deptId) return '-';
  const dept = deptList.value.find(d => d.id === deptId);
  return dept ? dept.name : `部门${deptId}`;
};

const fetchDepts = async () => {
  try {
    const res = await axios.get('/api/dept/list');
    deptList.value = res.data.data || [];
  } catch (e) {
    deptList.value = [
      { id: 10, name: '研发部' },
      { id: 20, name: '产品部' },
      { id: 30, name: '市场部' }
    ];
  }
};

const getRoleTag = (role) => {
  if (role === 'ADMIN') return 'danger';
  if (role === 'MANAGER') return 'warning';
  return 'success';
};

const getRoleName = (role) => {
  if (role === 'ADMIN') return '管理员';
  if (role === 'MANAGER') return '部门经理';
  return '普通员工';
};

const isDisabled = (row) => {
  if (!row?.lockUntil) return false;
  const year = Number(String(row.lockUntil).substring(0, 4));
  return year >= 2099;
};

const fetchList = async () => {
  loading.value = true;
  try {
    const res = await axios.get('/api/user/page', {
      params: {
        current: currentPage.value,
        size: pageSize.value,
        keyword: keyword.value || undefined,
        deptId: filterDeptId.value ?? undefined
      }
    });
    tableData.value = res.data.data.records;
    total.value = res.data.data.total;
  } catch (error) {
    ElMessage.error('加载用户列表失败，请确保 Spring Boot 后端已启动');
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  currentPage.value = 1;
  fetchList();
};

const handleResetSearch = () => {
  keyword.value = '';
  filterDeptId.value = null;
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

const openDialog = (row) => {
  if (row) {
    form.value = {
      id: row.id,
      username: row.username,
      realName: row.realName,
      role: row.role,
      jobLevel: row.jobLevel || 'P1',
      deptId: row.deptId || null,
      phone: row.phone || ''
    };
  } else {
    form.value = { id: null, username: '', realName: '', role: 'EMPLOYEE', jobLevel: 'P1', deptId: 10, phone: '' };
  }
  dialogVisible.value = true;
};

const openBatchDialog = () => {
  batchRows.value = [createBatchRow(), createBatchRow(), createBatchRow()];
  batchDialogVisible.value = true;
};

const normalizeRole = (value) => {
  const text = String(value || '').trim().toUpperCase();
  if (['ADMIN', '管理员'].includes(text)) return 'ADMIN';
  if (['MANAGER', '经理', '部门经理'].includes(text)) return 'MANAGER';
  return 'EMPLOYEE';
};

const getCellValue = (row, keys) => {
  for (const key of keys) {
    if (row[key] !== undefined && row[key] !== null && String(row[key]).trim() !== '') {
      return String(row[key]).trim();
    }
  }
  return '';
};

const handleBatchFileChange = async (uploadFile) => {
  const file = uploadFile.raw;
  if (!file) return;
  try {
    const buffer = await file.arrayBuffer();
    const workbook = XLSX.read(buffer, { type: 'array' });
    const sheet = workbook.Sheets[workbook.SheetNames[0]];
    const rawRows = XLSX.utils.sheet_to_json(sheet, { defval: '' });
    const parsedRows = rawRows.map(row => {
      const role = normalizeRole(getCellValue(row, ['角色', 'role', 'Role']));
      const jobLevel = getCellValue(row, ['职级', 'jobLevel', 'JobLevel']) || 'P1';
      const deptRaw = getCellValue(row, ['部门', '部门ID', 'deptId', 'DeptId']);
      let deptId = 10;
      if (deptRaw) {
        const matched = deptList.value.find(d => d.name === deptRaw || String(d.id) === deptRaw);
        deptId = matched ? matched.id : (Number(deptRaw) || 10);
      }
      return {
        username: getCellValue(row, ['工号', '账号', 'username', 'Username']),
        realName: getCellValue(row, ['真实姓名', '姓名', 'realName', 'RealName']),
        role,
        jobLevel: role === 'EMPLOYEE' ? jobLevel.toUpperCase() : null,
        deptId: role === 'ADMIN' ? 0 : deptId,
        phone: getCellValue(row, ['手机号', '手机号码', '联系方式', 'phone', 'Phone'])
      };
    }).filter(row => row.username || row.realName || row.phone);
    if (parsedRows.length === 0) {
      ElMessage.warning('未从表格中解析到有效用户数据');
      return;
    }
    batchRows.value = parsedRows;
    ElMessage.success(`已解析 ${parsedRows.length} 行用户数据，正在自动录入`);
    await submitBatchRows();
  } catch (error) {
    ElMessage.error('解析表格失败，请检查文件格式和表头');
  }
};

const addBatchRow = () => {
  batchRows.value.push(createBatchRow());
};

const removeBatchRow = (index) => {
  if (batchRows.value.length === 1) {
    ElMessage.warning('至少保留一行');
    return;
  }
  batchRows.value.splice(index, 1);
};

const submitForm = async () => {
  try {
    if (!form.value.id && !String(form.value.username || '').trim()) {
      ElMessage.warning('工号不能为空');
      return;
    }
    if (!String(form.value.realName || '').trim()) {
      ElMessage.warning('姓名不能为空');
      return;
    }
    if (form.value.role !== 'EMPLOYEE') {
      form.value.jobLevel = null;
    } else if (!form.value.jobLevel) {
      form.value.jobLevel = 'P1';
    }
    if ((form.value.role === 'EMPLOYEE' || form.value.role === 'MANAGER') && !form.value.deptId) {
      ElMessage.warning('请选择所属部门');
      return;
    }
    if (form.value.role === 'ADMIN') {
      form.value.deptId = 0;
    }
    if (form.value.id) {
      await axios.put('/api/user/update', form.value);
    } else {
      await axios.post('/api/user/add', form.value);
    }
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    fetchList();
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败');
  }
};

const submitBatchRows = async () => {
  const rows = batchRows.value.filter(row => row.username || row.realName || row.phone);
  if (rows.length === 0) {
    ElMessage.warning('请至少录入一名用户');
    return;
  }
  for (const row of rows) {
    if (!row.username || !row.realName || !row.role) {
      ElMessage.warning('请补全每行的工号、姓名和角色');
      return;
    }
    if ((row.role === 'EMPLOYEE' || row.role === 'MANAGER') && !row.deptId) {
      ElMessage.warning('员工/经理需选择部门');
      return;
    }
  }

  batchSubmitting.value = true;
  try {
    for (const row of rows) {
      await axios.post('/api/user/add', {
        ...row,
        jobLevel: row.role === 'EMPLOYEE' ? (row.jobLevel || 'P1') : null,
        deptId: row.role === 'ADMIN' ? 0 : row.deptId
      });
    }
    ElMessage.success(`成功录入 ${rows.length} 名用户`);
    batchDialogVisible.value = false;
    fetchList();
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '批量录入失败');
  } finally {
    batchSubmitting.value = false;
  }
};

const handleResetPassword = (row) => {
  ElMessageBox.confirm(`确认重置用户 [${row.realName}] 的登录密码吗？`, '重置密码', { type: 'warning' })
    .then(async () => {
      await axios.post(`/api/user/${row.id}/reset-password`);
      ElMessage.success('密码已重置');
    })
    .catch(() => {});
};

const handleToggleDisable = (row) => {
  const disabled = !isDisabled(row);
  const action = disabled ? '禁用' : '启用';
  ElMessageBox.confirm(`确认${action}用户 [${row.realName}] 吗？`, action, { type: 'warning' })
    .then(async () => {
      await axios.post(`/api/user/${row.id}/disable`, null, { params: { disabled } });
      ElMessage.success(`已${action}`);
      fetchList();
    })
    .catch(() => {});
};

const handleDelete = (row) => {
  ElMessageBox.confirm(`确认删除用户 [${row.realName}] 吗？`, '警告', { type: 'warning' }).then(async () => {
    await axios.delete(`/api/user/${row.id}`);
    ElMessage.success('删除成功！');
    fetchList();
  }).catch(() => {});
};

const viewOperationLogs = (row) => {
  router.push({
    path: '/admin/operation-logs',
    query: { userId: row.id }
  });
};

onMounted(() => {
  fetchDepts();
  fetchList();
});
</script>

<style scoped>
.user-manager { padding: 20px; }
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
.mb-12 { margin-bottom: 12px; }
.batch-actions { margin-top: 12px; }
</style>
