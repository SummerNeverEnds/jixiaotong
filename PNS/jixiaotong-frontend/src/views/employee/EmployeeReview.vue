<template>
  <div class="employee-review-container" v-loading.fullscreen.lock="isSubmitting">
    <el-card v-if="!examStarted" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>我的试卷与历史记录</span>
          <el-button size="small" @click="fetchReviewList">刷新</el-button>
        </div>
      </template>
      <div class="filter-bar">
        <el-input
          v-model="filters.templateName"
          clearable
          placeholder="按试卷名称搜索"
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="filters.cycleName" clearable placeholder="按周期搜索" style="width: 140px">
          <el-option v-for="c in cycleOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="filters.sortBy" clearable placeholder="排序字段" style="width: 140px">
          <el-option label="按周期排序" value="cycle" />
          <el-option label="按得分排序" value="score" />
        </el-select>
        <el-select v-model="filters.sortOrder" clearable placeholder="排序方向" style="width: 120px">
          <el-option label="升序" value="asc" />
          <el-option label="降序" value="desc" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <el-table :data="reviewList" style="width: 100%" v-loading="listLoading">
        <el-table-column prop="reviewId" label="考核编号" width="100" />
        <el-table-column prop="templateName" label="试卷名称" min-width="220" />
        <el-table-column prop="cycleName" label="周期" width="120" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="scope">
            <el-tag :type="getListStatusType(scope.row.status)">{{ getListStatusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalScore" label="得分" width="100" />
        <el-table-column label="申诉机会" width="110">
          <template #default="scope">
            {{ scope.row.appealCount || 0 }}/2
          </template>
        </el-table-column>
        <el-table-column label="申诉截止" width="170">
          <template #default="scope">
            {{ formatDate(scope.row.appealDeadline) || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="scope">
            <el-button
              v-if="['UNSTARTED', 'IN_PROGRESS'].includes(scope.row.status)"
              type="primary"
              size="small"
              @click="startExam(scope.row)"
            >
              {{ scope.row.status === 'IN_PROGRESS' ? '继续考试' : '开始考试' }}
            </el-button>
            <template v-else>
              <el-button size="small" @click="viewReview(scope.row)">查看详情</el-button>
              <el-button v-if="scope.row.canAppeal" size="small" type="warning" @click="openAppealDialog(scope.row)">
                提交申诉
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-box">
        <el-pagination
          v-model:current-page="listPage"
          v-model:page-size="listPageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="listTotal"
          @size-change="handleListSizeChange"
          @current-change="handleListPageChange"
        />
      </div>
    </el-card>

    <el-card v-if="examStarted" shadow="hover" class="header-card">
      <div class="header-content">
        <div>
          <h2>{{ reviewData.templateName || '加载中...' }}</h2>
          <el-tag :type="statusTagType" effect="dark" size="large">
            {{ statusText }}
          </el-tag>
        </div>
        <div v-if="examFinished" class="header-actions">
          <el-button @click="refreshCurrentReview">刷新评语</el-button>
          <el-button @click="backToList">返回试卷列表</el-button>
        </div>
        <div v-if="reviewData.status === 'IN_PROGRESS'" class="timer-box">
          <span class="timer-label">距离自动提交还剩：</span>
          <span :class="{'timer-danger': timeLeft <= 300}">{{ formattedTime }}</span>
        </div>
      </div>
    </el-card>

    <el-card v-if="examStarted && reviewData.id" shadow="hover" class="main-card">
      <el-alert
        v-if="reviewData.status === 'SUBMITTED'"
        title="成绩已返回"
        :description="`当前总分：${reviewData.totalScore} 分。若对成绩有异议，可在 ${formatDate(reviewData.appealDeadline)} 前提交申诉，最多两次。`"
        type="warning"
        show-icon
        :closable="false"
        class="mb-20"
      />

      <el-alert
        v-if="reviewData.status === 'APPEALING'"
        title="申诉复核中"
        description="您的申诉已提交，当前等待经理匿名复核。复核完成后成绩将返回到历史记录中。"
        type="warning"
        show-icon
        :closable="false"
        class="mb-20"
      />

      <el-alert
        v-if="reviewData.status === 'GRADED'"
        title="考核已归档"
        :description="`最终总得分：${reviewData.totalScore} 分。请查看下方得分明细与复核评语。`"
        type="success"
        show-icon
        :closable="false"
        class="mb-20"
      />

      <el-alert
        v-if="reviewData.status === 'CHEATED'"
        title="作弊零分"
        description="系统检测到考试过程中多次切屏或离开页面，本次考核已登记为作弊，成绩为 0 分。"
        type="error"
        show-icon
        :closable="false"
        class="mb-20"
      />
      
      <div v-if="reviewData.latestReviewOpinion || reviewData.managerComment" class="manager-comment-box mb-20">
        <h3>复核意见</h3>
        <p>{{ reviewData.latestReviewOpinion || reviewData.managerComment }}</p>
      </div>

      <el-form :model="submitForm" ref="formRef" label-position="top">
        <div v-for="(item, index) in reviewData.details" :key="item.indicatorId" class="indicator-item">
          <div class="indicator-header">
            <h4>{{ index + 1 }}. {{ item.indicatorName }} <el-tag size="small" type="info">{{ item.type === 'OBJECTIVE' ? '定量测评' : '定性总结' }}</el-tag></h4>
            <div v-if="reviewData.status === 'GRADED'" class="score-badge">
              得分：{{ item.finalScore }}
            </div>
          </div>

          <div v-if="reviewData.status === 'IN_PROGRESS'">
            <template v-if="item.type === 'OBJECTIVE'">
              <el-radio-group v-model="item.answer">
                <el-radio v-for="(val, key) in parseOptions(item.optionsContent)" :key="key" :label="key">
                  {{ key }}. {{ val }}
                </el-radio>
              </el-radio-group>
            </template>
            <template v-else>
              <el-input 
                v-model="item.answer" 
                type="textarea" 
                :rows="4" 
                placeholder="请输入您的工作总结或自评说明..." 
              />
            </template>
          </div>

          <div v-else class="readonly-answer">
            <p><strong>您的作答/自评：</strong> {{ item.answer || '未填写' }}</p>
            
            <div v-if="reviewData.status === 'GRADED' && item.type === 'OBJECTIVE'" class="grading-feedback mt-10">
              <p :class="{'text-success': item.finalScore > 0, 'text-danger': item.finalScore <= 0}">
                <i :class="item.finalScore > 0 ? 'el-icon-check' : 'el-icon-close'"></i>
                客观题系统判定打分：{{ item.objectiveScore }}
              </p>
            </div>

            <div v-if="['SUBMITTED', 'GRADED', 'APPEALING'].includes(reviewData.status) && item.type === 'SUBJECTIVE'" class="grading-feedback mt-10">
              <el-descriptions border :column="1" size="small">
                <el-descriptions-item label="AI 预评分析">
                  <span v-if="item.aiComment">{{ item.aiComment }}</span>
                  <span v-else-if="!item.answer" class="text-muted">未填写作答，已跳过 AI 预审</span>
                  <span v-else class="text-muted">AI 评语生成中，请稍后点击刷新...</span>
                </el-descriptions-item>
                <el-descriptions-item label="主观题得分">
                  <span class="text-primary">{{ item.managerScore }}</span> 分
                </el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </div>
      </el-form>

      <div class="submit-action" v-if="reviewData.status === 'IN_PROGRESS'">
        <el-button size="large" @click="handleSaveDraft" :loading="draftSaving">暂存草稿</el-button>
        <el-button type="primary" size="large" @click="handleManualSubmit" :loading="isSubmitting">
          提交考核
        </el-button>
      </div>
    </el-card>

    <el-empty v-if="!examStarted && !listLoading && reviewList.length === 0" description="暂无下发给您的考核单，请联系经理发布考核。" />

    <el-dialog title="提交成绩申诉" v-model="appealDialogVisible" width="520px">
      <el-alert title="每张试卷最多可申诉两次，且须在考试结束后三天内提交。" type="info" :closable="false" class="mb-20" />
      <el-form label-position="top">
        <el-form-item label="申诉理由" required>
          <el-input v-model="appealForm.reason" type="textarea" :rows="5" placeholder="请说明需要复核的主观题或成绩异议点" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appealDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="appealSubmitting" @click="submitAppeal">提交申诉</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import axios from 'axios';

const reviewData = ref({});
const reviewList = ref([]);
const listTotal = ref(0);
const listPage = ref(1);
const listPageSize = ref(10);
const filters = ref({
  templateName: '',
  cycleName: '',
  sortBy: '',
  sortOrder: ''
});
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
const submitForm = ref({});
const isSubmitting = ref(false);
const listLoading = ref(false);
const examStarted = ref(false);
const examFinished = ref(false);
const cheatingReporting = ref(false);
const cheatWarned = ref(false);
const navigatingAway = ref(false);
let blurCheckTimer = null;
const draftSaving = ref(false);
const appealDialogVisible = ref(false);
const appealSubmitting = ref(false);
const appealForm = ref({ reviewId: null, reason: '' });
const timeLeft = ref(0);
const timerInterval = ref(null);
const aiPollTimer = ref(null);
const timerKey = computed(() => `perf_timer_${reviewData.value.id}`);
const draftLocalKey = computed(() => `perf_draft_${reviewData.value.id}`);

const statusMap = {
  'UNSTARTED': { text: '未开始', type: 'info' },
  'IN_PROGRESS': { text: '考试中', type: 'primary' },
  'SUBMITTED': { text: '申诉期', type: 'warning' },
  'APPEALING': { text: '申诉中', type: 'warning' },
  'GRADED': { text: '已归档', type: 'success' },
  'CHEATED': { text: '作弊零分', type: 'danger' }
};
const statusText = computed(() => statusMap[reviewData.value.status]?.text || '未知');
const statusTagType = computed(() => statusMap[reviewData.value.status]?.type || 'info');

const formattedTime = computed(() => {
  if (timeLeft.value <= 0) return '00:00:00';
  const h = Math.floor(timeLeft.value / 3600).toString().padStart(2, '0');
  const m = Math.floor((timeLeft.value % 3600) / 60).toString().padStart(2, '0');
  const s = (timeLeft.value % 60).toString().padStart(2, '0');
  return `${h}:${m}:${s}`;
});

const parseOptions = (optionsStr) => {
  try {
    if (!optionsStr) return {};
    return typeof optionsStr === 'string' ? JSON.parse(optionsStr) : optionsStr;
  } catch (e) {
    return {};
  }
};

const getCurrentUser = () => JSON.parse(localStorage.getItem('userInfo') || '{}');

const getListStatusText = (status) => statusMap[status]?.text || '未知';
const getListStatusType = (status) => statusMap[status]?.type || 'info';
const formatDate = (dateStr) => {
  if (!dateStr) return '';
  return dateStr.replace('T', ' ').substring(0, 16);
};

const openAppealDialog = (row) => {
  appealForm.value = { reviewId: row.reviewId, reason: '' };
  appealDialogVisible.value = true;
};

const submitAppeal = async () => {
  if (!appealForm.value.reason) {
    ElMessage.warning('请填写申诉理由');
    return;
  }
  appealSubmitting.value = true;
  try {
    await axios.post('/api/review/appeal', {
      reviewId: appealForm.value.reviewId,
      employeeId: getCurrentUser().id,
      reason: appealForm.value.reason
    });
    ElMessage.success('申诉已提交，等待经理匿名复核');
    appealDialogVisible.value = false;
    await fetchReviewList();
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '提交申诉失败');
  } finally {
    appealSubmitting.value = false;
  }
};

const fetchReviewList = async () => {
  listLoading.value = true;
  try {
    const userInfo = getCurrentUser();
    const res = await axios.get('/api/review/my/page', {
      params: {
        employeeId: userInfo.id,
        current: listPage.value,
        size: listPageSize.value,
        templateName: filters.value.templateName || undefined,
        cycleName: filters.value.cycleName || undefined,
        sortBy: filters.value.sortBy || undefined,
        sortOrder: filters.value.sortOrder || undefined
      }
    });
    reviewList.value = res.data.data.records || [];
    listTotal.value = res.data.data.total || 0;
  } catch (error) {
    ElMessage.error('获取试卷列表失败');
  } finally {
    listLoading.value = false;
  }
};

const handleSearch = () => {
  listPage.value = 1;
  fetchReviewList();
};

const resetFilters = () => {
  filters.value = { templateName: '', cycleName: '', sortBy: '', sortOrder: '' };
  listPage.value = 1;
  fetchReviewList();
};

const handleListSizeChange = (val) => {
  listPageSize.value = val;
  listPage.value = 1;
  fetchReviewList();
};

const handleListPageChange = (val) => {
  listPage.value = val;
  fetchReviewList();
};

const restoreDraftAnswers = async () => {
  const userId = getCurrentUser().id;
  let draftItems = null;
  try {
    const res = await axios.get('/api/review/draft', {
      params: { reviewId: reviewData.value.id, employeeId: userId }
    });
    draftItems = res.data.data?.detailItems;
  } catch (e) {
  }
  if (!draftItems?.length) {
    try {
      const local = JSON.parse(localStorage.getItem(draftLocalKey.value) || 'null');
      draftItems = local?.detailItems;
    } catch (e) {
      draftItems = null;
    }
  }
  if (!draftItems?.length || !reviewData.value.details) return;
  const answerMap = {};
  draftItems.forEach(item => {
    answerMap[item.reviewDetailId] = item.answer;
  });
  reviewData.value.details.forEach(detail => {
    if (answerMap[detail.reviewDetailId] !== undefined) {
      detail.answer = answerMap[detail.reviewDetailId];
    }
  });
};

const startExam = async (row) => {
  try {
    const res = await axios.post(`/api/review/${row.reviewId}/start`, null, {
      params: { employeeId: getCurrentUser().id }
    });
    reviewData.value = res.data.data;
    examStarted.value = true;
    examFinished.value = false;
    cheatWarned.value = false;
    navigatingAway.value = false;
    await restoreDraftAnswers();
    initTimer();
    ElMessage.warning('考试已开始：首次切屏/切换系统菜单/打开其它窗口会警告，再次离开将成绩无效。');
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '开始考试失败');
  }
};

const viewReview = async (row) => {
  try {
    const res = await axios.get(`/api/review/${row.reviewId}`);
    reviewData.value = res.data.data;
    examStarted.value = true;
    examFinished.value = true;
    pollAiCommentsIfNeeded();
  } catch (error) {
    ElMessage.error('加载试卷详情失败');
  }
};

const pollAiCommentsIfNeeded = () => {
  if (aiPollTimer.value) {
    clearInterval(aiPollTimer.value);
    aiPollTimer.value = null;
  }
  const status = reviewData.value?.status;
  if (!['SUBMITTED', 'APPEALING', 'GRADED'].includes(status)) {
    return;
  }
  const subjective = (reviewData.value.details || []).filter((d) => d.type === 'SUBJECTIVE' && d.answer);
  if (!subjective.length || subjective.every((d) => d.aiComment)) {
    return;
  }
  let tries = 0;
  aiPollTimer.value = setInterval(async () => {
    tries += 1;
    try {
      const res = await axios.get(`/api/review/${reviewData.value.id}`);
      reviewData.value = res.data.data;
      const list = (reviewData.value.details || []).filter((d) => d.type === 'SUBJECTIVE' && d.answer);
      if (list.every((d) => d.aiComment) || tries >= 12) {
        clearInterval(aiPollTimer.value);
        aiPollTimer.value = null;
      }
    } catch (e) {
      clearInterval(aiPollTimer.value);
      aiPollTimer.value = null;
    }
  }, 3000);
};

const calcTimeLeftSeconds = () => {
  const startedAt = reviewData.value.startTime
    ? new Date(String(reviewData.value.startTime).replace(' ', 'T')).getTime()
    : Date.now();
  const durationSeconds = (reviewData.value.durationMinutes || 120) * 60;
  const durationLeft = Math.max(durationSeconds - Math.floor((Date.now() - startedAt) / 1000), 0);
  if (reviewData.value.deadline) {
    const deadlineLeft = Math.max(
      Math.floor((new Date(String(reviewData.value.deadline).replace(' ', 'T')).getTime() - Date.now()) / 1000),
      0
    );
    return Math.min(durationLeft, deadlineLeft);
  }
  return durationLeft;
};

const initTimer = () => {
  if (timerInterval.value) clearInterval(timerInterval.value);
  timeLeft.value = calcTimeLeftSeconds();
  if (timeLeft.value <= 0) {
    handleAutoSubmit();
    return;
  }

  timerInterval.value = setInterval(() => {
    timeLeft.value = calcTimeLeftSeconds();
    if (timeLeft.value <= 0) {
      clearInterval(timerInterval.value);
      handleAutoSubmit();
    }
  }, 1000);
};

const isExamInProgress = () =>
  examStarted.value && !examFinished.value && reviewData.value.status === 'IN_PROGRESS';

const reportCheating = async () => {
  if (!isExamInProgress() || cheatingReporting.value) {
    return;
  }
  cheatingReporting.value = true;
  try {
    const res = await axios.post(`/api/review/${reviewData.value.id}/cheat`, null, {
      params: { employeeId: getCurrentUser().id }
    });
    const result = res.data.data;
    if (result === 'WARNED') {
      cheatWarned.value = true;
      ElMessage.warning('检测到切屏/离开页面或切换系统菜单，这是第 1 次警告；再次离开将成绩无效！');
    } else if (result === 'CHEATED') {
      if (timerInterval.value) clearInterval(timerInterval.value);
      localStorage.removeItem(draftLocalKey.value);
      reviewData.value.status = 'CHEATED';
      reviewData.value.totalScore = 0;
      examFinished.value = true;
      ElMessage.error('多次切屏、离开页面或切换系统菜单，本次考核已登记为作弊零分。');
      await fetchReviewList();
    }
  } catch (error) {
    console.error('切屏上报失败', error);
  } finally {
    cheatingReporting.value = false;
  }
};

const handleVisibilityChange = () => {
  if (document.hidden) {
    reportCheating();
  }
};

const handleWindowBlur = () => {
  if (!isExamInProgress() || navigatingAway.value) {
    return;
  }
  if (blurCheckTimer) {
    clearTimeout(blurCheckTimer);
  }
  blurCheckTimer = setTimeout(() => {
    blurCheckTimer = null;
    if (!isExamInProgress() || navigatingAway.value) {
      return;
    }
    if (!document.hasFocus() || document.hidden) {
      reportCheating();
    }
  }, 280);
};

const handleBeforeUnload = () => {
  if (isExamInProgress()) {
    try {
      localStorage.setItem(draftLocalKey.value, JSON.stringify(buildSubmitPayload()));
    } catch (e) { /* ignore */ }
  }
};

const refreshCurrentReview = async () => {
  if (!reviewData.value?.id) return;
  try {
    const res = await axios.get(`/api/review/${reviewData.value.id}`);
    reviewData.value = res.data.data;
    pollAiCommentsIfNeeded();
    ElMessage.success('已刷新');
  } catch (e) {
    ElMessage.error('刷新失败');
  }
};

const backToList = async () => {
  if (timerInterval.value) clearInterval(timerInterval.value);
  if (aiPollTimer.value) {
    clearInterval(aiPollTimer.value);
    aiPollTimer.value = null;
  }
  examStarted.value = false;
  examFinished.value = false;
  reviewData.value = {};
  await fetchReviewList();
};

onBeforeRouteLeave(async () => {
  if (isExamInProgress()) {
    navigatingAway.value = true;
    try {
      localStorage.setItem(draftLocalKey.value, JSON.stringify(buildSubmitPayload()));
      await axios.post('/api/review/draft', buildSubmitPayload());
    } catch (e) { /* ignore */ }
    await reportCheating();
  }
  return true;
});

const buildSubmitPayload = () => {
  const detailItems = reviewData.value.details.map(item => ({
    reviewDetailId: item.reviewDetailId,
    indicatorId: item.indicatorId,
    answer: item.answer
  }));
  return {
    employeeId: getCurrentUser().id,
    templateId: reviewData.value.templateId,
    reviewId: reviewData.value.id,
    detailItems
  };
};

const submitData = async (isAuto = false) => {
  isSubmitting.value = true;
  try {
    const payload = buildSubmitPayload();
    await axios.post('/api/review/submit', payload);
    
    ElMessage.success(isAuto ? '考核时间到，已自动为您提交！' : '提交成功！');
    
    if (timerInterval.value) clearInterval(timerInterval.value);
    localStorage.removeItem(timerKey.value);
    localStorage.removeItem(draftLocalKey.value);
    examFinished.value = true;
    
    await fetchReviewList();
    examStarted.value = false;
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '提交失败，请重试');
  } finally {
    isSubmitting.value = false;
  }
};

const handleSaveDraft = async () => {
  draftSaving.value = true;
  try {
    const payload = buildSubmitPayload();
    localStorage.setItem(draftLocalKey.value, JSON.stringify(payload));
    await axios.post('/api/review/draft', payload);
    ElMessage.success('草稿已暂存，可安全离开后继续作答（仍请勿多次切屏）');
  } catch (error) {
    ElMessage.warning(error.response?.data?.message || '服务端暂存失败，已保存到本地');
  } finally {
    draftSaving.value = false;
  }
};

const handleManualSubmit = () => {
  ElMessageBox.confirm(
    '提交后将无法修改，确定要提交考核吗？',
    '提示',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(() => {
    submitData(false);
  }).catch(() => {});
};

const handleAutoSubmit = () => {
  ElMessage.warning('考试时长已用尽，系统正在自动提交问卷。');
  submitData(true);
};

onMounted(() => {
  fetchReviewList();
  document.addEventListener('visibilitychange', handleVisibilityChange);
  window.addEventListener('blur', handleWindowBlur);
  window.addEventListener('beforeunload', handleBeforeUnload);
});

onUnmounted(() => {
  if (timerInterval.value) clearInterval(timerInterval.value);
  if (aiPollTimer.value) clearInterval(aiPollTimer.value);
  if (blurCheckTimer) {
    clearTimeout(blurCheckTimer);
    blurCheckTimer = null;
  }
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  window.removeEventListener('blur', handleWindowBlur);
  window.removeEventListener('beforeunload', handleBeforeUnload);
});
</script>

<style scoped>
.employee-review-container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
}
.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-content h2 {
  margin: 0 0 10px 0;
}
.timer-box {
  font-size: 18px;
  font-weight: bold;
}
.timer-danger {
  color: #F56C6C;
  animation: blink 1s infinite;
}
@keyframes blink {
  50% { opacity: 0.5; }
}
.mb-20 {
  margin-bottom: 20px;
}
.mt-10 {
  margin-top: 10px;
}
.text-muted {
  color: #909399;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.manager-comment-box {
  background-color: #f4f4f5;
  padding: 15px;
  border-radius: 4px;
  border-left: 4px solid #67C23A;
}
.indicator-item {
  border-bottom: 1px dashed #ebeef5;
  padding-bottom: 20px;
  margin-bottom: 20px;
}
.indicator-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}
.score-badge {
  font-size: 16px;
  font-weight: bold;
  color: #F56C6C;
}
.readonly-answer {
  background-color: #fafafa;
  padding: 15px;
  border-radius: 4px;
}
.text-success { color: #67C23A; }
.text-danger { color: #F56C6C; }
.text-primary { color: #409EFF; font-weight: bold; font-size: 16px; }
.submit-action {
  text-align: center;
  margin-top: 30px;
}
.pagination-box {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
  align-items: center;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
