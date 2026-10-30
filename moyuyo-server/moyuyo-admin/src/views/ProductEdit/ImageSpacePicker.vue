<template>
  <!-- 图片空间选择器：供商品图库 / 详情富文本选择已上传图片 -->
  <el-dialog
    v-model="visible"
    :title="title"
    width="900px"
    append-to-body
    :close-on-click-modal="false"
  >
    <div v-loading="loading" class="picker-body">
      <div class="picker-toolbar">
        <el-checkbox
          :model-value="allSelected"
          :indeterminate="indeterminate"
          @change="toggleSelectAll"
        >
          全选本页
        </el-checkbox>
        <el-select
          v-model="groupId"
          size="small"
          style="width: 170px"
          @change="handleGroupChange"
        >
          <el-option label="全部分组" :value="-1" />
          <el-option label="未分组" :value="0" />
          <el-option
            v-for="g in groups"
            :key="g.id"
            :label="`${g.name}（${g.imageCount}）`"
            :value="Number(g.id)"
          />
        </el-select>
        <span class="picker-tip">已选 {{ selectedCount }} 张（点击图片切换选择）</span>
      </div>

      <div v-if="images.length" class="picker-grid">
        <div
          v-for="img in images"
          :key="img.url"
          class="picker-item"
          :class="{ 'is-selected': isSelected(img.url) }"
          :title="img.url"
          @click="toggle(img)"
        >
          <img :src="img.url" alt="图片空间图片" loading="lazy" @error="onImgError" />
          <span v-if="isSelected(img.url)" class="picker-check">✓</span>
          <span class="picker-meta">{{ formatSize(img.size) }}</span>
        </div>
      </div>
      <el-empty v-else description="图片空间暂无图片，请先在「图片空间」页面上传" :image-size="80" />

      <div v-if="total > 0" class="picker-pager">
        <el-pagination
          small
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="size"
          v-model:current-page="page"
          @current-change="loadData"
        />
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!selectedCount" @click="confirm">
        确定选择{{ selectedCount ? `（${selectedCount}）` : '' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getUploadedImages, getUploadGroups } from '../../api/admin'
import { toArray } from '../../utils/safeArray'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '从图片空间选择图片' }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const visible = computed({
  get: () => props.modelValue,
  set: val => emit('update:modelValue', val)
})

const loading = ref(false)
const images = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(24)

// 分组筛选：-1=全部，0=未分组，正数=指定分组
const groups = ref([])
const groupId = ref(-1)

// 选中项按 url 存 Map，支持跨页保留选择
const selected = ref(new Map())

const selectedCount = computed(() => selected.value.size)
const allSelected = computed(
  () => images.value.length > 0 && images.value.every(img => selected.value.has(img.url))
)
const indeterminate = computed(
  () => !allSelected.value && images.value.some(img => selected.value.has(img.url))
)

async function loadData() {
  loading.value = true
  try {
    const res = await getUploadedImages({ page: page.value, size: size.value, groupId: groupId.value })
    images.value = toArray(res)
    total.value = Number(res?.total || 0)
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
    images.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadGroups() {
  try {
    const res = await getUploadGroups()
    groups.value = Array.isArray(res) ? res : toArray(res)
  } catch (e) {
    groups.value = []
  }
}

function handleGroupChange() {
  page.value = 1
  loadData()
}

function isSelected(url) {
  return selected.value.has(url)
}

function toggle(img) {
  const map = selected.value
  if (map.has(img.url)) {
    map.delete(img.url)
  } else {
    map.set(img.url, img)
  }
}

function toggleSelectAll(checked) {
  const map = selected.value
  for (const img of images.value) {
    if (checked) {
      map.set(img.url, img)
    } else {
      map.delete(img.url)
    }
  }
}

function confirm() {
  if (!selectedCount.value) return
  emit('confirm', Array.from(selected.value.values()))
  visible.value = false
}

function onImgError(e) {
  e.target.style.opacity = '0.35'
}

function formatSize(bytes) {
  const value = Number(bytes) || 0
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

// 每次打开都重置分页、分组与选择，避免上次的筛选/选中状态影响本次操作
watch(
  () => props.modelValue,
  val => {
    if (val) {
      selected.value = new Map()
      page.value = 1
      groupId.value = -1
      loadGroups()
      loadData()
    }
  }
)
</script>

<style scoped>
.picker-body { min-height: 260px; }

.picker-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.picker-tip { font-size: 12px; color: var(--text-400); }

.picker-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 10px;
  max-height: 52vh;
  overflow-y: auto;
  padding-right: 4px;
}
.picker-item {
  position: relative;
  aspect-ratio: 1 / 1;
  border: 2px solid transparent;
  border-radius: 6px;
  overflow: hidden;
  background: var(--background-200);
  cursor: pointer;
  transition: border-color 0.15s ease, transform 0.15s ease;
}
.picker-item:hover { transform: translateY(-1px); border-color: var(--brand-300); }
.picker-item.is-selected { border-color: var(--brand-500); }
.picker-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.picker-check {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--brand-500);
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.picker-meta {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 2px 6px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 10px;
  text-align: right;
}

.picker-pager { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
