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
          <el-input v-model="filters.keyword" placeholder="请输入原订单号搜索" clearable />
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
        <el-table-column prop="orderNo" label="原订单号" width="160" />
        <el-table-column prop="productCount" label="商品数" width="80" />
        <el-table-column prop="splitCount" label="分包数" width="80" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="row.status === '已分包裹' ? 'success' : 'warning'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
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
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="原订单号">
          <el-input v-model="editForm.orderNo" placeholder="请输入原订单号" />
        </el-form-item>
        <el-form-item label="商品数">
          <el-input-number v-model="editForm.productCount" :min="1" />
        </el-form-item>
        <el-form-item label="分包数">
          <el-input-number v-model="editForm.splitCount" :min="1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
            <el-option label="待分包裹" value="待分包裹" />
            <el-option label="已分包裹" value="已分包裹" />
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

const pageTitle = '分包裹'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  orderNo: '',
  productCount: 1,
  splitCount: 1,
  status: '待分包裹'
})

const mockData = [
  { id: 1, orderNo: 'ORD-20260701-001', productCount: 5, splitCount: 2, status: '已分包裹', createTime: '2026-07-01 10:00' },
  { id: 2, orderNo: 'ORD-20260702-002', productCount: 8, splitCount: 3, status: '已分包裹', createTime: '2026-07-02 11:30' },
  { id: 3, orderNo: 'ORD-20260703-003', productCount: 3, splitCount: 1, status: '待分包裹', createTime: '2026-07-03 09:15' },
  { id: 4, orderNo: 'ORD-20260704-004', productCount: 10, splitCount: 4, status: '待分包裹', createTime: '2026-07-04 14:20' },
  { id: 5, orderNo: 'ORD-20260705-005', productCount: 6, splitCount: 2, status: '已分包裹', createTime: '2026-07-05 16:45' },
  { id: 6, orderNo: 'ORD-20260706-006', productCount: 4, splitCount: 1, status: '待分包裹', createTime: '2026-07-06 08:30' }
]

function loadData() {
  let filtered = [...mockData]
  if (filters.keyword) {
    filtered = filtered.filter(item => item.orderNo.includes(filters.keyword))
  }
  tableData.value = filtered
  total.value = filtered.length
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建分包裹'; editForm.orderNo = ''; editForm.productCount = 1; editForm.splitCount = 1; editForm.status = '待分包裹'; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑分包裹'; Object.assign(editForm, row); dialogVisible.value = true }
function handleDelete(row) { ElMessageBox.confirm('确定删除？','提示').then(() => { ElMessage.success('删除成功'); loadData() }) }
function handleSave() { ElMessage.success('保存成功'); dialogVisible.value = false; loadData() }
onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
