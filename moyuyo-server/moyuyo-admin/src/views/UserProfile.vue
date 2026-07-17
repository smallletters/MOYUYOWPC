<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>用户画像</h2>
    </div>
    <!-- 搜索 -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="searchForm" inline @keyup.enter="handleSearch">
        <el-form-item label="用户名/ID">
          <el-input v-model="searchForm.keyword" placeholder="请输入用户名或ID" clearable style="width:240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    <!-- 用户信息卡片 -->
    <el-card shadow="never" style="margin-bottom:16px">
      <template #header><span>用户基本信息</span></template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="用户ID">U10001</el-descriptions-item>
        <el-descriptions-item label="用户名">zhangsan</el-descriptions-item>
        <el-descriptions-item label="昵称">张三</el-descriptions-item>
        <el-descriptions-item label="手机号">138****5678</el-descriptions-item>
        <el-descriptions-item label="邮箱">zhang***@example.com</el-descriptions-item>
        <el-descriptions-item label="注册时间">2025-03-15 10:30</el-descriptions-item>
        <el-descriptions-item label="会员等级">
          <el-tag type="warning" size="small">黄金会员</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="累计消费">¥12,580</el-descriptions-item>
        <el-descriptions-item label="用户标签">
          <el-tag v-for="tag in userTags" :key="tag" size="small" style="margin-right:4px">{{ tag }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
    <!-- 行为数据 -->
    <el-card shadow="never">
      <template #header><span>用户行为数据</span></template>
      <el-table :data="behaviorData" stripe style="width: 100%">
        <el-table-column prop="type" label="行为类型" />
        <el-table-column prop="count" label="次数" width="100" />
        <el-table-column prop="lastTime" label="最近时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'

const searchForm = reactive({ keyword: '' })

const userTags = ['高价值', '高频购买', '新品偏好', '移动端用户', '优惠券敏感']

const behaviorData = ref([
  { type: '浏览商品', count: 256, lastTime: '2026-07-17 14:30' },
  { type: '加入购物车', count: 89, lastTime: '2026-07-16 20:15' },
  { type: '提交订单', count: 45, lastTime: '2026-07-15 18:20' },
  { type: '支付成功', count: 42, lastTime: '2026-07-15 18:22' },
  { type: '搜索关键词', count: 178, lastTime: '2026-07-17 13:00' },
  { type: '收藏商品', count: 34, lastTime: '2026-07-14 09:45' },
  { type: '评价商品', count: 18, lastTime: '2026-07-10 11:30' },
  { type: '客服咨询', count: 6, lastTime: '2026-07-08 15:00' },
])

function handleSearch() {
  ElMessage.success('搜索完成：' + searchForm.keyword)
}

function handleReset() {
  searchForm.keyword = ''
}
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
</style>
