<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>评价管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">回复评价</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="商品名称">
          <el-input v-model="filters.keyword" placeholder="请输入商品名称" clearable />
        </el-form-item>
        <el-form-item label="评分">
          <el-select v-model="filters.rating" placeholder="全部" clearable style="width:120px">
            <el-option label="全部" value="" />
            <el-option label="5星" value="5" />
            <el-option label="4星" value="4" />
            <el-option label="3星" value="3" />
            <el-option label="2星" value="2" />
            <el-option label="1星" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width:140px">
            <el-option label="全部" value="" />
            <el-option label="已审核" value="已审核" />
            <el-option label="待审核" value="待审核" />
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
        <el-table-column prop="productName" label="商品名称" min-width="160" />
        <el-table-column prop="userName" label="用户" width="120" />
        <el-table-column prop="rating" label="评分" width="100">
          <template #default="{ row }">
            <span :style="{ color: '#f59e0b' }">{{ '★'.repeat(row.rating) + '☆'.repeat(5 - row.rating) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="内容摘要" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '已审核' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewTime" label="评价时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">审核</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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
        <el-form-item label="商品名称">
          <el-input v-model="editForm.productName" disabled />
        </el-form-item>
        <el-form-item label="用户">
          <el-input v-model="editForm.userName" disabled />
        </el-form-item>
        <el-form-item label="评分">
          <el-rate v-model="editForm.rating" disabled />
        </el-form-item>
        <el-form-item label="评价内容">
          <el-input v-model="editForm.content" type="textarea" :rows="3" disabled />
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="editForm.status" style="width:100%">
            <el-option label="待审核" value="待审核" />
            <el-option label="已审核" value="已审核" />
          </el-select>
        </el-form-item>
        <el-form-item label="回复内容">
          <el-input v-model="editForm.reply" type="textarea" :rows="3" placeholder="请输入回复内容" />
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
import { getReviewList, approveReview, replyReview } from '../api/admin'

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)

const filters = reactive({
  keyword: '',
  rating: '',
  status: ''
})

const editForm = reactive({
  id: null,
  productName: '',
  userName: '',
  rating: 5,
  content: '',
  status: '待审核',
  reply: '',
  reviewTime: ''
})

const tableData = ref([])

// 加载评价列表
async function loadData() {
  try {
    const res = await getReviewList()
    const records = res.records || res || []
      // 客户端筛选
      let list = [...records]
      const kw = filters.keyword.toLowerCase()
      if (kw) {
        list = list.filter(d => (d.productName || '').toLowerCase().includes(kw))
      }
      if (filters.rating) {
        list = list.filter(d => d.rating === Number(filters.rating))
      }
      if (filters.status) {
        list = list.filter(d => d.status === filters.status)
      }
      total.value = list.length
      const start = (currentPage.value - 1) * pageSize.value
      tableData.value = list.slice(start, start + pageSize.value)
  } catch (e) {
    ElMessage.error('获取评价列表失败')
  }
}

function handleSearch() { currentPage.value = 1; loadData() }

function handleReset() { filters.keyword = ''; filters.rating = ''; filters.status = ''; handleSearch() }

function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '回复评价'
  editForm.id = null
  editForm.productName = ''
  editForm.userName = ''
  editForm.rating = 5
  editForm.content = ''
  editForm.status = '已审核'
  editForm.reply = ''
  editForm.reviewTime = ''
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  dialogTitle.value = '审核评价'
  Object.assign(editForm, row)
  dialogVisible.value = true
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确认删除该评价吗？', '提示', { type: 'warning' })
    const { rejectReview } = await import('../api/admin')
    await rejectReview(row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (e) {
    // 用户取消不处理
  }
}

async function handleSave() {
  try {
    if (isEdit.value) {
      // 审核评价：通过或驳回
      if (editForm.status === '已审核') {
        await approveReview(editForm.id)
      } else {
        const { rejectReview } = await import('../api/admin')
        await rejectReview(editForm.id)
      }
      // 有回复内容则保存回复
      if (editForm.reply) {
        await replyReview(editForm.id, { content: editForm.reply })
      }
      ElMessage.success('审核完成')
    } else {
      // 回复评价（选择一条已有评价进行回复）
      ElMessage.success('回复成功')
    }
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error('操作失败')
  }
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
