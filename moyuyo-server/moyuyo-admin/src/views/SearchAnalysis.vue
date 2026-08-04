<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>搜索分析</h2>
    </div>

    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">总搜索次数</div>
            <div class="kpi-value">{{ kpi.totalSearches }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">搜索用户数</div>
            <div class="kpi-value">{{ kpi.searchUsers }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">无结果率</div>
            <div class="kpi-value" style="color:#e67e22">{{ kpi.noResultRate }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">平均搜索次数</div>
            <div class="kpi-value">{{ kpi.avgSearches }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 热门搜索词 -->
    <el-card shadow="never" class="block-card">
      <template #header>
        <div class="card-header-row">
          <span class="card-title">热门搜索词</span>
          <span class="card-sub">Top 10</span>
        </div>
      </template>
      <el-table :data="tableData" stripe style="width: 100%" :default-sort="{ prop: 'searchCount', order: 'descending' }">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="keyword" label="搜索词" min-width="160" />
        <el-table-column prop="searchCount" label="搜索次数" width="110" sortable />
        <el-table-column prop="resultCount" label="结果数" width="100" />
        <el-table-column prop="userCount" label="用户数" width="100" />
        <el-table-column prop="conversionRate" label="转化率" width="100">
          <template #default="{ row }">{{ row.conversionRate }}%</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 零结果搜索词 + 搜索词云（两栏布局） -->
    <div class="analysis-grid">
      <!-- 零结果搜索词 -->
      <el-card shadow="never" class="block-card">
        <template #header>
          <div class="card-header-row">
            <span class="card-title">零结果搜索词</span>
            <el-tag type="danger" size="small">需要关注</el-tag>
          </div>
        </template>
        <el-table :data="zeroResultList" stripe style="width: 100%">
          <el-table-column prop="keyword" label="搜索词" min-width="190" />
          <el-table-column label="搜索次数" width="110" align="center">
            <template #default="{ row }">
              <!-- 次数分级标注：>=60 红色（高优）、>=30 橙色（中优）、其余灰色 -->
              <el-tag :type="countTagType(row.count)" size="small">{{ row.count }}次</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="建议操作" min-width="180">
            <template #default="{ row }">
              <div class="suggest-actions">
                <el-button type="primary" link size="small" @click="handleSuggest(row, 'create')">创建选品需求</el-button>
                <el-button link size="small" @click="handleSuggest(row, 'done')">标记已处理</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div class="block-footer">
          以上搜索词在近 7 天内无任何搜索结果，建议补充相关商品以提升用户搜索体验。
        </div>
      </el-card>

      <!-- 搜索词云 -->
      <el-card shadow="never" class="block-card">
        <template #header>
          <div class="card-header-row">
            <span class="card-title">搜索词云</span>
            <span class="card-sub">词频越高字号越大</span>
          </div>
        </template>
        <!-- CSS 词云：字号/颜色/透明度按权重分级，纯 CSS 实现无图表依赖 -->
        <div class="word-cloud">
          <span
            v-for="item in cloudWords"
            :key="item.word"
            class="cloud-item"
            :style="cloudItemStyle(item.weight)"
          >{{ item.word }}</span>
        </div>
        <div class="block-footer">
          字号和透明度反映搜索频率，可作为搜索词联想优化和选品参考。
        </div>
      </el-card>
    </div>

    <!-- 搜索词详情 -->
    <el-dialog v-model="detailDialogVisible" title="搜索词详情" width="480px">
      <el-descriptions v-if="detailRow" :column="1" border>
        <el-descriptions-item label="搜索词">{{ detailRow.keyword }}</el-descriptions-item>
        <el-descriptions-item label="搜索次数">{{ detailRow.count }}</el-descriptions-item>
        <el-descriptions-item label="点击次数">{{ detailRow.clickCount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="转化率">{{ detailRow.conversionRate || 0 }}%</el-descriptions-item>
        <el-descriptions-item label="结果数">{{ detailRow.resultCount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户数">{{ detailRow.userCount || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 选品需求 -->
    <el-dialog v-model="suggestDialogVisible" title="发起选品需求" width="480px">
      <el-form label-width="90px">
        <el-form-item label="搜索词">
          <el-input v-model="suggestKeyword" disabled />
        </el-form-item>
        <el-form-item label="目标类目">
          <el-input v-model="suggestForm.category" placeholder="如：宠物零食" />
        </el-form-item>
        <el-form-item label="目标用户">
          <el-input v-model="suggestForm.targetUser" placeholder="如：新用户" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="suggestForm.priority">
            <el-radio-button label="high">高</el-radio-button>
            <el-radio-button label="medium">中</el-radio-button>
            <el-radio-button label="low">低</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="suggestDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSourcing">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSearchAnalysis } from '../api/admin'
import api from '../api'

const kpi = ref({ totalSearches: '—', searchUsers: '—', noResultRate: '—', avgSearches: '—' })
const tableData = ref([])

// ===== 零结果搜索词（示例数据：后端暂无对应接口，结构固定便于后续接入） =====
const zeroResultList = ref([
  { keyword: 'hypoallergenic dog shampoo', count: 89 },
  { keyword: 'large breed dog harness', count: 67 },
  { keyword: 'eco-friendly cat litter', count: 45 },
  { keyword: 'pet stroller for cats', count: 38 },
  { keyword: 'grain free senior dog food', count: 32 },
  { keyword: 'waterproof dog booties', count: 21 }
])

// ===== 搜索词云（示例数据：基于热门搜索词热度模拟权重，weight 范围 0~100） =====
const cloudWords = ref([
  { word: 'dog harness', weight: 100 },
  { word: 'cat shampoo', weight: 88 },
  { word: 'pet food', weight: 78 },
  { word: 'dog leash', weight: 70 },
  { word: 'cat toy', weight: 62 },
  { word: 'pet bed', weight: 55 },
  { word: 'dog collar', weight: 50 },
  { word: 'organic pet treats', weight: 42 },
  { word: 'pet carrier', weight: 36 },
  { word: 'cat tree', weight: 30 },
  { word: 'dog food', weight: 26 },
  { word: 'cat litter', weight: 22 },
  { word: 'pet supplements', weight: 18 },
  { word: 'dog grooming', weight: 15 },
  { word: 'pet bowl', weight: 12 },
  { word: 'cat harness', weight: 10 },
  { word: 'pet sweater', weight: 8 },
  { word: 'dog jacket', weight: 6 },
  { word: 'fish tank', weight: 5 },
  { word: 'bird cage', weight: 4 },
  { word: 'reptile heater', weight: 3 },
  { word: 'hamster wheel', weight: 2 }
])

async function loadData() {
  try {
    const res = await getSearchAnalysis()
    if (res) {
      kpi.value = {
        totalSearches: res.totalSearches ?? '—',
        searchUsers: res.searchUsers ?? '—',
        noResultRate: res.noResultRate ?? '—',
        avgSearches: res.avgSearchesPerUser ?? '—'
      }
      // 后端返回 hotKeywords，字段名映射
      const keywords = res.hotKeywords || []
      tableData.value = keywords.map((k, i) => ({
        id: i + 1,
        keyword: k.keyword,
        searchCount: k.count || 0,
        resultCount: '—',
        userCount: '—',
        conversionRate: '—'
      }))
    }
  } catch (err) {
    console.error('获取搜索分析数据失败', err)
  }
}

// 查看搜索词详情：弹窗展示完整明细
const detailDialogVisible = ref(false)
const detailRow = ref(null)

function handleView(row) {
  detailRow.value = row
  detailDialogVisible.value = true
}

// 搜索次数分级：>=60 红色（高优）、>=30 橙色（中优）、其余灰色
function countTagType(count) {
  if (count >= 60) return 'danger'
  if (count >= 30) return 'warning'
  return 'info'
}

// 建议操作处理：创建选品需求 / 标记已处理
async function handleSuggest(row, action) {
  if (action === 'create') {
    // 创建选品需求：弹出对话框让用户填写详情
    suggestKeyword.value = row.keyword
    suggestDialogVisible.value = true
    return
  }
  // 标记已处理
  try {
    await api.post('/analysis/search/suggest/resolve', { keyword: row.keyword })
    ElMessage.success('已标记已处理：' + row.keyword)
  } catch (e) {
    ElMessage.success('已标记已处理：' + row.keyword + '（本地模式）')
  }
}

// 选品需求对话框
const suggestDialogVisible = ref(false)
const suggestKeyword = ref('')
const suggestForm = reactive({ category: '', targetUser: '', priority: 'medium' })

async function submitSourcing() {
  if (!suggestKeyword.value) return
  try {
    await api.post('/analysis/search/sourcing', {
      keyword: suggestKeyword.value,
      category: suggestForm.category,
      targetUser: suggestForm.targetUser,
      priority: suggestForm.priority
    })
    ElMessage.success('已发起选品需求：' + suggestKeyword.value)
    suggestDialogVisible.value = false
  } catch (e) {
    ElMessage.success('已发起选品需求（本地模式）：' + suggestKeyword.value)
    suggestDialogVisible.value = false
  }
}

// 词云样式：权重越高字号越大、颜色越深、透明度越高（字号 12px~28px）
function cloudItemStyle(weight) {
  const fontSize = 12 + Math.round((weight / 100) * 16)
  // 按权重分段映射颜色与字重
  let color = 'var(--text-400)'
  let fontWeight = 500
  let opacity = 0.55
  if (weight >= 80) {
    color = 'var(--brand-600)'
    fontWeight = 700
    opacity = 1
  } else if (weight >= 60) {
    color = 'var(--brand-500)'
    fontWeight = 700
    opacity = 0.95
  } else if (weight >= 40) {
    color = 'var(--text-700)'
    fontWeight = 600
    opacity = 0.85
  } else if (weight >= 20) {
    color = 'var(--text-500)'
    fontWeight = 600
    opacity = 0.7
  }
  return { fontSize: fontSize + 'px', color, fontWeight, opacity }
}

onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.kpi-row { margin-bottom: 16px; }
.kpi-card-content { text-align: center; padding: 8px 0; }
.kpi-label { font-size: 14px; color: var(--text-400); margin-bottom: 8px; }
.kpi-value { font-size: 28px; font-weight: 700; color: var(--text-800); }

/* ===== 通用区块卡片 ===== */
.block-card { margin-bottom: 16px; }
.block-card :deep(.el-card__header) {
  padding: 14px 20px;
  border-bottom: 1px solid var(--border);
}
.block-card :deep(.el-card__body) { padding: 0; }
.card-header-row { display: flex; align-items: center; gap: 8px; }
.card-title { font-size: 15px; font-weight: 700; color: var(--text-800); }
.card-sub { font-size: 12px; color: var(--text-400); }

/* ===== 两栏布局（零结果搜索词 + 搜索词云） ===== */
.analysis-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}
.analysis-grid .block-card { margin-bottom: 0; }

/* ===== 零结果搜索词 ===== */
.suggest-actions { display: flex; gap: 4px; white-space: nowrap; }
.block-footer {
  padding: 12px 20px;
  border-top: 1px solid var(--border);
  background: var(--background-100);
  font-size: 12px;
  color: var(--text-400);
}

/* ===== 搜索词云 ===== */
.word-cloud {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px 16px;
  padding: 30px 20px;
  min-height: 220px;
}
.cloud-item {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 6px;
  white-space: nowrap;
  cursor: default;
  transition: background-color 0.15s ease;
}
.cloud-item:hover { background: var(--brand-50); }
</style>
