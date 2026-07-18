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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAbTests, updateAbTest } from '../api/admin'

const tableData = ref([])

async function loadData() {
  try {
    const res = await getAbTests()
    tableData.value = res || []
  } catch (err) {
    console.error('获取A/B测试数据失败', err)
  }
}

function statusTag(status) {
  if (status === '运行中') return 'success'
  if (status === '已结束') return 'info'
  return 'warning'
}

function handleAdd() { ElMessage.info('新建 A/B 实验') }
function handleEdit(row) { ElMessage.warning('编辑功能开发中') }
async function handleStop(row) {
  try {
    await ElMessageBox.confirm('确定结束实验「' + row.name + '」吗？', '提示')
    await updateAbTest(row.id, { status: '已结束' })
    ElMessage.success('实验已结束')
    await loadData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('结束实验失败: ' + (e.message || '未知错误'))
    }
  }
}
function handleView(row) { ElMessage.warning('详情功能开发中') }

onMounted(() => loadData())
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.header-actions { display: flex; gap: 8px; }
</style>
