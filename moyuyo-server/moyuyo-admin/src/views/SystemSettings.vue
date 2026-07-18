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
            <button class="btn btn-sm btn-outline">添加角色</button>
          </div>
          <div class="card-body">
            <div class="role-list">
              <div class="role-item" v-for="role in roles" :key="role.name">
                <div class="role-info">
                  <span class="role-name">{{ role.name }}</span>
                  <span class="role-count">{{ role.count }} 人</span>
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
          </div>
          <div class="card-body">
            <div class="security-list">
              <div class="security-item" v-for="item in securityConfig" :key="item.label">
                <div class="security-info">
                  <div class="security-label">{{ item.label }}</div>
                  <div class="security-desc">{{ item.desc }}</div>
                </div>
                <span :class="'tag tag-' + (item.statusType || 'gray')">{{ item.status }}</span>
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
import { ElMessage } from 'element-plus'
import { getSystemLogs, getRoles, getPermissions, getAdminInfo, getSecurityConfig, getSystemInfo } from '../api/admin'

const roles = ref([])
const recentLogs = ref([])
const permissions = ref([])
const adminInfo = ref({ name: '', email: '', role: '' })
const securityConfig = ref([])
const systemInfo = ref({ version: '', dbStatus: '', cacheStatus: '', lastBackup: '' })

// 加载角色列表
async function loadRoles() {
  try {
    const res = await getRoles()
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
  try {
    const res = await getSecurityConfig()
    securityConfig.value = res || []
  } catch (e) {
    ElMessage.error('获取安全设置失败')
  }
}

// 加载系统信息
async function loadSystemInfo() {
  try {
    const res = await getSystemInfo()
    systemInfo.value = res || { version: '', dbStatus: '', cacheStatus: '', lastBackup: '' }
  } catch (e) {
    ElMessage.error('获取系统信息失败')
  }
}

// 加载最近操作日志
async function loadRecentLogs() {
  try {
    const res = await getSystemLogs()
    const logs = res.records || res || []
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
  font-size: 20px;
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
  padding: 8px 0;
  border-bottom: 1px solid var(--background-100);
}

.security-item:last-child {
  border-bottom: none;
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
