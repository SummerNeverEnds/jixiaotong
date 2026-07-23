<template>
  <div class="admin-performance">
    <div class="page-header">
      <h2>公司员工绩效总览</h2>
      <el-button type="primary" :loading="exporting" @click="exportData">导出 Excel</el-button>
    </div>

    <el-card shadow="never" class="mb-16">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="考核周期">
          <el-select v-model="query.cycleName" clearable placeholder="默认当前周期" style="width: 140px">
            <el-option v-for="c in cycleOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="query.deptId" clearable placeholder="全部部门" style="width: 160px">
            <el-option v-for="d in deptList" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="姓名/工号" style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table :data="tableData" border v-loading="loading" style="width: 100%">
      <el-table-column prop="username" label="工号" width="110" />
      <el-table-column prop="employeeName" label="姓名" width="120" />
      <el-table-column prop="deptName" label="部门" width="120" />
      <el-table-column prop="jobLevel" label="职级" width="80" />
      <el-table-column prop="cycleName" label="考核周期" width="110" />
      <el-table-column prop="learningScore" label="学习积分" width="100" />
      <el-table-column label="学习完成" width="110">
        <template #default="scope">
          {{ scope.row.materialCompleted }} / {{ scope.row.materialTotal }}
        </template>
      </el-table-column>
      <el-table-column prop="examScore" label="考试均分" width="100" />
      <el-table-column prop="workScore" label="工作实绩" width="100" />
      <el-table-column prop="examCount" label="考试场次" width="90" />
      <el-table-column prop="performanceScore" label="绩效总分" min-width="110">
        <template #default="scope">
          <span class="score-text">{{ scope.row.performanceScore }}</span>
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
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import axios from 'axios';
import * as XLSX from 'xlsx';

const loading = ref(false);
const exporting = ref(false);
const tableData = ref([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);
const deptList = ref([]);
const query = ref({ cycleName: '', keyword: '', deptId: null });

const buildCycleOptions = () => {
  const now = new Date();
  const year = now.getFullYear();
  const list = [];
  for (let y = year; y >= year - 2; y--) {
    for (let q = 4; q >= 1; q--) {
      list.push(`${y}-Q${q}`);
    }
  }
  return list;
};
const cycleOptions = buildCycleOptions();

const fetchDepts = async () => {
  try {
    const res = await axios.get('/api/dept/list');
    deptList.value = res.data.data || [];
  } catch {
    deptList.value = [];
  }
};

const fetchList = async () => {
  loading.value = true;
  try {
    const res = await axios.get('/api/review/performance/company/page', {
      params: {
        cycleName: query.value.cycleName || undefined,
        keyword: query.value.keyword || undefined,
        deptId: query.value.deptId || undefined,
        current: currentPage.value,
        size: pageSize.value
      }
    });
    tableData.value = res.data.data.records || [];
    total.value = res.data.data.total || 0;
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载公司绩效失败');
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  currentPage.value = 1;
  fetchList();
};

const handleReset = () => {
  query.value = { cycleName: '', keyword: '', deptId: null };
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

const exportData = async () => {
  exporting.value = true;
  try {
    const res = await axios.get('/api/review/performance/company/export', {
      params: {
        cycleName: query.value.cycleName || undefined,
        keyword: query.value.keyword || undefined,
        deptId: query.value.deptId || undefined
      }
    });
    const rows = res.data.data || [];
    if (rows.length === 0) {
      ElMessage.warning('暂无可导出的绩效数据');
      return;
    }
    const sheetData = rows.map(item => ({
      '工号': item.username,
      '姓名': item.employeeName,
      '部门': item.deptName,
      '职级': item.jobLevel,
      '考核周期': item.cycleName,
      '学习积分': item.learningScore,
      '学习完成数': item.materialCompleted,
      '学习任务总数': item.materialTotal,
      '考试均分': item.examScore,
      '工作实绩': item.workScore,
      '考试场次': item.examCount,
      '绩效总分': item.performanceScore
    }));
    const worksheet = XLSX.utils.json_to_sheet(sheetData);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, '公司绩效');
    const cycle = query.value.cycleName || '当前周期';
    XLSX.writeFile(workbook, `公司员工绩效_${cycle}_${Date.now()}.xlsx`);
    ElMessage.success(`已导出 ${rows.length} 条绩效记录`);
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '导出失败');
  } finally {
    exporting.value = false;
  }
};

onMounted(async () => {
  await fetchDepts();
  fetchList();
});
</script>

<style scoped>
.admin-performance { padding: 20px; }
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.mb-16 { margin-bottom: 16px; }
.score-text {
  color: #F56C6C;
  font-weight: bold;
}
.pagination-box {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
