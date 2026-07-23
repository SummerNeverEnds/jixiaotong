<template>
  <div class="performance-container">
    <div class="page-header">
      <h2>个人绩效面板</h2>
      <div class="header-actions">
        <el-button @click="exportHistory" :loading="exporting">导出历史绩效</el-button>
        <el-button type="primary" @click="fetchPerformance">刷新</el-button>
      </div>
    </div>

    <el-row :gutter="20" class="mt-20" v-loading="loading">
      <el-col :span="6">
        <el-card shadow="hover" class="score-card">
          <div class="score-label">绩效总分</div>
          <div class="score-main">{{ currentPerformance.performanceScore || 0 }}</div>
          <div class="score-tip">学习 30% + 考试 40% + 实绩 30%</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="score-card clickable-card" @click="goStudy">
          <div class="score-label">学习积分</div>
          <div class="score-sub">{{ currentPerformance.learningScore || 0 }}</div>
          <div class="score-tip">
            已完成 {{ currentPerformance.materialCompleted || 0 }} / {{ currentPerformance.materialTotal || 0 }} 项
          </div>
          <div class="jump-tip">点击查看我的课程资料</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="score-card clickable-card" @click="goReview">
          <div class="score-label">考试成绩</div>
          <div class="score-sub">{{ currentPerformance.examScore || 0 }}</div>
          <div class="score-tip">本周期 {{ currentPerformance.examCount || 0 }} 场考试均分</div>
          <div class="jump-tip">点击查看我的考试自评</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="score-card">
          <div class="score-label">工作实绩</div>
          <div class="score-sub">{{ currentPerformance.workScore || 0 }}</div>
          <div class="score-tip">来自经理上传的工作实绩表</div>
          <div class="jump-tip jump-tip-spacer">&nbsp;</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" class="mt-20">
      <template #header>
        <div class="card-header">
          <span>历史每周期绩效</span>
          <div class="header-actions">
            <el-tag type="info">当前周期：{{ currentPerformance.cycleName || '-' }}</el-tag>
          </div>
        </div>
      </template>

      <el-table :data="historyList" border style="width: 100%" v-loading="loading">
        <el-table-column prop="cycleName" label="绩效周期" width="130" />
        <el-table-column prop="learningScore" label="学习积分" width="120" />
        <el-table-column label="学习完成" width="140">
          <template #default="scope">
            {{ scope.row.materialCompleted }} / {{ scope.row.materialTotal }}
          </template>
        </el-table-column>
        <el-table-column prop="examScore" label="考试均分" width="120" />
        <el-table-column prop="workScore" label="工作实绩" width="120" />
        <el-table-column prop="examCount" label="考试场次" width="120" />
        <el-table-column prop="performanceScore" label="绩效总分">
          <template #default="scope">
            <span class="score-text">{{ scope.row.performanceScore }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-box">
        <el-pagination
          v-model:current-page="historyPage"
          v-model:page-size="historyPageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="historyTotal"
          @size-change="handleHistorySizeChange"
          @current-change="handleHistoryPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import axios from 'axios';
import * as XLSX from 'xlsx';

const router = useRouter();
const loading = ref(false);
const exporting = ref(false);
const currentPerformance = ref({});
const historyList = ref([]);
const historyTotal = ref(0);
const historyPage = ref(1);
const historyPageSize = ref(10);
const getCurrentUser = () => JSON.parse(localStorage.getItem('userInfo') || '{}');

const goStudy = () => {
  router.push('/employee/study');
};

const goReview = () => {
  router.push('/employee/review');
};

const fetchPerformance = async () => {
  loading.value = true;
  try {
    const employeeId = getCurrentUser().id;
    const [currentRes, historyRes] = await Promise.all([
      axios.get('/api/review/performance', { params: { employeeId } }),
      axios.get('/api/review/performance/history/page', {
        params: {
          employeeId,
          current: historyPage.value,
          size: historyPageSize.value
        }
      })
    ]);
    currentPerformance.value = currentRes.data.data || {};
    historyList.value = historyRes.data.data.records || [];
    historyTotal.value = historyRes.data.data.total || 0;
  } catch (error) {
    ElMessage.error('加载个人绩效失败');
  } finally {
    loading.value = false;
  }
};

const handleHistorySizeChange = (val) => {
  historyPageSize.value = val;
  historyPage.value = 1;
  fetchPerformance();
};

const handleHistoryPageChange = (val) => {
  historyPage.value = val;
  fetchPerformance();
};

const exportHistory = async () => {
  exporting.value = true;
  try {
    const userInfo = getCurrentUser();
    const res = await axios.get('/api/review/performance/history', {
      params: { employeeId: userInfo.id }
    });
    const rows = res.data.data || [];
    if (rows.length === 0) {
      ElMessage.warning('暂无可导出的历史绩效');
      return;
    }
    const sheetData = rows.map(item => ({
      '绩效周期': item.cycleName,
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
    XLSX.utils.book_append_sheet(workbook, worksheet, '历史绩效');
    XLSX.writeFile(workbook, `个人历史绩效_${userInfo.realName || userInfo.username || userInfo.id}_${Date.now()}.xlsx`);
    ElMessage.success(`已导出 ${rows.length} 条历史绩效`);
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '导出失败');
  } finally {
    exporting.value = false;
  }
};

onMounted(() => {
  fetchPerformance();
});
</script>

<style scoped>
.performance-container {
  padding: 20px;
}
.page-header,
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.mt-20 {
  margin-top: 20px;
}
.score-card {
  text-align: center;
  min-height: 168px;
  box-sizing: border-box;
}
.score-card :deep(.el-card__body) {
  min-height: 148px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.jump-tip-spacer {
  visibility: hidden;
}
.clickable-card {
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.clickable-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08);
}
.jump-tip {
  margin-top: 8px;
  color: #409EFF;
  font-size: 12px;
}
.score-label {
  color: #606266;
  font-size: 14px;
}
.score-main {
  color: #409EFF;
  font-size: 42px;
  font-weight: bold;
  margin: 12px 0;
}
.score-sub {
  color: #67C23A;
  font-size: 34px;
  font-weight: bold;
  margin: 12px 0;
}
.score-tip {
  color: #909399;
  font-size: 13px;
}
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
