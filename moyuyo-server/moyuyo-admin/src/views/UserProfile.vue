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
        <el-descriptions-item label="用户ID">{{ userInfo.userId }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ userInfo.username }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ userInfo.nickname }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ userInfo.phone }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ userInfo.email }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ userInfo.registerTime }}</el-descriptions-item>
        <el-descriptions-item label="会员等级">
          <el-tag :type="userInfo.levelTag || 'info'" size="small">{{ userInfo.level }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="累计消费">{{ userInfo.totalSpent }}</el-descriptions-item>
        <el-descriptions-item label="用户标签">
          <el-tag v-for="tag in userInfo.tags" :key="tag" size="small" style="margin-right:4px">{{ tag }}</el-tag>
          <span v-if="userInfo.tags.length === 0" style="color:var(--text-400);font-size:12px;">暂无标签</span>
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
import { getUserProfile, getUserBehaviors } from '../api/admin'

const searchForm = reactive({ keyword: '' })

// 用户基本信息
const userInfo = reactive({
  userId: '-',
  username: '-',
  nickname: '-',
  phone: '-',
  email: '-',
  registerTime: '-',
  level: '-',
  levelTag: '',
  totalSpent: '-',
  tags: []
})

// 用户行为数据
const behaviorData = ref([])

const loading = ref(false)

// 搜索用户
async function handleSearch() {
  const keyword = searchForm.keyword.trim()
  if (!keyword) {
    ElMessage.warning('请输入用户名或ID')
    return
  }
  loading.value = true
  try {
    const res = await getUserProfile(keyword)
    if (res) {
      Object.assign(userInfo, {
        userId: res.userId || res.id || '-',
        username: res.username || '-',
        nickname: res.nickname || '-',
        phone: res.phone || '-',
        email: res.email || '-',
        registerTime: res.registerTime || '-',
        level: res.level || '-',
        levelTag: res.levelTag || '',
        totalSpent: res.totalSpent || '-',
        tags: res.tags || []
      })
    }

    // 获取行为数据
    const behaviorRes = await getUserBehaviors(keyword)
    if (behaviorRes) {
      behaviorData.value = behaviorRes.records || behaviorRes.list || behaviorRes
    }

    ElMessage.success('搜索完成')
  } catch (err) {
    console.error('获取用户数据失败:', err)
    ElMessage.error('未找到该用户或查询失败')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  searchForm.keyword = ''
  Object.assign(userInfo, {
    userId: '-', username: '-', nickname: '-', phone: '-', email: '-',
    registerTime: '-', level: '-', levelTag: '', totalSpent: '-', tags: []
  })
  behaviorData.value = []
}
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.filter-card { margin-bottom: 16px; }
</style>
