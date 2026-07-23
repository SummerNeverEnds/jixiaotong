<template>
  <div class="archive-search">
    <div class="header-actions">
      <h2>历年绩效档案全文检索</h2>
      <el-tag type="warning" effect="dark">Powered by Elasticsearch</el-tag>
    </div>

    <el-card shadow="never" class="mb-20">
      <div class="search-row">
        <el-input
          v-model="keyword"
          placeholder="请输入关键词（员工姓名、考核周期、自评、AI评语、主管评语）"
          clearable
          @keyup.enter="handleSearch"
          class="keyword-input"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <div class="score-range">
          <span class="score-label">分数区间</span>
          <el-input-number v-model="minScore" :min="0" :max="100" :precision="1" :controls="false" placeholder="最低分" />
          <span class="score-sep">-</span>
          <el-input-number v-model="maxScore" :min="0" :max="100" :precision="1" :controls="false" placeholder="最高分" />
        </div>
        <el-button type="primary" @click="handleSearch" :loading="loading">深度搜索</el-button>
        <el-button @click="resetSearch">重置</el-button>
        <el-button @click="syncArchive" :loading="syncing">同步归档数据到 ES</el-button>
      </div>
    </el-card>

    <el-table :data="tableData" border style="width: 100%" v-loading="loading">
      <el-table-column prop="employeeName" label="员工姓名" width="120" />
      <el-table-column prop="cycleName" label="考核周期" width="120" />
      <el-table-column prop="totalScore" label="最终总分" width="100">
        <template #default="scope">
          <span style="color: #F56C6C; font-weight: bold">{{ scope.row.totalScore }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="employeeAnswers" label="员工主观自评摘要" min-width="250" show-overflow-tooltip />
      <el-table-column prop="aiComments" label="AI 预评分析摘要" min-width="200" show-overflow-tooltip />
      <el-table-column prop="managerComment" label="主管最终评语" min-width="200" show-overflow-tooltip />
      <el-table-column prop="archiveTime" label="归档时间" width="180" />
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

const keyword = ref('');
const minScore = ref(null);
const maxScore = ref(null);
const loading = ref(false);
const syncing = ref(false);
const tableData = ref([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);

const fetchArchive = async (showMessage = true) => {
  if (minScore.value != null && maxScore.value != null && minScore.value > maxScore.value) {
    ElMessage.warning('最低分不能大于最高分');
    return;
  }
  loading.value = true;
  try {
    const res = await axios.get('/api/search/archive', {
      params: {
        keyword: keyword.value || '',
        minScore: minScore.value,
        maxScore: maxScore.value,
        current: currentPage.value,
        size: pageSize.value
      }
    });
    tableData.value = res.data.data.records || [];
    total.value = res.data.data.total || 0;
    if (showMessage) {
      if (total.value === 0) {
        ElMessage.info(
          keyword.value?.trim() || minScore.value != null || maxScore.value != null
            ? '未检索到匹配的绩效档案，可先点「同步归档数据到 ES」后再试'
            : '暂无已归档的绩效档案'
        );
      } else {
        ElMessage.success(`共检索到 ${total.value} 条数据`);
      }
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '检索失败，请检查后端服务是否启动');
  } finally {
    loading.value = false;
  }
};

const handleSearch = async () => {
  currentPage.value = 1;
  await fetchArchive(true);
};

const resetSearch = () => {
  keyword.value = '';
  minScore.value = null;
  maxScore.value = null;
  currentPage.value = 1;
  fetchArchive(false);
};

const handleSizeChange = (val) => {
  pageSize.value = val;
  currentPage.value = 1;
  fetchArchive(false);
};

const handleCurrentChange = (val) => {
  currentPage.value = val;
  fetchArchive(false);
};

const syncArchive = async () => {
  syncing.value = true;
  try {
    const res = await axios.post('/api/search/sync-archive');
    ElMessage.success(`已同步 ${res.data.data || 0} 条归档绩效数据到 ES`);
    await fetchArchive(false);
  } catch (error) {
    ElMessage.error('同步失败，请检查 Elasticsearch 服务是否启动');
  } finally {
    syncing.value = false;
  }
};

onMounted(() => {
  fetchArchive(false);
});
</script>

<style scoped>
.archive-search { padding: 20px; }
.header-actions { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.mb-20 { margin-bottom: 20px; }
.search-row { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.keyword-input { width: 360px; min-width: 240px; }
.score-range { display: flex; align-items: center; gap: 8px; }
.score-label { color: #606266; white-space: nowrap; }
.score-sep { color: #909399; }
.score-range :deep(.el-input-number) { width: 100px; }
.pagination-box { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
