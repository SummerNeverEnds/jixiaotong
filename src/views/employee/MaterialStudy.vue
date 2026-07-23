<template>
  <div class="material-study-container">
    <div class="page-header">
      <h2>SOP 学习与技能提升资料库</h2>
      <div class="header-tags">
        <el-tag type="success">仅展示本周期上架资料</el-tag>
        <el-tag type="warning">视频进度≥95% / 其它类型在链接页停留≥1分钟 方可完成</el-tag>
      </div>
    </div>

    <el-skeleton :rows="5" animated v-if="loading" class="mt-20" />
    <el-empty v-else-if="materialList.length === 0" description="暂无学习资料" class="mt-20" />

    <el-row :gutter="20" v-else class="mt-20">
      <el-col :span="8" v-for="item in materialList" :key="item.id" class="mb-20">
        <el-card shadow="hover" class="material-card">
          <div class="material-header">
            <i :class="getIconByType(item.type)" :style="{ color: getColorByType(item.type) }"></i>
            <span class="material-title">{{ item.title }}</span>
            <el-tag :type="item.completed ? 'success' : 'info'" size="small">
              {{ item.completed ? '已完成' : '未完成' }}
            </el-tag>
          </div>
          <div class="material-desc">{{ item.description || '暂无详细描述...' }}</div>
          <div class="material-meta">
            <span>截止：{{ formatDate(item.deadline) || '未设置' }}</span>
            <span class="rule-tip">{{ item.type === 'VIDEO' ? '需观看≥95%' : '需停留≥1分钟' }}</span>
          </div>
          <div class="material-footer">
            <span class="time">发布：{{ formatDate(item.createTime) }}</span>
            <el-button :type="item.completed ? 'success' : 'primary'" size="small" plain @click="openStudy(item)">
              {{ item.completed ? '再次学习' : '开始学习' }}
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div v-if="!loading && total > 0" class="pagination-box">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[6, 12, 24]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <el-dialog
      v-model="studyVisible"
      :title="currentMaterial?.title || '学习中'"
      width="820px"
      destroy-on-close
      :close-on-click-modal="false"
      @closed="onStudyClosed"
    >
      <div v-if="currentMaterial" class="study-body">
        <el-alert
          :type="canComplete ? 'success' : 'info'"
          :closable="false"
          show-icon
          class="mb-12"
          :title="studyHint"
        />

        <template v-if="currentMaterial.type === 'VIDEO'">
          <video
            v-if="!videoFallback"
            ref="videoRef"
            class="study-video"
            :src="currentMaterial.url"
            controls
            controlsList="nodownload noplaybackrate"
            disablePictureInPicture
            @timeupdate="onVideoTimeUpdate"
            @seeking="onVideoSeeking"
            @seeked="onVideoSeeking"
            @loadedmetadata="onVideoLoaded"
            @error="onVideoError"
          />
          <div v-else class="fallback-box">
            <p>当前视频无法内嵌播放，请打开链接窗口学习；需在链接窗口连续停留折算进度（约 1 分钟计为 100%）。未达标前切换离开将重新计时。</p>
            <el-button type="primary" @click="openExternal">打开资料链接</el-button>
            <p class="link-status">{{ linkStatusText }}</p>
          </div>
          <div class="progress-row">
            <span>观看进度</span>
            <el-progress :percentage="Math.min(100, Math.floor(watchProgress))" :status="watchProgress >= 95 ? 'success' : ''" />
            <span class="progress-text">{{ Math.floor(watchProgress) }}% / 95%</span>
          </div>
        </template>

        <template v-else>
          <div class="fallback-box">
            <p>请打开资料链接进行学习。仅累计<strong>停留在链接窗口</strong>的连续时间，满 <b>1 分钟</b> 可完成；未达标前切换离开将重新计时，达标后切回可直接标记完成。</p>
            <el-button type="primary" @click="openExternal">打开资料链接</el-button>
            <p class="link-status">{{ linkStatusText }}</p>
          </div>
          <div class="progress-row">
            <span>停留时间</span>
            <el-progress :percentage="stayPercent" :status="staySeconds >= REQUIRED_STAY ? 'success' : ''" />
            <span class="progress-text">{{ formatStay(staySeconds) }} / 01:00</span>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="studyVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :disabled="!canComplete || completing || currentMaterial?.completed"
          :loading="completing"
          @click="submitComplete"
        >
          {{ currentMaterial?.completed ? '已完成' : '标记完成' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import axios from 'axios';

const REQUIRED_STAY = 60;
const VIDEO_REQUIRED = 95;

const FALLBACK_VIDEO_SECONDS = 60;

const materialList = ref([]);
const loading = ref(false);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(6);
const studyVisible = ref(false);
const currentMaterial = ref(null);
const watchProgress = ref(0);
const staySeconds = ref(0);
const videoFallback = ref(false);
const completing = ref(false);
const linkWindowOpen = ref(false);

const linkTimingActive = ref(false);

const linkProgressLocked = ref(false);
const videoRef = ref(null);

let stayTimer = null;
let completedSubmitted = false;

let maxWatchedTime = 0;

let externalWin = null;

let wasLinkForeground = false;

const getCurrentUser = () => JSON.parse(localStorage.getItem('userInfo') || '{}');

const stayPercent = computed(() => Math.min(100, Math.floor((staySeconds.value / REQUIRED_STAY) * 100)));
const canComplete = computed(() => {
  if (!currentMaterial.value) return false;
  if (currentMaterial.value.type === 'VIDEO') {
    return watchProgress.value >= VIDEO_REQUIRED;
  }
  return staySeconds.value >= REQUIRED_STAY;
});
const studyHint = computed(() => {
  if (!currentMaterial.value) return '';
  if (currentMaterial.value.completed) return '该资料已完成，可继续复习';
  if (currentMaterial.value.type === 'VIDEO') {
    if (videoFallback.value) {
      return canComplete.value
        ? '链接窗口累计进度已达标，可点击「标记完成」'
        : '请保持资料链接窗口打开，累计进度达到 95%';
    }
    return canComplete.value
      ? '观看进度已达标，可点击「标记完成」'
      : '请顺序观看，不可拖动进度条；进度达到 95% 后可完成';
  }
  return canComplete.value
    ? '连续停留已达标，可点击「标记完成」'
    : '请打开资料链接并保持停留在该窗口，满 1 分钟；未达标前中途切换将重新计时';
});

const linkStatusText = computed(() => {
  if (linkProgressLocked.value || canComplete.value) {
    return '链接学习已达标，进度已锁定，可点击「标记完成」';
  }
  if (!linkWindowOpen.value) return '链接窗口：未打开（不计时）';
  if (linkTimingActive.value) return '链接窗口：前台停留中，连续计时…';
  return '链接窗口：已离开前台，计时已清零，回到链接窗口后重新计时';
});

const needsLinkTiming = () => {
  if (!currentMaterial.value) return false;
  return currentMaterial.value.type !== 'VIDEO' || videoFallback.value;
};

const isLinkWindowForeground = () => {
  if (!externalWin || externalWin.closed) return false;
  try {
    return !!externalWin.document?.hasFocus?.();
  } catch {
    return !document.hasFocus();
  }
};

const isLinkProgressMet = () => {
  if (!currentMaterial.value) return false;
  if (currentMaterial.value.type === 'VIDEO' && videoFallback.value) {
    return watchProgress.value >= VIDEO_REQUIRED;
  }
  if (currentMaterial.value.type !== 'VIDEO') {
    return staySeconds.value >= REQUIRED_STAY;
  }
  return false;
};

const lockLinkProgressIfMet = () => {
  if (isLinkProgressMet()) {
    linkProgressLocked.value = true;
  }
};

const resetLinkStayProgress = () => {
  if (linkProgressLocked.value || isLinkProgressMet()) {
    linkProgressLocked.value = true;
    return;
  }
  staySeconds.value = 0;
  if (videoFallback.value) {
    watchProgress.value = 0;
  }
};

const fetchMaterials = async () => {
  loading.value = true;
  try {
    const res = await axios.get('/api/material/page', {
      params: {
        employeeId: getCurrentUser().id,
        current: currentPage.value,
        size: pageSize.value
      }
    });
    materialList.value = res.data.data.records || [];
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
  fetchMaterials();
};

const handleCurrentChange = (val) => {
  currentPage.value = val;
  fetchMaterials();
};

const getIconByType = (type) => {
  const map = { VIDEO: 'el-icon-video-camera', DOC: 'el-icon-document', LINK: 'el-icon-link' };
  return map[type] || 'el-icon-collection';
};

const getColorByType = (type) => {
  const map = { VIDEO: '#F56C6C', DOC: '#409EFF', LINK: '#67C23A' };
  return map[type] || '#909399';
};

const formatDate = (dateStr) => {
  if (!dateStr) return '';
  return String(dateStr).replace('T', ' ').substring(0, 16);
};

const formatStay = (seconds) => {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
};

const clearStayTimer = () => {
  if (stayTimer) {
    clearInterval(stayTimer);
    stayTimer = null;
  }
};

const closeExternalWin = () => {
  if (externalWin && !externalWin.closed) {
    try { externalWin.close(); } catch (_) { /* ignore */ }
  }
  externalWin = null;
  linkWindowOpen.value = false;
  linkTimingActive.value = false;
  wasLinkForeground = false;
};

const startLinkStayTimer = () => {
  clearStayTimer();
  wasLinkForeground = false;
  stayTimer = setInterval(() => {
    if (!needsLinkTiming()) return;

    if (linkProgressLocked.value || isLinkProgressMet()) {
      lockLinkProgressIfMet();
      linkTimingActive.value = false;
      const open = !!(externalWin && !externalWin.closed);
      linkWindowOpen.value = open;
      return;
    }

    const open = !!(externalWin && !externalWin.closed);
    linkWindowOpen.value = open;
    if (!open) {
      if (wasLinkForeground || staySeconds.value > 0) {
        resetLinkStayProgress();
      }
      linkTimingActive.value = false;
      wasLinkForeground = false;
      return;
    }

    const foreground = isLinkWindowForeground();
    linkTimingActive.value = foreground;

    if (wasLinkForeground && !foreground) {
      resetLinkStayProgress();
      wasLinkForeground = false;
      return;
    }

    if (!foreground) {
      wasLinkForeground = false;
      return;
    }

    wasLinkForeground = true;
    staySeconds.value += 1;
    if (currentMaterial.value?.type === 'VIDEO' && videoFallback.value) {
      watchProgress.value = Math.min(100, (staySeconds.value / FALLBACK_VIDEO_SECONDS) * 100);
    }
    lockLinkProgressIfMet();
  }, 1000);
};

const onParentFocus = () => {
  if (!studyVisible.value || !needsLinkTiming()) return;
  lockLinkProgressIfMet();
  if (linkProgressLocked.value) {
    linkTimingActive.value = false;
    wasLinkForeground = false;
    return;
  }
  if (wasLinkForeground || staySeconds.value > 0) {
    resetLinkStayProgress();
  }
  linkTimingActive.value = false;
  wasLinkForeground = false;
};

const openStudy = (item) => {
  closeExternalWin();
  clearStayTimer();
  currentMaterial.value = item;
  watchProgress.value = 0;
  staySeconds.value = 0;
  maxWatchedTime = 0;
  videoFallback.value = false;
  completedSubmitted = false;
  linkWindowOpen.value = false;
  linkProgressLocked.value = false;
  studyVisible.value = true;
  if (item.type !== 'VIDEO') {
    startLinkStayTimer();
  }
};

const onStudyClosed = () => {
  clearStayTimer();
  closeExternalWin();
  currentMaterial.value = null;
  watchProgress.value = 0;
  staySeconds.value = 0;
  maxWatchedTime = 0;
  videoFallback.value = false;
  linkWindowOpen.value = false;
  linkTimingActive.value = false;
  linkProgressLocked.value = false;
};

const onVideoLoaded = () => {
  videoFallback.value = false;
  maxWatchedTime = 0;
};

const onVideoSeeking = () => {
  const el = videoRef.value;
  if (!el) return;
  if (el.currentTime > maxWatchedTime + 0.35) {
    el.currentTime = maxWatchedTime;
  }
};

const onVideoTimeUpdate = () => {
  const el = videoRef.value;
  if (!el || !el.duration || !Number.isFinite(el.duration) || el.duration <= 0) return;

  if (el.currentTime > maxWatchedTime + 0.35) {
    el.currentTime = maxWatchedTime;
    return;
  }
  if (el.currentTime > maxWatchedTime) {
    maxWatchedTime = el.currentTime;
  }
  watchProgress.value = Math.min(100, (maxWatchedTime / el.duration) * 100);
};

const onVideoError = () => {
  videoFallback.value = true;
  startLinkStayTimer();
  ElMessage.warning('视频无法内嵌播放，请打开链接窗口学习');
};

const openExternal = () => {
  if (!currentMaterial.value?.url) {
    ElMessage.warning('该资料暂未配置链接');
    return;
  }
  if (externalWin && !externalWin.closed) {
    try { externalWin.focus(); } catch (_) { /* ignore */ }
    linkWindowOpen.value = true;
    return;
  }
  externalWin = window.open(currentMaterial.value.url, '_blank');
  if (!externalWin) {
    ElMessage.error('弹窗被拦截，请允许本站打开新窗口后重试');
    linkWindowOpen.value = false;
    return;
  }
  linkWindowOpen.value = true;
  if (currentMaterial.value.type !== 'VIDEO' || videoFallback.value) {
    startLinkStayTimer();
  }
};

const submitComplete = async () => {
  if (!canComplete.value || !currentMaterial.value) return;
  completing.value = true;
  try {
    await axios.post('/api/material/complete', {
      employeeId: getCurrentUser().id,
      materialId: currentMaterial.value.id,
      watchProgress: Number(watchProgress.value.toFixed(2)),
      staySeconds: staySeconds.value
    });
    currentMaterial.value.completed = true;
    completedSubmitted = true;
    const row = materialList.value.find(m => m.id === currentMaterial.value.id);
    if (row) row.completed = true;
    ElMessage.success(`已完成学习任务：${currentMaterial.value.title}`);
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '学习完成状态更新失败');
  } finally {
    completing.value = false;
  }
};

onMounted(() => {
  window.addEventListener('focus', onParentFocus);
  fetchMaterials();
});

onBeforeUnmount(() => {
  window.removeEventListener('focus', onParentFocus);
  clearStayTimer();
  closeExternalWin();
});
</script>

<style scoped>
.material-study-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 15px;
  gap: 12px;
  flex-wrap: wrap;
}
.header-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.mt-20 { margin-top: 20px; }
.mb-20 { margin-bottom: 20px; }
.mb-12 { margin-bottom: 12px; }

.material-card {
  height: 240px;
}
.material-card :deep(.el-card__body) {
  height: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  padding: 16px;
}
.material-meta {
  font-size: 12px;
  color: #e6a23c;
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
}
.rule-tip { color: #909399; }
.material-header {
  font-size: 16px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  flex-shrink: 0;
}
.material-header .el-tag { margin-left: auto; flex-shrink: 0; }
.material-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}
.material-desc {
  color: #606266;
  font-size: 13px;
  flex: 1;
  min-height: 54px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.material-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  border-top: 1px solid #f0f2f5;
  padding-top: 10px;
  flex-shrink: 0;
}
.time { font-size: 12px; color: #909399; }
.pagination-box {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}
.study-video {
  width: 100%;
  max-height: 420px;
  background: #000;
  border-radius: 4px;
}
.study-video::-webkit-media-controls-timeline {
  pointer-events: none;
}
.study-video::-webkit-media-controls-current-time-display,
.study-video::-webkit-media-controls-time-remaining-display {
  pointer-events: none;
}
.fallback-box {
  padding: 24px;
  background: #f5f7fa;
  border-radius: 6px;
  text-align: center;
  color: #606266;
  line-height: 1.8;
}
.link-status {
  margin-top: 10px;
  font-size: 13px;
  color: #909399;
}
.progress-row {
  margin-top: 16px;
  display: grid;
  grid-template-columns: 70px 1fr 90px;
  align-items: center;
  gap: 12px;
}
.progress-text {
  font-size: 13px;
  color: #606266;
  text-align: right;
}
</style>
