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
    <!-- 两栏布局：左侧投诉分类分布 + 右侧投诉列表 -->
    <div class="content-grid">
      <!-- 投诉分类分布（纯 CSS 条形图，对齐设计稿） -->
      <el-card shadow="never" class="category-card">
        <h3 class="card-title">投诉分类分布</h3>
        <!-- 遍历分类数据渲染条形图 -->
        <div class="category-bar-item" v-for="item in categoryData" :key="item.name">
          <span class="category-bar-label">{{ item.name }}</span>
          <div class="category-bar-track">
            <div class="category-bar-fill" :style="{ width: item.percent + '%', background: item.color }">
              <span>{{ item.percent }}%</span>
            </div>
          </div>
        </div>
        <!-- 数据来源说明 -->
        <p class="category-source">数据来源：近30天投诉分类统计</p>
      </el-card>
      <!-- 右侧：投诉列表（原表格） -->
      <el-card shadow="never" class="table-card">
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
        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="total"
            layout="total, sizes, prev, pager, next"
            @change="loadData"
          />
        </div>
      </el-card>
    </div>
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

// 类型标签映射
const typeLabelMap = { '售后': '售后问题', '咨询': '售前咨询', '投诉': '用户投诉' }

// 投诉分类分布示例数据（无真实统计 API，先用结构化示例数据展示图表形态）
// 颜色沿用设计稿：产品质量-红 / 物流配送-蓝 / 服务态度-橙 / 价格问题-紫 / 其他-灰
const categoryData = [
  { name: '产品质量', percent: 35, color: 'var(--state-error)' },
  { name: '物流配送', percent: 28, color: 'var(--brand-500)' },
  { name: '服务态度', percent: 15, color: '#ff9500' },
  { name: '价格问题', percent: 12, color: '#5856d6' },
  { name: '其他', percent: 10, color: 'var(--text-400)' }
]

// 从API加载投诉列表数据
async function loadData() {
  try {
    const res = await getComplaintList()
    const rawList = (res && res.list) || []
    // 字段映射：与 AdminComplaintController#list 返回结构对齐
    let list = rawList.map(item => ({
      id: item.id,
      complaintNo: 'CP' + item.id,
      complainant: item.userId ? '用户' + item.userId : '',
      // 被投诉对象：mo_feedback.type 即投诉类型
      target: item.type || '',
      defendant: typeLabelMap[item.type] || item.type || '',
      // 投诉原因/内容
      reason: item.content || '',
      // 客服处理备注
      handlerReply: item.replyContent || '',
      contact: item.contact || '',
      status: ({'PENDING':'待处理','PROCESSING':'处理中','CLOSED':'已完结','RESOLVED':'已完结'})[item.status] || item.status || '待处理',
      handleTime: item.updateTime || item.createTime || ''
    }))
    if (filters.keyword) {
      list = list.filter(item => item.complaintNo.includes(filters.keyword) || item.complainant.includes(filters.keyword))
    }
    tableData.value = list
    total.value = list.length
  } catch (e) {
    console.error('加载投诉列表失败:', e)
    ElMessage.error('加载投诉列表失败')
    tableData.value = []
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

// 删除/关闭投诉
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定关闭投诉 ${row.complaintNo}？该操作不可撤销。`, '关闭投诉', { type: 'warning' })
    // closeComplaint 为真实的"完结投诉"接口（POST /api/admin/complaint/{id}/close）
    await closeComplaint(row.id)
    ElMessage.success('投诉已关闭')
    loadData()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      console.error('关闭投诉失败:', e)
      ElMessage.error('关闭失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    }
  }
}

// 保存投诉处理（调用API分配处理人）
async function handleSave() {
  try {
    // 后端 /complaint/{id}/assign 接收 { assignee, remark, status }
    // assignee=handler, remark=handlerReply, status=当前状态
    await assignComplaint(editForm.id, {
      assignee: editForm.handler,
      remark: editForm.handlerReply || editForm.reason || '',
      status: editForm.status
    })
    ElMessage.success('投诉处理已保存')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    console.error('保存投诉处理失败:', e)
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
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

/* 两栏布局：左侧分类分布 + 右侧投诉列表（对齐设计稿 340px + 1fr） */
.content-grid {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 20px;
  align-items: start;
}

/* 投诉分类分布卡片 */
.category-card { align-self: stretch; }
.category-card :deep(.el-card__body) { padding: 20px; }
.card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0 0 20px 0;
}

/* 投诉分类条形图（对齐设计稿样式） */
.category-bar-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.category-bar-item:last-child {
  margin-bottom: 0;
}
.category-bar-label {
  width: 72px;
  font-size: 13px;
  color: var(--text-600);
  text-align: right;
  flex-shrink: 0;
}
.category-bar-track {
  flex: 1;
  height: 28px;
  background: var(--background-200);
  border-radius: 6px;
  overflow: hidden;
  position: relative;
}
.category-bar-fill {
  height: 100%;
  border-radius: 6px;
  transition: width 0.4s ease;
  display: flex;
  align-items: center;
  padding-left: 10px;
}
.category-bar-fill span {
  font-size: 11px;
  font-weight: 700;
  color: #ffffff;
}

/* 数据来源说明 */
.category-source {
  font-size: 11px;
  color: var(--muted-foreground);
  margin-top: 20px;
  margin-bottom: 0;
}

/* 右侧表格卡片 */
.table-card { min-width: 0; }
.pagination-wrap { display: flex; justify-content: flex-end; padding: 16px 0 0; }
</style>
