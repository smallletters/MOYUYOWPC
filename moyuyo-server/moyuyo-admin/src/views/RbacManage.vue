<template>
  <div class="rbac-page">
    <!-- 页面标题 -->
    <div class="rbac-header">
      <div class="rbac-header-text">
        <h2>RBAC 权限管理</h2>
        <p class="rbac-subtitle">管理角色与权限分配，确保系统安全</p>
      </div>
      <button class="rbac-btn rbac-btn-primary" @click="handleCreateRole">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        新建角色
      </button>
    </div>

    <!-- 主体：两列布局 -->
    <div class="rbac-main">
      <!-- 左列：角色列表 -->
      <div class="rbac-left">
        <div class="rbac-section-header">
          <span class="rbac-section-title">角色列表</span>
          <span class="rbac-section-count">共 {{ roles.length }} 个角色</span>
        </div>

        <div class="rbac-role-list">
          <div
            v-for="role in roles"
            :key="role.id"
            class="rbac-role-card"
            :class="{ active: selectedRole && selectedRole.id === role.id }"
            @click="selectRole(role)"
          >
            <!-- 角色头部 -->
            <div class="rbac-role-card-header">
              <span class="rbac-role-name">{{ role.name }}</span>
              <span v-if="role.isPreset" class="rbac-preset-badge">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                </svg>
                系统预设
              </span>
            </div>
            <!-- 角色描述 -->
            <p class="rbac-role-desc">{{ role.description }}</p>
            <!-- 关联人员 -->
            <div class="rbac-role-users">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
              <span>{{ role.userCount }} 人</span>
            </div>
            <!-- 权限范围标签 -->
            <div class="rbac-perm-tags">
              <span
                v-for="perm in role.permissions"
                :key="perm"
                class="perm-tag"
              >{{ perm }}</span>
            </div>
            <!-- 操作按钮 -->
            <div class="rbac-role-actions" @click.stop>
              <button
                class="rbac-btn-sm"
                :class="role.isPreset ? 'rbac-btn-disabled' : 'rbac-btn-blue'"
                :disabled="role.isPreset"
                title="编辑权限"
                @click="selectRole(role)"
              >
                <svg v-if="role.isPreset" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                </svg>
                <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
                {{ role.isPreset ? '不可编辑' : '编辑权限' }}
              </button>
              <button class="rbac-btn-sm rbac-btn-gray" title="查看成员" @click="handleViewMembers(role)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                  <circle cx="9" cy="7" r="4"/>
                </svg>
                查看成员
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右列：权限分配矩阵 -->
      <div class="rbac-right">
        <div v-if="selectedRole" class="rbac-perm-panel">
          <div class="rbac-panel-header">
            <span class="rbac-panel-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
              </svg>
              权限分配：{{ selectedRole.name }}
            </span>
          </div>

          <!-- 权限矩阵表 -->
          <div class="rbac-perm-table-wrapper">
            <table class="rbac-perm-table">
              <thead>
                <tr>
                  <th class="rbac-col-module">权限模块</th>
                  <th class="rbac-col-action">查看</th>
                  <th class="rbac-col-action">创建</th>
                  <th class="rbac-col-action">编辑</th>
                  <th class="rbac-col-action">删除</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="module in permModules" :key="module.name">
                  <td class="rbac-col-module">
                    <span class="rbac-module-name">{{ module.name }}</span>
                  </td>
                  <td class="rbac-col-action">
                    <label class="rbac-checkbox-wrapper">
                      <input
                        type="checkbox"
                        :checked="getPerm(module.key, 'view')"
                        :disabled="selectedRole.isPreset"
                        @change="togglePerm(module.key, 'view')"
                      />
                      <span class="rbac-checkbox-mimic"></span>
                    </label>
                  </td>
                  <td class="rbac-col-action">
                    <label class="rbac-checkbox-wrapper">
                      <input
                        type="checkbox"
                        :checked="getPerm(module.key, 'create')"
                        :disabled="selectedRole.isPreset"
                        @change="togglePerm(module.key, 'create')"
                      />
                      <span class="rbac-checkbox-mimic"></span>
                    </label>
                  </td>
                  <td class="rbac-col-action">
                    <label class="rbac-checkbox-wrapper">
                      <input
                        type="checkbox"
                        :checked="getPerm(module.key, 'edit')"
                        :disabled="selectedRole.isPreset"
                        @change="togglePerm(module.key, 'edit')"
                      />
                      <span class="rbac-checkbox-mimic"></span>
                    </label>
                  </td>
                  <td class="rbac-col-action">
                    <label class="rbac-checkbox-wrapper">
                      <input
                        type="checkbox"
                        :checked="getPerm(module.key, 'delete')"
                        :disabled="selectedRole.isPreset"
                        @change="togglePerm(module.key, 'delete')"
                      />
                      <span class="rbac-checkbox-mimic"></span>
                    </label>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 保存按钮 -->
          <div class="rbac-panel-footer">
            <button
              class="rbac-btn rbac-btn-primary"
              :disabled="selectedRole.isPreset"
              @click="handleSavePerms"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
                <polyline points="17 21 17 13 7 13 7 21"/>
                <polyline points="7 3 7 8 15 8"/>
              </svg>
              保存权限配置
            </button>
          </div>
        </div>

        <!-- 未选择角色时的提示 -->
        <div v-else class="rbac-empty-panel">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
          </svg>
          <p>请从左侧选择一个角色</p>
          <p class="rbac-empty-hint">选择角色后，可在此处分配权限</p>
        </div>
      </div>
    </div>

    <!-- 新建角色折叠表单 -->
    <div class="rbac-collapsible" :class="{ open: showCreateForm }">
      <div class="rbac-collapsible-header" @click="showCreateForm = !showCreateForm">
        <div class="rbac-collapsible-header-left">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <line x1="19" y1="8" x2="19" y2="14"/>
            <line x1="22" y1="11" x2="16" y2="11"/>
          </svg>
          <span>新建角色</span>
        </div>
        <svg class="rbac-chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </div>
      <div class="rbac-collapsible-body" :class="{ open: showCreateForm }">
        <div class="rbac-form">
          <div class="rbac-form-row">
            <div class="rbac-form-group">
              <label class="rbac-form-label">角色名称</label>
              <input v-model="newRole.name" type="text" class="rbac-form-input" placeholder="输入角色名称" />
            </div>
          </div>
          <div class="rbac-form-row">
            <div class="rbac-form-group">
              <label class="rbac-form-label">角色描述</label>
              <input v-model="newRole.description" type="text" class="rbac-form-input" placeholder="输入角色描述" />
            </div>
          </div>
          <div class="rbac-form-actions">
            <button class="rbac-btn rbac-btn-text" @click="cancelCreate">取消</button>
            <button class="rbac-btn rbac-btn-primary" @click="handleCreateSubmit">创建角色</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRbacRoles, createRbacRole, getRolePermissions, updateRolePermissions
} from '../api/admin'

// ---- 角色列表（从API获取） ----
const roles = ref([])
// 当前选中角色的权限数据
const currentPerms = ref({})

// ---- 权限模块（静态UI配置） ----
const permModules = [
  { key: 'product', name: '商品管理' },
  { key: 'order', name: '订单管理' },
  { key: 'user', name: '用户管理' },
  { key: 'marketing', name: '营销管理' },
  { key: 'stats', name: '数据统计' },
  { key: 'system', name: '系统设置' },
  { key: 'finance', name: '财务管理' },
  { key: 'review', name: '内容审核' }
]

// ---- 选中角色 ----
const selectedRole = ref(null)
const showCreateForm = ref(false)

const newRole = reactive({
  name: '',
  description: ''
})

// 加载角色列表
async function loadRoles() {
  try {
    const data = await getRbacRoles()
    roles.value = (data || []).map(role => ({
      id: role.id,
      name: role.name,
      description: role.description || '暂无描述',
      userCount: role.userCount ?? 0,
      isPreset: role.isPreset ?? false,
      permissions: role.permissions || []
    }))
    // 默认选中第一个角色并加载其权限
    if (roles.value.length > 0) {
      selectedRole.value = roles.value[0]
      await loadPermissions(roles.value[0].id)
    }
  } catch (e) {
    console.error('获取角色列表失败', e)
  }
}

// 加载指定角色的权限矩阵
async function loadPermissions(roleId) {
  try {
    const perms = await getRolePermissions(roleId)
    currentPerms.value = perms || {}
  } catch (e) {
    console.error('获取权限数据失败', e)
    currentPerms.value = {}
  }
}

// ---- 角色选择 ----
async function selectRole(role) {
  selectedRole.value = role
  await loadPermissions(role.id)
}

// ---- 权限读取/切换 ----
function getPerm(moduleKey, action) {
  if (!selectedRole.value) return false
  return currentPerms.value[moduleKey]?.[action] ?? false
}

function togglePerm(moduleKey, action) {
  if (!selectedRole.value || selectedRole.value.isPreset) return
  if (!currentPerms.value[moduleKey]) {
    currentPerms.value[moduleKey] = { view: false, create: false, edit: false, delete: false }
  }
  currentPerms.value[moduleKey][action] = !currentPerms.value[moduleKey][action]
}

// ---- 保存权限 ----
async function handleSavePerms() {
  if (!selectedRole.value) return
  try {
    await updateRolePermissions(selectedRole.value.id, currentPerms.value)
    ElMessage.success(`「${selectedRole.value.name}」权限配置已更新`)
  } catch (e) {
    ElMessage.error('保存权限失败')
  }
}

// ---- 查看成员 ----
function handleViewMembers(role) {
  ElMessage.info(`「${role.name}」共有 ${role.userCount} 名成员`)
}

// ---- 新建角色 ----
function handleCreateRole() {
  showCreateForm.value = !showCreateForm.value
  if (!showCreateForm.value) {
    newRole.name = ''
    newRole.description = ''
  }
}

function cancelCreate() {
  showCreateForm.value = false
  newRole.name = ''
  newRole.description = ''
}

async function handleCreateSubmit() {
  if (!newRole.name.trim()) {
    ElMessage.warning('请输入角色名称')
    return
  }
  try {
    const created = await createRbacRole({
      name: newRole.name,
      description: newRole.description || '暂无描述'
    })
    // 将新角色加入到本地列表
    roles.value.push({
      id: created.id,
      name: created.name,
      description: created.description || '暂无描述',
      userCount: created.userCount ?? 0,
      isPreset: created.isPreset ?? false,
      permissions: created.permissions || []
    })
    ElMessage.success(`角色「${newRole.name}」创建成功`)
    newRole.name = ''
    newRole.description = ''
    showCreateForm.value = false
  } catch (e) {
    ElMessage.error('创建角色失败')
  }
}

onMounted(() => {
  loadRoles()
})
</script>

<style scoped>
/* ---- 页面容器 ---- */
.rbac-page {
  padding: 24px 28px;
}

/* ---- 页面标题 ---- */
.rbac-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.rbac-header-text h2 {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0;
}

.rbac-subtitle {
  font-size: 13px;
  color: var(--text-400);
  margin: 6px 0 0;
}

/* ---- 按钮 ---- */
.rbac-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 40px;
  padding: 0 20px;
  border: none;
  border-radius: calc(var(--radius) * 0.7);
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.15s ease;
}

.rbac-btn svg {
  width: 16px;
  height: 16px;
}

.rbac-btn-primary {
  background: var(--primary);
  color: var(--primary-foreground);
}

.rbac-btn-primary:hover {
  filter: brightness(0.92);
}

.rbac-btn-primary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  filter: none;
}

.rbac-btn-text {
  background: transparent;
  color: var(--text-500);
  box-shadow: none;
  padding: 0 12px;
}

.rbac-btn-text:hover {
  color: var(--text-800);
}

/* ---- 主体：两列布局 ---- */
.rbac-main {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

/* ---- 左列 ---- */
.rbac-left {
  width: 40%;
  min-width: 320px;
  flex-shrink: 0;
}

.rbac-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.rbac-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-600);
}

.rbac-section-count {
  font-size: 13px;
  color: var(--text-400);
}

.rbac-role-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ---- 角色卡片 ---- */
.rbac-role-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
  padding: 18px 20px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.rbac-role-card:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--brand-200);
}

.rbac-role-card.active {
  border-color: var(--primary);
  box-shadow: 0 0 0 1px var(--primary), var(--shadow-sm);
}

.rbac-role-card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.rbac-role-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-800);
}

.rbac-preset-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
  background: var(--brand-50);
  color: var(--brand-600);
}

.rbac-preset-badge svg {
  width: 12px;
  height: 12px;
}

.rbac-role-desc {
  font-size: 12px;
  color: var(--text-500);
  margin: 0 0 10px;
  line-height: 1.5;
}

.rbac-role-users {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-400);
  margin-bottom: 12px;
}

.rbac-role-users svg {
  width: 14px;
  height: 14px;
  color: var(--text-400);
}

/* ---- 权限范围标签 ---- */
.rbac-perm-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 14px;
}

.rbac-perm-tags .perm-tag {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
  background: var(--brand-50);
  color: var(--brand-600);
}

/* ---- 角色操作按钮 ---- */
.rbac-role-actions {
  display: flex;
  gap: 8px;
  padding-top: 14px;
  border-top: 1px solid var(--background-200);
}

.rbac-btn-sm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 32px;
  padding: 0 14px;
  border: none;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  font-family: inherit;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.15s ease;
}

.rbac-btn-sm svg {
  width: 14px;
  height: 14px;
}

.rbac-btn-blue {
  background: var(--brand-50);
  color: var(--brand-600);
}

.rbac-btn-blue:hover {
  background: var(--brand-100);
}

.rbac-btn-gray {
  background: var(--background-200);
  color: var(--text-600);
}

.rbac-btn-gray:hover {
  background: var(--background-300);
}

.rbac-btn-disabled {
  background: var(--background-200);
  color: var(--text-400);
  opacity: 0.55;
  cursor: not-allowed;
}

/* ---- 右列 ---- */
.rbac-right {
  flex: 1;
  min-width: 0;
}

/* ---- 权限面板 ---- */
.rbac-perm-panel {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
  overflow: hidden;
}

.rbac-panel-header {
  padding: 18px 20px;
  border-bottom: 1px solid var(--border);
}

.rbac-panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-800);
}

.rbac-panel-title svg {
  width: 18px;
  height: 18px;
  color: var(--brand-500);
}

/* ---- 权限矩阵表 ---- */
.rbac-perm-table-wrapper {
  overflow-x: auto;
}

.rbac-perm-table {
  width: 100%;
  border-collapse: collapse;
}

.rbac-perm-table thead th {
  padding: 14px 16px;
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-500);
  background: var(--background-100);
  border-bottom: 1px solid var(--border);
}

.rbac-perm-table tbody td {
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  vertical-align: middle;
}

.rbac-perm-table tbody tr:last-child td {
  border-bottom: none;
}

.rbac-perm-table tbody tr:hover {
  background: var(--background-50);
}

.rbac-col-module {
  width: auto;
}

.rbac-col-action {
  width: 80px;
  text-align: center;
}

.rbac-module-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-700);
}

/* ---- 自定义复选框 ---- */
.rbac-checkbox-wrapper {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: pointer;
}

.rbac-checkbox-wrapper input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.rbac-checkbox-mimic {
  display: inline-block;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  border: 2px solid var(--border);
  background: var(--background);
  transition: all 0.15s ease;
}

.rbac-checkbox-wrapper input:checked + .rbac-checkbox-mimic {
  background: var(--primary);
  border-color: var(--primary);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='white' stroke-width='3' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpolyline points='20 6 9 17 4 12'%3E%3C/polyline%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: center;
}

.rbac-checkbox-wrapper input:disabled + .rbac-checkbox-mimic {
  opacity: 0.35;
  cursor: not-allowed;
}

.rbac-checkbox-wrapper input:disabled:checked + .rbac-checkbox-mimic {
  opacity: 0.45;
}

/* ---- 面板底部 ---- */
.rbac-panel-footer {
  padding: 16px 20px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: flex-end;
}

/* ---- 空面板 ---- */
.rbac-empty-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-xs);
  color: var(--text-400);
  gap: 12px;
}

.rbac-empty-panel svg {
  width: 56px;
  height: 56px;
  opacity: 0.25;
  color: var(--text-400);
}

.rbac-empty-panel p {
  font-size: 15px;
  margin: 0;
}

.rbac-empty-hint {
  font-size: 12px !important;
  opacity: 0.7;
}

/* ---- 新建角色折叠表单 ---- */
.rbac-collapsible {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--card);
  box-shadow: var(--shadow-sm);
  margin-top: 24px;
  overflow: hidden;
}

.rbac-collapsible-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  cursor: pointer;
  user-select: none;
  transition: background-color 0.15s ease;
}

.rbac-collapsible-header:hover {
  background: var(--background-100);
}

.rbac-collapsible-header svg {
  width: 20px;
  height: 20px;
  color: var(--primary);
}

.rbac-collapsible-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
}

.rbac-chevron {
  width: 18px;
  height: 18px;
  color: var(--text-400);
  transition: transform 0.25s ease;
}

.rbac-collapsible.open .rbac-chevron {
  transform: rotate(180deg);
}

.rbac-collapsible-body {
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.3s ease;
}

.rbac-collapsible-body.open {
  max-height: 300px;
}

/* ---- 表单 ---- */
.rbac-form {
  padding: 0 18px 18px;
}

.rbac-form-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.rbac-form-group {
  flex: 1;
}

.rbac-form-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-500);
  margin-bottom: 6px;
}

.rbac-form-input {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--input);
  border-radius: calc(var(--radius) * 0.6);
  background: var(--background);
  color: var(--foreground);
  font-size: 14px;
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
  box-sizing: border-box;
}

.rbac-form-input:focus {
  border-color: var(--ring);
  box-shadow: 0 0 0 1px var(--ring);
}

.rbac-form-input::placeholder {
  color: var(--text-400);
}

.rbac-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
</style>
