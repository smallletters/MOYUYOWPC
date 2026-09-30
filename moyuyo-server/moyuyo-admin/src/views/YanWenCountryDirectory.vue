<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>燕文国家目录</h2>
      <div class="page-actions">
        <el-button type="primary" :loading="refreshing" @click="handleRefresh">
          <el-icon :size="14" style="margin-right:4px"><Refresh /></el-icon>
          强制刷新
        </el-button>
      </div>
    </div>
    <p class="page-tip">
      用于 CountryResolver 解析 receiverAddress 时按"中/英文国名"查 ISO 3166-1 alpha-2 二字码。
      缓存由后端启动期预热 + 24h 定时刷新；可在此页查看状态 / 主动刷新 / 测试地址解析。
    </p>

    <!-- ===== 状态卡片 ===== -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-header">
          <span class="section-title">缓存状态</span>
        </div>
      </template>
      <el-skeleton v-if="statusLoading" :rows="3" animated />
      <template v-else>
        <el-row :gutter="24">
          <el-col :span="6">
            <div class="metric-block">
              <div class="metric-label">燕文启用</div>
              <div class="metric-value">
                <el-tag :type="status.yanwenEnabled ? 'success' : 'danger'" size="large">
                  {{ status.yanwenEnabled ? '已启用' : '未启用' }}
                </el-tag>
              </div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="metric-block">
              <div class="metric-label">缓存大小</div>
              <div class="metric-value">
                <span style="font-size:24px;font-weight:600;">{{ status.cacheSize }}</span>
                <span style="font-size:13px;color:var(--text-400);margin-left:4px;">条（中英文去重后）</span>
              </div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="metric-block">
              <div class="metric-label">上次刷新</div>
              <div class="metric-value">
                <span style="font-size:14px;">{{ formatDate(status.lastRefreshAt) }}</span>
                <div style="font-size:12px;color:var(--text-400);">
                  耗时 {{ status.lastRefreshCostMs || 0 }} ms
                </div>
              </div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="metric-block">
              <div class="metric-label">下次自动刷新</div>
              <div class="metric-value" style="font-size:14px;">
                <span v-if="status.lastRefreshAt">
                  {{ formatDate(status.lastRefreshAt + status.refreshScheduleHours * 3600000) }}
                </span>
                <span v-else style="color:var(--text-400);">—</span>
                <div style="font-size:12px;color:var(--text-400);">
                  每 {{ status.refreshScheduleHours }} 小时
                </div>
              </div>
            </div>
          </el-col>
        </el-row>
        <!-- P0：错误高亮（admin 第一眼能看到） -->
        <!-- P0：上次刷新状态 —— 区分"业务跳过"和"HTTP 失败"两种语义 -->
        <el-alert
          v-if="status.lastRefreshError"
          :type="lastRefreshAlertType"
          :closable="false"
          show-icon
          style="margin-top:16px;"
          :title="lastRefreshAlertTitle"
          :description="status.lastRefreshError"
        />
      </template>
    </el-card>

    <!-- ===== 地址解析预览 ===== -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-header">
          <span class="section-title">地址解析预览（调试 CountryResolver 行为）</span>
          <span class="section-tip">输入收货地址，看会被解析成哪个国家码、命中哪条规则</span>
        </div>
      </template>
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="收货地址" style="flex:1;min-width:300px;">
          <el-input
            v-model="previewAddress"
            placeholder="例如：北京市朝阳区建国路 100000"
            clearable
            @keyup.enter="handlePreview"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="previewLoading" @click="handlePreview">
            <el-icon :size="14" style="margin-right:4px"><Search /></el-icon>
            解析
          </el-button>
          <el-button @click="handlePreviewClear">清空</el-button>
        </el-form-item>
      </el-form>
      <el-skeleton v-if="previewLoading && !previewResult" :rows="2" animated />
      <div v-else-if="previewResult" class="preview-result">
        <el-row :gutter="16">
          <el-col :span="6">
            <div class="preview-label">解析结果</div>
            <div class="preview-value">
              <el-tag type="success" size="large" effect="dark">{{ previewResult.resolvedCountry || '—' }}</el-tag>
              <div style="font-size:11px;color:var(--text-400);margin-top:2px;">
                命中路径：<el-tag size="small" :type="hitPathTagType">{{ hitPathLabel }}</el-tag>
              </div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="preview-label">燕文目录命中</div>
            <div class="preview-value">
              <el-tag v-if="previewResult.directoryLookupHit" type="success" size="small">
                {{ previewResult.directoryLookupHit }}
              </el-tag>
              <el-tag v-else-if="previewResult.directoryActive" type="info" size="small">未命中</el-tag>
              <el-tag v-else type="warning" size="small">未启用</el-tag>
              <div style="font-size:11px;color:var(--text-400);margin-top:2px;">
                （优先级最高）
              </div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="preview-label">yml aliases 命中</div>
            <div class="preview-value">
              <el-tag v-if="previewResult.aliasHit" type="warning" size="small">
                {{ previewResult.aliasPattern }}
              </el-tag>
              <el-tag v-else type="info" size="small">未命中</el-tag>
              <div style="font-size:11px;color:var(--text-400);margin-top:2px;">
                （目录未命中时兜底）
              </div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="preview-label">默认兜底</div>
            <div class="preview-value">
              <el-tag size="small">{{ previewResult.defaultCountry || '—' }}</el-tag>
              <div style="font-size:11px;color:var(--text-400);margin-top:2px;">
                （以上都不命中时用）
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
    </el-card>

    <!-- ===== 国家列表表格 ===== -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-header">
          <span class="section-title">国家列表（燕文通达国家全集）</span>
          <span class="section-tip">支持按国名 / ISO code 模糊搜索；点击复制 code</span>
        </div>
      </template>
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="搜索">
          <el-input
            v-model="searchKeyword"
            placeholder="国名 或 ISO code"
            clearable
            style="width:240px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="listLoading" @click="handleSearch">
            <el-icon :size="14" style="margin-right:4px"><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="filteredCountries" stripe max-height="540">
        <el-table-column prop="code" label="ISO code" width="120">
          <template #default="{ row }">
            <el-tag
              size="small"
              effect="plain"
              class="code-tag"
              @click="handleCopyCode(row.code)"
            >
              {{ row.code }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="国名（缓存归一后）" min-width="240">
          <template #default="{ row }">
            <span style="font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;">
              {{ row.name }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="handleFillPreview(row)">
              用作预览
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!listLoading && filteredCountries.length === 0" class="empty-tip">
        未匹配到任何国家（缓存大小 {{ status.cacheSize || 0 }} 条）
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  getYanwenCountryDirectoryStatus,
  listYanwenCountryDirectory,
  refreshYanwenCountryDirectory,
  previewYanwenCountryResolve
} from '../api/admin'

// ===== 状态 =====
const status = ref({
  yanwenEnabled: false,
  cacheSize: 0,
  lastRefreshAt: null,
  lastRefreshCostMs: 0,
  lastRefreshError: null,
  refreshScheduleHours: 24
})
const statusLoading = ref(false)

const countries = ref([])
const listLoading = ref(false)
const searchKeyword = ref('')

const previewAddress = ref('')
const previewResult = ref(null)
const previewLoading = ref(false)

const refreshing = ref(false)

// ===== 客户端搜索过滤（前端做，不重复打接口）=====
const filteredCountries = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return countries.value
  return countries.value.filter(c =>
    c.code.toLowerCase().includes(kw) || c.name.toLowerCase().includes(kw)
  )
})

// ===== 命中路径展示（hitPath → 类型 + 文本）=====
// 后端 hitPath: "directory"（燕文目录） / "alias"（yml aliases） / "default"（兜底）
// 前端用 computed 转成 el-tag 的 type 和中文标签
const hitPathTagType = computed(() => {
  const p = previewResult.value?.hitPath
  if (p === 'directory') return 'success'
  if (p === 'alias') return 'warning'
  return 'info'
})
const hitPathLabel = computed(() => {
  const p = previewResult.value?.hitPath
  if (p === 'directory') return '燕文目录'
  if (p === 'alias') return 'yml 别名'
  if (p === 'default') return '默认兜底'
  return '—'
})

// ===== 上次刷新 alert 标题 + 类型（区分业务跳过 vs HTTP 失败）=====
// 业务跳过（燕文未启用 / 凭证未配）→ warning，不算 error
// HTTP 失败（燕文 API 5xx / 网络超时）→ error
const lastRefreshAlertType = computed(() => {
  const err = status.value.lastRefreshError || ''
  if (err.startsWith('燕文未启用') || err.startsWith('燕文凭证未配置')) {
    return 'warning'
  }
  return 'error'
})
const lastRefreshAlertTitle = computed(() => {
  const t = lastRefreshAlertType.value
  return t === 'warning' ? '上次刷新跳过（业务原因）' : '上次刷新失败'
})

// ===== 数据加载 =====
async function loadStatus() {
  statusLoading.value = true
  try {
    const res = await getYanwenCountryDirectoryStatus()
    if (res && res.data) {
      status.value = { ...status.value, ...res.data }
    }
  } catch (e) {
    ElMessage.error('加载缓存状态失败：' + (e?.message || '未知错误'))
  } finally {
    statusLoading.value = false
  }
}

async function loadCountries() {
  listLoading.value = true
  try {
    const res = await listYanwenCountryDirectory({ limit: 500 })
    if (res && Array.isArray(res.data)) {
      countries.value = res.data
    } else {
      countries.value = []
    }
  } catch (e) {
    ElMessage.error('加载国家列表失败：' + (e?.message || '未知错误'))
    countries.value = []
  } finally {
    listLoading.value = false
  }
}

async function handleRefresh() {
  refreshing.value = true
  // 记录刷新前的缓存大小 —— 2 秒后轮询时对比，判断"是否真的刷新成功了"
  const sizeBefore = status.value.cacheSize
  try {
    const res = await refreshYanwenCountryDirectory()
    if (res && res.data) {
      ElMessage.success(res.data.message || '已提交刷新任务')
    }
    // 2 秒后轮询状态（避免立刻读还在 in-flight 的缓存）
    setTimeout(async () => {
      await loadStatus()
      await loadCountries()
      // 检测后台刷新结果，给用户明确反馈（避免运营点了刷新按钮后看不到结果）
      if (status.value.lastRefreshError) {
        ElMessage.error(`后台刷新失败：${status.value.lastRefreshError}`)
      } else if (status.value.cacheSize > sizeBefore) {
        ElMessage.success(`刷新完成：缓存 ${sizeBefore} → ${status.value.cacheSize} 条`)
      } else if (status.value.cacheSize === 0 && sizeBefore === 0) {
        // 启动时就是 0 且刷新后还是 0（说明燕文未启用或根本没拉到数据）
        ElMessage.warning('刷新完成，但缓存仍为空。请检查燕文凭证配置')
      }
    }, 2000)
  } catch (e) {
    ElMessage.error('刷新失败：' + (e?.message || '未知错误'))
  } finally {
    refreshing.value = false
  }
}

async function handlePreview() {
  if (!previewAddress.value.trim()) {
    ElMessage.warning('请输入收货地址')
    return
  }
  previewLoading.value = true
  try {
    const res = await previewYanwenCountryResolve({ address: previewAddress.value })
    if (res && res.data) {
      previewResult.value = res.data
    } else {
      previewResult.value = null
    }
  } catch (e) {
    ElMessage.error('解析失败：' + (e?.message || '未知错误'))
    previewResult.value = null
  } finally {
    previewLoading.value = false
  }
}

function handlePreviewClear() {
  previewAddress.value = ''
  previewResult.value = null
}

function handleSearch() {
  // 客户端已 computed 过滤，这里无需做事
}

function handleReset() {
  searchKeyword.value = ''
}

function handleCopyCode(code) {
  // 用现代 Clipboard API；fallback 到 execCommand（兼容旧浏览器）
  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText(code).then(
      () => ElMessage.success('已复制：' + code),
      () => fallbackCopy(code)
    )
  } else {
    fallbackCopy(code)
  }
}

function fallbackCopy(code) {
  const ta = document.createElement('textarea')
  ta.value = code
  ta.style.position = 'fixed'
  ta.style.top = '-1000px'
  document.body.appendChild(ta)
  ta.select()
  try {
    document.execCommand('copy')
    ElMessage.success('已复制：' + code)
  } catch {
    ElMessage.warning('复制失败，请手动复制：' + code)
  }
  document.body.removeChild(ta)
}

function handleFillPreview(row) {
  // 把国名拼到 address 里，方便运营直观验证"这条记录在真实地址里能命中"
  previewAddress.value = `测试地址 ${row.name}`
  ElMessage.info(`已填入"${row.name}"到预览框（点解析按钮验证）`)
}

function formatDate(ts) {
  // P0：后端改返毫秒戳（避免 ISO 字符串 + 时区漂移）。
  // 注意：如果还有旧版本后端返回 ISO 字符串，也兼容一下。
  if (!ts) return '—'
  let ms
  if (typeof ts === 'number') {
    ms = ts
  } else if (typeof ts === 'string') {
    ms = new Date(ts).getTime()
  } else {
    return '—'
  }
  if (!ms || isNaN(ms)) return '—'
  const date = new Date(ms)
  // toLocaleString 用本地时区显示 —— 不会出现 UTC 漂移
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

onMounted(() => {
  loadStatus()
  loadCountries()
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.page-tip { font-size: 12px; color: var(--text-500); margin-bottom: 16px; line-height: 1.6; }

.section-card { margin-bottom: 16px; }
.section-header { display: flex; align-items: center; justify-content: space-between; }
.section-title { font-size: 14px; font-weight: 600; color: var(--text-800); }
.section-tip { font-size: 12px; color: var(--text-400); }

.metric-block { padding: 12px 0; }
.metric-label { font-size: 12px; color: var(--text-400); margin-bottom: 8px; }
.metric-value { color: var(--text-800); }

.preview-result {
  background: var(--background-100);
  border-radius: 6px;
  padding: 16px;
  margin-top: 8px;
}
.preview-label { font-size: 12px; color: var(--text-500); margin-bottom: 6px; }
.preview-value { min-height: 36px; display: flex; flex-direction: column; gap: 2px; }

.code-tag {
  cursor: pointer;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-weight: 600;
  transition: all 0.18s ease;
}
.code-tag:hover {
  transform: scale(1.05);
  box-shadow: 0 0 0 2px var(--primary-200);
}

.empty-tip {
  padding: 40px 20px;
  text-align: center;
  color: var(--text-400);
  font-size: 13px;
}
</style>
