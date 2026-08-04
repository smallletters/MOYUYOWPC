<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>风控规则引擎</h2>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">新建规则</el-button>
      </div>
    </div>
    <!-- 工作流展示 -->
    <el-row :gutter="16" class="workflow-row">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span>① 数据采集</span></template>
          <div class="engine-desc">实时采集用户行为、交易数据、设备指纹等信息</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span>② 规则匹配</span></template>
          <div class="engine-desc">加载当前启用的规则列表，按优先级依次匹配</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span>③ 风险处置</span></template>
          <div class="engine-desc">根据匹配结果执行阻断/审核/预警/放行等动作</div>
        </el-card>
      </el-col>
    </el-row>
    <!-- 规则优先级列表 -->
    <el-card shadow="never" style="margin-top:16px">
      <template #header><span>规则优先级配置</span></template>
      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="ruleName" label="规则名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="90">
          <template #default="{ row }">
            <el-tag :type="row.priority <= 1 ? 'danger' : row.priority <= 3 ? 'warning' : 'info'" size="small">P{{ row.priority }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="conditionJson" label="条件" min-width="200" show-overflow-tooltip />
        <el-table-column label="动作" width="120">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled !== false ? 'success' : 'info'" size="small">{{ row.enabled !== false ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link size="small" @click="handleToggle(row)">{{ row.enabled !== false ? '禁用' : '启用' }}</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑规则对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑规则' : '新建规则'"
      width="560px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form :model="formData" label-width="80px" label-position="top">
        <el-form-item label="规则名称" required>
          <el-input v-model="formData.ruleName" placeholder="输入规则名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="formData.priority" :min="1" :max="10" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="处置动作">
              <el-select v-model="formData.action" style="width:100%">
                <el-option v-for="act in actionOptions" :key="act.value" :label="act.label" :value="act.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="匹配条件" required>
          <el-input
            v-model="formData.conditionJson"
            type="textarea"
            :rows="3"
            placeholder="输入匹配条件，如：单日下单次数 > 10 AND 设备指纹异常"
          />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch
            v-model="formData.enabled"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSave">
          {{ isEditing ? '保存修改' : '创建规则' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRiskRules, createRiskRule, updateRiskRule, toggleRiskRule, deleteRiskRule } from '../api/admin'
import { toArray } from '../utils/safeArray'

const tableData = ref([])

// ---- 对话框状态 ----
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref(null)
const submitting = ref(false)

// ---- 表单数据 ----
const formData = reactive({
  ruleName: '',
  priority: 1,
  conditionJson: '',
  action: 'REVIEW',
  enabled: true,
})

// ---- 处置动作选项（与后端枚举一致）----
const actionOptions = [
  { value: 'BLOCK', label: '阻断' },
  { value: 'REVIEW', label: '审核' },
  { value: 'VERIFY', label: '预警' },
  { value: 'LOG', label: '放行' },
]

// 动作枚举转中文标签
function actionLabel(action) {
  const hit = actionOptions.find(a => a.value === action)
  return hit ? hit.label : action
}

// 加载风控规则列表
async function loadData() {
  try {
    const res = await getRiskRules()
    tableData.value = toArray(res)
  } catch (e) {
    ElMessage.error('获取规则列表失败')
  }
}

// 新建规则：打开空白表单
function handleAdd() {
  isEditing.value = false
  editingId.value = null
  formData.ruleName = ''
  formData.priority = 1
  formData.conditionJson = ''
  formData.action = 'REVIEW'
  formData.enabled = true
  dialogVisible.value = true
}

// 编辑规则：填充表单
function handleEdit(row) {
  isEditing.value = true
  editingId.value = row.id
  formData.ruleName = row.ruleName || ''
  formData.priority = row.priority ?? 1
  formData.conditionJson = row.conditionJson || ''
  formData.action = row.action || 'REVIEW'
  formData.enabled = row.enabled !== false
  dialogVisible.value = true
}

// 保存规则（新建或编辑）
async function handleSave() {
  if (!formData.ruleName.trim()) {
    ElMessage.warning('请输入规则名称')
    return
  }
  if (!formData.conditionJson.trim()) {
    ElMessage.warning('请输入匹配条件')
    return
  }
  submitting.value = true
  // 组装与后端实体一致的字段
  const payload = {
    ruleName: formData.ruleName.trim(),
    priority: formData.priority,
    conditionJson: formData.conditionJson,
    action: formData.action,
    enabled: formData.enabled,
    ruleType: 'LOGIN',
  }
  try {
    if (isEditing.value) {
      await updateRiskRule(editingId.value, payload)
      ElMessage.success('规则已更新')
    } else {
      await createRiskRule(payload)
      ElMessage.success('规则已创建')
    }
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

// 启用/禁用规则
async function handleToggle(row) {
  try {
    const newEnabled = row.enabled === false
    await toggleRiskRule(row.id, { enabled: newEnabled })
    ElMessage.success('规则已' + (newEnabled ? '启用' : '禁用'))
    await loadData()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

// 删除规则
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除规则「' + row.name + '」吗？', '提示', { type: 'warning' })
    await deleteRiskRule(row.id)
    ElMessage.success('规则已删除')
    await loadData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.header-actions { display: flex; gap: 8px; }
.workflow-row { margin-bottom: 0; }
.engine-desc { font-size: 13px; color: var(--text-500); line-height: 1.6; }
</style>
