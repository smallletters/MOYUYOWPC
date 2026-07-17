<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建直播</el-button>
      </div>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="请输入直播标题/主播" clearable />
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
        <el-table-column prop="title" label="直播标题" width="200" show-overflow-tooltip />
        <el-table-column prop="anchor" label="主播" width="100" />
        <el-table-column label="直播状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === '直播中' ? 'danger' : row.status === '预告中' ? 'warning' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" label="观看人数" width="100" />
        <el-table-column prop="productCount" label="商品数" width="80" />
        <el-table-column prop="startTime" label="开始时间" width="160" />
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
        <el-form-item label="直播标题">
          <el-input v-model="editForm.title" placeholder="请输入直播标题" />
        </el-form-item>
        <el-form-item label="主播">
          <el-input v-model="editForm.anchor" placeholder="请输入主播名称" />
        </el-form-item>
        <el-form-item label="直播状态">
          <el-select v-model="editForm.status">
            <el-option label="预告中" value="预告中" />
            <el-option label="直播中" value="直播中" />
            <el-option label="已结束" value="已结束" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品数">
          <el-input-number v-model="editForm.productCount" :min="0" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="editForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm" placeholder="选择开始时间" />
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

const pageTitle = '直播管理'
const filters = reactive({ keyword: '' })
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editForm = reactive({
  title: '',
  anchor: '',
  status: '预告中',
  productCount: 0,
  startTime: ''
})

const mockData = [
  { id: 1, title: '跨境好物推荐专场', anchor: '小美', status: '直播中', viewCount: 12580, productCount: 15, startTime: '2026-07-17 14:00' },
  { id: 2, title: '夏日护肤必备', anchor: '丽丽', status: '预告中', viewCount: 0, productCount: 10, startTime: '2026-07-18 20:00' },
  { id: 3, title: '保健品超级福利日', anchor: '健康哥', status: '已结束', viewCount: 32100, productCount: 20, startTime: '2026-07-15 19:00' },
  { id: 4, title: '母婴好物分享', anchor: '宝妈小丽', status: '预告中', viewCount: 0, productCount: 12, startTime: '2026-07-19 10:00' },
  { id: 5, title: '家居生活用品大促', anchor: '生活家', status: '已结束', viewCount: 18500, productCount: 25, startTime: '2026-07-14 15:00' },
  { id: 6, title: '海鲜美食节', anchor: '美食猎人', status: '直播中', viewCount: 8900, productCount: 8, startTime: '2026-07-17 10:00' }
]

function loadData() {
  let filtered = [...mockData]
  if (filters.keyword) {
    filtered = filtered.filter(item => item.title.includes(filters.keyword) || item.anchor.includes(filters.keyword))
  }
  tableData.value = filtered
  total.value = filtered.length
}
function handleSearch() { currentPage.value = 1; loadData() }
function handleReset() { filters.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '新建直播'; editForm.title = ''; editForm.anchor = ''; editForm.status = '预告中'; editForm.productCount = 0; editForm.startTime = ''; dialogVisible.value = true }
function handleEdit(row) { dialogTitle.value = '编辑直播'; Object.assign(editForm, row); dialogVisible.value = true }
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
