<template>
  <div class="system-settings">
    <h2 class="page-title">系统设置</h2>

    <!-- 管理员信息 -->
    <div class="admin-profile card">
      <div class="card-body">
        <div class="profile-content">
          <div class="profile-avatar">A</div>
          <div class="profile-info">
            <div class="profile-name">
              {{ adminInfo.name || 'Admin' }}
              <span class="tag tag-blue" style="margin-left: 8px;">{{ adminInfo.role || '超级管理员' }}</span>
            </div>
            <div class="profile-email">{{ adminInfo.email || 'admin@moyuyo.com' }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="two-col">
      <!-- 左栏 -->
      <div class="col-left">
        <!-- 角色管理 -->
        <div class="card">
          <div class="card-header">
            <h3>角色管理</h3>
            <button class="btn btn-sm btn-outline" @click="handleAddRole">添加角色</button>
          </div>
          <div class="card-body">
            <div class="role-list">
              <div class="role-item" v-for="role in roles" :key="role.name">
                <div class="role-info">
                  <span class="role-name">{{ role.name }}</span>
                  <!-- 后端 /api/admin/rbac/roles 返回 userCount 字段，与右侧 RBAC 页面保持一致 -->
                  <span class="role-count">{{ (role.userCount ?? 0) }} 人</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 操作日志 -->
        <div class="card">
          <div class="card-header">
            <h3>最近操作日志</h3>
          </div>
          <div class="card-body">
            <div class="log-list">
              <div class="log-item" v-for="log in recentLogs" :key="log.time">
                <div class="log-action">{{ log.action }}</div>
                <div class="log-meta">{{ log.operator }} · {{ log.time }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右栏 -->
      <div class="col-right">
        <!-- 权限开关 -->
        <div class="card">
          <div class="card-header">
            <h3>模块权限</h3>
            <button class="btn btn-sm btn-primary" :disabled="savingPermissions" @click="handleSavePermissions">
              {{ savingPermissions ? '保存中...' : '保存设置' }}
            </button>
          </div>
          <div class="card-body">
            <div class="toggle-list">
              <div class="toggle-item" v-for="perm in permissions" :key="perm.key">
                <span>{{ perm.label }}</span>
                <label class="toggle-switch">
                  <input type="checkbox" v-model="perm.enabled" />
                  <span class="toggle-slider"></span>
                </label>
              </div>
            </div>
          </div>
        </div>

        <!-- 安全设置 -->
        <div class="card">
          <div class="card-header">
            <h3>安全设置</h3>
            <button class="btn btn-sm btn-primary" :disabled="savingSecurity" @click="handleSaveSecurity">
              {{ savingSecurity ? '保存中...' : '保存设置' }}
            </button>
          </div>
          <div class="card-body">
            <div v-if="securityLoading" class="security-loading">加载中…</div>
            <div v-else class="security-list">
              <div class="security-item" v-for="item in securityConfig" :key="item.key">
                <div class="security-info">
                  <div class="security-label">{{ item.label }}</div>
                  <div class="security-desc">{{ item.desc }}</div>
                </div>
                <div class="security-control">
                  <template v-if="item.type === 'select' && item.options">
                    <select class="security-select" v-model="item.value">
                      <option v-for="opt in item.options" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                    </select>
                  </template>
                  <template v-else-if="item.type === 'number'">
                    <input
                      class="security-input"
                      type="number"
                      v-model.number="item.value"
                      :min="item.min"
                      :max="item.max"
                      :step="item.step || 1"
                    />
                    <span class="security-unit" v-if="item.unit">{{ item.unit }}</span>
                  </template>
                  <template v-else>
                    <input class="security-input" type="text" v-model="item.value" />
                  </template>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 系统信息 -->
        <div class="card">
          <div class="card-header">
            <h3>系统信息</h3>
          </div>
          <div class="card-body">
            <div class="sys-info">
              <div class="sys-row"><span>系统版本</span><span>{{ systemInfo.version || 'v1.0.0' }}</span></div>
              <div class="sys-row"><span>数据库状态</span><span :class="systemInfo.dbStatus === '正常' ? 'status-ok' : ''">{{ systemInfo.dbStatus || '正常' }}</span></div>
              <div class="sys-row"><span>缓存状态</span><span :class="systemInfo.cacheStatus === '正常' ? 'status-ok' : ''">{{ systemInfo.cacheStatus || '正常' }}</span></div>
              <div class="sys-row"><span>上次备份</span><span>{{ systemInfo.lastBackup || '2026-07-08 03:00' }}</span></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSystemLogs, getRbacRoles, getPermissions, getSecurityConfig, saveSecurityConfig, getSystemInfo, saveSystemConfig } from '../api/admin'
import { toArray } from '../utils/safeArray'
import { getAdminInfo } from '../api/auth'

const router = useRouter()

const roles = ref([])
const recentLogs = ref([])
const permissions = ref([])
const adminInfo = ref({ name: '', email: '', role: '' })
const securityConfig = ref([])
const securityLoading = ref(false)
const savingSecurity = ref(false)
const systemInfo = ref({ version: '', dbStatus: '', cacheStatus: '', lastBackup: '' })
const savingPermissions = ref(false)

// 安全设置字段 UI 元数据：与后端 /system-info/security-config 返回的 key 对应
// type=select -> 下拉；type=number -> 数字输入；其他 -> 文本输入
const SECURITY_FIELD_META = {
  password_policy: {
    label: '密码策略',
    type: 'select',
    description: '新管理员/重置密码时使用的复杂度规则',
    options: [
      { value: 'low', label: '低（≥6 位）' },
      { value: 'medium', label: '中（≥8 位，含字母+数字）' },
      { value: 'strong', label: '高（≥12 位，含大小写+数字+符号）' }
    ]
  },
  session_timeout: {
    label: '会话超时时间',
    type: 'number',
    description: '超过该时长（分钟）未操作则强制退出登录',
    unit: '分钟',
    min: 5,
    max: 1440,
    step: 5
  },
  max_login_attempts: {
    label: '最大登录尝试次数',
    type: 'number',
    description: '连续失败达到上限后锁定账号 30 分钟',
    unit: '次',
    min: 1,
    max: 20,
    step: 1
  }
}

// 加载角色列表
async function loadRoles() {
  try {
    const res = await getRbacRoles()
    roles.value = res || []
  } catch (e) {
    console.error('获取角色列表失败', e)
  }
}

// 加载权限配置
async function loadPermissions() {
  try {
    const res = await getPermissions()
    permissions.value = res || []
  } catch (e) {
    console.error('获取权限配置失败', e)
  }
}

// 加载管理员信息
async function loadAdminInfo() {
  try {
    const res = await getAdminInfo()
    adminInfo.value = res || { name: '', email: '', role: '' }
  } catch (e) {
    ElMessage.error('获取管理员信息失败')
  }
}

// 加载安全设置
async function loadSecurityConfig() {
  securityLoading.value = true
  try {
    const res = await getSecurityConfig()
    // 后端返回 List<{key, value, description}>，与 SECURITY_FIELD_META 合并得到完整 UI 字段
    const list = Array.isArray(res) ? res : []
    securityConfig.value = list
      .filter(item => item && item.key)
      .map(item => {
        const meta = SECURITY_FIELD_META[item.key] || {}
        // 后端返回的是字符串，比如 "medium" / "30"；number 类型字段需要 Number()
        let value = item.value
        if (meta.type === 'number') {
          const num = Number(value)
          value = isNaN(num) ? (meta.min ?? 0) : num
        }
        return {
          key: item.key,
          label: meta.label || item.key,
          type: meta.type || 'text',
          desc: meta.description || item.description || '',
          value,
          options: meta.options,
          min: meta.min,
          max: meta.max,
          step: meta.step,
          unit: meta.unit
        }
      })
  } catch (e) {
    ElMessage.error('获取安全设置失败')
  } finally {
    securityLoading.value = false
  }
}

// 保存安全设置：把当前表单值按 [{key, value, description}] 格式回传
async function handleSaveSecurity() {
  if (savingSecurity.value) return
  savingSecurity.value = true
  try {
    const payload = securityConfig.value.map(item => ({
      key: item.key,
      value: String(item.value ?? ''),
      description: item.desc || ''
    }))
    await saveSecurityConfig(payload)
    ElMessage.success('安全设置已保存')
  } catch (e) {
    ElMessage.error('保存安全设置失败: ' + (e.message || '未知错误'))
  } finally {
    savingSecurity.value = false
  }
}

// 加载系统信息
async function loadSystemInfo() {
  try {
    const res = await getSystemInfo()
    systemInfo.value = {
      version: res.version || res.appVersion || 'v1.0.0',
      dbStatus: res.dbStatus || res.databaseStatus || '正常',
      cacheStatus: res.cacheStatus || res.redisStatus || '正常',
      lastBackup: res.lastBackup || res.backupTime || '2026-07-08 03:00'
    }
  } catch (e) {
    ElMessage.error('获取系统信息失败')
  }
}

// 跳转到 RBAC 权限管理页面
  function handleAddRole() {
    router.push('/rbac')
  }

  // 保存权限配置
  async function handleSavePermissions() {
    savingPermissions.value = true
    try {
      // 将权限数组转换为后端期望的 List<Map> 格式
      const configs = permissions.value.map(p => ({
        key: p.key,
        value: p.enabled,
        label: p.label
      }))
      await saveSystemConfig(configs)
      ElMessage.success('权限配置已保存')
    } catch (e) {
      ElMessage.error('保存权限失败: ' + (e.message || '未知错误'))
    } finally {
      savingPermissions.value = false
    }
  }

  // 加载最近操作日志
  async function loadRecentLogs() {
  try {
    const res = await getSystemLogs()
    const logs = toArray(res)
    recentLogs.value = logs.slice(0, 5).map(item => ({
      action: item.action || item.content || '执行了操作',
      operator: item.operator || item.operatorName || '系统',
      time: item.createTime || item.operationTime || ''
    }))
  } catch (e) {
    // 静默失败，日志非关键数据
  }
}

onMounted(() => {
  Promise.all([loadRoles(), loadPermissions(), loadAdminInfo(), loadSecurityConfig(), loadSystemInfo(), loadRecentLogs()])
})
</script>

<style scoped lang="css">
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0 0 20px;
}

/* 管理员信息 */
.admin-profile .profile-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.profile-avatar {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--brand-500), var(--primary));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}

.profile-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-800);
  display: flex;
  align-items: center;
}

.profile-email {
  font-size: 13px;
  color: var(--text-400);
  margin-top: 4px;
}

/* 两栏 */
.col-left,
.col-right {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 角色列表 */
.role-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.role-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: var(--background-100);
  border-radius: var(--radius-sm);
}

.role-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-700);
}

.role-count {
  font-size: 12px;
  color: var(--text-400);
}

/* 操作日志 */
.log-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.log-item {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--background-100);
}

.log-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.log-action {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-700);
  margin-bottom: 2px;
}

.log-meta {
  font-size: 11px;
  color: var(--text-400);
}

/* 开关列表 */
.toggle-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.toggle-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--background-100);
  font-size: 13px;
  color: var(--text-600);
}

.toggle-item:last-child {
  border-bottom: none;
}

/* 开关样式 */
.toggle-switch {
  position: relative;
  display: inline-block;
  width: 40px;
  height: 22px;
  cursor: pointer;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  inset: 0;
  background: var(--background-200);
  border-radius: 11px;
  transition: all 0.2s ease;
}

.toggle-slider::before {
  content: '';
  position: absolute;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  top: 2px;
  left: 2px;
  background: #fff;
  transition: all 0.2s ease;
  box-shadow: 0 1px 3px rgba(0,0,0,0.15);
}

.toggle-switch input:checked + .toggle-slider {
  background: var(--state-success);
}

.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(18px);
}

/* 安全设置 */
.security-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.security-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
}
.security-item:last-child { border-bottom: none; }

.security-loading {
  padding: 20px 0;
  text-align: center;
  color: var(--text-400);
  font-size: 13px;
}

.security-control {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.security-input,
.security-select {
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--input);
  border-radius: var(--radius-sm);
  background: var(--background);
  color: var(--foreground);
  font-size: 13px;
  font-family: inherit;
  width: 140px;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.security-input:focus,
.security-select:focus {
  border-color: var(--ring);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--ring) 25%, transparent);
}

.security-unit {
  font-size: 12px;
  color: var(--text-400);
}

.security-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-700);
}

.security-desc {
  font-size: 11px;
  color: var(--text-400);
  margin-top: 2px;
}

/* 系统信息 */
.sys-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sys-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-600);
}

.status-ok {
  color: var(--state-success);
  font-weight: 600;
}
</style>
