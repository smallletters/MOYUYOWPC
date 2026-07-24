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

    <!-- 新建/编辑文章对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑文章' : '新建文章'"
      width="640px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form :model="formData" label-position="top">
        <el-form-item label="文章标题" required>
          <el-input v-model="formData.title" placeholder="输入文章标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="formData.category" style="width:100%">
                <el-option v-for="cat in categoryOptions" :key="cat" :label="cat" :value="cat" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="formData.status" style="width:100%">
                <el-option label="草稿" value="草稿" />
                <el-option label="已发布" value="已发布" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="正文内容">
          <el-input
            v-model="formData.content"
            type="textarea"
            :rows="6"
            placeholder="输入文章正文内容..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSave">
          {{ isEditing ? '保存修改' : '创建文章' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getKnowledgeList, createKnowledge, updateKnowledge, deleteKnowledge } from '../api/admin'

// ---- 数据 ----
const allData = ref([]) // 原始全量数据
const filters = reactive({ title: '', category: '', status: '' })

// ---- 前端筛选：从全量数据中过滤 ----
const tableData = computed(() => {
  let list = [...allData.value]
  const t = filters.title.trim().toLowerCase()
  if (t) {
    list = list.filter(item => item.title && item.title.toLowerCase().includes(t))
  }
  if (filters.category) {
    list = list.filter(item => item.category === filters.category)
  }
  if (filters.status) {
    list = list.filter(item => item.status === filters.status)
  }
  return list
})

// ---- 对话框状态 ----
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref(null)
const submitting = ref(false)

// ---- 表单数据 ----
const formData = reactive({
  title: '',
  category: '常见问题',
  status: '草稿',
  content: ''
})

// ---- 分类选项 ----
const categoryOptions = ['常见问题', '操作指南', '政策法规']

async function loadData() {
  try {
    const res = await getKnowledgeList()
    const list = (res && res.records) || res || []
    allData.value = list
  } catch (err) {
    console.error('获取知识库数据失败', err)
  }
}

function categoryTag(category) {
  if (category === '常见问题') return 'warning'
  if (category === '操作指南') return 'primary'
  return 'success'
}

// 搜索：computed 已自动响应 filters 变化，此处仅做用户反馈
function handleSearch() {
  // tableData 依赖 filters，自动重新计算
}

function handleReset() {
  filters.title = ''
  filters.category = ''
  filters.status = ''
}

// 新建文章：打开对话框
function handleAdd() {
  isEditing.value = false
  editingId.value = null
  formData.title = ''
  formData.category = '常见问题'
  formData.status = '草稿'
  formData.content = ''
  dialogVisible.value = true
}

// 编辑文章：填充表单
function handleEdit(row) {
  isEditing.value = true
  editingId.value = row.id
  formData.title = row.title
  formData.category = row.category
  formData.status = row.status
  formData.content = row.content || ''
  dialogVisible.value = true
}

// 保存文章（新建或编辑）
async function handleSave() {
  if (!formData.title.trim()) {
    ElMessage.warning('请输入文章标题')
    return
  }
  submitting.value = true
  try {
    if (isEditing.value) {
      await updateKnowledge({ id: editingId.value, ...formData })
      ElMessage.success('文章已更新')
    } else {
      await createKnowledge({ ...formData })
      ElMessage.success('文章已创建')
    }
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

// 发布文章
async function handlePublish(row) {
  try {
    await updateKnowledge({ id: row.id, status: '已发布' })
    ElMessage.success('文章已发布')
    await loadData()
  } catch (e) {
    ElMessage.error('发布失败: ' + (e.message || '未知错误'))
  }
}

// 删除文章
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除文章「' + row.title + '」吗？', '提示')
    await deleteKnowledge(row.id)
    ElMessage.success('已删除')
    await loadData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
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
</style>
