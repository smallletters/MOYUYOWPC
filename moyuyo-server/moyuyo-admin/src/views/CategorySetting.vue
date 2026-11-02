<template>
  <div class="page-wrapper">
    <div class="page-header">
      <div>
        <h2>分类设置</h2>
        <p class="page-desc">
          维护商品一级 / 二级分类及多语言名称；分类会同步用于 APP 分类页与新增商品页面。
        </p>
      </div>
      <div class="header-actions">
        <el-button plain @click="loadData">刷新</el-button>
        <el-button type="primary" @click="openCreate(1)">新增一级分类</el-button>
      </div>
    </div>

    <div class="category-layout">
      <!-- 左侧：一级分类 -->
      <el-card shadow="never" class="panel top-panel" v-loading="loading">
        <template #header>
          <div class="panel-header">
            <span class="panel-title">一级分类</span>
            <span class="panel-tip">共 {{ topCategories.length }} 个</span>
          </div>
        </template>

        <div class="top-list">
          <div
            v-for="cat in topCategories"
            :key="cat.id"
            class="top-item"
            :class="{ active: isActiveTop(cat) }"
            @click="activeTopId = String(cat.id)"
          >
            <span class="top-icon">{{ cat.icon || '🏷️' }}</span>
            <span class="top-name" :title="cat.name">{{ cat.name }}</span>
            <span class="top-count">{{ countChildren(cat.id) }}</span>
            <span class="top-ops" @click.stop>
              <el-button link type="primary" @click="openEdit(cat)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(cat)">删除</el-button>
            </span>
          </div>
          <el-empty v-if="!topCategories.length" description="暂无一级分类" :image-size="56" />
        </div>
      </el-card>

      <!-- 右侧：当前一级分类下的二级分类 -->
      <el-card shadow="never" class="panel sub-panel" v-loading="loading">
        <template #header>
          <div class="panel-header">
            <span class="panel-title">
              二级分类
              <template v-if="activeTopName">（{{ activeTopName }}）</template>
            </span>
            <el-button
              type="primary"
              link
              :disabled="!activeTopId"
              @click="openCreate(2)"
            >+ 新增二级分类</el-button>
          </div>
        </template>

        <el-table v-if="subCategories.length" :data="subCategories" stripe>
          <el-table-column label="排序" prop="sort" width="72" align="center" />
          <el-table-column label="默认名称" prop="name" min-width="150" show-overflow-tooltip />
          <el-table-column label="简体中文" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ nameOf(row, 'zh-CN') || '-' }}</template>
          </el-table-column>
          <el-table-column label="English" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ nameOf(row, 'en-US') || '-' }}</template>
          </el-table-column>
          <el-table-column label="图标" width="80" align="center">
            <template #default="{ row }">{{ row.icon || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="130" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty
          v-if="!subCategories.length"
          description="该一级分类下暂无二级分类"
          :image-size="56"
        />
      </el-card>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="540px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-width="104px">
        <el-form-item v-if="form.level === 2" label="所属一级分类">
          <el-input :model-value="activeTopName" disabled />
        </el-form-item>
        <el-form-item label="默认名称" required>
          <el-input
            v-model="form.name"
            maxlength="64"
            show-word-limit
            placeholder="后台列表与未配置多语言时使用"
          />
        </el-form-item>
        <el-form-item label="简体中文">
          <el-input v-model="form.zhCN" maxlength="64" placeholder="例如：洗护美容" />
        </el-form-item>
        <el-form-item label="English">
          <el-input v-model="form.enUS" maxlength="64" placeholder="例如：Bath & Grooming" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" maxlength="64" placeholder="可选，例如：🧴" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
          <span class="sort-tip">数值越小越靠前</span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getCategoryList,
  createCategory,
  updateCategory,
  deleteCategory
} from '../api/admin'

const categories = ref([])
const loading = ref(false)
const saving = ref(false)
const activeTopId = ref('')
const dialogVisible = ref(false)

// 弹窗表单：level=1/2，编辑时 id 非空
const form = ref({
  id: null,
  level: 1,
  parentId: '0',
  name: '',
  zhCN: '',
  enUS: '',
  icon: '',
  sort: 0
})

// 一级分类：parentId 为空 / 0；WooCommerce 同步数据可能是 null
function isTopCategory(cat) {
  const parentId = cat?.parentId
  return parentId == null || parentId === '' || String(parentId) === '0'
}

// 按排序值升序排列，排序值相同再按 id 保持稳定
function sortCategories(list) {
  return [...list].sort((a, b) => {
    const diff = (Number(a.sort) || 0) - (Number(b.sort) || 0)
    return diff !== 0 ? diff : String(a.id).localeCompare(String(b.id))
  })
}

const topCategories = computed(() => sortCategories(categories.value.filter(isTopCategory)))
const subCategories = computed(() =>
  sortCategories(
    categories.value.filter(
      cat => !isTopCategory(cat) && String(cat.parentId) === String(activeTopId.value)
    )
  )
)
const activeTopName = computed(() => {
  const top = categories.value.find(cat => String(cat.id) === String(activeTopId.value))
  return top ? top.name : ''
})
const dialogTitle = computed(() => {
  const action = form.value.id ? '编辑' : '新增'
  return `${action}${form.value.level === 1 ? '一级' : '二级'}分类`
})

function isActiveTop(cat) {
  return String(cat.id) === String(activeTopId.value)
}

function countChildren(catId) {
  return categories.value.filter(cat => !isTopCategory(cat) && String(cat.parentId) === String(catId)).length
}

// 解析后端返回的多语言 JSON，异常数据按空对象处理
function parseNameI18n(raw) {
  if (!raw) return {}
  if (typeof raw === 'object') return raw
  try {
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch (e) {
    return {}
  }
}

function nameOf(cat, locale) {
  return parseNameI18n(cat?.nameI18n)[locale] || ''
}

async function loadData() {
  loading.value = true
  try {
    const res = await getCategoryList()
    categories.value = Array.isArray(res) ? res : []
    // 保持当前选中的一级分类；分类被删除后自动回退到第一个
    const topList = sortCategories(categories.value.filter(isTopCategory))
    if (!topList.some(cat => String(cat.id) === String(activeTopId.value))) {
      activeTopId.value = topList.length ? String(topList[0].id) : ''
    }
  } catch (e) {
    categories.value = []
    activeTopId.value = ''
  } finally {
    loading.value = false
  }
}

function nextSort(list) {
  return list.reduce((max, cat) => Math.max(max, Number(cat.sort) || 0), 0) + 1
}

function openCreate(level) {
  if (level === 2 && !activeTopId.value) {
    ElMessage.warning('请先选择或新增一个一级分类')
    return
  }
  const sortBase =
    level === 1
      ? nextSort(categories.value.filter(isTopCategory))
      : nextSort(subCategories.value)
  form.value = {
    id: null,
    level,
    parentId: level === 2 ? String(activeTopId.value) : '0',
    name: '',
    zhCN: '',
    enUS: '',
    icon: '',
    sort: sortBase
  }
  dialogVisible.value = true
}

function openEdit(cat) {
  const level = isTopCategory(cat) ? 1 : 2
  const i18n = parseNameI18n(cat.nameI18n)
  form.value = {
    id: String(cat.id),
    level,
    parentId: String(cat.parentId ?? '0'),
    name: cat.name || '',
    zhCN: i18n['zh-CN'] || '',
    enUS: i18n['en-US'] || '',
    icon: cat.icon || '',
    sort: Number(cat.sort) || 0
  }
  // 编辑二级分类时，左侧选中的一级分类作为所属分类展示
  if (level === 2) {
    activeTopId.value = String(cat.parentId)
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const name = (form.value.name || '').trim()
  if (!name) {
    ElMessage.warning('请输入默认分类名称')
    return
  }

  // 组装多语言名称；未填写的语言不写入，APP 端会回落到默认名称
  const nameI18n = {}
  const zhCN = (form.value.zhCN || '').trim()
  const enUS = (form.value.enUS || '').trim()
  if (zhCN) nameI18n['zh-CN'] = zhCN
  if (enUS) nameI18n['en-US'] = enUS

  const payload = {
    name,
    nameI18n: Object.keys(nameI18n).length ? JSON.stringify(nameI18n) : null,
    icon: (form.value.icon || '').trim() || null,
    sort: Number(form.value.sort) || 0,
    level: form.value.level,
    parentId: form.value.level === 2 ? String(form.value.parentId) : '0'
  }

  saving.value = true
  try {
    if (form.value.id) {
      await updateCategory(form.value.id, payload)
      ElMessage.success('分类已更新')
    } else {
      const saved = await createCategory(payload)
      ElMessage.success('分类已创建')
      // 新增一级分类后直接选中它，便于继续维护二级分类
      if (form.value.level === 1 && saved?.id) {
        activeTopId.value = String(saved.id)
      }
    }
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    // 接口错误已由 axios 拦截器统一提示
  } finally {
    saving.value = false
  }
}

async function handleDelete(cat) {
  const childCount = countChildren(cat.id)
  const extra = childCount > 0 ? `该分类下还有 ${childCount} 个二级分类，删除后需重新创建。` : ''
  try {
    await ElMessageBox.confirm(
      `确定删除分类「${cat.name}」吗？${extra}`,
      '删除分类',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return // 用户取消
  }
  try {
    await deleteCategory(cat.id)
    ElMessage.success('分类已删除')
    await loadData()
  } catch (e) {
    // 存在商品引用等业务错误已由拦截器提示
  }
}

onMounted(loadData)
</script>

<style scoped>
.page-wrapper { }
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.page-desc { margin-top: 6px; font-size: 12px; color: var(--text-400); }
.header-actions { display: flex; gap: 8px; }

.category-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.panel { border-radius: var(--radius); }
.top-panel {
  width: 340px;
  min-width: 340px;
}
.sub-panel { flex: 1; min-width: 0; }
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.panel-title { font-size: 13px; font-weight: 600; color: var(--text-700); }
.panel-tip { font-size: 11px; color: var(--text-400); }

.top-list { display: flex; flex-direction: column; gap: 4px; }
.top-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-700);
  transition: background 0.15s ease;
}
.top-item:hover { background: var(--background-100); }
.top-item.active { background: var(--brand-50); color: var(--brand-700); font-weight: 600; }
.top-icon { flex-shrink: 0; }
.top-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.top-count { font-size: 11px; color: var(--text-400); font-variant-numeric: tabular-nums; }
.top-item.active .top-count { color: var(--brand-600); }
.top-ops { display: none; gap: 2px; }
.top-item:hover .top-ops { display: inline-flex; }

.sort-tip { margin-left: 8px; font-size: 11px; color: var(--text-400); }

@media (max-width: 1100px) {
  .category-layout { flex-direction: column; }
  .top-panel { width: 100%; min-width: 0; }
}
</style>
