<template>
  <div class="rbac-page">
    <!-- 页面标题 -->
    <div class="rbac-header">
      <div class="rbac-header-text">
        <h2>RBAC 权限管理</h2>
        <p class="rbac-subtitle">管理角色与权限分配，确保系统安全</p>
      </div>
      <button class="rbac-btn rbac-btn-primary" @click="openCreateDialog">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        新建
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
              <!-- 权限分配：选中后右侧矩阵即可编辑/查看权限（包括预设角色，只读） -->
              <button
                class="rbac-btn-sm rbac-btn-blue"
                title="权限分配"
                @click="selectRole(role)"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                </svg>
                权限分配
              </button>
              <!-- 编辑角色名称/描述 -->
              <button class="rbac-btn-sm rbac-btn-gray" title="编辑角色" @click="openEditDialog(role)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
                编辑
              </button>
              <button class="rbac-btn-sm rbac-btn-gray" title="查看成员" @click="openMembersDialog(role)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                  <circle cx="9" cy="7" r="4"/>
                </svg>
                查看成员
              </button>
              <!-- 删除角色：系统预设角色（isPreset=true）不允许删除，由系统统一维护 -->
              <button
                v-if="!role.isPreset"
                class="rbac-btn-sm rbac-btn-danger"
                title="删除角色"
                :disabled="deletingId === role.id"
                @click="handleDeleteRole(role)"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="3 6 5 6 21 6"/>
                  <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                  <path d="M10 11v6"/>
                  <path d="M14 11v6"/>
                  <path d="M9 6V4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2"/>
                </svg>
                {{ deletingId === role.id ? '删除中...' : '删除角色' }}
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

    <!-- 新建角色 / 新建管理员账号 合并弹窗 -->
    <el-dialog
      v-model="createDialogVisible"
      :title="createDialogMode === 'role' ? '新建角色' : '新建管理员账号'"
      width="500px"
      :close-on-click-modal="false"
      :close-on-press-escape="true"
      destroy-on-close
      @closed="onCreateDialogClosed"
    >
      <el-tabs v-model="createDialogMode" class="rbac-create-tabs">
        <!-- Tab 1: 创建角色 -->
        <el-tab-pane label="创建角色" name="role">
          <div class="rbac-form">
            <div class="rbac-form-row">
              <div class="rbac-form-group">
                <label class="rbac-form-label">角色名称 <span class="rbac-required">*</span></label>
                <input
                  v-model="newRole.name"
                  type="text"
                  class="rbac-form-input"
                  placeholder="例如：内容审核员"
                  maxlength="32"
                  @keyup.enter="handleCreateSubmit"
                />
              </div>
            </div>
            <div class="rbac-form-row">
              <div class="rbac-form-group">
                <label class="rbac-form-label">角色描述</label>
                <input
                  v-model="newRole.description"
                  type="text"
                  class="rbac-form-input"
                  placeholder="简要说明该角色的职责"
                  maxlength="120"
                  @keyup.enter="handleCreateSubmit"
                />
              </div>
            </div>
          </div>
        </el-tab-pane>
        <!-- Tab 2: 新建管理员账号 -->
        <el-tab-pane label="新建管理员账号" name="user">
          <div class="rbac-form">
            <div class="rbac-form-row">
              <div class="rbac-form-group">
                <label class="rbac-form-label">用户名 <span class="rbac-required">*</span></label>
                <input v-model="newUser.username" type="text" class="rbac-form-input"
                  placeholder="字母或数字，唯一" maxlength="32" @keyup.enter="handleCreateUserSubmit" />
              </div>
            </div>
            <div class="rbac-form-row">
              <div class="rbac-form-group">
                <label class="rbac-form-label">角色 <span class="rbac-required">*</span></label>
                <select v-model="newUser.roleCode" class="rbac-form-input rbac-form-select">
                  <option value="">请选择角色</option>
                  <option v-for="r in roles" :key="r.id" :value="r.code">
                    {{ r.name }}（{{ r.code }}）
                  </option>
                </select>
              </div>
            </div>
            <div class="rbac-form-row">
              <div class="rbac-form-group">
                <label class="rbac-form-label">姓名 <span class="rbac-required">*</span></label>
                <input v-model="newUser.name" type="text" class="rbac-form-input"
                  placeholder="管理员显示名" maxlength="32" @keyup.enter="handleCreateUserSubmit" />
              </div>
            </div>
            <div class="rbac-form-row">
              <div class="rbac-form-group">
                <label class="rbac-form-label">邮箱 <span class="rbac-required">*</span></label>
                <input v-model="newUser.email" type="text" class="rbac-form-input"
                  placeholder="例如：manager@moyuyo.com" @keyup.enter="handleCreateUserSubmit" />
              </div>
            </div>
            <div class="rbac-form-row">
              <div class="rbac-form-group">
                <label class="rbac-form-label">初始密码 <span class="rbac-required">*</span></label>
                <input v-model="newUser.password" type="password" class="rbac-form-input"
                  placeholder="至少 12 位" @keyup.enter="handleCreateUserSubmit" />
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
      <!-- 弹窗底部操作栏：根据当前 Tab 动态切换文案与提交方法 -->
      <template #footer>
        <div class="rbac-dialog-footer">
          <button class="rbac-btn rbac-btn-text" @click="createDialogVisible = false">取消</button>
          <button
            v-if="createDialogMode === 'role'"
            class="rbac-btn rbac-btn-primary"
            :disabled="creating"
            @click="handleCreateSubmit"
          >
            {{ creating ? '创建中...' : '创建角色' }}
          </button>
          <button
            v-else
            class="rbac-btn rbac-btn-primary"
            :disabled="creatingUser"
            @click="handleCreateUserSubmit"
          >
            {{ creatingUser ? '创建中...' : '创建账号' }}
          </button>
        </div>
      </template>
     </el-dialog>

    <!-- 编辑角色（名称/描述）弹窗 -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑角色"
      width="480px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="rbac-form">
        <div class="rbac-form-row">
          <div class="rbac-form-group">
            <label class="rbac-form-label">
              角色名称 <span class="rbac-required">*</span>
              <span v-if="editTarget && editTarget.isPreset" class="rbac-preset-hint">系统预设</span>
            </label>
            <input
              v-model="editForm.name"
              type="text"
              class="rbac-form-input"
              placeholder="例如：内容审核员"
              maxlength="32"
              :disabled="editTarget && editTarget.isPreset"
              @keyup.enter="handleEditSubmit"
            />
            <p v-if="editTarget && editTarget.isPreset" class="rbac-form-help">
              预设角色名称由系统维护，仅可调整描述。
            </p>
          </div>
        </div>
        <div class="rbac-form-row">
          <div class="rbac-form-group">
            <label class="rbac-form-label">角色描述</label>
            <input
              v-model="editForm.description"
              type="text"
              class="rbac-form-input"
              placeholder="简要说明该角色的职责"
              maxlength="120"
              @keyup.enter="handleEditSubmit"
            />
          </div>
        </div>
      </div>
      <template #footer>
        <div class="rbac-dialog-footer">
          <button class="rbac-btn rbac-btn-text" @click="editDialogVisible = false">取消</button>
          <button class="rbac-btn rbac-btn-primary" :disabled="editing" @click="handleEditSubmit">
            {{ editing ? '保存中...' : '保存修改' }}
          </button>
        </div>
      </template>
    </el-dialog>

    <!-- 查看角色下的成员 -->
    <el-dialog
      v-model="membersDialogVisible"
      :title="membersTitle"
      width="560px"
      destroy-on-close
    >
      <!-- 最后一名 SUPER_ADMIN 保护提示：让用户明确知道为何删除按钮被禁用 -->
      <div v-if="viewingSuperAdminRole && !membersLoading" class="rbac-members-notice">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
        </svg>
        <span v-if="isLastSuperAdmin">系统必须保留至少 1 名超级管理员，所有删除按钮已禁用</span>
        <span v-else>当前有 {{ membersList.length }} 名超级管理员，可删除多余成员</span>
      </div>
      <div v-if="membersLoading" class="rbac-members-loading">加载成员中…</div>
      <div v-else-if="membersList.length === 0" class="rbac-members-empty">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
          <circle cx="9" cy="7" r="4"/>
        </svg>
        <p>该角色下还没有关联管理员</p>
      </div>
      <div v-else class="rbac-members-list">
        <div v-for="m in membersList" :key="m.id" class="rbac-member-item">
          <div class="rbac-member-avatar">{{ avatarChar(m.name || m.username) }}</div>
          <div class="rbac-member-info">
            <div class="rbac-member-name">
              {{ m.name || m.username }}
              <span class="rbac-member-status" :class="'status-' + (m.status || '').toLowerCase()">{{ m.status }}</span>
            </div>
            <div class="rbac-member-meta">{{ m.email || m.username }} · 创建于 {{ formatDate(m.createTime) }}</div>
          </div>
          <!-- 单个成员的操作按钮组：重置密码 + 删除人员 -->
          <div class="rbac-member-actions">
            <button
              class="rbac-btn-sm rbac-btn-gray"
              :disabled="resettingPwdUserId === m.id || deletingUserId === m.id"
              title="重置密码"
              @click="openResetPwdDialog(m)"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
              {{ resettingPwdUserId === m.id ? '重置中...' : '重置密码' }}
            </button>
            <!-- 单个成员的删除按钮：二次确认后调用后端 DELETE /rbac/users/{id} -->
            <!-- 超级管理员角色（SUPER_ADMIN）：成员数 < 2 时禁用，避免出现"零超级管理员"无法恢复 -->
            <!-- 非超级管理员角色：直接允许删除 -->
            <button
              class="rbac-btn-sm rbac-btn-danger"
              :disabled="deletingUserId === m.id || isLastSuperAdmin"
              :title="isLastSuperAdmin ? '超级管理员至少保留 1 个成员' : '删除人员'"
              @click="handleDeleteMember(m)"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                <path d="M10 11v6"/>
                <path d="M14 11v6"/>
                <path d="M9 6V4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2"/>
              </svg>
              {{ deletingUserId === m.id ? '删除中...' : '删除人员' }}
            </button>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="rbac-dialog-footer">
          <button class="rbac-btn rbac-btn-text" @click="membersDialogVisible = false">关闭</button>
          <!-- 新建管理员账号入口已迁移到"新建角色"合并弹窗的 Tab -->
        </div>
      </template>
    </el-dialog>

    <!-- 新建管理员账号 已合并到创建角色弹窗 Tab -->


    <!-- 重置成员密码 -->
    <el-dialog
      v-model="resetPwdDialogVisible"
      :title="resetPwdDialogTitle"
      width="440px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="rbac-form">
        <div class="rbac-form-row">
          <div class="rbac-form-group">
            <label class="rbac-form-label">账号</label>
            <input
              class="rbac-form-input"
              :value="resetPwdTarget ? (resetPwdTarget.name || resetPwdTarget.username) : ''"
              disabled
            />
          </div>
        </div>
        <div class="rbac-form-row">
          <div class="rbac-form-group">
            <label class="rbac-form-label">新密码 <span class="rbac-required">*</span></label>
            <input
              v-model="resetPwdNew"
              type="password"
              class="rbac-form-input"
              placeholder="至少 12 位，建议包含大小写+数字+符号"
              @keyup.enter="handleResetPwdSubmit"
            />
          </div>
        </div>
      </div>
      <template #footer>
        <div class="rbac-dialog-footer">
          <button class="rbac-btn rbac-btn-text" @click="resetPwdDialogVisible = false">取消</button>
          <button class="rbac-btn rbac-btn-primary" :disabled="resettingPwd" @click="handleResetPwdSubmit">
            {{ resettingPwd ? '重置中...' : '确认重置' }}
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRbacRoles, createRbacRole, updateRbacRole, deleteRbacRole, getRbacRoleMembers, createRbacUser, resetRbacUserPassword, deleteRbacUser, getRolePermissions, updateRolePermissions
} from '../api/admin'

// ---- 角色列表（从API获取） ----
const roles = ref([])
// 当前选中角色的权限ID列表（扁平格式）
const currentPermKeys = ref([])

// ---- 权限模块（静态UI配置） ----
const permModules = [
  { key: 'dashboard', name: '仪表盘' },
  { key: 'analysis', name: '数据分析' },
  { key: 'products', name: '商品管理' },
  { key: 'product-approval', name: '商品审批' },
  { key: 'product-analysis', name: '商品分析' },
  { key: 'orders', name: '订单管理' },
  { key: 'order-ops', name: '订单操作' },
  { key: 'order-tags', name: '订单标签' },
  { key: 'users', name: '用户管理' },
  { key: 'user-profile', name: '用户画像' },
  { key: 'blacklist', name: '黑名单' },
  { key: 'crm', name: '客户管理' },
  { key: 'marketing', name: '营销管理' },
  { key: 'coupons', name: '优惠券' },
  { key: 'flash-sales', name: '秒杀活动' },
  { key: 'live', name: '直播管理' },
  { key: 'push', name: '推送管理' },
  { key: 'sms', name: '短信管理' },
  { key: 'finance', name: '财务管理' },
  { key: 'settlement', name: '结算管理' },
  { key: 'refunds', name: '退款管理' },
  { key: 'review', name: '评价管理' },
  { key: 'content-review', name: '内容审核' },
  { key: 'cms', name: '内容管理' },
  { key: 'knowledge-base', name: '知识库' },
  { key: 'cs-sessions', name: '客服会话' },
  { key: 'ticket', name: '工单管理' },
  { key: 'complaint', name: '投诉管理' },
  { key: 'satisfaction', name: '满意度' },
  { key: 'logistics', name: '物流管理' },
  { key: 'inventory', name: '库存管理' },
  { key: 'inventory-transfer', name: '库存调拨' },
  { key: 'price', name: '价格管理' },
  { key: 'tariff', name: '关税管理' },
  { key: 'points', name: '积分管理' },
  { key: 'risk', name: '风控管理' },
  { key: 'risk-alert', name: '风控预警' },
  { key: 'gdpr', name: 'GDPR合规' },
  { key: 'sensitive', name: '敏感词' },
  { key: 'rbac', name: '权限管理' },
  { key: 'system', name: '系统管理' },
  { key: 'system-info', name: '系统信息' },
  { key: 'settings', name: '系统设置' },
  { key: 'app-version', name: '版本管理' },
  { key: 'batch-import', name: '批量导入' },
  { key: 'audit-log', name: '审计日志' }
]

// ---- 选中角色 ----
const selectedRole = ref(null)
// 弹窗可见性
const createDialogVisible = ref(false)
// 提交中的 loading 状态
const creating = ref(false)
// 当前正在删除的角色 id（用于按钮 disabled 与文案切换）
const deletingId = ref(null)

// ---- 编辑角色弹窗状态 ----
const editDialogVisible = ref(false)
const editing = ref(false)
const editTarget = ref(null)
const editForm = reactive({ name: '', description: '' })

// ---- 查看成员弹窗状态 ----
const membersDialogVisible = ref(false)
const membersLoading = ref(false)
const membersList = ref([])
// 当前查看的角色是否为超级管理员（code=SUPER_ADMIN 或 id=1）
const viewingSuperAdminRole = computed(() => isSuperAdminRole(editTarget.value))
// 当前超级管理员角色下"成员数 < 2" 即处于"最后一名"保护态
const isLastSuperAdmin = computed(() => viewingSuperAdminRole.value && membersList.value.length < 2)
const membersTitle = computed(() => {
  return editTarget.value && membersDialogVisible.value
    ? `角色成员：${editTarget.value.name}`
    : '角色成员'
})

// ---- 新建管理员账号弹窗 ----
const createUserDialogVisible = ref(false)
const creatingUser = ref(false)
const newUser = reactive({ username: '', name: '', email: '', password: '', roleCode: '' })
const createUserDialogTitle = computed(() => {
  return editTarget.value
    ? `新建管理员账号（绑定到「${editTarget.value.name}」）`
    : '新建管理员账号'
})

// ---- 重置成员密码弹窗 ----
const resetPwdDialogVisible = ref(false)
const resettingPwd = ref(false)
const resettingPwdUserId = ref(null)
// 当前正在删除的成员 id（用于按钮 disabled 与文案切换）
const deletingUserId = ref(null)
const resetPwdTarget = ref(null)
const resetPwdNew = ref('')
const resetPwdDialogTitle = computed(() => {
  const t = resetPwdTarget.value
  return t ? `重置密码：${t.name || t.username}` : '重置密码'
})

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
      code: role.code, // 保留角色编码：新建管理员账号弹窗 <option :value="r.code"> 需要它
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
    const data = await getRolePermissions(roleId)
    currentPermKeys.value = data?.permKeys || []
  } catch (e) {
    console.error('获取权限数据失败', e)
    currentPermKeys.value = []
  }
}

// ---- 角色选择 ----
async function selectRole(role) {
  selectedRole.value = role
  await loadPermissions(role.id)
}

// ---- 权限读取/切换（权限键格式：resource:action，如 products:view） ----
function getPerm(moduleKey, action) {
  if (!selectedRole.value) return false
  return currentPermKeys.value.includes(`${moduleKey}:${action}`)
}

function togglePerm(moduleKey, action) {
  if (!selectedRole.value || selectedRole.value.isPreset) return
  const permKey = `${moduleKey}:${action}`
  const idx = currentPermKeys.value.indexOf(permKey)
  if (idx === -1) {
    currentPermKeys.value.push(permKey)
  } else {
    currentPermKeys.value.splice(idx, 1)
  }
}

// ---- 保存权限 ----
async function handleSavePerms() {
  if (!selectedRole.value) return
  try {
    await updateRolePermissions(selectedRole.value.id, { permKeys: currentPermKeys.value })
    ElMessage.success(`「${selectedRole.value.name}」权限配置已更新`)
  } catch (e) {
    ElMessage.error('保存权限失败')
  }
}

// ---- 查看成员 ----
// ---- 编辑角色：打开弹窗 ----
function openEditDialog(role) {
  editTarget.value = role
  editForm.name = role.name || ''
  editForm.description = role.description || ''
  editDialogVisible.value = true
  // 弹窗打开后聚焦到第一个输入框
  nextTick(() => {
    const input = document.querySelector('.el-dialog__body .rbac-form-input')
    if (input && !input.disabled) input.focus()
  })
}

async function handleEditSubmit() {
  if (editing.value) return
  if (!editTarget.value) return
  const name = (editForm.name || '').trim()
  if (!name) {
    ElMessage.warning('请输入角色名称')
    return
  }
  editing.value = true
  try {
    const updated = await updateRbacRole(editTarget.value.id, {
      name,
      description: (editForm.description || '').trim() || '暂无描述'
    })
    // 同步更新本地列表中的该角色
    const idx = roles.value.findIndex(r => r.id === editTarget.value.id)
    if (idx > -1) {
      roles.value[idx].name = updated.name || name
      roles.value[idx].description = (editForm.description || '').trim() || '暂无描述'
    }
    // 若被编辑的是当前选中角色，同步刷新右侧 header
    if (selectedRole.value && selectedRole.value.id === editTarget.value.id) {
      selectedRole.value = { ...selectedRole.value, name: roles.value[idx]?.name || name }
    }
    ElMessage.success(`角色「${name}」修改成功`)
    editDialogVisible.value = false
  } catch (e) {
    const msg = (e && e.message) || ''
    // 后端校验类错误（400/409）会进入 message，提取更具体的提示
    ElMessage.error(msg.includes('更新角色失败') ? msg : '修改角色失败')
  } finally {
    editing.value = false
  }
}

// ---- 查看成员：弹窗加载该角色关联的管理员 ----
async function openMembersDialog(role) {
  editTarget.value = role
  membersList.value = []
  membersDialogVisible.value = true
  membersLoading.value = true
  try {
    const list = await getRbacRoleMembers(role.id)
    membersList.value = (list || []).map(u => ({
      ...u,
      createTime: u.createTime || u.lastLoginTime || null
    }))
  } catch (e) {
    ElMessage.error('获取成员失败')
  } finally {
    membersLoading.value = false
  }
}

// 取首字符作为头像文案，兼容中文与英文
function avatarChar(s) {
  if (!s) return '?'
  const str = String(s).trim()
  return str.charAt(0).toUpperCase() || '?'
}

// 简单格式化日期：YYYY-MM-DD HH:mm
function formatDate(d) {
  if (!d) return '-'
  try {
    const dt = new Date(d)
    if (isNaN(dt.getTime())) return '-'
    const pad = n => String(n).padStart(2, '0')
    return `${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())} ${pad(dt.getHours())}:${pad(dt.getMinutes())}`
  } catch (e) {
    return '-'
  }
}

// ---- 新建管理员账号：弹窗提交，调用 createRbacUser ----
// 此入口已迁移到"创建角色"合并弹窗内的 Tab "新建管理员账号"。
// 保留 openCreateUserDialog 函数供其他地方复用，默认行为：打开合并弹窗并切到 user tab。
const createDialogMode = ref('role') // 'role' | 'user'

function openCreateUserDialog() {
  // 不再独立打开弹窗：统一进入合并弹窗并切换到"新建管理员账号"Tab
  createDialogMode.value = 'user'
  newUser.username = ''
  newUser.name = ''
  newUser.email = ''
  newUser.password = ''
  // 默认绑定当前选中的角色（如果有）；避免用户每次都要重新选择
  newUser.roleCode = editTarget.value ? editTarget.value.code : ''
  createDialogVisible.value = true
  nextTick(() => {
    const input = document.querySelector('.el-dialog__body .rbac-form-input')
    if (input) input.focus()
  })
}

function onCreateDialogClosed() {
  // 关闭后重置 Tab 状态，避免下次打开时还是停留在 user
  createDialogMode.value = 'role'
}

// 判断角色是否超级管理员（code === 'SUPER_ADMIN'）
// 用于：隐藏删除按钮、显示"不可删除"徽章
function isSuperAdminRole(role) {
  return role && (role.code === 'SUPER_ADMIN' || role.id === 1)
}

async function handleCreateUserSubmit() {
  if (creatingUser.value) return
  // 基础校验
  const username = (newUser.username || '').trim()
  const name = (newUser.name || '').trim()
  const email = (newUser.email || '').trim()
  const password = (newUser.password || '').trim()
  if (!username) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!name) {
    ElMessage.warning('请输入姓名')
    return
  }
  // 简单邮箱格式校验，避免让后端兜底
  if (!/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(email)) {
    ElMessage.warning('邮箱格式不正确')
    return
  }
  if (password.length < 12) {
    ElMessage.warning('初始密码至少 12 位')
    return
  }
  // 角色 code：优先用表单上的 roleCode（独立弹窗场景），否则用当前选中角色
  let roleCode = (newUser.roleCode || '').trim()
  if (!roleCode && editTarget.value) roleCode = editTarget.value.code
  if (!roleCode) {
    ElMessage.warning('请选择角色')
    return
  }
  creatingUser.value = true
  try {
    // 后端 createUser 接收 Map<String, Object>；admin_user.role 字段必须为 RBAC 角色 code（如 SUPER_ADMIN）
    // 不能传 role.name（中文），否则后端会拒绝（无效的角色编码）
    await createRbacUser({
      username,
      name,
      email,
      password,
      role: roleCode,
      status: 'ACTIVE'
    })
    ElMessage.success(`管理员账号「${name}」创建成功`)
    // 关闭合并弹窗
    createDialogVisible.value = false
    // 如果是当前查看成员列表的弹窗场景，刷新成员列表
    if (editTarget.value) {
      await openMembersDialog(editTarget.value)
    }
    // 同步刷新左侧角色列表的 userCount
    await loadRoles()
  } catch (e) {
    const msg = (e && e.message) || ''
    ElMessage.error(msg.includes('创建管理员') || msg.includes('新建管理员') ? msg : '创建管理员账号失败')
  } finally {
    creatingUser.value = false
  }
}

// ---- 重置成员密码 ----
function openResetPwdDialog(member) {
  resetPwdTarget.value = member
  resetPwdNew.value = ''
  resetPwdDialogVisible.value = true
  nextTick(() => {
    const input = document.querySelectorAll('.el-dialog__body .rbac-form-input')[1]
    if (input) input.focus()
  })
}

async function handleResetPwdSubmit() {
  if (resettingPwd.value) return
  if (!resetPwdTarget.value) return
  const newPwd = (resetPwdNew.value || '').trim()
  if (newPwd.length < 12) {
    ElMessage.warning('新密码至少 12 位')
    return
  }
  resettingPwd.value = true
  resettingPwdUserId.value = resetPwdTarget.value.id
  try {
    await resetRbacUserPassword(resetPwdTarget.value.id, { password: newPwd })
    ElMessage.success(`已重置「${resetPwdTarget.value.name || resetPwdTarget.value.username}」的密码`)
    resetPwdDialogVisible.value = false
    resetPwdTarget.value = null
    resetPwdNew.value = ''
  } catch (e) {
    const msg = (e && e.message) || ''
    ElMessage.error(msg.includes('重置') ? msg : '重置密码失败')
  } finally {
    resettingPwd.value = false
    resettingPwdUserId.value = null
  }
}

// ---- 删除单个成员：二次确认 + 调后端接口 ----
async function handleDeleteMember(member) {
  if (deletingUserId.value) return
  if (!member || !member.id) return
  // 超级管理员角色（SUPER_ADMIN）：成员数 < 2 时禁止删除，避免出现"零超级管理员"无法恢复
  if (isLastSuperAdmin.value) {
    ElMessage.warning('超级管理员至少保留 1 个成员')
    return
  }
  const displayName = member.name || member.username
  try {
    await ElMessageBox.confirm(
      `确定删除管理员「${displayName}」吗？该账号将被永久移除，无法恢复。`,
      '删除人员确认',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'rbac-danger-confirm',
        dangerouslyUseHTMLString: false
      }
    )
  } catch (e) {
    // 用户取消
    return
  }
  deletingUserId.value = member.id
  try {
    await deleteRbacUser(member.id)
    ElMessage.success(`已删除「${displayName}」`)
    // 刷新当前角色成员列表与左侧角色 userCount
    if (editTarget.value) {
      await openMembersDialog(editTarget.value)
    }
    await loadRoles()
  } catch (e) {
    const msg = (e && e.message) || ''
    ElMessage.error(msg.includes('删除管理员') || msg.includes('无权删除') ? msg : '删除成员失败')
  } finally {
    deletingUserId.value = null
  }
}

// ---- 新建角色：打开弹窗 ----
function openCreateDialog() {
  // 重置表单
  newRole.name = ''
  newRole.description = ''
  createDialogVisible.value = true
  // 弹窗打开后自动聚焦到名称输入框
  nextTick(() => {
    const input = document.querySelector('.rbac-form-input')
    if (input) input.focus()
  })
}

function cancelCreate() {
  createDialogVisible.value = false
  newRole.name = ''
  newRole.description = ''
}

async function handleCreateSubmit() {
  if (creating.value) return
  const name = (newRole.name || '').trim()
  if (!name) {
    ElMessage.warning('请输入角色名称')
    return
  }
  creating.value = true
  try {
    const created = await createRbacRole({
      name,
      description: (newRole.description || '').trim() || '暂无描述'
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
    ElMessage.success(`角色「${name}」创建成功`)
    newRole.name = ''
    newRole.description = ''
    createDialogVisible.value = false
  } catch (e) {
    ElMessage.error('创建角色失败')
  } finally {
    creating.value = false
  }
}

// ---- 删除角色：二次确认防止误操作 ----
async function handleDeleteRole(role) {
  // 系统预设角色不允许删除（前端兜底 + 后端再校验）
  if (role.isPreset) {
    ElMessage.warning('系统预设角色不可删除')
    return
  }
  // 若该角色下还有用户，提示先解绑
  const userCount = role.userCount ?? 0
  let confirmText
  if (userCount > 0) {
    confirmText = `「${role.name}」当前已关联 ${userCount} 名管理员，删除后这些管理员将失去该角色及其所有权限。是否继续？`
  } else {
    confirmText = `确定删除角色「${role.name}」吗？删除后不可恢复。`
  }
  try {
    await ElMessageBox.confirm(confirmText, '删除角色确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'rbac-danger-confirm',
      dangerouslyUseHTMLString: false
    })
  } catch (e) {
    // 用户取消
    return
  }
  deletingId.value = role.id
  try {
    await deleteRbacRole(role.id)
    // 从本地列表移除
    const idx = roles.value.findIndex(r => r.id === role.id)
    if (idx > -1) {
      roles.value.splice(idx, 1)
    }
    // 若被删除的是当前选中角色，清空右侧面板
    if (selectedRole.value && selectedRole.value.id === role.id) {
      selectedRole.value = null
      currentPermKeys.value = []
    }
    ElMessage.success(`角色「${role.name}」删除成功`)
  } catch (e) {
    // 后端在校验失败时会返回"删除角色失败: xxx"，提取 msg 给用户
    const msg = (e && e.message) || ''
    ElMessage.error(msg.includes('删除角色失败') ? msg : '删除角色失败')
  } finally {
    deletingId.value = null
  }
}

onMounted(() => {
  loadRoles()
})
</script>

<style scoped>
/* ---- 页面容器 ---- */
.rbac-page {
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

/* 超级管理员"不可删除"徽章已移除 */

/* "创建角色"弹窗的 Tab 样式 */
.rbac-create-tabs {
  margin: -10px 0 12px;
}
.rbac-create-tabs :deep(.el-tabs__header) {
  margin-bottom: 4px;
}
.rbac-form-select {
  appearance: none;
  -webkit-appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%238e8e93' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 10px center;
  padding-right: 28px;
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

/* ---- 危险按钮（删除） ---- */
.rbac-btn-danger {
  background: #fee2e2;
  color: #dc2626;
}

.rbac-btn-danger:hover {
  background: #fecaca;
}

.rbac-btn-danger:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  background: #fee2e2;
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

/* ---- 新建角色弹窗 ---- */
.rbac-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  width: 100%;
  box-sizing: border-box;
}

/* 兜底：确保 el-dialog 内部的 footer 插槽始终可见、不会被裁剪或被 el-tabs 撑开的 body 顶出可视区 */
:deep(.el-dialog .el-dialog__footer) {
  padding: 16px 20px;
  border-top: 1px solid var(--border);
  background: var(--background-50);
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}

/* ---- 表单（弹窗内部） ---- */
.rbac-form {
  padding: 4px 2px 0;
}

.rbac-form-row {
  margin-bottom: 16px;
}

.rbac-form-row:last-child {
  margin-bottom: 0;
}

.rbac-form-group {
  width: 100%;
}

.rbac-form-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-700);
  margin-bottom: 8px;
}

.rbac-required {
  color: #ef4444;
  margin-left: 2px;
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
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--ring) 25%, transparent);
}

.rbac-form-input::placeholder {
  color: var(--text-400);
}

.rbac-form-input:disabled {
  background: var(--background-100);
  color: var(--text-400);
  cursor: not-allowed;
}

.rbac-preset-hint {
  display: inline-block;
  font-size: 11px;
  font-weight: 500;
  color: var(--brand-600);
  background: var(--brand-50);
  border-radius: 999px;
  padding: 1px 8px;
  margin-left: 8px;
}

.rbac-form-help {
  font-size: 12px;
  color: var(--text-400);
  margin: 6px 0 0;
  line-height: 1.5;
}

/* ---- 查看成员弹窗 ---- */
.rbac-members-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px;
  padding: 10px 14px;
  border-radius: calc(var(--radius) * 0.6);
  background: var(--brand-50);
  color: var(--brand-700);
  font-size: 13px;
  line-height: 1.5;
  border: 1px solid var(--brand-200, transparent);
}
.rbac-members-notice svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: var(--brand-500);
}
.rbac-members-loading,
.rbac-members-empty {
  padding: 32px 0;
  text-align: center;
  color: var(--text-400);
  font-size: 13px;
}

.rbac-members-empty svg {
  width: 48px;
  height: 48px;
  opacity: 0.35;
  margin-bottom: 8px;
  color: var(--text-400);
}

.rbac-members-empty p {
  margin: 0;
}

.rbac-members-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 360px;
  overflow-y: auto;
  padding: 2px;
}

.rbac-member-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--background);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.rbac-member-item > .rbac-btn-sm {
  flex-shrink: 0;
}

.rbac-member-item > .rbac-member-actions {
  margin-left: auto;
  display: inline-flex;
  gap: 8px;
  flex-shrink: 0;
}

.rbac-member-item:hover {
  border-color: var(--brand-200);
  box-shadow: var(--shadow-xs);
}

.rbac-member-avatar {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--brand-500), var(--primary));
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.rbac-member-info {
  flex: 1;
  min-width: 0;
}

.rbac-member-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-800);
  display: flex;
  align-items: center;
  gap: 8px;
}

.rbac-member-status {
  display: inline-block;
  font-size: 11px;
  font-weight: 500;
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--background-200);
  color: var(--text-500);
}

.rbac-member-status.status-active {
  background: #dcfce7;
  color: #15803d;
}

.rbac-member-status.status-disabled {
  background: var(--background-200);
  color: var(--text-400);
}

.rbac-member-meta {
  font-size: 12px;
  color: var(--text-400);
  margin-top: 4px;
}
</style>
