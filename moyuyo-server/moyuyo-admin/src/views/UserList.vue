<template>
  <div class="user-list-page">
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="kpi-card" v-for="stat in statsList" :key="stat.label">
        <div class="kpi-label">{{ stat.label }}</div>
        <div class="kpi-value">{{ stat.value }}</div>
        <div v-if="stat.change" class="kpi-change" :class="stat.trend === 'up' ? 'kpi-change--up' : 'kpi-change--down'">
          {{ stat.change }}
        </div>
      </div>
    </div>

    <!-- 筛选面板 -->
    <div class="query-panel">
      <div class="form-row">
        <div class="form-group">
          <label>搜索</label>
          <input v-model="filters.search" type="text" class="form-input" placeholder="用户名/邮箱/ID" @keyup.enter="handleSearch" />
        </div>
        <div class="form-group">
          <label>会员等级</label>
          <select v-model="filters.level" class="form-input">
            <option value="">全部等级</option>
            <option value="NORMAL">普通会员</option>
            <option value="SILVER">银卡会员</option>
            <option value="GOLD">金卡会员</option>
            <option value="DIAMOND">钻石会员</option>
          </select>
        </div>
        <div class="form-group">
          <label>注册渠道</label>
          <select v-model="filters.channel" class="form-input">
            <option value="">全部渠道</option>
            <option value="web">网页端</option>
            <option value="app">App</option>
            <option value="wechat">微信小程序</option>
          </select>
        </div>
        <div class="form-group">
          <label>状态</label>
          <select v-model="filters.status" class="form-input">
            <option value="">全部</option>
            <option value="active">正常</option>
            <option value="banned">封禁</option>
            <option value="inactive">未激活</option>
          </select>
        </div>
        <div class="form-group form-group--action">
          <label>&nbsp;</label>
          <button class="btn btn-primary" @click="handleSearch">搜索</button>
          <button class="btn" @click="handleReset">重置</button>
        </div>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="table-wrapper">
      <div class="table-toolbar">
        <div class="table-toolbar__info">已选 {{ selectedIds.length }} 项</div>
        <div class="table-toolbar__actions">
          <button class="btn btn-sm" :disabled="selectedIds.length === 0" @click="handleBatchUpdateStatus('INACTIVE')">批量封禁</button>
          <button class="btn btn-sm" :disabled="selectedIds.length === 0" @click="handleBatchUpdateStatus('ACTIVE')">批量解封</button>
          <button class="btn btn-sm" @click="handleExportCsv">导出 CSV</button>
          <button class="btn btn-sm btn-primary" @click="handleCreate">新建用户</button>
        </div>
      </div>
      <table class="data-table">
        <thead>
          <tr>
            <th><input type="checkbox" v-model="selectAll" @change="toggleSelectAll" /></th>
            <th>ID</th>
            <th>用户信息</th>
            <th>邮箱</th>
            <th>注册时间</th>
            <th>会员等级</th>
            <th>订单数</th>
            <th>消费额</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="10" class="empty-cell">加载中…</td>
          </tr>
          <tr v-else-if="userList.length === 0">
            <td colspan="10" class="empty-cell">暂无数据</td>
          </tr>
          <tr v-for="user in userList" v-else :key="user.id">
            <td><input type="checkbox" v-model="selectedIds" :value="user.id" /></td>
            <td class="user-id">{{ user.id }}</td>
            <td class="user-info-cell">
              <div class="user-avatar" :style="{ backgroundColor: user.avatarColor }">
                {{ user.name.charAt(0) }}
              </div>
              <span class="user-name">{{ user.name }}</span>
            </td>
            <td>{{ user.email }}</td>
            <td class="user-time">{{ user.registerTime }}</td>
            <td>
              <span :class="'user-level level-' + user.levelClass">{{ user.level }}</span>
            </td>
            <td>{{ user.orders }}</td>
            <td>¥{{ user.spent }}</td>
            <td>
              <span class="status-dot" :class="'status-' + user.statusClass"></span>
              {{ user.status }}
            </td>
            <td class="action-cell">
              <button class="btn btn-sm" @click="handleDetail(user)">详情</button>
              <button class="btn btn-sm" @click="handleEdit(user)">编辑</button>
              <button class="btn btn-sm" @click="handleResetPwd(user)">重置密码</button>
              <button class="btn btn-sm" @click="handleBan(user)">
                {{ user.statusClass === 'active' ? '封禁' : '解封' }}
              </button>
              <button class="btn btn-sm btn-danger" @click="handleDelete(user)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div class="pagination">
      <button class="btn btn-sm" :disabled="currentPage <= 1" @click="currentPage--">上一页</button>
      <span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页（共 {{ total }} 条）</span>
      <button class="btn btn-sm" :disabled="currentPage >= totalPages" @click="currentPage++">下一页</button>
    </div>

    <!-- 新建/编辑用户弹窗 -->
    <el-dialog
      v-model="formDialog.visible"
      :title="formDialog.isEdit ? '编辑用户' : '新建用户'"
      width="520px"
      :close-on-click-modal="false"
      @closed="resetFormDialog"
    >
      <el-form ref="formRef" :model="formDialog.form" :rules="formDialog.rules" label-width="90px">
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="formDialog.form.nickname" placeholder="请输入昵称" maxlength="32" show-word-limit />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="formDialog.form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="formDialog.form.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="会员等级" prop="level">
          <el-select v-model="formDialog.form.level" placeholder="请选择" style="width:100%">
            <el-option label="普通会员" value="NORMAL" />
            <el-option label="银卡会员" value="SILVER" />
            <el-option label="金卡会员" value="GOLD" />
            <el-option label="钻石会员" value="DIAMOND" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formDialog.form.status">
            <el-radio value="ACTIVE">正常</el-radio>
            <el-radio value="INACTIVE">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!formDialog.isEdit" label="初始密码" prop="password">
          <el-input v-model="formDialog.form.password" type="password" placeholder="至少 12 位，含大小写+数字+特殊字符" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="formDialog.submitting" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="pwdDialog.visible" title="重置密码" width="420px" :close-on-click-modal="false">
      <el-form ref="pwdFormRef" :model="pwdDialog.form" :rules="pwdDialog.rules" label-width="80px">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="pwdDialog.form.password" type="password" placeholder="至少 12 位" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="pwdDialog.submitting" @click="handlePwdSubmit">确定重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getUserStats,
  getUserList,
  updateUserStatus,
  createUser,
  updateUser,
  deleteUser,
  resetUserPassword
} from '../api/admin'
import { toArray } from '../utils/safeArray'
import { exportCsv } from '../utils/exportCsv'

const router = useRouter()

const selectAll = ref(false)
const selectedIds = ref([])
const currentPage = ref(1)
const pageSize = 10
const loading = ref(false)
const total = ref(0)

// 头像颜色池：用用户 ID 哈希取色，让不同用户头像颜色有差异
const AVATAR_COLORS = ['#2563eb', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6', '#f97316']
function pickAvatarColor(id) {
  const n = Number(id) || 0
  return AVATAR_COLORS[n % AVATAR_COLORS.length]
}

const statsList = ref([
  { label: '总用户数', value: '-' },
  { label: '今日新增', value: '-', change: '', trend: 'up' },
  { label: '活跃用户', value: '-', change: '', trend: 'up' },
  { label: '会员总数', value: '-', change: '', trend: 'down' }
])

const filters = reactive({
  search: '',
  level: '',
  channel: '',
  status: ''
})

const userList = ref([])

// 获取用户统计数据
async function fetchStats() {
  try {
    const res = await getUserStats()
    if (res) {
      statsList.value = [
        { label: '总用户数', value: res.totalUsers ?? '-', change: '', trend: 'up' },
        { label: '今日新增', value: res.newToday ?? '-', change: '', trend: 'up' },
        { label: '活跃用户', value: res.activeToday ?? '-', change: '', trend: 'up' },
        { label: '会员总数', value: res.totalMembers ?? '-', change: '', trend: 'down' }
      ]
    }
  } catch (err) {
    console.error('获取用户统计数据失败:', err)
    ElMessage.warning('用户统计数据加载失败')
  }
}

// 获取用户列表
async function fetchUsers() {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize,
      search: filters.search,
      level: filters.level,
      channel: filters.channel,
      status: filters.status
    }
    Object.keys(params).forEach(k => {
      if (!params[k]) delete params[k]
    })
    const res = await getUserList(params)
    if (res) {
      const rawList = toArray(res, 'list')
      userList.value = rawList.map(u => ({
        id: u.id,
        name: u.nickname || u.name || '-',
        email: u.email || '',
        registerTime: u.registerTime || '-',
        level: u.level || 'NORMAL',
        levelClass: (u.level || 'NORMAL').toLowerCase(),
        orders: u.orders ?? '-',
        spent: u.spent ?? '0',
        status: u.status === 'ACTIVE' ? '正常' : '禁用',
        statusClass: u.status === 'ACTIVE' ? 'active' : 'inactive',
        avatarColor: pickAvatarColor(u.id)
      }))
      total.value = res.total || 0
      // 翻页后清空选中，避免误操作跨页用户
      selectedIds.value = []
      selectAll.value = false
    }
  } catch (err) {
    console.error('获取用户列表失败:', err)
    ElMessage.error('获取用户列表失败')
  } finally {
    loading.value = false
  }
}

const totalPages = computed(() => Math.ceil(total.value / pageSize) || 1)

// 全选切换：仅作用于当前页可见用户
function toggleSelectAll() {
  if (selectAll.value) {
    selectedIds.value = userList.value.map(u => u.id)
  } else {
    selectedIds.value = []
  }
}

// 单项选择变化时，自动同步 selectAll 状态
watch(selectedIds, (val) => {
  if (userList.value.length === 0) {
    selectAll.value = false
    return
  }
  selectAll.value = val.length === userList.value.length
}, { deep: true })

function handleSearch() {
  currentPage.value = 1
  fetchUsers()
}

function handleReset() {
  filters.search = ''
  filters.level = ''
  filters.channel = ''
  filters.status = ''
  currentPage.value = 1
  fetchUsers()
}

// 详情：跳转到用户画像页（带 id 参数，由画像页自动加载）
// 使用 window.location.href 强制整页跳转，避免 SPA chunk 缓存导致组件渲染失败
function handleDetail(user) {
  window.location.href = `/admin/user-profile?id=${user.id}`
}

// 单项封禁/解封
async function handleBan(user) {
  const newStatus = user.statusClass === 'active' ? 'INACTIVE' : 'ACTIVE'
  const actionText = newStatus === 'ACTIVE' ? '解封' : '封禁'
  try {
    await ElMessageBox.confirm(
      `确认${actionText}用户「${user.name}」？`,
      '操作确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await updateUserStatus(user.id, { status: newStatus })
    ElMessage.success(`${user.name} 已${actionText}`)
    fetchUsers()
  } catch (err) {
    console.error('更新用户状态失败:', err)
    ElMessage.error('操作失败')
  }
}

// 批量封禁/解封：复用 updateUserStatus 逐个调用，简单可靠；并发请求避免阻塞
async function handleBatchUpdateStatus(targetStatus) {
  const actionText = targetStatus === 'ACTIVE' ? '解封' : '封禁'
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确认批量${actionText}所选 ${ids.length} 位用户？`,
      '批量操作确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  const results = await Promise.allSettled(
    ids.map(id => updateUserStatus(id, { status: targetStatus }))
  )
  const success = results.filter(r => r.status === 'fulfilled').length
  const failed = results.length - success
  if (failed === 0) {
    ElMessage.success(`批量${actionText}成功，共 ${success} 位`)
  } else {
    ElMessage.warning(`批量${actionText}完成：成功 ${success}，失败 ${failed}`)
  }
  fetchUsers()
}

// 导出 CSV：导出当前筛选结果的全量数据
async function handleExportCsv() {
  try {
    // 拉取全量当前筛选条件的用户（最大 5000 条，防止 OOM）
    const res = await getUserList({ page: 1, size: 5000, ...filters })
    const list = toArray(res, 'list')
    if (list.length === 0) {
      ElMessage.warning('当前筛选条件下没有可导出的数据')
      return
    }
    const rows = list.map(u => ({
      id: u.id,
      nickname: u.nickname || u.name || '-',
      email: u.email || '',
      registerTime: u.registerTime || '-',
      level: u.level || '-',
      orders: u.orders ?? 0,
      spent: u.spent ?? 0,
      status: u.status === 'ACTIVE' ? '正常' : '禁用'
    }))
    const ok = exportCsv(
      rows,
      [
        { key: 'id', label: 'ID' },
        { key: 'nickname', label: '昵称' },
        { key: 'email', label: '邮箱' },
        { key: 'registerTime', label: '注册时间' },
        { key: 'level', label: '会员等级' },
        { key: 'orders', label: '订单数' },
        { key: 'spent', label: '消费额' },
        { key: 'status', label: '状态' }
      ],
      `users-${new Date().toISOString().slice(0, 10)}.csv`
    )
    if (ok) ElMessage.success(`已导出 ${list.length} 条用户数据`)
  } catch (err) {
    console.error('导出失败:', err)
    ElMessage.error('导出失败')
  }
}

// ============== 新建/编辑用户 ==============
const formRef = ref(null)
const formDialog = reactive({
  visible: false,
  isEdit: false,
  editingId: null,
  submitting: false,
  form: {
    nickname: '',
    email: '',
    phone: '',
    level: 'NORMAL',
    status: 'ACTIVE',
    password: ''
  },
  rules: {
    nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
    email: [
      { required: true, message: '请输入邮箱', trigger: 'blur' },
      { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
    ],
    phone: [
      { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
    ],
    level: [{ required: true, message: '请选择会员等级', trigger: 'change' }],
    status: [{ required: true, message: '请选择状态', trigger: 'change' }],
    password: [
      { required: true, message: '请输入初始密码', trigger: 'blur' },
      { min: 12, message: '密码至少 12 位', trigger: 'blur' }
    ]
  }
})

function resetFormDialog() {
  formDialog.isEdit = false
  formDialog.editingId = null
  formDialog.form.nickname = ''
  formDialog.form.email = ''
  formDialog.form.phone = ''
  formDialog.form.level = 'NORMAL'
  formDialog.form.status = 'ACTIVE'
  formDialog.form.password = ''
  if (formRef.value) formRef.value.clearValidate()
}

function handleCreate() {
  resetFormDialog()
  formDialog.isEdit = false
  formDialog.visible = true
}

function handleEdit(user) {
  resetFormDialog()
  formDialog.isEdit = true
  formDialog.editingId = user.id
  formDialog.form.nickname = user.name === '-' ? '' : user.name
  formDialog.form.email = user.email
  formDialog.form.phone = user.phone || ''
  formDialog.form.level = user.level
  formDialog.form.status = user.statusClass === 'active' ? 'ACTIVE' : 'INACTIVE'
  // 编辑时不展示密码字段，但保留 password 字段为空不会提交
  formDialog.visible = true
}

async function handleFormSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  formDialog.submitting = true
  try {
    if (formDialog.isEdit) {
      const payload = {
        nickname: formDialog.form.nickname,
        email: formDialog.form.email,
        phone: formDialog.form.phone,
        level: formDialog.form.level,
        status: formDialog.form.status
      }
      await updateUser(formDialog.editingId, payload)
      ElMessage.success('用户信息已更新')
    } else {
      await createUser({ ...formDialog.form })
      ElMessage.success('用户创建成功')
    }
    formDialog.visible = false
    fetchUsers()
    fetchStats()
  } catch (err) {
    console.error('保存用户失败:', err)
    ElMessage.error(err.message || '保存失败')
  } finally {
    formDialog.submitting = false
  }
}

// ============== 重置密码 ==============
const pwdFormRef = ref(null)
const pwdDialog = reactive({
  visible: false,
  submitting: false,
  targetUser: null,
  form: { password: '' },
  rules: {
    password: [
      { required: true, message: '请输入新密码', trigger: 'blur' },
      { min: 12, message: '密码至少 12 位', trigger: 'blur' }
    ]
  }
})

function handleResetPwd(user) {
  pwdDialog.targetUser = user
  pwdDialog.form.password = ''
  pwdDialog.visible = true
}

async function handlePwdSubmit() {
  try {
    await pwdFormRef.value.validate()
  } catch {
    return
  }
  pwdDialog.submitting = true
  try {
    await resetUserPassword(pwdDialog.targetUser.id, { password: pwdDialog.form.password })
    ElMessage.success(`已重置 ${pwdDialog.targetUser.name} 的密码`)
    pwdDialog.visible = false
  } catch (err) {
    console.error('重置密码失败:', err)
    ElMessage.error(err.message || '重置失败')
  } finally {
    pwdDialog.submitting = false
  }
}

// ============== 删除用户 ==============
async function handleDelete(user) {
  try {
    await ElMessageBox.confirm(
      `确认删除用户「${user.name}」？该操作不可恢复。`,
      '删除确认',
      { type: 'error', confirmButtonText: '删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
  } catch {
    return
  }
  try {
    await deleteUser(user.id)
    ElMessage.success('用户已删除')
    fetchUsers()
    fetchStats()
  } catch (err) {
    console.error('删除失败:', err)
    ElMessage.error(err.message || '删除失败')
  }
}

// 监听页码变化，重新加载数据
watch(currentPage, () => {
  fetchUsers()
})

onMounted(() => {
  fetchStats()
  fetchUsers()
})
</script>

<style scoped lang="css">
.user-list-page {
  max-width: 1200px;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1d1d1f;
  margin: 0;
}

/* 统计卡片 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.kpi-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.kpi-label {
  font-size: 13px;
  color: #8e8e93;
  margin-bottom: 6px;
}

.kpi-value {
  font-size: 28px;
  font-weight: 700;
  color: #1d1d1f;
  margin-bottom: 4px;
}

.kpi-change {
  font-size: 13px;
  font-weight: 500;
}

.kpi-change--up {
  color: #10b981;
}

.kpi-change--down {
  color: #ef4444;
}

/* 查询面板 */
.query-panel {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.form-row {
  display: flex;
  gap: 16px;
  align-items: flex-end;
  flex-wrap: wrap;
}

.form-group {
  flex: 1;
  min-width: 160px;
}

.form-group--action {
  flex: 0 0 auto;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #555;
  margin-bottom: 6px;
}

.form-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #e5e5ea;
  border-radius: 6px;
  font-size: 13px;
  color: #1d1d1f;
  background: #f9f9fb;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: #2563eb;
}

/* 表格 */
.table-wrapper {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafafa;
}

.table-toolbar__info {
  font-size: 13px;
  color: #555;
}

.table-toolbar__actions {
  display: flex;
  gap: 8px;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th {
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  color: #8e8e93;
  padding: 12px 14px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafafa;
  white-space: nowrap;
}

.data-table td {
  padding: 12px 14px;
  font-size: 13px;
  color: #333;
  border-bottom: 1px solid #f5f5f7;
}

.data-table tr:hover {
  background: #fafafa;
}

.user-id {
  font-family: monospace;
  font-size: 12px;
  color: #8e8e93;
}

.user-info-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  flex-shrink: 0;
}

.user-name {
  font-weight: 500;
  color: #1d1d1f;
}

.user-time {
  font-size: 12px;
  color: #8e8e93;
  white-space: nowrap;
}

/* 会员等级标签 */
.user-level {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.level-diamond {
  background: #fefce8;
  color: #ca8a04;
}

.level-gold {
  background: #fff7ed;
  color: #c2410c;
}

.level-silver {
  background: #f3f4f6;
  color: #6b7280;
}

.level-normal {
  background: #eff6ff;
  color: #2563eb;
}

/* 状态点 */
.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}

.status-active {
  background: #10b981;
}

.status-banned {
  background: #ef4444;
}

.status-inactive {
  background: #d1d5db;
}

.action-cell {
  display: flex;
  gap: 6px;
  white-space: nowrap;
  flex-wrap: wrap;
}

.btn-sm {
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid #e5e5ea;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  color: #555;
  transition: all 0.2s;
}

.btn-sm:hover:not(:disabled) {
  border-color: #2563eb;
  color: #2563eb;
}

.btn-sm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-danger:hover:not(:disabled) {
  border-color: #ef4444;
  color: #ef4444;
}

.btn {
  padding: 8px 16px;
  border: 1px solid #e5e5ea;
  background: #fff;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  color: #555;
  transition: all 0.2s;
}

.btn-primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: #1d4ed8;
}

.empty-cell {
  text-align: center;
  padding: 40px 0;
  color: #aeaeb2;
  font-size: 14px;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
}

.page-info {
  font-size: 13px;
  color: #8e8e93;
}
</style>
