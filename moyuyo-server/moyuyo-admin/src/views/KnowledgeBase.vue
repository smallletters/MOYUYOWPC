<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>知识库</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建文章</el-button>
      </div>
    </div>
    <!-- 筛选 -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="标题">
          <el-input v-model="filters.title" placeholder="搜索标题" clearable style="width:200px" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="filters.category" placeholder="全部" clearable style="width:150px">
            <el-option label="常见问题" value="常见问题" />
            <el-option label="操作指南" value="操作指南" />
            <el-option label="政策法规" value="政策法规" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width:130px">
            <el-option label="已发布" value="已发布" />
            <el-option label="草稿" value="草稿" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <!-- 表格 -->
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <el-tag :type="categoryTag(row.category)" size="small">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '已发布' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" label="浏览次数" width="110" />
        <el-table-column prop="updateTime" label="更新时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === '草稿'" type="primary" link size="small" @click="handlePublish(row)">发布</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const filters = reactive({ title: '', category: '', status: '' })

const tableData = ref([
  { id: 1, title: '如何申请退款？', category: '常见问题', status: '已发布', viewCount: 12580, updateTime: '2026-07-17 10:30' },
  { id: 2, title: '商品上架操作指南', category: '操作指南', status: '已发布', viewCount: 8920, updateTime: '2026-07-16 14:20' },
  { id: 3, title: '用户隐私保护政策', category: '政策法规', status: '已发布', viewCount: 6540, updateTime: '2026-07-15 09:00' },
  { id: 4, title: '订单处理方法汇总', category: '常见问题', status: '草稿', viewCount: 0, updateTime: '2026-07-14 16:45' },
  { id: 5, title: '物流发货操作指南', category: '操作指南', status: '已发布', viewCount: 4560, updateTime: '2026-07-13 11:30' },
  { id: 6, title: '平台交易管理规定', category: '政策法规', status: '草稿', viewCount: 0, updateTime: '2026-07-12 08:15' },
])

function categoryTag(category) {
  if (category === '常见问题') return 'warning'
  if (category === '操作指南') return 'primary'
  return 'success'
}

function handleSearch() { ElMessage.success('搜索完成') }
function handleReset() { filters.title = ''; filters.category = ''; filters.status = '' }
function handleAdd() { ElMessage.info('新建知识库文章') }
function handleEdit(row) { ElMessage.info('编辑文章：' + row.title) }
function handlePublish(row) {
  row.status = '已发布'
  ElMessage.success('文章已发布')
}
function handleDelete(row) {
  ElMessageBox.confirm('确定删除文章「' + row.title + '」吗？', '提示').then(() => {
    tableData.value = tableData.value.filter(item => item.id !== row.id)
    ElMessage.success('已删除')
  }).catch(() => {})
}
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
