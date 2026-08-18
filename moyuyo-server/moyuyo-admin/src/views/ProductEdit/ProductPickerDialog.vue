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
      <span class="picker-count">已选 {{ selectedIds.length }} 个 · 共 {{ list.length }} 条</span>
    </div>

    <el-table
      :data="list"
      height="400"
      @selection-change="onSelectionChange"
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
      <button class="btn btn-primary" @click="confirm">确定（{{ selectedIds.length }}）</button>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
// Element Plus 组件已在 main.js 中全局注册，无需单独引入
import { searchProductsLite } from '../../api/admin'

const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: '选择商品' },
  excludeId: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const keyword = ref('')
const list = ref([])
const loading = ref(false)
const selectedIds = ref([])
const selectedRows = ref([])

watch(() => props.modelValue, (v) => {
  if (v) loadList(true)
})

async function loadList(reset = false) {
  if (reset) selectedIds.value = []
  loading.value = true
  try {
    const res = await searchProductsLite({
      keyword: keyword.value || '',
      size: 50,
      excludeId: props.excludeId
    })
    const data = Array.isArray(res) ? res : (res?.records || res?.list || res?.data?.records || [])
    list.value = data.filter(p => !props.excludeId || String(p.id) !== String(props.excludeId))
  } catch (e) {
    console.error('搜索商品失败:', e)
    list.value = []
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows) {
  selectedRows.value = rows
  selectedIds.value = rows.map(r => r.id)
}

function close() {
  emit('update:modelValue', false)
}

function confirm() {
  emit('confirm', selectedRows.value)
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