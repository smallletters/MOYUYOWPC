<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-title-area">
      <h1>3D 空间模型</h1>
      <p>管理 APP 宠物 3D 空间的房间场景、角色等模型；支持模型 3D 预览、用途自定义与版本启用切换。</p>
    </div>

    <!-- KPI 概览 -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">🧊</span>
          <span class="kpi-card-label">模型总数</span>
        </div>
        <div class="kpi-card-value">{{ allModels.length }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-text">含历史版本</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">✅</span>
          <span class="kpi-card-label">启用中</span>
        </div>
        <div class="kpi-card-value">{{ enabledCount }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-up">APP 当前使用</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">🏷️</span>
          <span class="kpi-card-label">用途总数</span>
        </div>
        <div class="kpi-card-value">{{ purposes.length }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-text">自定义 + 内置</span></div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card-header">
          <span class="kpi-card-icon">🟢</span>
          <span class="kpi-card-label">启用中用途</span>
        </div>
        <div class="kpi-card-value">{{ enabledPurposeCount }}</div>
        <div class="kpi-card-trend"><span class="kpi-trend-up">可用于新增模型</span></div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <div class="action-bar-left">
        <span class="list-title">模型列表</span>
        <el-select v-model="filterKey" clearable placeholder="全部用途" class="filter-select" @change="currentPage = 1">
          <el-option v-for="p in purposes" :key="p.purposeKey" :label="p.purposeName" :value="p.purposeKey" />
        </el-select>
        <el-select v-model="filterStatus" clearable placeholder="全部状态" class="filter-select" @change="currentPage = 1">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </div>
      <div class="action-bar-right">
        <button class="btn btn-outline" @click="loadData">刷新</button>
        <button class="btn btn-outline" @click="handleManagePurposes">用途管理</button>
        <button class="btn btn-primary" @click="handleAdd">＋ 新增模型</button>
      </div>
    </div>

    <!-- 模型表格 -->
    <div class="data-table-wrapper" v-loading="loading">
      <table class="data-table">
        <thead>
          <tr>
            <th>用途</th>
            <th>模型名称</th>
            <th>文件</th>
            <th>版本</th>
            <th>状态</th>
            <th>更新时间</th>
            <th style="min-width: 240px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in pagedData" :key="row.id">
            <td>
              <span :class="keyTagClass(row.modelKey)">{{ keyLabel(row.modelKey) }}</span>
            </td>
            <td>
              <div class="name-cell">{{ row.displayName }}</div>
              <div v-if="row.remark" class="sub-cell" :title="row.remark">{{ row.remark }}</div>
            </td>
            <td>
              <div class="file-cell" :title="row.fileUrl">{{ fileNameOf(row.fileUrl) }}</div>
              <div class="sub-cell">{{ row.fileFormat }} · {{ formatSize(row.fileSize) }}</div>
            </td>
            <td>{{ row.version || '-' }}</td>
            <td>
              <span :class="row.status === 'ENABLED' ? 'tag tag-green' : 'tag tag-gray'">
                {{ row.status === 'ENABLED' ? '启用' : '停用' }}
              </span>
            </td>
            <td class="time-cell">{{ formatTime(row.updateTime) }}</td>
            <td>
              <div class="cell-actions">
                <button class="btn btn-sm btn-outline" @click="handleView(row)">查看</button>
                <button
                  v-if="row.status !== 'ENABLED'"
                  class="btn btn-sm btn-primary"
                  @click="handleEnable(row)"
                >启用</button>
                <button class="btn btn-sm btn-outline" @click="handleEdit(row)">编辑</button>
                <button
                  class="btn btn-sm btn-danger"
                  :disabled="row.status === 'ENABLED'"
                  @click="handleDelete(row)"
                >删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="pagedData.length === 0">
            <td colspan="7">
              <div class="empty-state">
                <div class="empty-state-icon">🧊</div>
                <div class="empty-state-text">暂无模型记录，点击右上角「新增模型」创建</div>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 分页 -->
      <div class="pagination" v-if="filteredModels.length > pageSize">
        <span class="pagination-info">共 {{ filteredModels.length }} 条 · 第 {{ currentPage }} / {{ totalPages }} 页</span>
        <div class="pagination-btns">
          <button class="pagination-btn" :disabled="currentPage <= 1" @click="currentPage--">‹ 上一页</button>
          <button class="pagination-btn" :disabled="currentPage >= totalPages" @click="currentPage++">下一页 ›</button>
        </div>
      </div>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑模型' : '新增模型'" width="560px">
      <el-form :model="form" label-width="92px">
        <el-form-item label="模型用途" required>
          <el-select v-model="form.modelKey" :disabled="isEdit" style="width: 100%" @change="handleModelKeyChange">
            <el-option
              v-for="p in purposeSelectOptions"
              :key="p.purposeKey"
              :label="`${p.purposeName}（${p.purposeKey}）`"
              :value="p.purposeKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模型名称" required>
          <el-input v-model="form.displayName" maxlength="100" placeholder="如：默认房间场景" />
        </el-form-item>
        <el-form-item label="模型文件" required>
          <div class="upload-row">
            <el-input v-model="form.fileUrl" placeholder="上传后自动填充，也可直接填写 /uploads/... 或 http(s):// 地址" />
            <button class="btn btn-outline" :disabled="uploading" @click="triggerFileInput">
              {{ uploading ? `上传中 ${uploadPercent}%` : '上传文件' }}
            </button>
          </div>
          <input
            ref="fileInputRef"
            type="file"
            accept=".glb,.gltf,.fbx"
            style="display: none"
            @change="onFileInputChange"
          />
          <div class="upload-tip">推荐 GLB（单文件自包含）；支持 GLB / GLTF / FBX，单文件最大 200MB。</div>
          <div v-if="uploading" class="upload-progress">
            <div class="upload-progress-bar" :style="{ width: uploadPercent + '%' }" />
          </div>
        </el-form-item>
        <el-form-item label="文件格式">
          <el-radio-group v-model="form.fileFormat">
            <el-radio v-for="fmt in formFormatOptions" :key="fmt" :value="fmt">{{ fmt }}</el-radio>
          </el-radio-group>
          <div class="format-tip">
            {{ formFormatOptions.length ? `当前用途支持 ${formFormatOptions.join(' / ')}` : '请先选择模型用途' }}
          </div>
        </el-form-item>
        <el-form-item label="版本标识">
          <el-input v-model="form.version" maxlength="32" placeholder="如 v1 / 20261104" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" placeholder="可选，便于运营区分版本" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 用途管理弹窗 -->
    <el-dialog v-model="purposeDialogVisible" title="模型用途管理" width="760px" append-to-body>
      <div class="purpose-toolbar">
        <span class="purpose-tip">
          用途标识是 APP 端读取模型的键，创建后不可修改；内置用途
          「SCENE / AVATAR_IDLE / AVATAR_WALK」请勿删除。自定义用途可用于扩展分类，
          APP 需适配后才会读取新增用途。
        </span>
        <button class="btn btn-sm btn-primary" @click="handlePurposeAdd">＋ 新增用途</button>
      </div>
      <div class="data-table-wrapper">
        <table class="data-table">
          <thead>
            <tr>
              <th>用途名称</th>
              <th>用途标识</th>
              <th>支持格式</th>
              <th>排序</th>
              <th>状态</th>
              <th style="min-width: 140px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in purposes" :key="p.id">
              <td>
                <div class="name-cell">{{ p.purposeName }}</div>
                <div v-if="p.description" class="sub-cell" :title="p.description">{{ p.description }}</div>
              </td>
              <td><span class="purpose-key">{{ p.purposeKey }}</span></td>
              <td>{{ formatFormats(p.allowedFormats) }}</td>
              <td>{{ p.sortOrder ?? 0 }}</td>
              <td>
                <span :class="p.enabled ? 'tag tag-green' : 'tag tag-gray'">
                  {{ p.enabled ? '启用' : '停用' }}
                </span>
              </td>
              <td>
                <div class="cell-actions">
                  <button class="btn btn-sm btn-outline" @click="handlePurposeEdit(p)">编辑</button>
                  <button class="btn btn-sm btn-danger" @click="handlePurposeDelete(p)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="purposes.length === 0">
              <td colspan="6">
                <div class="empty-state">
                  <div class="empty-state-icon">🏷️</div>
                  <div class="empty-state-text">暂无用途，点击「新增用途」创建</div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </el-dialog>

    <!-- 新增 / 编辑用途弹窗 -->
    <el-dialog
      v-model="purposeFormVisible"
      :title="purposeIsEdit ? '编辑用途' : '新增用途'"
      width="520px"
      append-to-body
    >
      <el-form :model="purposeForm" label-width="92px">
        <el-form-item label="用途标识" required>
          <el-input
            v-model="purposeForm.purposeKey"
            :disabled="purposeIsEdit"
            maxlength="32"
            placeholder="如 CUSTOM_ITEM（大写字母/数字/下划线）"
          />
        </el-form-item>
        <el-form-item label="用途名称" required>
          <el-input v-model="purposeForm.purposeName" maxlength="64" placeholder="如 家具模型" />
        </el-form-item>
        <el-form-item label="支持格式" required>
          <el-checkbox-group v-model="purposeForm.allowedFormats">
            <el-checkbox value="GLB">GLB</el-checkbox>
            <el-checkbox value="GLTF">GLTF</el-checkbox>
            <el-checkbox value="FBX">FBX</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="purposeForm.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="purposeForm.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="用途说明">
          <el-input v-model="purposeForm.description" type="textarea" :rows="3" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="purposeFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="purposeSaving" @click="handlePurposeSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 3D 模型预览 -->
    <ModelPreviewDialog
      v-model:visible="previewVisible"
      :model="previewModel"
      :purpose-name="previewPurposeName"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPetSpaceModels,
  getPetSpaceModelPurposes,
  createPetSpaceModel,
  updatePetSpaceModel,
  enablePetSpaceModel,
  deletePetSpaceModel,
  createPetSpaceModelPurpose,
  updatePetSpaceModelPurpose,
  deletePetSpaceModelPurpose,
  uploadPetSpaceModelFile
} from '../api/admin'
import { toArray } from '../utils/safeArray'
import ModelPreviewDialog from './PetSpaceModel/ModelPreviewDialog.vue'

// 允许上传的模型扩展名（与后端 /upload/model 白名单的主模型部分保持一致）
const ALLOWED_MODEL_EXT_RE = /\.(glb|gltf|fbx)$/i
const MAX_MODEL_SIZE = 200 * 1024 * 1024

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const uploadPercent = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const fileInputRef = ref(null)

const allModels = ref([])
const purposes = ref([])
const filterKey = ref('')
const filterStatus = ref('')

// 用途管理状态
const purposeDialogVisible = ref(false)
const purposeFormVisible = ref(false)
const purposeSaving = ref(false)
const purposeIsEdit = ref(false)
const purposeEditId = ref(null)
const purposeForm = reactive({
  purposeKey: '',
  purposeName: '',
  allowedFormats: ['GLB'],
  description: '',
  sortOrder: 0,
  enabled: true
})

// 3D 预览状态
const previewVisible = ref(false)
const previewModel = ref(null)
const previewPurposeName = ref('-')

// 客户端分页（模型数量少，一次拉全量更方便做 KPI 统计）
const pageSize = 10
const currentPage = ref(1)

const form = reactive({
  modelKey: 'SCENE',
  displayName: '',
  fileUrl: '',
  fileFormat: 'GLB',
  fileSize: 0,
  version: '',
  remark: ''
})

const filteredModels = computed(() => {
  return allModels.value.filter((row) => {
    if (filterKey.value && row.modelKey !== filterKey.value) return false
    if (filterStatus.value && row.status !== filterStatus.value) return false
    return true
  })
})

const pagedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredModels.value.slice(start, start + pageSize)
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredModels.value.length / pageSize)))

const enabledCount = computed(() => allModels.value.filter((row) => row.status === 'ENABLED').length)
const enabledPurposeCount = computed(() => purposes.value.filter((p) => p.enabled).length)

// 新增模型时只能选择启用用途；编辑历史模型时保留当前用途（即使已停用）
const purposeSelectOptions = computed(() => {
  const enabled = purposes.value.filter((p) => p.enabled)
  if (!isEdit.value || !form.modelKey) return enabled
  if (enabled.some((p) => p.purposeKey === form.modelKey)) return enabled
  const current = purposes.value.find((p) => p.purposeKey === form.modelKey)
  return current ? [...enabled, current] : enabled
})

// 当前表单用途约束的允许格式
const formFormatOptions = computed(() => {
  const purpose = purposes.value.find((p) => p.purposeKey === form.modelKey)
  return parseFormats(purpose?.allowedFormats)
})

/** 把后端逗号分隔的格式串解析成数组 */
function parseFormats(raw) {
  if (!raw) return []
  return String(raw)
    .split(',')
    .map((v) => v.trim().toUpperCase())
    .filter(Boolean)
}

function formatFormats(raw) {
  const list = parseFormats(raw)
  return list.length ? list.join(' / ') : '-'
}

function keyLabel(key) {
  const purpose = purposes.value.find((p) => p.purposeKey === key)
  return purpose ? purpose.purposeName : (key || '-')
}

function keyTagClass(key) {
  const index = purposes.value.findIndex((p) => p.purposeKey === key)
  const classes = ['tag tag-blue', 'tag tag-green', 'tag tag-gray']
  return classes[Math.max(0, index) % classes.length]
}

function fileNameOf(url) {
  if (!url) return '-'
  const last = String(url).split('?')[0].split('/').filter(Boolean).pop()
  return last || url
}

function formatSize(bytes) {
  const n = Number(bytes) || 0
  if (n <= 0) return '-'
  if (n < 1024 * 1024) return (n / 1024).toFixed(0) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}

function formatTime(value) {
  if (!value) return '-'
  // 后端 LocalDateTime 序列化为 ISO 字符串，直接替换 T 便于阅读
  return String(value).replace('T', ' ').slice(0, 19)
}

function resetForm() {
  const firstPurpose = purposes.value.find((p) => p.enabled)
  const formats = parseFormats(firstPurpose?.allowedFormats)
  form.modelKey = firstPurpose?.purposeKey || ''
  form.displayName = ''
  form.fileUrl = ''
  form.fileFormat = formats[0] || 'GLB'
  form.fileSize = 0
  form.version = ''
  form.remark = ''
  uploadPercent.value = 0
}

async function loadData() {
  loading.value = true
  try {
    const res = await getPetSpaceModels()
    allModels.value = toArray(res)
    // 删除 / 筛选后当前页可能越界，回退到有效页
    const pages = Math.max(1, Math.ceil(filteredModels.value.length / pageSize))
    if (currentPage.value > pages) currentPage.value = pages
  } catch (e) {
    allModels.value = []
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  isEdit.value = false
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  editId.value = row.id
  form.modelKey = row.modelKey
  form.displayName = row.displayName || ''
  form.fileUrl = row.fileUrl || ''
  form.fileFormat = row.fileFormat || 'GLB'
  form.fileSize = row.fileSize || 0
  form.version = row.version || ''
  form.remark = row.remark || ''
  uploadPercent.value = 0
  dialogVisible.value = true
}

function triggerFileInput() {
  if (uploading.value) return
  fileInputRef.value?.click()
}

/** 切换用途时取该用途支持的第一个格式 */
function handleModelKeyChange(key) {
  const purpose = purposes.value.find((p) => p.purposeKey === key)
  const formats = parseFormats(purpose?.allowedFormats)
  form.fileFormat = formats[0] || ''
}

function onFileInputChange(e) {
  const file = (e.target.files || [])[0]
  // 清空 input 值，保证重复选择同一文件也能再次触发 change
  e.target.value = ''
  if (!file) return
  if (!ALLOWED_MODEL_EXT_RE.test(file.name)) {
    ElMessage.warning('仅支持 GLB / GLTF / FBX 模型文件')
    return
  }
  const ext = (/\.([a-z0-9]+)$/i.exec(file.name)?.[1] || '').toUpperCase()
  if (!formFormatOptions.value.includes(ext)) {
    ElMessage.warning(
      formFormatOptions.value.length
        ? `当前用途仅支持 ${formFormatOptions.value.join(' / ')} 文件`
        : '请先选择模型用途'
    )
    return
  }
  if (file.size > MAX_MODEL_SIZE) {
    ElMessage.warning('模型文件不能超过 200MB')
    return
  }
  doUpload(file)
}

async function doUpload(file) {
  uploading.value = true
  uploadPercent.value = 0
  try {
    const res = await uploadPetSpaceModelFile(file, (event) => {
      if (event.total) {
        uploadPercent.value = Math.round((event.loaded / event.total) * 100)
      }
    })
    form.fileUrl = res?.url || ''
    form.fileSize = Number(res?.size || file.size)
    const ext = /\.([a-z0-9]+)$/i.exec(res?.filename || file.name)?.[1]
    if (ext && formFormatOptions.value.includes(ext.toUpperCase())) {
      form.fileFormat = ext.toUpperCase()
    }
    ElMessage.success('模型文件上传成功')
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    uploading.value = false
  }
}

async function handleSave() {
  if (!form.modelKey) {
    ElMessage.warning('请选择模型用途')
    return
  }
  if (!form.displayName.trim()) {
    ElMessage.warning('请填写模型名称')
    return
  }
  if (!form.fileUrl.trim()) {
    ElMessage.warning('请上传模型文件或填写文件地址')
    return
  }
  if (!formFormatOptions.value.includes(form.fileFormat)) {
    ElMessage.warning(
      formFormatOptions.value.length
        ? `当前用途仅支持 ${formFormatOptions.value.join(' / ')} 格式`
        : '请先选择模型用途'
    )
    return
  }
  const payload = {
    id: isEdit.value ? editId.value : undefined,
    modelKey: form.modelKey,
    displayName: form.displayName.trim(),
    fileUrl: form.fileUrl.trim(),
    fileFormat: form.fileFormat,
    fileSize: form.fileSize || 0,
    version: form.version.trim() || null,
    remark: form.remark.trim() || null
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updatePetSpaceModel(payload)
      ElMessage.success('模型已更新')
    } else {
      await createPetSpaceModel(payload)
      ElMessage.success('模型已创建，默认停用；请在列表中点击「启用」')
    }
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    saving.value = false
  }
}

async function handleEnable(row) {
  try {
    await ElMessageBox.confirm(
      `启用「${row.displayName}」后，同用途下的其它版本会自动停用，APP 端下次进入 3D 空间即可生效。`,
      '启用模型',
      { type: 'warning' }
    )
    await enablePetSpaceModel(row.id)
    ElMessage.success('模型已启用')
    await loadData()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      // 错误提示由 axios 拦截器统一处理
    }
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除模型「${row.displayName}」吗？`, '删除模型')
    await deletePetSpaceModel(row.id)
    ElMessage.success('模型已删除')
    await loadData()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      // 错误提示由 axios 拦截器统一处理
    }
  }
}

async function loadPurposes() {
  try {
    purposes.value = toArray(await getPetSpaceModelPurposes())
  } catch (e) {
    purposes.value = []
  }
}

/** 打开模型 3D 预览 */
function handleView(row) {
  previewModel.value = row
  previewPurposeName.value = keyLabel(row.modelKey)
  previewVisible.value = true
}

// ==================== 用途管理 ====================

function handleManagePurposes() {
  purposeDialogVisible.value = true
}

function handlePurposeAdd() {
  purposeIsEdit.value = false
  purposeEditId.value = null
  purposeForm.purposeKey = ''
  purposeForm.purposeName = ''
  purposeForm.allowedFormats = ['GLB']
  purposeForm.description = ''
  purposeForm.sortOrder = purposes.value.length ? Math.max(...purposes.value.map((p) => Number(p.sortOrder) || 0)) + 10 : 10
  purposeForm.enabled = true
  purposeFormVisible.value = true
}

function handlePurposeEdit(purpose) {
  purposeIsEdit.value = true
  purposeEditId.value = purpose.id
  purposeForm.purposeKey = purpose.purposeKey
  purposeForm.purposeName = purpose.purposeName || ''
  purposeForm.allowedFormats = parseFormats(purpose.allowedFormats)
  purposeForm.description = purpose.description || ''
  purposeForm.sortOrder = Number(purpose.sortOrder) || 0
  purposeForm.enabled = purpose.enabled !== false
  purposeFormVisible.value = true
}

async function handlePurposeSave() {
  const key = purposeForm.purposeKey.trim().toUpperCase()
  if (!/^[A-Z][A-Z0-9_]{1,31}$/.test(key)) {
    ElMessage.warning('用途标识需以大写字母开头，仅允许大写字母/数字/下划线，长度 2~32')
    return
  }
  if (!purposeForm.purposeName.trim()) {
    ElMessage.warning('请填写用途名称')
    return
  }
  if (!purposeForm.allowedFormats.length) {
    ElMessage.warning('请至少选择一种支持格式')
    return
  }
  const payload = {
    id: purposeIsEdit.value ? purposeEditId.value : undefined,
    purposeKey: key,
    purposeName: purposeForm.purposeName.trim(),
    allowedFormats: purposeForm.allowedFormats.join(','),
    description: purposeForm.description.trim() || null,
    sortOrder: Number(purposeForm.sortOrder) || 0,
    enabled: !!purposeForm.enabled
  }
  purposeSaving.value = true
  try {
    if (purposeIsEdit.value) {
      await updatePetSpaceModelPurpose(payload)
      ElMessage.success('用途已更新')
    } else {
      await createPetSpaceModelPurpose(payload)
      ElMessage.success('用途已创建')
    }
    purposeFormVisible.value = false
    await loadPurposes()
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    purposeSaving.value = false
  }
}

async function handlePurposeDelete(purpose) {
  try {
    await ElMessageBox.confirm(
      `确定删除用途「${purpose.purposeName}」吗？内置用途或已有模型的用途不能删除。`,
      '删除用途',
      { type: 'warning' }
    )
    await deletePetSpaceModelPurpose(purpose.id)
    ElMessage.success('用途已删除')
    await loadPurposes()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      // 错误提示由 axios 拦截器统一处理
    }
  }
}

onMounted(async () => {
  await loadPurposes()
  await loadData()
})
</script>

<style scoped>
.action-bar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-select {
  width: 150px;
}

.name-cell {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-700);
}

.file-cell {
  font-size: 12px;
  color: var(--text-600);
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub-cell {
  font-size: 11px;
  color: var(--text-400);
  margin-top: 2px;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time-cell {
  font-size: 12px;
  color: var(--text-500);
  font-variant-numeric: tabular-nums;
}

.upload-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.upload-row .btn {
  flex: none;
  white-space: nowrap;
}

.upload-tip {
  font-size: 12px;
  color: var(--text-400);
  line-height: 1.6;
  margin-top: 4px;
}

.format-tip {
  font-size: 12px;
  color: var(--text-400);
  line-height: 1.6;
}

.upload-progress {
  width: 100%;
  height: 4px;
  margin-top: 6px;
  background: var(--background-300, #e5e5ea);
  border-radius: 2px;
  overflow: hidden;
}

.upload-progress-bar {
  height: 100%;
  background: var(--brand-500, #007aff);
  transition: width 0.2s;
}

.purpose-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.purpose-tip {
  font-size: 12px;
  color: var(--text-400);
  line-height: 1.6;
}

.purpose-key {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-700);
}
</style>
