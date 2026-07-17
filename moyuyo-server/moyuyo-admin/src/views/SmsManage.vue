<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>短信管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">发送短信</el-button>
      </div>
    </div>
    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">今日发送</div>
            <div class="kpi-value" style="color:#409eff">{{ kpiData.todaySent }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">本月发送</div>
            <div class="kpi-value" style="color:#e6a23c">{{ kpiData.monthlySent }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">剩余条数</div>
            <div class="kpi-value" style="color:#67c23a">{{ kpiData.remaining }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card">
            <div class="kpi-label">成功率</div>
            <div class="kpi-value" style="color:#909399">{{ kpiData.successRate }}%</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="接收号码" clearable />
        </el-form-item>
        <el-form-item label="发送状态">
          <el-select v-model="filters.sendStatus" placeholder="全部" clearable style="width:140px">
            <el-option label="全部" value="" />
            <el-option label="成功" value="成功" />
            <el-option label="失败" value="失败" />
            <el-option label="发送中" value="发送中" />
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
        <el-table-column prop="content" label="短信内容" min-width="260" show-overflow-tooltip />
        <el-table-column prop="phone" label="接收号码" width="140" />
        <el-table-column prop="sendStatus" label="发送状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.sendStatus)" size="small">{{ row.sendStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sendTime" label="发送时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
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
    <el-dialog v-model="dialogVisible" title="发送短信" width="600px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="接收号码" required>
          <el-input v-model="editForm.phone" placeholder="多个号码用逗号分隔" />
        </el-form-item>
        <el-form-item label="短信内容" required>
          <el-input v-model="editForm.content" type="textarea" :rows="4" placeholder="请输入短信内容" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)

const filters = reactive({
  keyword: '',
  sendStatus: ''
})

const editForm = reactive({
  phone: '',
  content: ''
})

const kpiData = reactive({
  todaySent: 1258,
  monthlySent: 28650,
  remaining: 142000,
  successRate: 97.8
})

const mockData = [
  { id: 1, content: '尊敬的用户，您的订单MOY20260717001已发货，快递单号：SF1234567890，预计3日内送达。', phone: '138****1234', sendStatus: '成功', sendTime: '2026-07-17 10:30:00' },
  { id: 2, content: '【店铺通知】您关注的商品"智能手表S3"已降价至¥339，点击查看详情。', phone: '159****5678', sendStatus: '成功', sendTime: '2026-07-17 09:20:00' },
  { id: 3, content: '尊敬的VIP会员，您获得了一张满300减50的优惠券，有效期至7月31日。', phone: '177****9012', sendStatus: '发送中', sendTime: '2026-07-17 11:15:00' },
  { id: 4, content: '您的验证码是：884216，5分钟内有效，请勿泄露给他人。', phone: '136****3456', sendStatus: '失败', sendTime: '2026-07-16 18:00:00' },
  { id: 5, content: '订单MOY20260715004已签收，欢迎评价商品获得积分奖励！', phone: '150****7890', sendStatus: '成功', sendTime: '2026-07-16 14:30:00' }
]

const tableData = ref([])

function statusTag(status) {
  const map = { '成功': 'success', '失败': 'danger', '发送中': 'warning' }
  return map[status] || ''
}

function loadData() {
  const start = (currentPage.value - 1) * pageSize.value
  const list = mockData.filter(d => {
    const kw = filters.keyword.toLowerCase()
    if (kw && !d.phone.includes(kw)) return false
    if (filters.sendStatus && d.sendStatus !== filters.sendStatus) return false
    return true
  })
  total.value = list.length
  tableData.value = list.slice(start, start + pageSize.value)
}

function handleSearch() { currentPage.value = 1; loadData() }

function handleReset() { filters.keyword = ''; filters.sendStatus = ''; handleSearch() }

function handleAdd() {
  editForm.phone = ''
  editForm.content = ''
  dialogVisible.value = true
}

function handleSave() {
  if (!editForm.phone || !editForm.content) {
    ElMessage.warning('请填写完整信息')
    return
  }
  const newId = Math.max(...mockData.map(d => d.id)) + 1
  mockData.push({
    id: newId,
    content: editForm.content,
    phone: editForm.phone.split(',')[0].trim(),
    sendStatus: '发送中',
    sendTime: new Date().toLocaleString('zh-CN', { hour12: false })
  })
  dialogVisible.value = false
  loadData()
  ElMessage.success('短信发送请求已提交')
}

function handleDetail(row) {
  ElMessage.info('短信内容：' + row.content)
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.kpi-row { margin-bottom: 16px; }
.kpi-card { text-align: center; padding: 8px 0; }
.kpi-label { font-size: 14px; color: var(--text-400); margin-bottom: 8px; }
.kpi-value { font-size: 28px; font-weight: 700; color: var(--text-800); }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
