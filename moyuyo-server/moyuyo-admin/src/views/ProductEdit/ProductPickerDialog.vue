<template>
  <!-- 关联商品选择弹窗：用于 Upsell / Cross-sell -->
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    :title="title"
    width="780px"
    :close-on-click-modal="false"
    append-to-body
    class="product-picker-dialog"
  >
    <div class="picker-toolbar">
      <input
        v-model="keyword"
        placeholder="搜索商品名称 / SKU"
        class="picker-search"
        @keyup.enter="loadList(true)"
      />
      <button class="btn btn-outline" @click="loadList(true)">搜索</button>
      <span class="picker-count">已选 {{ pickedIds.length }} 个 · 共 {{ list.length }} 条</span>
    </div>

    <el-table
      ref="tableRef"
      :data="list"
      height="400"
      @select="onSelect"
      @select-all="onSelectAll"
      v-loading="loading"
      empty-text="暂无匹配商品"
      size="small"
    >
      <el-table-column type="selection" width="44" />
      <el-table-column label="ID" prop="id" width="70" />
      <el-table-column label="名称" prop="name" min-width="200" show-overflow-tooltip />
      <el-table-column label="SKU" prop="spuCode" width="140" show-overflow-tooltip />
      <el-table-column label="价格" width="100">
        <template #default="{ row }">
          {{ row.price != null ? `¥${row.price}` : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <span v-if="row.onSale" class="tag tag-green">上架</span>
          <span v-else class="tag tag-gray">下架</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="picker-footer">
      <button class="btn btn-outline" @click="close">取消</button>
      <button class="btn btn-primary" @click="confirm">确定（{{ pickedIds.length }}）</button>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
// Element Plus 组件已在 main.js 中全局注册，无需单独引入
import { searchProductsLite } from '../../api/admin'

const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: '选择商品' },
  excludeId: { type: [Number, String], default: null },
  // 打开弹窗时需要回显的已选商品 id
  selectedIds: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const keyword = ref('')
const list = ref([])
const loading = ref(false)
const tableRef = ref(null)
// 已选商品：跨关键词搜索保留；pickedIds 仅用于计数/展示（响应式）
const pickedRows = new Map()
const pickedIds = ref([])

watch(() => props.modelValue, (v) => {
  if (v) {
    // 每次打开都以父组件当前已选 id 为基准重置
    resetPickedFromProps()
    loadList()
  }
})

// 用父组件传入的 id 初始化已选集合（此时只有 id，名称等确认后由父组件补齐）
function resetPickedFromProps() {
  pickedRows.clear()
  ;(props.selectedIds || []).forEach(id => {
    if (id != null) pickedRows.set(String(id), { id })
  })
  syncPickedIds()
}

function syncPickedIds() {
  pickedIds.value = [...pickedRows.keys()]
}

async function loadList() {
  loading.value = true
  try {
    const res = await searchProductsLite({
      keyword: keyword.value || '',
      size: 50,
      excludeId: props.excludeId
    })
    const data = Array.isArray(res) ? res : (res?.records || res?.list || res?.data?.records || [])
    list.value = data.filter(p => !props.excludeId || String(p.id) !== String(props.excludeId))
    // 数据渲染完成后回显已选商品（跨搜索的已选项不受影响）
    await nextTick()
    preselect()
  } catch (e) {
    console.error('搜索商品失败:', e)
    list.value = []
  } finally {
    loading.value = false
  }
}

// 按已选集合勾选当前列表中的行
function preselect() {
  if (!tableRef.value) return
  list.value.forEach(row => {
    if (pickedRows.has(String(row.id))) {
      tableRef.value.toggleRowSelection(row, true)
    }
  })
}

// 用户勾选/取消单行：同步到跨搜索保留的集合
function onSelect(selection, row) {
  const id = String(row.id)
  if (selection.some(r => String(r.id) === id)) {
    pickedRows.set(id, row)
  } else {
    pickedRows.delete(id)
  }
  syncPickedIds()
}

// 全选/取消全选：仅作用于当前搜索结果
function onSelectAll(selection) {
  if (selection.length > 0) {
    selection.forEach(row => pickedRows.set(String(row.id), row))
  } else {
    list.value.forEach(row => pickedRows.delete(String(row.id)))
  }
  syncPickedIds()
}

function close() {
  emit('update:modelValue', false)
}

function confirm() {
  // 当前列表中能查到的行用完整对象，其余用 { id } 占位
  const rows = [...pickedRows.entries()].map(([id, fallback]) =>
    list.value.find(r => String(r.id) === id) || fallback)
  emit('confirm', rows)
  close()
}
</script>

<style scoped>
.picker-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.picker-search {
  flex: 1;
  padding: 7px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
}
.picker-count {
  font-size: 12px;
  color: var(--text-500);
}
.picker-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  border: 1px solid transparent;
  font-family: inherit;
}
.btn-primary { background: var(--brand-600); color: #fff; border-color: var(--brand-600); }
.btn-primary:hover { background: var(--brand-700); }
.btn-outline { background: var(--card); color: var(--text-700); border-color: var(--border); }
.btn-outline:hover { background: var(--background-100); }

.tag {
  display: inline-flex;
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
}
.tag-green { background: var(--state-success-surface); color: var(--state-success); }
.tag-gray { background: var(--background-200); color: var(--text-500); }
</style>