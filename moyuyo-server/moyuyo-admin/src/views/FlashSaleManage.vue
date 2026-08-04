<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>秒杀管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建秒杀</el-button>
      </div>
    </div>
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="活动名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="productName" label="商品名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="flashPrice" label="秒杀价" width="100">
          <template #default="{ row }">¥{{ row.flashPrice }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column prop="endTime" label="结束时间" width="160" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
              :type="row.status === 'active' ? 'warning' : 'success'"
              link size="small"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 'active' ? '停用' : '启用' }}
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑秒杀' : '新建秒杀'" width="550px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="活动名称" required>
          <el-input v-model="form.name" placeholder="秒杀活动名称" />
        </el-form-item>
        <el-form-item label="商品ID" required>
          <el-input-number v-model="form.productId" :min="1" />
        </el-form-item>
        <el-form-item label="秒杀价" required>
          <el-input-number v-model="form.flashPrice" :min="0.01" :precision="2" />
        </el-form-item>
        <el-form-item label="原价">
          <el-input-number v-model="form.originalPrice" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="库存" required>
          <el-input-number v-model="form.stock" :min="1" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useStatusTag } from '../composables/useStatusTag'
import { getFlashSaleList, createFlashSale, updateFlashSale, deleteFlashSale, updateFlashSaleStatus } from '../api/admin'
import { toArray } from '../utils/safeArray'

const tableData = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)

const form = reactive({
  name: '',
  productId: null,
  flashPrice: 0,
  originalPrice: 0,
  stock: 0,
  startTime: null,
  endTime: null,
})

const { getStatusText, getStatusType } = useStatusTag(
  { active: '进行中', inactive: '未开始', ended: '已结束' },
  { active: 'success', inactive: 'warning', ended: 'info' }
)

function resetForm() {
  form.name = ''
  form.productId = null
  form.flashPrice = 0
  form.originalPrice = 0
  form.stock = 0
  form.startTime = null
  form.endTime = null
}

async function loadData() {
  try {
    const res = await getFlashSaleList()
    tableData.value = toArray(res)
  } catch (e) {
    ElMessage.error('获取秒杀列表失败')
  }
}

function handleAdd() {
  isEdit.value = false
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  editId.value = row.id
  form.name = row.name
  form.productId = row.productId
  form.flashPrice = row.flashPrice
  form.originalPrice = row.originalPrice
  form.stock = row.stock
  form.startTime = row.startTime
  form.endTime = row.endTime
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name || !form.productId) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    const payload = {
      name: form.name,
      productId: form.productId,
      flashPrice: form.flashPrice,
      originalPrice: form.originalPrice,
      stock: form.stock,
      startTime: form.startTime ? new Date(form.startTime).toISOString() : null,
      endTime: form.endTime ? new Date(form.endTime).toISOString() : null,
    }
    if (isEdit.value) {
      await updateFlashSale({ id: editId.value, ...payload })
      ElMessage.success('编辑成功')
    } else {
      await createFlashSale(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

async function handleToggleStatus(row) {
  const newStatus = row.status === 'active' ? 'inactive' : 'active'
  const label = newStatus === 'active' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确定${label}秒杀「${row.name}」吗？`, '提示')
    await updateFlashSaleStatus(row.id, { active: newStatus === 'active' })
    ElMessage.success(`已${label}`)
    await loadData()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      console.error('更新秒杀状态失败:', e)
      ElMessage.error('状态更新失败: ' + (e?.message || '未知错误'))
    }
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除秒杀「' + row.name + '」吗？', '提示')
    await deleteFlashSale(row.id)
    ElMessage.success('已删除')
    await loadData()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      console.error('删除秒杀失败:', e)
      ElMessage.error('删除失败: ' + (e?.message || '未知错误'))
    }
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.header-actions { display: flex; gap: 8px; }
</style>
