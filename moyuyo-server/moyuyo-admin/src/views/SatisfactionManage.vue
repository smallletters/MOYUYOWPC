<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>满意度管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">发起调查</el-button>
      </div>
    </div>
    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">满意度评分</div>
            <div class="kpi-value" style="color:#f59e0b">4.8</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">评价总数</div>
            <div class="kpi-value">2,560</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">好评率</div>
            <div class="kpi-value" style="color:#10b981">96.2%</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="kpi-card-content">
            <div class="kpi-label">回复率</div>
            <div class="kpi-value">88.5%</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <!-- 表格 -->
    <el-card shadow="never">
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="user" label="用户" width="120" />
        <el-table-column prop="content" label="评价内容" min-width="250" show-overflow-tooltip />
        <el-table-column prop="score" label="评分" width="100">
          <template #default="{ row }">
            <el-rate v-model="row.score" disabled :colors="rateColors" score-template="{value}" :texts="['','','','','']" />
          </template>
        </el-table-column>
        <el-table-column prop="replyStatus" label="回复状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.replyStatus === '已回复' ? 'success' : 'danger'" size="small">{{ row.replyStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="评价时间" width="180" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.replyStatus === '未回复'" type="primary" link size="small" @click="handleReply(row)">回复</el-button>
            <el-button type="primary" link size="small" @click="handleView(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const rateColors = ['#f56c6c', '#e6a23c', '#5cb87a', '#1989fa', '#f59e0b']

const tableData = ref([
  { id: 1, user: '张***', content: '商品质量很好，物流速度也很快，非常满意！', score: 5, replyStatus: '已回复', createTime: '2026-07-17 14:30' },
  { id: 2, user: '李***', content: '衣服质量一般，洗了一次就变形了，希望能改进。', score: 3, replyStatus: '未回复', createTime: '2026-07-16 20:15' },
  { id: 3, user: '王***', content: '性价比很高，推荐购买。', score: 5, replyStatus: '已回复', createTime: '2026-07-15 18:20' },
  { id: 4, user: '赵***', content: '发货速度太慢了，等了一个星期才收到。', score: 2, replyStatus: '未回复', createTime: '2026-07-14 09:45' },
  { id: 5, user: '孙***', content: '包装很精美，细节做得好，以后还会回购。', score: 5, replyStatus: '已回复', createTime: '2026-07-13 11:30' },
  { id: 6, user: '周***', content: '和描述一致，好评！', score: 5, replyStatus: '已回复', createTime: '2026-07-12 16:00' },
])

function handleAdd() { ElMessage.info('发起满意度调查') }
function handleReply(row) { ElMessage.info('回复评价：' + row.user) }
function handleView(row) { ElMessage.info('查看评价详情') }
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.kpi-row { margin-bottom: 16px; }
.kpi-card-content { text-align: center; padding: 8px 0; }
.kpi-label { font-size: 14px; color: var(--text-400); margin-bottom: 8px; }
.kpi-value { font-size: 28px; font-weight: 700; color: var(--text-800); }
.header-actions { display: flex; gap: 8px; }
</style>
