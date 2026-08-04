<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>订单标签</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建标签</el-button>
      </div>
    </div>
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="标签名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="color" label="颜色" width="100">
          <template #default="{ row }">
            <el-tag :color="row.color" style="color:#fff" size="small">{{ row.name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column prop="usageCount" label="使用次数" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑标签' : '新建标签'" width="450px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="标签名称" required>
          <el-input v-model="form.name" placeholder="标签名称" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="form.color" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="标签描述（可选）" />
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
import { getOrderTagList, createOrderTag, updateOrderTag, deleteOrderTag } from '../api/admin'
import { toArray } from '../utils/safeArray'

const tableData = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)

const form = reactive({
  name: '',
  color: '#007aff',
  description: '',
})

function resetForm() {
  form.name = ''
  form.color = '#007aff'
  form.description = ''
}

async function loadData() {
  try {
    const res = await getOrderTagList()
    tableData.value = toArray(res)
  } catch (e) {
    ElMessage.error('获取标签列表失败')
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
  form.color = row.color || '#007aff'
  form.description = row.description || ''
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name) {
    ElMessage.warning('请输入标签名称')
    return
  }
  try {
    if (isEdit.value) {
      await updateOrderTag({ id: editId.value, ...form })
      ElMessage.success('编辑成功')
    } else {
      await createOrderTag({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除标签「' + row.name + '」吗？', '提示')
  } catch (e) {
    return // 用户取消或关闭
  }
  try {
    await deleteOrderTag(row.id)
    ElMessage.success('已删除')
    await loadData()
  } catch (e) {
    ElMessage.error('删除失败')
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
