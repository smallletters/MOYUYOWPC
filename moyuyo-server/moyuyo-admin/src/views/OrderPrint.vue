<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>订单打印</h2>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="订单编号">
          <el-input v-model="filters.keyword" placeholder="请输入订单编号" clearable />
        </el-form-item>
        <el-form-item label="打印状态">
          <el-select v-model="filters.printStatus" placeholder="全部" clearable style="width:140px">
            <el-option label="全部" value="" />
            <el-option label="待打印" value="待打印" />
            <el-option label="已打印" value="已打印" />
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
        <el-table-column prop="orderNo" label="订单编号" width="170" />
        <el-table-column prop="productInfo" label="商品信息" min-width="200" show-overflow-tooltip />
        <el-table-column prop="receiver" label="收件人" width="120" />
        <el-table-column prop="printStatus" label="打印状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.printStatus === '已打印' ? 'success' : 'warning'" size="small">{{ row.printStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="printCount" label="打印次数" width="90" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handlePrint(row)">打印</el-button>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  keyword: '',
  printStatus: ''
})

const mockData = [
  { id: 1, orderNo: 'MOY20260717001', productInfo: '无线蓝牙耳机 Pro × 1', receiver: '张三', printStatus: '待打印', printCount: 0, createTime: '2026-07-17 10:30:00' },
  { id: 2, orderNo: 'MOY20260717002', productInfo: '智能手表S3 × 1, 表带 × 1', receiver: '李四', printStatus: '已打印', printCount: 1, createTime: '2026-07-17 09:20:00' },
  { id: 3, orderNo: 'MOY20260716003', productInfo: '运动休闲鞋 × 2', receiver: '王五', printStatus: '待打印', printCount: 0, createTime: '2026-07-16 14:45:00' },
  { id: 4, orderNo: 'MOY20260715004', productInfo: '不锈钢保温杯 × 3', receiver: '赵六', printStatus: '已打印', printCount: 2, createTime: '2026-07-15 11:10:00' },
  { id: 5, orderNo: 'MOY20260714005', productInfo: '轻薄笔记本电脑包 × 1', receiver: '孙七', printStatus: '待打印', printCount: 0, createTime: '2026-07-14 16:30:00' }
]

const tableData = ref([])

function loadData() {
  const start = (currentPage.value - 1) * pageSize.value
  const list = mockData.filter(d => {
    const kw = filters.keyword.toLowerCase()
    if (kw && !d.orderNo.toLowerCase().includes(kw)) return false
    if (filters.printStatus && d.printStatus !== filters.printStatus) return false
    return true
  })
  total.value = list.length
  tableData.value = list.slice(start, start + pageSize.value)
}

function handleSearch() { currentPage.value = 1; loadData() }

function handleReset() { filters.keyword = ''; filters.printStatus = ''; handleSearch() }

function handlePrint(row) {
  row.printStatus = '已打印'
  row.printCount++
  loadData()
  ElMessage.success('打印任务已提交，订单：' + row.orderNo)
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
