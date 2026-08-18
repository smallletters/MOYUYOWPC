<template>
  <!-- 商品图库组件（对齐 WC product_images_container）
       关键设计：完全不用 el-upload 的 action，自己用 axios 上传。
       原因：el-upload 内部用 XMLHttpRequest，路径处理与 Vite base /admin/ 冲突，
            且 headers 无法动态绑定 token。改用 axios 一举解决。
       功能：
       - 多选上传（隐藏 file input + 普通按钮 / 拖拽 / 粘贴）
       - 第一张即为封面（WC 行为）
       - 单张删除（同时调后端）
  -->
  <div class="image-uploader">
    <!-- 隐藏的文件选择器 -->
    <input
      ref="fileInputRef"
      type="file"
      :accept="acceptTypes"
      multiple
      style="display: none"
      @change="onFileInputChange"
    />

    <!-- 上传触发按钮（点击触发 file input） -->
    <div class="upload-btn" @click="triggerFileInput">
      <span class="upload-icon">+</span>
      <span class="upload-text">添加商品图片</span>
      <span class="upload-hint">支持点击 / 拖拽 / 粘贴；第一张为封面图</span>
    </div>

    <!-- 已上传图片网格 -->
    <div v-if="modelValue && modelValue.length" class="image-grid">
      <div
        v-for="(img, idx) in modelValue"
        :key="img.url || img"
        class="image-item"
        :class="{ 'is-cover': idx === 0, 'dragging-over': dragOverIdx === idx }"
        draggable="true"
        @dragstart="onDragStart(idx, $event)"
        @dragover.prevent="onDragOver(idx, $event)"
        @dragenter.prevent="onDragEnter(idx)"
        @dragleave="onDragLeave"
        @drop.prevent="onDrop(idx, $event)"
      >
        <img :src="absUrl(img.url || img)" :alt="`商品图 ${idx + 1}`" @error="onImgError" />
        <div class="image-actions">
          <button
            v-if="idx !== 0"
            type="button"
            class="action-btn cover-btn"
            title="设为封面"
            @click="setCover(idx)"
          >★</button>
          <button
            type="button"
            class="action-btn remove-btn"
            title="删除"
            @click="removeImage(idx)"
          >×</button>
        </div>
        <span v-if="idx === 0" class="cover-badge">封面</span>
        <span class="image-index">{{ idx + 1 }}</span>
      </div>
    </div>
    <div v-else class="empty-tip">尚未上传商品图片。建议尺寸 800×800 或更高，首张将作为封面。</div>

    <!-- 上传中 loading -->
    <div v-if="uploading" class="uploading-tip">
      上传中... {{ uploadProgress }}
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadImagesViaAxios, deleteImage } from '../../api/admin'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  baseUrl: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'change'])

const acceptTypes = 'image/png,image/jpeg,image/jpg,image/gif,image/webp,image/svg+xml'

const fileInputRef = ref(null)
const uploading = ref(false)
const uploadProgress = ref('')

// 拖拽排序状态
const dragFromIdx = ref(-1)
const dragOverIdx = ref(-1)

function absUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//.test(url)) return url
  return props.baseUrl + url
}

function triggerFileInput() {
  fileInputRef.value?.click()
}

function onFileInputChange(e) {
  const files = Array.from(e.target.files || [])
  if (files.length) doUpload(files)
  // 清空 input 值以便重复选择同一文件能再次触发 change
  e.target.value = ''
}

function normalizeImg(d) {
  if (!d) return null
  return { url: d.url, name: d.filename || d.originalName || '' }
}

async function doUpload(files) {
  // 校验
  const valid = []
  for (const f of files) {
    const isImg = /\.(png|jpg|jpeg|gif|webp|svg)$/i.test(f.name) || (f.type || '').startsWith('image/')
    if (!isImg) {
      ElMessage.warning(`已跳过非图片文件：${f.name}`)
      continue
    }
    if (f.size > 20 * 1024 * 1024) {
      ElMessage.warning(`已跳过超大文件（>20MB）：${f.name}`)
      continue
    }
    valid.push(f)
  }
  if (!valid.length) return

  uploading.value = true
  uploadProgress.value = `${valid.length} 个文件`
  try {
    const data = await uploadImagesViaAxios(valid)
    // 后端返回 Result<List<UploadResult>>，api 拦截器已自动解包 data
    const list = Array.isArray(data) ? data : []
    if (!list.length) {
      ElMessage.error('上传失败：返回为空')
      return
    }
    const normalized = list.map(normalizeImg).filter(Boolean)
    const next = [...(props.modelValue || []), ...normalized]
    emit('update:modelValue', next)
    emit('change', next)
    ElMessage.success(`已上传 ${normalized.length} 张图片`)
  } catch (err) {
    console.error('上传失败：', err)
    const msg = err?.response?.data?.message || err?.message || '上传失败'
    ElMessage.error(msg)
  } finally {
    uploading.value = false
    uploadProgress.value = ''
  }
}

function setCover(idx) {
  if (idx <= 0 || idx >= props.modelValue.length) return
  const next = [...props.modelValue]
  const [picked] = next.splice(idx, 1)
  next.unshift(picked)
  emit('update:modelValue', next)
  emit('change', next)
}

async function removeImage(idx) {
  const img = props.modelValue[idx]
  if (!img) return
  const url = img.url || img
  if (typeof url === 'string' && url.startsWith('/uploads/')) {
    try {
      await deleteImage(url)
    } catch (e) {
      console.warn('后端删除失败，仍从前端移除：', e)
    }
  }
  const next = props.modelValue.filter((_, i) => i !== idx)
  emit('update:modelValue', next)
  emit('change', next)
}

function onImgError(e) {
  e.target.style.opacity = '0.3'
}

// ========== 拖拽排序 ==========
function onDragStart(idx, e) {
  dragFromIdx.value = idx
  e.dataTransfer.effectAllowed = 'move'
  // 必须设置数据才能在 Firefox 中拖拽
  try { e.dataTransfer.setData('text/plain', String(idx)) } catch (_) {}
}
function onDragOver(idx, e) {
  e.dataTransfer.dropEffect = 'move'
}
function onDragEnter(idx) {
  dragOverIdx.value = idx
}
function onDragLeave() {
  dragOverIdx.value = -1
}
function onDrop(idx, e) {
  e.preventDefault()
  const from = dragFromIdx.value
  dragOverIdx.value = -1
  dragFromIdx.value = -1
  if (from < 0 || from === idx || from >= props.modelValue.length) return
  const next = [...props.modelValue]
  const [picked] = next.splice(from, 1)
  next.splice(idx, 0, picked)
  emit('update:modelValue', next)
  emit('change', next)
}

// 暴露 triggerFileInput 供父组件需要时主动触发
defineExpose({ triggerFileInput })
</script>

<style scoped>
.image-uploader { display: flex; flex-direction: column; gap: 12px; }
.upload-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 18px;
  border: 2px dashed var(--border);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
  background: var(--background-50, #fafafa);
}
.upload-btn:hover {
  border-color: var(--brand-500);
  background: var(--brand-50, #f0f5ff);
}
.upload-icon {
  font-size: 22px;
  font-weight: 600;
  color: var(--brand-600);
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--card);
  border: 1px solid var(--brand-200, #c7d7fe);
}
.upload-text { font-size: 13px; color: var(--text-700); font-weight: 500; }
.upload-hint { font-size: 11px; color: var(--text-400); }

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 10px;
}
.image-item {
  position: relative;
  aspect-ratio: 1;
  border-radius: 6px;
  overflow: hidden;
  border: 2px solid var(--border);
  background: var(--background-100);
  transition: border-color 0.2s, transform 0.2s;
  cursor: move;
}
.image-item.is-cover { border-color: var(--brand-600); }
.image-item.dragging-over {
  border-color: var(--brand-500);
  transform: scale(0.97);
}
.image-item:hover { transform: translateY(-1px); }
.image-item img {
  width: 100%; height: 100%;
  object-fit: cover;
  display: block;
}

.image-actions {
  position: absolute;
  top: 4px;
  right: 4px;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}
.image-item:hover .image-actions { opacity: 1; }
.action-btn {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: none;
  cursor: pointer;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.cover-btn { background: rgba(255, 200, 0, 0.9); }
.cover-btn:hover { background: rgba(255, 200, 0, 1); }
.remove-btn { background: rgba(220, 38, 38, 0.85); }
.remove-btn:hover { background: rgba(220, 38, 38, 1); }

.cover-badge {
  position: absolute;
  top: 4px;
  left: 4px;
  background: var(--brand-600);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
}
.image-index {
  position: absolute;
  bottom: 4px;
  right: 4px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 500;
}

.empty-tip {
  padding: 14px;
  text-align: center;
  font-size: 12px;
  color: var(--text-400);
  background: var(--background-100);
  border: 1px dashed var(--border);
  border-radius: 6px;
}
.uploading-tip {
  padding: 8px 12px;
  background: var(--brand-50);
  border: 1px solid var(--brand-200);
  border-radius: 4px;
  font-size: 12px;
  color: var(--brand-700);
}
</style>