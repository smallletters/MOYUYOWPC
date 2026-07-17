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

const mockData = [
  { id: 1, complaintNo: 'CP-20260701-001', complainant: '王小明', target: '商家-全球购', reason: '收到的商品包装破损，要求退换', handler: '客服-张', status: '已完结', handleTime: '2026-07-05 14:30' },
  { id: 2, complaintNo: 'CP-20260702-002', complainant: '李小红', target: '商家-保健品专营', reason: '商品与描述不符，要求退货退款', handler: '客服-李', status: '处理中', handleTime: '2026-07-15 10:00' },
  { id: 3, complaintNo: 'CP-20260703-003', complainant: '赵大勇', target: '物流-顺丰速运', reason: '物流配送严重超时，要求赔偿', handler: '客服-王', status: '待处理', handleTime: '' },
  { id: 4, complaintNo: 'CP-20260704-004', complainant: '陈美丽', target: '商家-母婴用品店', reason: '购买的商品缺件，少发了一个配件', handler: '客服-张', status: '已完结', handleTime: '2026-07-12 16:00' },
  { id: 5, complaintNo: 'CP-20260705-005', complainant: '刘强东', target: '商家-家居生活馆', reason: '收到货物有异味，怀疑质量问题', handler: '客服-李', status: '处理中', handleTime: '2026-07-16 09:20' },
  { id: 6, complaintNo: 'CP-20260706-006', complainant: '周思思', target: '保税仓', reason: '清关时间太久，超过承诺时效', handler: '', status: '待处理', handleTime: '' }
]

function loadData() {
  let filtered = [...mockData]
  if (filters.keyword) {
    filtered = filtered.filter(item => item.complaintNo.includes(filters.keyword) || item.complainant.includes(filters.keyword))
  }
  tableData.value = filtered
  total.value = filtered.length
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建投诉'; editForm.complaintNo = ''; editForm.complainant = ''; editForm.target = ''; editForm.reason = ''; editForm.handler = ''; editForm.status = '待处理'; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '处理投诉'; Object.assign(editForm, row); dialogVisible.value = true }
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
