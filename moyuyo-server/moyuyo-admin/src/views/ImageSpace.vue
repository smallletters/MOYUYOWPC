<template>
  <div class="page-wrapper">
    <div class="page-header">
      <div>
        <h2>图片空间</h2>
        <p class="page-desc">统一管理商品图片的上传、删除与分组；分组后可快速筛选，便于商品图 / 详情图取材。</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :loading="uploading" @click="triggerFileInput">上传图片</el-button>
      </div>
    </div>

    <!-- 概览统计 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="8">
        <el-card shadow="never">
          <div class="stat-item">
            <span class="stat-label">图片总数</span>
            <span class="stat-value">{{ allTotal }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="stat-item">
            <span class="stat-label">当前范围占用</span>
            <span class="stat-value">{{ formatSize(totalSize) }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="stat-item">
            <span class="stat-label">本页图片</span>
            <span class="stat-value">{{ images.length }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 上传区：点击 / 拖拽 -->
    <div
      class="upload-zone"
      :class="{ 'is-over': dragOver, 'is-uploading': uploading }"
      @click="triggerFileInput"
      @dragenter.prevent="dragOver = true"
      @dragover.prevent="dragOver = true"
      @dragleave.prevent="dragOver = false"
      @drop.prevent="onDrop"
    >
      <span class="upload-icon">+</span>
      <div class="upload-text">
        <strong>{{ uploading ? '正在上传...' : '点击或拖拽图片到此处上传' }}</strong>
        <span>
          支持 PNG / JPG / JPEG / GIF / WebP / SVG，单张不超过 20MB，可多选
          <template v-if="groupId > 0">；当前会归入「{{ currentGroupName }}」</template>
        </span>
      </div>
    </div>

    <!-- 隐藏文件选择器 -->
    <input
      ref="fileInputRef"
      type="file"
      multiple
      :accept="acceptTypes"
      style="display: none"
      @change="onFileInputChange"
    />

    <div class="space-layout">
      <!-- 左侧：分组筛选 -->
      <aside class="group-panel">
        <div class="group-panel-header">
          <span class="group-panel-title">图片分组</span>
          <button type="button" class="group-add" @click="handleCreateGroup">+ 新建</button>
        </div>
        <div class="group-list">
          <div
            class="group-item"
            :class="{ active: groupId === -1 }"
            @click="selectGroup(-1)"
          >
            <span class="group-name">全部图片</span>
            <span class="group-count">{{ allTotal }}</span>
          </div>
          <div
            class="group-item"
            :class="{ active: groupId === 0 }"
            @click="selectGroup(0)"
          >
            <span class="group-name">未分组</span>
            <span class="group-count">{{ ungroupedCount }}</span>
          </div>

          <div
            v-for="g in groups"
            :key="g.id"
            class="group-item"
            :class="{ active: groupId === g.id }"
            @click="selectGroup(g.id)"
          >
            <span class="group-name" :title="g.name">{{ g.name }}</span>
            <span class="group-count">{{ g.imageCount }}</span>
            <span class="group-ops" @click.stop>
              <button type="button" title="重命名分组" @click="handleRenameGroup(g)">✎</button>
              <button type="button" title="删除分组" @click="handleDeleteGroup(g)">✕</button>
            </span>
          </div>

          <el-empty
            v-if="!groups.length"
            description="暂无分组"
            :image-size="48"
            class="group-empty"
          />
        </div>
      </aside>

      <!-- 右侧：图片列表 -->
      <div class="space-main">
        <el-card shadow="never" v-loading="loading" class="grid-card">
          <template #header>
            <div class="grid-toolbar">
              <div class="toolbar-left">
                <el-checkbox
                  :model-value="allSelected"
                  :indeterminate="indeterminate"
                  @change="toggleSelectAll"
                >
                  全选本页
                </el-checkbox>
                <span v-if="selected.length" class="selected-tip">已选 {{ selected.length }} 张</span>
              </div>
              <div class="toolbar-right">
                <el-dropdown
                  trigger="click"
                  :disabled="!selected.length"
                  @command="handleMoveToGroup"
                >
                  <el-button size="small" plain :disabled="!selected.length" :loading="moving">
                    移动到分组 ▾
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="0">未分组</el-dropdown-item>
                      <el-dropdown-item v-for="g in groups" :key="g.id" :command="String(g.id)">
                        {{ g.name }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-button size="small" plain @click="loadData">刷新</el-button>
                <el-button
                  size="small"
                  type="danger"
                  plain
                  :disabled="!selected.length"
                  :loading="batchDeleting"
                  @click="handleBatchDelete"
                >
                  批量删除
                </el-button>
              </div>
            </div>
          </template>

          <div v-if="images.length" class="image-grid">
            <div
              v-for="img in images"
              :key="img.url"
              class="image-card"
              :class="{ 'is-selected': isSelected(img.url) }"
            >
              <div class="image-thumb" @click="openPreview(img)">
                <img :src="img.url" alt="已上传图片" loading="lazy" @error="onImgError" />
                <el-checkbox
                  class="image-check"
                  :model-value="isSelected(img.url)"
                  @change="value => toggleSelect(img.url, value)"
                  @click.stop
                />
                <div class="image-overlay" @click.stop>
                  <button type="button" class="overlay-btn" @click="openPreview(img)">预览</button>
                  <button type="button" class="overlay-btn" @click="copyUrl(img)">复制</button>
                  <button type="button" class="overlay-btn danger" @click="handleDelete(img)">删除</button>
                </div>
              </div>
              <div class="image-info">
                <span>{{ formatSize(img.size) }}</span>
                <span>{{ formatTime(img.lastModified) }}</span>
              </div>
            </div>
          </div>
          <el-empty
            v-else
            :description="groupId === 0 ? '未分组下暂无图片' : (groupId > 0 ? '该分组下暂无图片' : '还没有上传图片')"
          />

          <div v-if="total > 0" class="pager">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next, jumper"
              :total="total"
              :page-sizes="[12, 24, 48, 96]"
              v-model:current-page="page"
              v-model:page-size="size"
              @current-change="loadData"
              @size-change="handleSizeChange"
            />
          </div>
        </el-card>
      </div>
    </div>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" title="图片预览" width="720px" append-to-body>
      <div v-if="previewImage" class="preview-body">
        <img :src="previewImage.url" alt="图片预览" />
        <div class="preview-meta">
          <span>地址：{{ previewImage.url }}</span>
          <span>大小：{{ formatSize(previewImage.size) }}</span>
          <span>上传时间：{{ formatTime(previewImage.lastModified) }}</span>
          <span>分组：{{ previewImage.groupId ? groupNameOf(previewImage.groupId) : '未分组' }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="copyUrl(previewImage)">复制地址</el-button>
        <el-button type="danger" @click="handleDelete(previewImage)">删除</el-button>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getUploadedImages,
  uploadImagesViaAxios,
  deleteImage,
  getUploadGroups,
  createUploadGroup,
  renameUploadGroup,
  deleteUploadGroup,
  assignUploadGroup
} from '../api/admin'
import { toArray } from '../utils/safeArray'

// 与后端 AdminUploadController 的白名单 / multipart 限制保持一致
const ALLOWED_EXT_RE = /\.(png|jpg|jpeg|gif|webp|svg)$/i
const MAX_FILE_SIZE = 20 * 1024 * 1024
const acceptTypes = 'image/png,image/jpeg,image/jpg,image/gif,image/webp,image/svg+xml'

const fileInputRef = ref(null)
const loading = ref(false)
const uploading = ref(false)
const batchDeleting = ref(false)
const moving = ref(false)
const dragOver = ref(false)

const images = ref([])
const total = ref(0)
const totalSize = ref(0)
const page = ref(1)
const size = ref(24)

// 分组：-1=全部，0=未分组，正数=指定分组
const groups = ref([])
const groupId = ref(-1)
// 全部图片总数（切到分组筛选后仍保留，用于侧栏展示）
const allTotal = ref(0)

// 选中项按 url 记录，避免分页/刷新后对象引用失效
const selected = ref([])

const previewVisible = ref(false)
const previewImage = ref(null)

const groupedTotal = computed(() =>
  groups.value.reduce((sum, g) => sum + (Number(g.imageCount) || 0), 0)
)
const ungroupedCount = computed(() => Math.max(0, allTotal.value - groupedTotal.value))
const currentGroupName = computed(() => groupNameOf(groupId.value))

const allSelected = computed(
  () => images.value.length > 0 && images.value.every(img => selected.value.includes(img.url))
)
const indeterminate = computed(() => selected.value.length > 0 && !allSelected.value)

function groupNameOf(id) {
  const group = groups.value.find(g => Number(g.id) === Number(id))
  return group ? group.name : '未分组'
}

async function loadData() {
  loading.value = true
  try {
    let res = await getUploadedImages({ page: page.value, size: size.value, groupId: groupId.value })
    let records = toArray(res)
    // 删除后当前页可能已无数据，自动回退到上一页
    while (records.length === 0 && page.value > 1) {
      page.value -= 1
      res = await getUploadedImages({ page: page.value, size: size.value, groupId: groupId.value })
      records = toArray(res)
    }
    images.value = records
    total.value = Number(res?.total || 0)
    totalSize.value = Number(res?.totalSize || 0)
    // 仅在「全部图片」视图同步全量总数，避免筛选结果覆盖统计卡片
    if (groupId.value === -1) {
      allTotal.value = total.value
    }
    selected.value = []
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
    images.value = []
    total.value = 0
    totalSize.value = 0
  } finally {
    loading.value = false
  }
}

async function loadGroups() {
  try {
    const res = await getUploadGroups()
    groups.value = Array.isArray(res) ? res : toArray(res)
  } catch (e) {
    groups.value = []
  }
}

function selectGroup(id) {
  if (groupId.value === id) return
  groupId.value = id
  page.value = 1
  loadData()
}

function handleSizeChange() {
  page.value = 1
  loadData()
}

function triggerFileInput() {
  if (uploading.value) return
  fileInputRef.value?.click()
}

function onFileInputChange(e) {
  const files = Array.from(e.target.files || [])
  // 清空 input 值，保证重复选择同一文件也能再次触发 change
  e.target.value = ''
  if (files.length) doUpload(files)
}

function onDrop(e) {
  dragOver.value = false
  const files = Array.from(e.dataTransfer?.files || [])
  if (files.length) doUpload(files)
}

async function doUpload(files) {
  const valid = []
  for (const file of files) {
    const isImage = ALLOWED_EXT_RE.test(file.name) || (file.type || '').startsWith('image/')
    if (!isImage) {
      ElMessage.warning(`已跳过非图片文件：${file.name}`)
      continue
    }
    if (file.size > MAX_FILE_SIZE) {
      ElMessage.warning(`已跳过超过 20MB 的文件：${file.name}`)
      continue
    }
    valid.push(file)
  }
  if (!valid.length) return

  uploading.value = true
  try {
    // 当前处于某个分组时，上传图片直接归入该分组
    const groupIdForUpload = groupId.value > 0 ? groupId.value : undefined
    const data = await uploadImagesViaAxios(valid, groupIdForUpload)
    const list = Array.isArray(data) ? data : []
    if (!list.length) {
      ElMessage.error('上传失败：返回为空')
      return
    }
    // 全部视图下 loadData 会重新取准确总数，这里先累加以保证分组视图统计同步
    if (groupId.value !== -1) {
      allTotal.value += list.length
    }
    ElMessage.success(`成功上传 ${list.length} 张图片`)
    page.value = 1
    await Promise.all([loadGroups(), loadData()])
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    uploading.value = false
  }
}

function isSelected(url) {
  return selected.value.includes(url)
}

function toggleSelect(url, checked) {
  const idx = selected.value.indexOf(url)
  if (checked && idx < 0) {
    selected.value.push(url)
  } else if (!checked && idx >= 0) {
    selected.value.splice(idx, 1)
  }
}

function toggleSelectAll(checked) {
  selected.value = checked ? images.value.map(img => img.url) : []
}

// ========== 分组管理 ==========
async function handleCreateGroup() {
  try {
    const { value } = await ElMessageBox.prompt('请输入分组名称', '新建分组', {
      confirmButtonText: '创建',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：商品主图 / 详情图 / 活动素材',
      inputValidator: input => {
        const name = (input || '').trim()
        if (!name) return '分组名称不能为空'
        if (name.length > 64) return '分组名称不能超过 64 个字符'
        if (groups.value.some(g => g.name === name)) return '分组名称已存在'
        return true
      }
    })
    await createUploadGroup(value.trim())
    ElMessage.success('分组已创建')
    await loadGroups()
  } catch (e) {
    // 用户取消或接口异常（接口异常已由拦截器提示）
  }
}

async function handleRenameGroup(group) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新的分组名称', '重命名分组', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: group.name,
      inputValidator: input => {
        const name = (input || '').trim()
        if (!name) return '分组名称不能为空'
        if (name.length > 64) return '分组名称不能超过 64 个字符'
        if (groups.value.some(g => Number(g.id) !== Number(group.id) && g.name === name)) {
          return '分组名称已存在'
        }
        return true
      }
    })
    await renameUploadGroup(group.id, value.trim())
    ElMessage.success('分组已重命名')
    await loadGroups()
  } catch (e) {
    // 用户取消或接口异常
  }
}

async function handleDeleteGroup(group) {
  try {
    await ElMessageBox.confirm(
      `确定删除分组「${group.name}」吗？分组内的图片不会被删除，将回到「未分组」。`,
      '删除分组',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return // 取消删除
  }
  try {
    await deleteUploadGroup(group.id)
    ElMessage.success('分组已删除')
    if (Number(groupId.value) === Number(group.id)) {
      groupId.value = -1
      page.value = 1
    }
    await Promise.all([loadGroups(), loadData()])
  } catch (e) {
    // 接口异常已由拦截器提示
  }
}

async function handleMoveToGroup(command) {
  if (!selected.value.length) return
  const target = Number(command)
  moving.value = true
  try {
    await assignUploadGroup([...selected.value], target)
    ElMessage.success(target === 0 ? '已移出分组' : `已移动到「${groupNameOf(target)}」`)
    await Promise.all([loadGroups(), loadData()])
  } catch (e) {
    // 接口异常已由拦截器提示
  } finally {
    moving.value = false
  }
}

function openPreview(img) {
  if (!img) return
  previewImage.value = img
  previewVisible.value = true
}

/**
 * 复制图片相对地址（/uploads/...）。
 * 非 HTTPS 环境下 navigator.clipboard 不可用，降级为临时 textarea + execCommand。
 */
async function copyUrl(img) {
  if (!img?.url) return
  const text = img.url
  let copied = false
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      copied = true
    }
  } catch (e) {
    copied = false
  }
  if (!copied) {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      copied = document.execCommand('copy')
    } catch (e) {
      copied = false
    }
    document.body.removeChild(textarea)
  }
  if (copied) {
    ElMessage.success('图片地址已复制')
  } else {
    ElMessage.warning('复制失败，请手动复制：' + text)
  }
}

/**
 * 删除单张图片：先确认，再删除本地文件并刷新列表。
 */
async function handleDelete(img) {
  if (!img?.url) return
  try {
    await ElMessageBox.confirm(
      '确定删除这张图片吗？若商品或页面正在引用它，删除后将无法显示。',
      '删除图片',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return // 取消删除
  }
  try {
    await deleteImage(img.url)
    if (previewImage.value?.url === img.url) {
      previewVisible.value = false
    }
    allTotal.value = Math.max(0, allTotal.value - 1)
    ElMessage.success('已删除')
    await Promise.all([loadData(), loadGroups()])
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  }
}

/**
 * 批量删除：后端仅提供单张删除接口，这里并发调用并汇总结果。
 */
async function handleBatchDelete() {
  if (!selected.value.length) return
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selected.value.length} 张图片吗？若商品或页面正在引用它们，删除后将无法显示。`,
      '批量删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return // 取消删除
  }

  batchDeleting.value = true
  try {
    const urls = [...selected.value]
    const results = await Promise.allSettled(urls.map(url => deleteImage(url)))
    const successCount = results.filter(r => r.status === 'fulfilled').length
    const failCount = results.length - successCount
    allTotal.value = Math.max(0, allTotal.value - successCount)
    if (successCount) ElMessage.success(`已删除 ${successCount} 张图片`)
    if (failCount) ElMessage.warning(`${failCount} 张图片删除失败，请稍后重试`)
    await Promise.all([loadData(), loadGroups()])
  } finally {
    batchDeleting.value = false
  }
}

function onImgError(e) {
  e.target.style.opacity = '0.35'
}

function formatSize(bytes) {
  const value = Number(bytes) || 0
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

function formatTime(ms) {
  const value = Number(ms) || 0
  if (!value) return '-'
  const date = new Date(value)
  const pad = num => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

onMounted(() => {
  Promise.all([loadGroups(), loadData()])
})
</script>

<style scoped>
.page-wrapper { }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.page-desc { margin-top: 6px; font-size: 12px; color: var(--text-400); }
.header-actions { display: flex; gap: 8px; }

.stat-row { margin-bottom: 16px; }
.stat-item { display: flex; flex-direction: column; align-items: center; }
.stat-label { font-size: 13px; color: var(--text-400); margin-bottom: 4px; }
.stat-value { font-size: 24px; font-weight: 700; color: var(--text-800); font-variant-numeric: tabular-nums; }

/* ===== 上传区 ===== */
.upload-zone {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 24px;
  margin-bottom: 16px;
  border: 2px dashed var(--border);
  border-radius: var(--radius);
  background: var(--background-100);
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}
.upload-zone:hover,
.upload-zone.is-over { border-color: var(--brand-500); background: var(--brand-50); }
.upload-zone.is-uploading { cursor: progress; opacity: 0.75; }
.upload-icon {
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--card);
  border: 1px solid var(--brand-200, #9fcbff);
  color: var(--brand-600);
  font-size: 22px;
  font-weight: 600;
  flex-shrink: 0;
}
.upload-text { display: flex; flex-direction: column; gap: 2px; }
.upload-text strong { font-size: 13px; color: var(--text-700); font-weight: 600; }
.upload-text span { font-size: 12px; color: var(--text-400); }

/* ===== 分组 + 列表布局 ===== */
.space-layout {
  display: flex;
  gap: 16px;
  align-items: stretch;
}
.group-panel {
  width: 200px;
  min-width: 200px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.group-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
  background: var(--background-100);
}
.group-panel-title { font-size: 13px; font-weight: 600; color: var(--text-700); }
.group-add {
  border: none;
  background: transparent;
  color: var(--brand-600);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 4px;
}
.group-add:hover { text-decoration: underline; }
.group-list { padding: 6px; max-height: 620px; overflow-y: auto; }
.group-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-700);
  transition: background 0.15s ease;
}
.group-item:hover { background: var(--background-100); }
.group-item.active { background: var(--brand-50); color: var(--brand-700); font-weight: 600; }
.group-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.group-count { font-size: 11px; color: var(--text-400); font-variant-numeric: tabular-nums; }
.group-item.active .group-count { color: var(--brand-600); }
.group-ops { display: none; gap: 2px; }
.group-item:hover .group-ops { display: inline-flex; }
.group-ops button {
  border: none;
  background: transparent;
  color: var(--text-400);
  cursor: pointer;
  font-size: 12px;
  padding: 2px;
  line-height: 1;
}
.group-ops button:hover { color: var(--state-error); }
.group-empty { padding: 12px 0; }

.space-main { flex: 1; min-width: 0; }
.grid-card { min-height: 240px; }
.grid-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.toolbar-left { display: flex; align-items: center; gap: 12px; }
.selected-tip { font-size: 12px; color: var(--state-error); }
.toolbar-right { display: flex; align-items: center; gap: 8px; }

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));
  gap: 16px;
}
.image-card {
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--card);
  transition: border-color 0.2s, box-shadow 0.2s;
}
.image-card:hover { box-shadow: var(--shadow-md); }
.image-card.is-selected { border-color: var(--brand-500); box-shadow: 0 0 0 1px var(--brand-500); }

.image-thumb {
  position: relative;
  aspect-ratio: 1 / 1;
  background: var(--background-200);
  cursor: zoom-in;
}
.image-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.image-check {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.9);
}
.image-overlay {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  justify-content: center;
  gap: 6px;
  padding: 8px;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.55), transparent);
  opacity: 0;
  transition: opacity 0.2s;
}
.image-thumb:hover .image-overlay { opacity: 1; }
.overlay-btn {
  border: none;
  border-radius: 4px;
  padding: 3px 8px;
  font-size: 12px;
  color: var(--text-700);
  background: rgba(255, 255, 255, 0.92);
  cursor: pointer;
}
.overlay-btn:hover { background: #fff; }
.overlay-btn.danger { color: var(--state-error); }

.image-info {
  display: flex;
  justify-content: space-between;
  padding: 8px 10px;
  font-size: 11px;
  color: var(--text-400);
}

.pager { display: flex; justify-content: flex-end; margin-top: 20px; }

/* ===== 预览 ===== */
.preview-body { display: flex; flex-direction: column; gap: 12px; }
.preview-body img {
  max-width: 100%;
  max-height: 60vh;
  margin: 0 auto;
  object-fit: contain;
  border-radius: 6px;
  background: var(--background-200);
}
.preview-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--text-500);
  word-break: break-all;
}
</style>
