<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>投诉管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新增投诉记录</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="投诉编号/投诉人" clearable />
        </el-form-item>
        <el-form-item label="投诉类型">
          <el-select v-model="filters.type" placeholder="全部类型" clearable style="width:140px">
            <el-option label="全部" value="" />
            <el-option label="商品质量" value="商品质量" />
            <el-option label="服务态度" value="服务态度" />
            <el-option label="物流问题" value="物流问题" />
            <el-option label="虚假宣传" value="虚假宣传" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width:140px">
            <el-option label="全部" value="" />
            <el-option label="待处理" value="待处理" />
            <el-option label="处理中" value="处理中" />
            <el-option label="已完结" value="已完结" />
          </el-select>
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
        <el-table-column prop="complaintNo" label="投诉编号" width="160" />
        <el-table-column prop="complainant" label="投诉人" width="120" />
        <el-table-column prop="defendant" label="被投诉人" width="120" />
        <el-table-column prop="type" label="投诉类型" width="120" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitTime" label="提交时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">处理</el-button>
            <el-button size="small" @click="handleDetail(row)">详情</el-button>
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
          <el-input v-model="editForm.complaintNo" disabled />
        </el-form-item>
        <el-form-item label="投诉人" required>
          <el-input v-model="editForm.complainant" />
        </el-form-item>
        <el-form-item label="被投诉人" required>
          <el-input v-model="editForm.defendant" />
        </el-form-item>
        <el-form-item label="投诉类型" required>
          <el-select v-model="editForm.type" placeholder="请选择" style="width:100%">
            <el-option label="商品质量" value="商品质量" />
            <el-option label="服务态度" value="服务态度" />
            <el-option label="物流问题" value="物流问题" />
            <el-option label="虚假宣传" value="虚假宣传" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="投诉内容">
          <el-input v-model="editForm.content" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="处理状态">
          <el-select v-model="editForm.status" placeholder="请选择" style="width:100%">
            <el-option label="待处理" value="待处理" />
            <el-option label="处理中" value="处理中" />
            <el-option label="已完结" value="已完结" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="3" />
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

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)

const filters = reactive({
  keyword: '',
  type: '',
  status: ''
})

const editForm = reactive({
  id: null,
  complaintNo: '',
  complainant: '',
  defendant: '',
  type: '',
  content: '',
  status: '待处理',
  remark: '',
  submitTime: ''
})

const mockData = [
  { id: 1, complaintNo: 'CP-20260701-001', complainant: '张三', defendant: '李四', type: '商品质量', status: '待处理', submitTime: '2026-07-01 10:30:00', content: '收到的商品有质量问题，表面有明显划痕', remark: '' },
  { id: 2, complaintNo: 'CP-20260702-002', complainant: '王五', defendant: '赵六', type: '服务态度', status: '处理中', submitTime: '2026-07-02 14:20:00', content: '客服态度恶劣，拒绝处理售后问题', remark: '已联系客服主管跟进' },
  { id: 3, complaintNo: 'CP-20260703-003', complainant: '孙七', defendant: '周八', type: '物流问题', status: '已完结', submitTime: '2026-07-03 09:15:00', content: '快递配送超时3天未送达', remark: '已协调物流公司配送并赔偿' },
  { id: 4, complaintNo: 'CP-20260705-004', complainant: '吴九', defendant: '郑十', type: '虚假宣传', status: '待处理', submitTime: '2026-07-05 16:45:00', content: '商品描述与实物严重不符，存在虚假宣传', remark: '' },
  { id: 5, complaintNo: 'CP-20260706-005', complainant: '陈一', defendant: '林二', type: '其他', status: '已完结', submitTime: '2026-07-06 11:00:00', content: '重复扣款问题，订单支付了两次', remark: '已核实并退款处理' }
]

const tableData = ref([])

function statusTag(status) {
  const map = { '待处理': 'danger', '处理中': 'warning', '已完结': 'success' }
  return map[status] || ''
}

function loadData() {
  const start = (currentPage.value - 1) * pageSize.value
  const list = mockData.filter(d => {
    const kw = filters.keyword.toLowerCase()
    if (kw && !d.complaintNo.toLowerCase().includes(kw) && !d.complainant.includes(kw)) return false
    if (filters.type && d.type !== filters.type) return false
    if (filters.status && d.status !== filters.status) return false
    return true
  })
  total.value = list.length
  tableData.value = list.slice(start, start + pageSize.value)
}

function handleSearch() { currentPage.value = 1; loadData() }

function handleReset() {
  filters.keyword = ''; filters.type = ''; filters.status = ''
  handleSearch()
}

function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '新增投诉记录'
  editForm.id = null
  editForm.complaintNo = 'CP-' + new Date().toISOString().slice(0,10).replace(/-/g,'') + '-00' + (mockData.length + 1)
  editForm.complainant = ''
  editForm.defendant = ''
  editForm.type = ''
  editForm.content = ''
  editForm.status = '待处理'
  editForm.remark = ''
  editForm.submitTime = ''
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  dialogTitle.value = '处理投诉'
  Object.assign(editForm, row)
  dialogVisible.value = true
}

function handleDetail(row) {
  ElMessage.info('投诉详情：' + row.content)
}

function handleSave() {
  if (!editForm.complainant || !editForm.defendant || !editForm.type) {
    ElMessage.warning('请填写必要信息')
    return
  }
  if (isEdit.value) {
    const item = mockData.find(d => d.id === editForm.id)
    if (item) Object.assign(item, editForm)
    ElMessage.success('保存成功')
  } else {
    const newId = Math.max(...mockData.map(d => d.id)) + 1
    mockData.push({
      ...editForm,
      id: newId,
      submitTime: new Date().toLocaleString('zh-CN', { hour12: false })
    })
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  loadData()
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
