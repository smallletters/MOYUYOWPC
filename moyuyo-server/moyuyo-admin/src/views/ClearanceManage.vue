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
          <el-input v-model="filters.keyword" placeholder="请输入报关单号/订单号" clearable />
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
        <el-table-column prop="declarationNo" label="报关单号" width="160" />
        <el-table-column prop="orderNo" label="订单号" width="160" />
        <el-table-column prop="productName" label="商品名称" width="140" />
        <el-table-column label="清关状态" width="120">
          <template #default="{ row }">
            <el-tag :type="clearanceTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="declarationTime" label="申报时间" width="160" />
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
        <el-form-item label="报关单号">
          <el-input v-model="editForm.declarationNo" placeholder="请输入报关单号" />
        </el-form-item>
        <el-form-item label="订单号">
          <el-input v-model="editForm.orderNo" placeholder="请输入订单号" />
        </el-form-item>
        <el-form-item label="商品名称">
          <el-input v-model="editForm.productName" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="清关状态">
          <el-select v-model="editForm.status">
            <el-option label="待申报" value="待申报" />
            <el-option label="申报中" value="申报中" />
            <el-option label="已放行" value="已放行" />
            <el-option label="被查验" value="被查验" />
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

const pageTitle = '清关管理'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  declarationNo: '',
  orderNo: '',
  productName: '',
  status: '待申报'
})

function clearanceTagType(status) {
  const map = { '待申报': 'info', '申报中': 'warning', '已放行': 'success', '被查验': 'danger' }
  return map[status] || 'info'
}

const mockData = [
  { id: 1, declarationNo: 'DC-20260701-001', orderNo: 'ORD-20260628-001', productName: '深海鱼油软胶囊', status: '已放行', declarationTime: '2026-07-01 09:00' },
  { id: 2, declarationNo: 'DC-20260702-002', orderNo: 'ORD-20260629-002', productName: '有机螺旋藻片', status: '申报中', declarationTime: '2026-07-02 10:30' },
  { id: 3, declarationNo: 'DC-20260703-003', orderNo: 'ORD-20260630-003', productName: '维生素C泡腾片', status: '待申报', declarationTime: '2026-07-03 08:00' },
  { id: 4, declarationNo: 'DC-20260704-004', orderNo: 'ORD-20260701-004', productName: '辅酶Q10胶囊', status: '被查验', declarationTime: '2026-07-04 14:00' },
  { id: 5, declarationNo: 'DC-20260705-005', orderNo: 'ORD-20260702-005', productName: '钙镁锌片', status: '已放行', declarationTime: '2026-07-05 11:20' },
  { id: 6, declarationNo: 'DC-20260706-006', orderNo: 'ORD-20260703-006', productName: '益生菌胶囊', status: '申报中', declarationTime: '2026-07-06 15:45' }
]

function loadData() {
  let filtered = [...mockData]
  if (filters.keyword) {
    filtered = filtered.filter(item => item.declarationNo.includes(filters.keyword) || item.orderNo.includes(filters.keyword))
  }
  tableData.value = filtered
  total.value = filtered.length
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建清关申报'; editForm.declarationNo = ''; editForm.orderNo = ''; editForm.productName = ''; editForm.status = '待申报'; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑清关'; Object.assign(editForm, row); dialogVisible.value = true }
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
