<template>
  <div class="manager-dashboard">
    <div class="header-actions">
      <h2>部门绩效审批与统计看板 <el-tag type="info" effect="plain">本周期 {{ currentCycleName || '-' }}</el-tag></h2>
      <div class="header-btns">
        <el-button type="success" :loading="uploadingWork" @click="triggerWorkUpload">上传工作实绩表</el-button>
        <el-button @click="exportReport" :loading="exporting">导出本周期报表</el-button>
        <el-button type="primary" @click="refreshAll">刷新数据</el-button>
      </div>
      <input ref="workFileRef" type="file" accept=".xlsx,.xls" class="work-file-input" @change="handleWorkFileChange" />
    </div>

    <el-row :gutter="20" class="charts-row">
      <el-col :span="8">
        <el-card shadow="hover">
          <div ref="completionChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="hover">
          <div ref="distChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" class="mt-20">
      <template #header>
        <div class="card-header">
          <span>待复核申诉列表</span>
          <el-tag type="warning">员工匿名展示</el-tag>
        </div>
      </template>

      <el-table :data="appealList" style="width: 100%" v-loading="loading">
        <el-table-column prop="appealId" label="申诉编号" width="100" />
        <el-table-column prop="appealNo" label="申诉次数" width="100">
          <template #default="scope">第 {{ scope.row.appealNo }} 次</template>
        </el-table-column>
        <el-table-column label="员工" width="120">
          <template #default>匿名员工</template>
        </el-table-column>
        <el-table-column label="考核周期" width="150">
          <template #default="scope">{{ scope.row.review?.cycleName }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="180">
          <template #default="scope">{{ formatDate(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="当前总分" width="120">
          <template #default="scope">
            <span class="score-text">{{ scope.row.review?.totalScore }} 分</span>
          </template>
        </el-table-column>
        <el-table-column label="申诉理由" min-width="220">
          <template #default="scope">
            <span class="reason-text">{{ scope.row.reason }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="150">
          <template #default="scope">
            <el-button size="small" type="primary" @click="openAppealDrawer(scope.row)">
              匿名复核
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-box">
        <el-pagination
          v-model:current-page="appealPage"
          v-model:page-size="appealPageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="appealTotal"
          @size-change="handleAppealSizeChange"
          @current-change="handleAppealPageChange"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="drawerVisible"
      title="申诉匿名复核"
      size="50%"
      destroy-on-close
    >
      <div v-if="currentAppeal && currentReview" class="drawer-content" v-loading="drawerLoading">
        <el-descriptions title="基本信息" border :column="2">
          <el-descriptions-item label="员工">匿名员工</el-descriptions-item>
          <el-descriptions-item label="周期">{{ currentReview.cycleName }}</el-descriptions-item>
          <el-descriptions-item label="当前总分">
            <span class="score-text">{{ currentReview.totalScore }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="第几次申诉">第 {{ currentAppeal.appealNo }} 次</el-descriptions-item>
          <el-descriptions-item label="申诉理由" :span="2">
            {{ currentAppeal.reason }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider>定性主观指标批阅</el-divider>

        <el-form :model="currentReview" label-position="top">
          <div v-for="(item, index) in currentReview.subjectiveDetails" :key="item.indicatorId" class="review-item">
            <h4>{{ index + 1 }}. {{ item.indicatorName }}</h4>
            <div class="employee-answer mb-10">
              <strong>员工自评：</strong> {{ item.employeeAnswer }}
            </div>
            
            <div class="ai-comment mb-10">
              <strong><i class="el-icon-cpu"></i> AI 预评草稿：</strong>
              <p>{{ item.aiComment || (!item.employeeAnswer ? '未填写作答，已跳过 AI 预审' : 'AI 尚未生成评语') }}</p>
            </div>

            <el-form-item :label="`复核后主观题得分（最高 ${item.weightRatio || 0} 分）：`" required>
              <el-input-number v-model="item.managerScore" :min="0" :max="Number(item.weightRatio || 100)" :precision="1" :step="1" />
            </el-form-item>
          </div>

          <el-divider>总体结论</el-divider>

          <el-form-item label="复核意见 (必填)：" required>
            <el-input v-model="reviewOpinion" type="textarea" :rows="4" placeholder="请填写本次申诉复核意见，成绩将返回给员工。" />
          </el-form-item>
        </el-form>

        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" @click="submitAppealReview" :loading="submitLoading">返回复核结果</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import * as XLSX from 'xlsx';
import axios from 'axios';

const completionChartRef = ref(null);
const distChartRef = ref(null);
const loading = ref(false);
const exporting = ref(false);
const uploadingWork = ref(false);
const workFileRef = ref(null);
const drawerLoading = ref(false);
const submitLoading = ref(false);
const drawerVisible = ref(false);
const appealList = ref([]);
const appealTotal = ref(0);
const appealPage = ref(1);
const appealPageSize = ref(10);
const currentAppeal = ref(null);
const currentReview = ref(null);
const reviewOpinion = ref('');
const currentCycleName = ref('');
let completionChart = null;
let distChart = null;
let resizeHandler = null;

const getCurrentUser = () => JSON.parse(localStorage.getItem('userInfo') || '{}');
const formatDate = (dateStr) => {
  if (!dateStr) return '';
  return dateStr.replace('T', ' ').substring(0, 16);
};

const initCharts = (statsData) => {
  if (completionChartRef.value && distChartRef.value) {
    if (!completionChart) {
      completionChart = echarts.init(completionChartRef.value);
    }
    if (!distChart) {
      distChart = echarts.init(distChartRef.value);
    }

    const option1 = {
      title: { text: `本周期(${statsData.cycleName || '-'})考核完成率`, left: 'center', top: 8 },
      series: [
        {
          type: 'gauge',
          center: ['50%', '48%'],
          radius: '75%',
          progress: { show: true, width: 10 },
          axisLine: { lineStyle: { width: 10 } },
          axisTick: { show: false },
          splitLine: { length: 8, distance: -8 },
          axisLabel: { distance: 12, fontSize: 11 },
          pointer: { length: '55%', width: 5 },
          title: { show: false },
          detail: {
            valueAnimation: true,
            formatter: '{value}%',
            offsetCenter: [0, '85%'],
            fontSize: 22,
            color: '#303133'
          },
          data: [{ value: statsData.completionRate }]
        }
      ]
    };

    const option2 = {
      title: { text: `本周期(${statsData.cycleName || '-'})绩效分数分布` },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: ['S级(>90)', 'A级(80-90)', 'B级(60-79)', 'C级(<60)'] },
      yAxis: { type: 'value' },
      series: [
        {
          data: statsData.distData, // [5, 20, 36, 10]
          type: 'bar',
          itemStyle: { color: '#409EFF' },
          label: { show: true, position: 'top' }
        }
      ]
    };

    completionChart.setOption(option1);
    distChart.setOption(option2);

    if (!resizeHandler) {
      resizeHandler = () => {
        completionChart?.resize();
        distChart?.resize();
      };
      window.addEventListener('resize', resizeHandler);
    }
  }
};

const fetchDashboardStats = async () => {
  try {
    const userInfo = getCurrentUser();
    const res = await axios.get('/api/review/dashboard/summary', {
      params: { managerId: userInfo.id }
    });
    const statsData = res.data.data || { completionRate: 0, distData: [0, 0, 0, 0], cycleName: '' };
    currentCycleName.value = statsData.cycleName || '';
    nextTick(() => {
      initCharts(statsData);
    });
  } catch (error) {
    ElMessage.error('加载看板统计失败');
  }
};

const refreshAll = () => {
  fetchDashboardStats();
  fetchPendingAppeals();
};

const exportReport = async () => {
  exporting.value = true;
  try {
    const userInfo = getCurrentUser();
    const res = await axios.get('/api/review/export', {
      params: { managerId: userInfo.id }
    });
    const rows = res.data.data || [];
    if (rows.length === 0) {
      ElMessage.warning('暂无可导出的绩效数据');
      return;
    }
    const sheetData = rows.map(item => ({
      '考核编号': item.reviewId,
      '工号': item.username,
      '姓名': item.employeeName,
      '考核周期': item.cycleName,
      '试卷名称': item.templateName,
      '状态': item.statusText,
      '客观题得分': item.objectiveScore,
      '主观题得分': item.subjectiveScore,
      '总分': item.totalScore,
      '提交时间': item.submitTime ? String(item.submitTime).replace('T', ' ').substring(0, 16) : '',
      '评语': item.managerComment || ''
    }));
    const worksheet = XLSX.utils.json_to_sheet(sheetData);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, '绩效报表');
    XLSX.writeFile(workbook, `部门绩效报表_${currentCycleName.value || '本周期'}_${userInfo.realName || userInfo.id}_${Date.now()}.xlsx`);
    ElMessage.success(`已导出本周期 ${rows.length} 条绩效记录`);
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '导出失败');
  } finally {
    exporting.value = false;
  }
};

const triggerWorkUpload = () => {
  workFileRef.value?.click();
};

const pickField = (row, keys) => {
  for (const key of keys) {
    if (row[key] !== undefined && row[key] !== null && String(row[key]).trim() !== '') {
      return row[key];
    }
  }
  return null;
};

const handleWorkFileChange = async (event) => {
  const file = event.target.files?.[0];
  event.target.value = '';
  if (!file) return;
  uploadingWork.value = true;
  try {
    const buffer = await file.arrayBuffer();
    const workbook = XLSX.read(buffer, { type: 'array' });
    const sheet = workbook.Sheets[workbook.SheetNames[0]];
    const rows = XLSX.utils.sheet_to_json(sheet, { defval: '' });
    const items = rows.map((row) => {
      const username = pickField(row, ['工号', '员工工号', 'username', 'Username']);
      const scoreRaw = pickField(row, ['工作实绩', '工作实绩分数', '实绩分数', 'workScore', '分数']);
      const workScore = Number(scoreRaw);
      if (!username || Number.isNaN(workScore)) return null;
      return { username: String(username).trim(), workScore };
    }).filter(Boolean);
    if (items.length === 0) {
      ElMessage.warning('未解析到有效数据，请使用列：工号、工作实绩分数');
      return;
    }
    const res = await axios.post('/api/review/work-score/upload', {
      cycleName: currentCycleName.value || undefined,
      items
    });
    ElMessage.success(`已导入本周期工作实绩 ${res.data.data?.updated || 0} 条`);
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '上传工作实绩表失败');
  } finally {
    uploadingWork.value = false;
  }
};

const fetchPendingAppeals = async () => {
  loading.value = true;
  try {
    const userInfo = getCurrentUser();
    const res = await axios.get('/api/review/appeals/pending/page', {
      params: {
        managerId: userInfo.id,
        current: appealPage.value,
        size: appealPageSize.value
      }
    });
    appealList.value = res.data.data.records || [];
    appealTotal.value = res.data.data.total || 0;
  } catch (error) {
    ElMessage.error('加载待复核申诉列表失败');
  } finally {
    loading.value = false;
  }
};

const handleAppealSizeChange = (val) => {
  appealPageSize.value = val;
  appealPage.value = 1;
  fetchPendingAppeals();
};

const handleAppealPageChange = (val) => {
  appealPage.value = val;
  fetchPendingAppeals();
};

const openAppealDrawer = async (row) => {
  drawerLoading.value = true;
  drawerVisible.value = true;
  try {
    const userInfo = getCurrentUser();
    const res = await axios.get(`/api/review/appeals/${row.appealId}`, {
      params: { managerId: userInfo.id }
    });
    currentAppeal.value = res.data.data;
    currentReview.value = currentAppeal.value.review;
    reviewOpinion.value = '';
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '加载申诉详情失败');
    drawerVisible.value = false;
  } finally {
    drawerLoading.value = false;
  }
};

const submitAppealReview = async () => {
  if (!reviewOpinion.value) {
    ElMessage.warning('请填写复核意见');
    return;
  }

  submitLoading.value = true;
  try {
    const userInfo = getCurrentUser();
    const payload = {
      appealId: currentAppeal.value.appealId,
      managerId: userInfo.id,
      reviewOpinion: reviewOpinion.value,
      scores: currentReview.value.subjectiveDetails.map(d => ({
        reviewDetailId: d.reviewDetailId,
        managerScore: d.managerScore
      }))
    };
    await axios.post('/api/review/appeals/review', payload);
    
    ElMessage.success('复核结果已返回给员工');
    drawerVisible.value = false;
    
    fetchPendingAppeals();
    fetchDashboardStats();
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '复核失败');
  } finally {
    submitLoading.value = false;
  }
};

onMounted(() => {
  refreshAll();
});

onUnmounted(() => {
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler);
  }
  completionChart?.dispose();
  distChart?.dispose();
});
</script>

<style scoped>
.manager-dashboard {
  padding: 20px;
}
.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.header-btns {
  display: flex;
  align-items: center;
  gap: 12px;
}
.work-file-input {
  display: none;
}
.chart-container {
  height: 300px;
  width: 100%;
}
.mt-20 { margin-top: 20px; }
.mb-10 { margin-bottom: 10px; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.score-text {
  font-weight: bold;
  color: #F56C6C;
}
.reason-text {
  display: inline-block;
  max-width: 360px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.drawer-content {
  padding: 0 20px;
  padding-bottom: 80px;
}
.review-item {
  background-color: #fafafa;
  padding: 15px;
  border-radius: 4px;
  margin-bottom: 20px;
}
.employee-answer {
  color: #606266;
  line-height: 1.6;
}
.ai-comment {
  background-color: #e1f3d8;
  padding: 10px;
  border-radius: 4px;
  border-left: 4px solid #67C23A;
  color: #409EFF;
}
.drawer-footer {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  padding: 15px 20px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
  display: flex;
  justify-content: flex-end;
  box-sizing: border-box;
}
.pagination-box {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
