<template>
  <div class="indicator-manager-container">
    <div class="header-actions">
      <h2>考核题库与指标管理</h2>
      <div>
        <el-button icon="Grid" @click="openBatchDialog">表格录入</el-button>
        <el-button type="primary" icon="Plus" @click="openDialog(null)">新建题目</el-button>
      </div>
    </div>

    <el-card shadow="never" class="mb-20">
      <el-form :inline="true" :model="queryParams" class="query-form" @submit.prevent>
        <el-form-item label="题目名称">
          <el-input
            v-model="queryParams.name"
            placeholder="请输入关键字"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.type" placeholder="全部类型" clearable style="width: 160px">
            <el-option label="客观定量题" value="OBJECTIVE" />
            <el-option label="主观定性题" value="SUBJECTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="职级题库">
          <el-select v-model="queryParams.jobLevel" placeholder="全部职级" clearable style="width: 140px">
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
            <el-option label="P3" value="P3" />
            <el-option label="P4" value="P4" />
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
      <el-table-column prop="name" label="指标名称 / 题目题干" min-width="200" />
      <el-table-column prop="type" label="题型" width="120">
        <template #default="scope">
          <el-tag :type="scope.row.type === 'OBJECTIVE' ? 'success' : 'warning'">
            {{ scope.row.type === 'OBJECTIVE' ? '客观题' : '主观题' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="jobLevel" label="职级题库" width="100" />
      <el-table-column prop="standardAnswer" label="标准答案(仅客观题)" width="150" />
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="scope">
          <el-button size="small" @click="openDialog(scope.row)">编辑</el-button>
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

    <el-dialog :title="form.id ? '编辑指标' : '新增指标'" v-model="dialogVisible" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="题型" required>
          <el-radio-group v-model="form.type">
            <el-radio label="OBJECTIVE">客观题 (系统判分)</el-radio>
            <el-radio label="SUBJECTIVE">主观题 (AI打分)</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="职级题库" required>
          <el-select v-model="form.jobLevel" style="width: 100%" placeholder="请选择职级">
            <el-option label="P1 题库" value="P1" />
            <el-option label="P2 题库" value="P2" />
            <el-option label="P3 题库" value="P3" />
            <el-option label="P4 题库" value="P4" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目名称" required>
          <el-input v-model="form.name" placeholder="请输入考核指标名称或题干" />
        </el-form-item>
        
        <template v-if="form.type === 'OBJECTIVE'">
          <el-form-item label="选项配置" required>
            <el-input type="textarea" v-model="form.optionsContent" rows="3" placeholder='JSON格式，如: {"A":"很棒", "B":"一般"}' />
          </el-form-item>
          <el-form-item label="标准答案" required>
            <el-input v-model="form.standardAnswer" placeholder="须为选项键，如 A / B / C" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定保存</el-button>
      </template>
    </el-dialog>

    <el-dialog title="表格录入题库" v-model="batchDialogVisible" width="1100px">
      <el-alert title="请上传 Excel/CSV 表格文件，支持列名：职级、题型、题目名称、选项JSON、答案；客观题需填写合法 JSON 选项和标准答案。" type="info" :closable="false" class="mb-12" />
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
        <el-table-column label="职级题库" width="120">
          <template #default="scope">
            <el-select v-model="scope.row.jobLevel">
              <el-option label="P1" value="P1" />
              <el-option label="P2" value="P2" />
              <el-option label="P3" value="P3" />
              <el-option label="P4" value="P4" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="题型" width="140">
          <template #default="scope">
            <el-select v-model="scope.row.type">
              <el-option label="客观题" value="OBJECTIVE" />
              <el-option label="主观题" value="SUBJECTIVE" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="题目名称" min-width="220">
          <template #default="scope">
            <el-input v-model="scope.row.name" placeholder="请输入题目" />
          </template>
        </el-table-column>
        <el-table-column label="选项JSON" min-width="260">
          <template #default="scope">
            <el-input v-model="scope.row.optionsContent" :disabled="scope.row.type !== 'OBJECTIVE'" placeholder='{"A":"选项一","B":"选项二"}' />
          </template>
        </el-table-column>
        <el-table-column label="答案" width="100">
          <template #default="scope">
            <el-input v-model="scope.row.standardAnswer" :disabled="scope.row.type !== 'OBJECTIVE'" placeholder="A" />
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
import { ElMessage, ElMessageBox } from 'element-plus';
import axios from 'axios';
import * as XLSX from 'xlsx';

const loading = ref(false);
const dialogVisible = ref(false);
const batchDialogVisible = ref(false);
const batchSubmitting = ref(false);
const tableData = ref([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);

const queryParams = ref({
  name: '',
  type: '',
  jobLevel: ''
});

const form = ref({
  id: null,
  name: '',
  type: 'OBJECTIVE',
  jobLevel: 'P1',
  optionsContent: '',
  standardAnswer: ''
});
const createBatchRow = () => ({
  name: '',
  type: 'OBJECTIVE',
  jobLevel: 'P1',
  optionsContent: '{"A":"选项一","B":"选项二","C":"选项三","D":"选项四"}',
  standardAnswer: 'A'
});
const batchRows = ref([createBatchRow(), createBatchRow(), createBatchRow()]);

const fetchList = async () => {
  loading.value = true;
  try {
    const res = await axios.get('/api/indicator/page', {
      params: {
        current: currentPage.value,
        size: pageSize.value,
        name: queryParams.value.name || undefined,
        type: queryParams.value.type || undefined,
        jobLevel: queryParams.value.jobLevel || undefined
      }
    });
    tableData.value = res.data.data.records || [];
    total.value = res.data.data.total || 0;
  } catch (error) {
    ElMessage.error('加载题库失败');
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  currentPage.value = 1;
  fetchList();
};

const handleResetSearch = () => {
  queryParams.value = { name: '', type: '', jobLevel: '' };
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
    form.value = { ...row };
  } else {
    form.value = { id: null, name: '', type: 'OBJECTIVE', jobLevel: 'P1', optionsContent: '', standardAnswer: '' };
  }
  dialogVisible.value = true;
};

const openBatchDialog = () => {
  batchRows.value = [createBatchRow(), createBatchRow(), createBatchRow()];
  batchDialogVisible.value = true;
};

const normalizeType = (value) => {
  const text = String(value || '').trim().toUpperCase();
  if (['SUBJECTIVE', '主观题', '主观', '定性题'].includes(text)) return 'SUBJECTIVE';
  return 'OBJECTIVE';
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
      const type = normalizeType(getCellValue(row, ['题型', '类型', 'type', 'Type']));
      return {
        jobLevel: (getCellValue(row, ['职级', '职级题库', 'jobLevel', 'JobLevel']) || 'P1').toUpperCase(),
        type,
        name: getCellValue(row, ['题目名称', '题目', '指标名称', 'name', 'Name']),
        optionsContent: type === 'OBJECTIVE' ? getCellValue(row, ['选项JSON', '选项', 'optionsContent', 'Options']) : '',
        standardAnswer: type === 'OBJECTIVE' ? getCellValue(row, ['答案', '标准答案', 'standardAnswer', 'Answer']) : ''
      };
    }).filter(row => row.name || row.optionsContent || row.standardAnswer);
    if (parsedRows.length === 0) {
      ElMessage.warning('未从表格中解析到有效题库数据');
      return;
    }
    batchRows.value = parsedRows;
    ElMessage.success(`已解析 ${parsedRows.length} 行题库数据，正在自动录入`);
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
  if (!form.value.name) {
    ElMessage.error('题目名称不能为空');
    return;
  }
  if (!form.value.jobLevel) {
    ElMessage.error('请选择职级题库');
    return;
  }
  if (form.value.type === 'OBJECTIVE') {
    let options;
    try {
      options = JSON.parse(form.value.optionsContent);
    } catch (error) {
      ElMessage.error('客观题选项必须是合法 JSON，如 {"A":"很棒", "B":"一般"}');
      return;
    }
    if (!options || typeof options !== 'object' || Array.isArray(options) || Object.keys(options).length === 0) {
      ElMessage.error('客观题至少需要一个选项');
      return;
    }
    const answer = String(form.value.standardAnswer || '').trim();
    if (!answer) {
      ElMessage.error('客观题标准答案不能为空');
      return;
    }
    if (!Object.prototype.hasOwnProperty.call(options, answer)) {
      ElMessage.error(`标准答案必须是选项键之一（${Object.keys(options).join('/')}），不能填写选项外的值`);
      return;
    }
    form.value.standardAnswer = answer;
  }
  try {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}');
    form.value.creatorId = form.value.creatorId || userInfo.id || 1;
    await axios.post('/api/indicator/save', form.value);
    ElMessage.success('保存成功！');
    dialogVisible.value = false;
    fetchList();
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败');
  }
};

const validateIndicatorRow = (row) => {
  if (!row.name || !row.type || !row.jobLevel) {
    return '请补全每行的职级、题型和题目名称';
  }
  if (row.type === 'OBJECTIVE') {
    let options;
    try {
      options = JSON.parse(row.optionsContent);
    } catch (error) {
      return `题目「${row.name}」的选项 JSON 不合法`;
    }
    if (!options || typeof options !== 'object' || Array.isArray(options) || Object.keys(options).length === 0) {
      return `题目「${row.name}」至少需要一个选项`;
    }
    const answer = String(row.standardAnswer || '').trim();
    if (!answer) {
      return `题目「${row.name}」缺少标准答案`;
    }
    if (!Object.prototype.hasOwnProperty.call(options, answer)) {
      return `题目「${row.name}」的标准答案必须是选项键之一（${Object.keys(options).join('/')}）`;
    }
    row.standardAnswer = answer;
  }
  return '';
};

const submitBatchRows = async () => {
  const rows = batchRows.value.filter(row => row.name || row.optionsContent || row.standardAnswer);
  if (rows.length === 0) {
    ElMessage.warning('请至少录入一道题目');
    return;
  }
  for (const row of rows) {
    const errorMessage = validateIndicatorRow(row);
    if (errorMessage) {
      ElMessage.warning(errorMessage);
      return;
    }
  }

  batchSubmitting.value = true;
  try {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}');
    for (const row of rows) {
      await axios.post('/api/indicator/save', {
        ...row,
        optionsContent: row.type === 'OBJECTIVE' ? row.optionsContent : null,
        standardAnswer: row.type === 'OBJECTIVE' ? row.standardAnswer : null,
        creatorId: userInfo.id || 1
      });
    }
    ElMessage.success(`成功录入 ${rows.length} 道题目`);
    batchDialogVisible.value = false;
    fetchList();
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '批量录入失败');
  } finally {
    batchSubmitting.value = false;
  }
};

const handleDelete = (row) => {
  ElMessageBox.confirm(`确认删除题库 [${row.name}] 吗？`, '警告', { type: 'warning' }).then(async () => {
    await axios.delete(`/api/indicator/${row.id}`);
    ElMessage.success('删除成功！');
    fetchList();
  }).catch(() => {});
};

onMounted(() => {
  fetchList();
});
</script>

<style scoped>
.indicator-manager-container {
  padding: 20px;
}
.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.query-form :deep(.el-select) {
  min-width: 140px;
}
.query-form :deep(.el-select .el-select__wrapper) {
  min-height: 32px;
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
