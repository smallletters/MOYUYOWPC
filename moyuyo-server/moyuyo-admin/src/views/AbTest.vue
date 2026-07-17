<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>A/B 测试</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建实验</el-button>
      </div>
    </div>
    <!-- 表格 -->
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="实验名称" min-width="160" />
        <el-table-column prop="versionA" label="版本 A" width="120" />
        <el-table-column prop="versionB" label="版本 B" width="120" />
        <el-table-column prop="sampleSize" label="样本量" width="100" />
        <el-table-column prop="conversionA" label="转化率 A" width="110">
          <template #default="{ row }">{{ row.conversionA }}%</template>
        </el-table-column>
        <el-table-column prop="conversionB" label="转化率 B" width="110">
          <template #default="{ row }">{{ row.conversionB }}%</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === '运行中'" type="success" link size="small" @click="handleStop(row)">结束</el-button>
            <el-button type="primary" link size="small" @click="handleView(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const tableData = ref([
  { id: 1, name: '首页 Banner 样式优化', versionA: '旧版 Banner', versionB: '新版 Banner', sampleSize: 50000, conversionA: 3.2, conversionB: 4.8, status: '运行中' },
  { id: 2, name: '商品详情页布局', versionA: '左右布局', versionB: '上下布局', sampleSize: 30000, conversionA: 5.1, conversionB: 5.6, status: '已结束' },
  { id: 3, name: '购物车按钮颜色', versionA: '蓝色', versionB: '红色', sampleSize: 80000, conversionA: 8.3, conversionB: 9.1, status: '运行中' },
  { id: 4, name: '搜索推荐算法', versionA: '热门推荐', versionB: '个性化推荐', sampleSize: 20000, conversionA: 12.5, conversionB: 15.2, status: '待开始' },
  { id: 5, name: '结算流程优化', versionA: '3步结算', versionB: '1步结算', sampleSize: 25000, conversionA: 18.6, conversionB: 22.3, status: '已结束' },
  { id: 6, name: '推送文案测试', versionA: '促销型', versionB: '故事型', sampleSize: 60000, conversionA: 6.7, conversionB: 5.9, status: '待开始' },
])

function statusTag(status) {
  if (status === '运行中') return 'success'
  if (status === '已结束') return 'info'
  return 'warning'
}

function handleAdd() { ElMessage.info('新建 A/B 实验') }
function handleEdit(row) { ElMessage.info('编辑实验：' + row.name) }
function handleStop(row) {
  ElMessageBox.confirm('确定结束实验「' + row.name + '」吗？', '提示').then(() => {
    row.status = '已结束'
    ElMessage.success('实验已结束')
  }).catch(() => {})
}
function handleView(row) { ElMessage.info('查看实验详情：' + row.name) }
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.header-actions { display: flex; gap: 8px; }
</style>
