<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>审计日志</h2>
      <div class="header-actions">
        <el-button @click="handleExport">导出日志</el-button>
      </div>
    </div>
    <!-- 筛选 -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="操作人">
          <el-input v-model="filters.operator" placeholder="操作人" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="filters.actionType" placeholder="全部" clearable style="width:140px">
            <el-option label="新增" value="新增" />
            <el-option label="修改" value="修改" />
            <el-option label="删除" value="删除" />
            <el-option label="查询" value="查询" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width:240px"
            value-format="YYYY-MM-DD"
          />
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
        <el-table-column prop="operator" label="操作人" width="120" />
        <el-table-column prop="actionType" label="操作类型" width="100">
          <template #default="{ row }">
            <el-tag :type="actionTag(row.actionType)" size="small">{{ row.actionType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="target" label="操作对象" width="160" />
        <el-table-column prop="detail" label="操作详情" min-width="250" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP 地址" width="150" />
        <el-table-column prop="createTime" label="操作时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'

const filters = reactive({
  operator: '',
  actionType: '',
  dateRange: null,
})

const tableData = ref([
  { id: 1, operator: 'admin', actionType: '修改', target: '商品 #1024', detail: '修改商品价格：¥89.00 → ¥79.00，修改库存数量：200 → 180', ipAddress: '192.168.1.100', createTime: '2026-07-17 14:30:25' },
  { id: 2, operator: 'editor01', actionType: '新增', target: '商品 #1025', detail: '新增商品「运动速干短裤」，SKU: ST-011，价格: ¥129.00', ipAddress: '192.168.1.101', createTime: '2026-07-17 13:20:10' },
  { id: 3, operator: 'admin', actionType: '删除', target: '用户 #U10086', detail: '删除已注销用户账号及相关数据', ipAddress: '192.168.1.100', createTime: '2026-07-16 16:45:00' },
  { id: 4, operator: 'csm01', actionType: '查询', target: '订单 #ORD-20260715', detail: '查询订单详情及退款状态', ipAddress: '192.168.1.102', createTime: '2026-07-16 14:30:30' },
  { id: 5, operator: 'editor01', actionType: '修改', target: '知识库 #8', detail: '编辑知识库文章「如何申请退款」，更新内容及分类', ipAddress: '192.168.1.101', createTime: '2026-07-15 11:30:15' },
  { id: 6, operator: 'admin', actionType: '新增', target: '用户 #U10090', detail: '新增管理员账号 editor02，角色：内容编辑', ipAddress: '192.168.1.100', createTime: '2026-07-15 09:15:45' },
  { id: 7, operator: 'system', actionType: '新增', target: '订单 #ORD-20260714', detail: '系统自动创建退款订单，金额: ¥298.00', ipAddress: '127.0.0.1', createTime: '2026-07-14 22:00:00' },
  { id: 8, operator: 'csm01', actionType: '查询', target: '用户 #U10045', detail: '查询用户订单历史及售后记录', ipAddress: '192.168.1.102', createTime: '2026-07-14 10:20:20' },
])

function actionTag(type) {
  if (type === '新增') return 'success'
  if (type === '修改') return 'warning'
  if (type === '删除') return 'danger'
  return 'info'
}

function handleSearch() { ElMessage.success('搜索完成') }
function handleReset() { filters.operator = ''; filters.actionType = ''; filters.dateRange = null }
function handleExport() { ElMessage.success('正在导出审计日志...') }
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
.header-actions { display: flex; gap: 8px; }
</style>
