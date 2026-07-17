<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>应用版本管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">发布新版本</el-button>
      </div>
    </div>
    <!-- 表格 -->
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="version" label="版本号" width="120" />
        <el-table-column prop="platform" label="平台" width="100">
          <template #default="{ row }">
            <el-tag :type="row.platform === 'iOS' ? 'primary' : 'success'" size="small">{{ row.platform }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="更新内容" min-width="220" show-overflow-tooltip />
        <el-table-column prop="forceUpdate" label="强制更新" width="100">
          <template #default="{ row }">
            <el-tag :type="row.forceUpdate ? 'danger' : 'info'" size="small">{{ row.forceUpdate ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishTime" label="发布时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === '草稿'" type="primary" link size="small" @click="handlePublish(row)">发布</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑版本' : '发布新版本'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="版本号" required>
          <el-input v-model="form.version" placeholder="如 2.1.0" />
        </el-form-item>
        <el-form-item label="平台" required>
          <el-radio-group v-model="form.platform">
            <el-radio value="iOS">iOS</el-radio>
            <el-radio value="Android">Android</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="更新内容" required>
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="请输入更新内容" />
        </el-form-item>
        <el-form-item label="强制更新">
          <el-switch v-model="form.forceUpdate" />
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
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)

const form = reactive({
  version: '',
  platform: 'iOS',
  description: '',
  forceUpdate: false,
})

const tableData = ref([
  { id: 1, version: '2.1.0', platform: 'iOS', description: '新增购物车功能，优化结算流程，修复已知 Bug', forceUpdate: false, status: '已发布', publishTime: '2026-07-15 10:00' },
  { id: 2, version: '2.0.5', platform: 'Android', description: '优化首页加载速度，修复支付回调问题', forceUpdate: true, status: '已发布', publishTime: '2026-07-10 14:30' },
  { id: 3, version: '2.1.0', platform: 'Android', description: '新增购物车功能，优化结算流程，修复已知 Bug', forceUpdate: false, status: '待发布', publishTime: '2026-07-18 09:00' },
  { id: 4, version: '2.0.4', platform: 'iOS', description: '兼容 iOS 16 系统，修复闪退问题', forceUpdate: false, status: '已发布', publishTime: '2026-07-05 11:00' },
  { id: 5, version: '2.0.6', platform: 'iOS', description: '优化搜索推荐算法，增加用户反馈入口', forceUpdate: false, status: '草稿', publishTime: '-' },
  { id: 6, version: '2.0.3', platform: 'Android', description: '优化图片加载，修复商品详情页显示异常', forceUpdate: true, status: '已发布', publishTime: '2026-06-28 16:00' },
])

function statusTag(status) {
  if (status === '已发布') return 'success'
  if (status === '待发布') return 'warning'
  return 'info'
}

function resetForm() {
  form.version = ''
  form.platform = 'iOS'
  form.description = ''
  form.forceUpdate = false
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
  form.version = row.version
  form.platform = row.platform
  form.description = row.description
  form.forceUpdate = row.forceUpdate
  dialogVisible.value = true
}

function handleSave() {
  if (!form.version || !form.description) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (isEdit.value) {
    const item = tableData.value.find(item => item.id === editId.value)
    if (item) {
      item.version = form.version
      item.platform = form.platform
      item.description = form.description
      item.forceUpdate = form.forceUpdate
    }
    ElMessage.success('编辑成功')
  } else {
    const newId = tableData.value.length + 1
    tableData.value.unshift({
      id: newId,
      version: form.version,
      platform: form.platform,
      description: form.description,
      forceUpdate: form.forceUpdate,
      status: '草稿',
      publishTime: '-'
    })
    ElMessage.success('创建成功')
  }
  dialogVisible.value = false
}

function handlePublish(row) {
  ElMessageBox.confirm('确定发布版本 ' + row.version + ' 吗？', '提示').then(() => {
    row.status = '待发布'
    row.publishTime = new Date().toLocaleString()
    ElMessage.success('版本已发布')
  }).catch(() => {})
}

function handleDelete(row) {
  ElMessageBox.confirm('确定删除版本 ' + row.version + ' 吗？', '提示').then(() => {
    tableData.value = tableData.value.filter(item => item.id !== row.id)
    ElMessage.success('已删除')
  }).catch(() => {})
}
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.header-actions { display: flex; gap: 8px; }
</style>
