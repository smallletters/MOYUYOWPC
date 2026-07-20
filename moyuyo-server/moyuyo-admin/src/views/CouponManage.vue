<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>优惠券管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建优惠券</el-button>
      </div>
    </div>
    <!-- 统计卡片 -->
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <span class="stat-label">总发放</span>
            <span class="stat-value">{{ stats.totalIssued || 0 }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <span class="stat-label">已使用</span>
            <span class="stat-value">{{ stats.totalUsed || 0 }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <span class="stat-label">使用率</span>
            <span class="stat-value">{{ stats.usageRate || '0%' }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-item">
            <span class="stat-label">优惠总额</span>
            <span class="stat-value">{{ stats.totalDiscount || 0 }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <!-- 表格 -->
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.type === 'fixed' ? '满减' : '折扣' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="value" label="面值" width="100">
          <template #default="{ row }">
            {{ row.type === 'fixed' ? '¥' + row.value : row.value + '折' }}
          </template>
        </el-table-column>
        <el-table-column prop="minAmount" label="最低消费" width="110" />
        <el-table-column prop="totalStock" label="库存" width="80" />
        <el-table-column prop="usedCount" label="已用" width="80" />
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column prop="endTime" label="结束时间" width="160" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">{{ row.status === 'active' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑优惠券' : '新建优惠券'" width="550px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="优惠券名称" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-radio-group v-model="form.type">
            <el-radio value="fixed">满减</el-radio>
            <el-radio value="discount">折扣</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="面值" required>
          <el-input-number v-model="form.value" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="最低消费">
          <el-input-number v-model="form.minAmount" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number v-model="form.totalStock" :min="0" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间" />
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
import { getCouponList, createCoupon, updateCoupon, deleteCoupon, getCouponStats } from '../api/admin'

const tableData = ref([])
const stats = ref({})
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)

const form = reactive({
  name: '',
  type: 'fixed',
  value: 0,
  minAmount: 0,
  totalStock: 0,
  startTime: '',
  endTime: '',
})

function resetForm() {
  form.name = ''
  form.type = 'fixed'
  form.value = 0
  form.minAmount = 0
  form.totalStock = 0
  form.startTime = ''
  form.endTime = ''
}

async function loadData() {
  try {
    const res = await getCouponList()
    tableData.value = res.records || res || []
  } catch (e) {
    ElMessage.error('获取优惠券列表失败')
  }
}

async function loadStats() {
  try {
    const res = await getCouponStats()
    stats.value = res || {}
  } catch (e) {
    // 统计加载失败不阻塞
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
  form.type = row.type
  form.value = row.value
  form.minAmount = row.minAmount
  form.totalStock = row.totalStock
  form.startTime = row.startTime
  form.endTime = row.endTime
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name) {
    ElMessage.warning('请填写名称')
    return
  }
  try {
    if (isEdit.value) {
      await updateCoupon({ id: editId.value, ...form })
      ElMessage.success('编辑成功')
    } else {
      await createCoupon({ ...form })
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
    await ElMessageBox.confirm('确定删除优惠券「' + row.name + '」吗？', '提示')
    await deleteCoupon(row.id)
    ElMessage.success('已删除')
    await loadData()
  } catch (e) {
    // 用户取消不处理
  }
}

onMounted(() => { loadData(); loadStats() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.header-actions { display: flex; gap: 8px; }
.stat-item { display: flex; flex-direction: column; align-items: center; }
.stat-label { font-size: 13px; color: #909399; margin-bottom: 4px; }
.stat-value { font-size: 24px; font-weight: 700; color: var(--text-800); }
</style>
