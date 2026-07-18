<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="请输入仓库名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="仓库名称" width="140" />
        <el-table-column label="仓库类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.type === '自营' ? 'primary' : 'success'">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="city" label="所在城市" width="120" />
        <el-table-column prop="area" label="面积(m²)" width="100" />
        <el-table-column prop="manager" label="管理员" width="100" />
        <el-table-column prop="phone" label="联系电话" width="130" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '启用' ? 'success' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;padding:16px 0 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @change="loadData"
        />
      </div>
    </el-card>
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form :model="editForm" label-width="110px">
        <el-form-item label="仓库名称">
          <el-input v-model="editForm.name" placeholder="请输入仓库名称" />
        </el-form-item>
        <el-form-item label="仓库类型">
          <el-select v-model="editForm.type">
            <el-option label="自营" value="自营" />
            <el-option label="第三方" value="第三方" />
          </el-select>
        </el-form-item>
        <el-form-item label="所在城市">
          <el-input v-model="editForm.city" placeholder="请输入所在城市" />
        </el-form-item>
        <el-form-item label="面积(m²)">
          <el-input-number v-model="editForm.area" :min="0" />
        </el-form-item>
        <el-form-item label="管理员">
          <el-input v-model="editForm.manager" placeholder="请输入管理员姓名" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="editForm.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
            <el-option label="启用" value="启用" />
            <el-option label="停用" value="停用" />
          </el-select>
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
import { getWarehouses, createWarehouse, updateWarehouse } from '../api/admin'

const pageTitle = '仓库管理'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  name: '',
  type: '自营',
  city: '',
  area: 0,
  manager: '',
  phone: '',
  status: '启用'
})
// 储存当前编辑的ID，用于判断新增还是编辑
const editingId = ref(null)

// 加载仓库数据
async function loadData() {
  try {
    const res = await getWarehouses()
    const list = res || []
    let filtered = [...list]
    if (filters.keyword) {
      filtered = filtered.filter(item => item.name.includes(filters.keyword) || item.city.includes(filters.keyword))
    }
    tableData.value = filtered
    total.value = filtered.length
  } catch (error) {
    console.error('获取仓库数据失败:', error)
    ElMessage.error('获取仓库数据失败')
  }
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { editingId.value = null; dialogTitle.value = '新建仓库'; editForm.name = ''; editForm.type = '自营'; editForm.city = ''; editForm.area = 0; editForm.manager = ''; editForm.phone = ''; editForm.status = '启用'; dialogVisible.value = true }
function handleEdit(row) { editingId.value = row.id; dialogTitle.value = '编辑仓库'; Object.assign(editForm, row); dialogVisible.value = true }
function handleDelete(row) { ElMessageBox.confirm('确定删除？','提示').then(() => { ElMessage.success('删除成功'); loadData() }) }
// 保存仓库（新增或编辑）
async function handleSave() {
  try {
    if (editingId.value) {
      await updateWarehouse(editingId.value, { ...editForm })
    } else {
      await createWarehouse({ ...editForm })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (error) {
    console.error('保存仓库失败:', error)
    ElMessage.error('保存仓库失败')
  }
}
onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
