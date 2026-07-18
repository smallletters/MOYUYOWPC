<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建投诉</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="请输入投诉编号/投诉人" clearable />
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
        <el-table-column prop="complaintNo" label="投诉编号" width="150" />
        <el-table-column prop="complainant" label="投诉人" width="100" />
        <el-table-column prop="target" label="投诉对象" width="120" />
        <el-table-column prop="reason" label="投诉原因" width="180" show-overflow-tooltip />
        <el-table-column prop="handler" label="处理人" width="100" />
        <el-table-column label="处理状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === '已完结' ? 'success' : row.status === '处理中' ? 'warning' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handleTime" label="处理时间" width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">处理</el-button>
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
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="投诉编号">
          <el-input v-model="editForm.complaintNo" placeholder="请输入投诉编号" />
        </el-form-item>
        <el-form-item label="投诉人">
          <el-input v-model="editForm.complainant" placeholder="请输入投诉人" />
        </el-form-item>
        <el-form-item label="投诉对象">
          <el-input v-model="editForm.target" placeholder="请输入投诉对象" />
        </el-form-item>
        <el-form-item label="投诉原因">
          <el-input v-model="editForm.reason" type="textarea" placeholder="请输入投诉原因" />
        </el-form-item>
        <el-form-item label="处理人">
          <el-input v-model="editForm.handler" placeholder="请输入处理人" />
        </el-form-item>
        <el-form-item label="处理状态">
          <el-select v-model="editForm.status">
            <el-option label="待处理" value="待处理" />
            <el-option label="处理中" value="处理中" />
            <el-option label="已完结" value="已完结" />
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
import { getComplaintList, closeComplaint, assignComplaint } from '../api/admin'

const pageTitle = '投诉处理详情'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  complaintNo: '',
  complainant: '',
  target: '',
  reason: '',
  handler: '',
  status: '待处理'
})

// 从API加载投诉列表数据
async function loadData() {
  try {
    const res = await getComplaintList()
    const list = res || []
    // 根据关键词筛选
    let filtered = [...list]
    if (filters.keyword) {
      filtered = filtered.filter(item => item.complaintNo.includes(filters.keyword) || item.complainant.includes(filters.keyword))
    }
    tableData.value = filtered
    total.value = filtered.length
  } catch (e) {
    console.error('加载投诉列表失败:', e)
    ElMessage.error('加载投诉列表失败')
  }
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建投诉'; editForm.complaintNo = ''; editForm.complainant = ''; editForm.target = ''; editForm.reason = ''; editForm.handler = ''; editForm.status = '待处理'; dialogVisible.value = true }

// 编辑投诉（分配处理人）
async function handleEdit(row) {
  dialogTitle.value = '处理投诉'
  Object.assign(editForm, row)
  dialogVisible.value = true
}

// 删除投诉
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除？','提示')
    await closeComplaint(row.id)
    ElMessage.success('投诉已关闭')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('关闭投诉失败:', e)
    }
  }
}

// 保存投诉处理（调用API分配处理人）
async function handleSave() {
  try {
    await assignComplaint({
      id: editForm.id,
      handler: editForm.handler,
      status: editForm.status
    })
    ElMessage.success('投诉处理已保存')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    console.error('保存投诉处理失败:', e)
    ElMessage.error('保存失败')
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
