<template>
  <!-- 商品描述富文本（轻量实现：对齐 WC description 编辑器）
       - textarea + 工具栏（粗体/斜体/列表/链接/图片）
       - 图片按钮触发隐藏的 file input，通过 axios 上传到后端
       - 支持 HTML 预览切换
       关键设计：图片上传完全用 axios（不走 el-upload action），避开 Vite base /admin/ 干扰。
  -->
  <div class="rich-text-editor">
    <div class="toolbar">
      <button type="button" class="tb-btn" title="加粗" @click="wrap('b')"><strong>B</strong></button>
      <button type="button" class="tb-btn" title="斜体" @click="wrap('i')"><em>I</em></button>
      <button type="button" class="tb-btn" title="下划线" @click="wrap('u')"><u>U</u></button>
      <span class="tb-sep"></span>
      <button type="button" class="tb-btn" title="段落" @click="insertBlockStart('<p>')">¶</button>
      <button type="button" class="tb-btn" title="换行" @click="insertText('&lt;br&gt;')">↵</button>
      <span class="tb-sep"></span>
      <button type="button" class="tb-btn" title="无序列表" @click="insertBlockStart('<ul><li>')">•</button>
      <button type="button" class="tb-btn" title="有序列表" @click="insertBlockStart('<ol><li>')">1.</button>
      <span class="tb-sep"></span>
      <button type="button" class="tb-btn" title="插入链接" @click="insertLink">🔗</button>
      <!-- 隐藏 file input + 工具栏按钮 -->
      <input
        ref="imgInputRef"
        type="file"
        :accept="acceptTypes"
        style="display: none"
        @change="onImageSelected"
      />
      <button type="button" class="tb-btn" title="插入图片" @click="triggerImagePicker">🖼️ 图片</button>
      <span class="tb-sep"></span>
      <button
        type="button"
        class="tb-btn"
        :class="{ active: preview }"
        title="预览"
        @click="preview = !preview"
      >👁 预览</button>
    </div>

    <textarea
      v-show="!preview"
      ref="textareaRef"
      :value="modelValue"
      :placeholder="placeholder"
      :rows="rows"
      class="rte-textarea"
      @input="onInput"
    />

    <div v-show="preview" class="rte-preview" v-html="previewHtml"></div>

    <div class="rte-meta">
      <span>{{ charCount }} 字符</span>
      <span v-if="modelValue && modelValue.length">{{ (modelValue.match(/<img\b/g) || []).length }} 张图片</span>
      <span v-if="uploading">上传中...</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadImageViaAxios } from '../../api/admin'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '商品详细描述（支持 HTML）' },
  rows: { type: Number, default: 6 }
})
const emit = defineEmits(['update:modelValue'])

const textareaRef = ref(null)
const imgInputRef = ref(null)
const preview = ref(false)
const uploading = ref(false)

const acceptTypes = 'image/png,image/jpeg,image/jpg,image/gif,image/webp,image/svg+xml'

const charCount = computed(() => (props.modelValue || '').length)
const previewHtml = computed(() => props.modelValue || '<span style="color:var(--text-400)">暂无内容</span>')

function onInput(e) {
  emit('update:modelValue', e.target.value)
}

function getSelection() {
  const ta = textareaRef.value
  if (!ta) return { start: 0, end: 0 }
  return { start: ta.selectionStart, end: ta.selectionEnd }
}

function insertText(text) {
  const ta = textareaRef.value
  if (!ta) return
  const { start, end } = getSelection()
  const before = props.modelValue.slice(0, start)
  const after = props.modelValue.slice(end)
  const next = before + text + after
  emit('update:modelValue', next)
  requestAnimationFrame(() => {
    ta.focus()
    const pos = start + text.length
    ta.setSelectionRange(pos, pos)
  })
}

function wrap(tag) {
  const ta = textareaRef.value
  if (!ta) return
  const { start, end } = getSelection()
  const selected = props.modelValue.slice(start, end) || '文本'
  const before = props.modelValue.slice(0, start)
  const after = props.modelValue.slice(end)
  const next = `${before}<${tag}>${selected}</${tag}>${after}`
  emit('update:modelValue', next)
  requestAnimationFrame(() => {
    ta.focus()
    ta.setSelectionRange(start, end + tag.length * 2 + 5)
  })
}

function insertBlockStart(openTag) {
  const ta = textareaRef.value
  if (!ta) return
  const { start } = getSelection()
  const before = props.modelValue.slice(0, start)
  const after = props.modelValue.slice(start)
  const closeTag = openTag.replace(/^<\w+/, m => m.replace('<', '</'))
  const placeholder = openTag.includes('li') ? '列表项' : '段落内容'
  const next = `${before}${openTag}${placeholder}${closeTag}${after}`
  emit('update:modelValue', next)
  requestAnimationFrame(() => {
    ta.focus()
    const pos = start + openTag.length
    ta.setSelectionRange(pos, pos + placeholder.length)
  })
}

function insertLink() {
  const url = window.prompt('请输入链接 URL', 'https://')
  if (!url) return
  const safe = String(url).replace(/[<>"']/g, c => ({ '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))
  const ta = textareaRef.value
  const { start, end } = getSelection()
  const selected = props.modelValue.slice(start, end) || url
  const html = `<a href="${safe}" target="_blank" rel="noopener">${selected}</a>`
  const before = props.modelValue.slice(0, start)
  const after = props.modelValue.slice(end)
  emit('update:modelValue', before + html + after)
}

// ========== 图片插入（走 axios） ==========
function triggerImagePicker() {
  imgInputRef.value?.click()
}

async function onImageSelected(e) {
  const file = (e.target.files || [])[0]
  e.target.value = ''
  if (!file) return
  if (!/\.(png|jpg|jpeg|gif|webp|svg)$/i.test(file.name) && !(file.type || '').startsWith('image/')) {
    ElMessage.warning('仅支持图片文件')
    return
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('单张图片不能超过 20MB')
    return
  }
  uploading.value = true
  try {
    const data = await uploadImageViaAxios(file)
    // api 拦截器已解包 data；data 即 UploadResult
    if (data && data.url) {
      insertText(`<img src="${data.url}" alt="" style="max-width:100%;height:auto" />`)
      ElMessage.success('图片已插入')
    } else {
      ElMessage.error('上传失败：返回为空')
    }
  } catch (err) {
    console.error('图片上传失败：', err)
    const msg = err?.response?.data?.message || err?.message || '上传失败'
    ElMessage.error(msg)
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.rich-text-editor {
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--card);
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--border);
  background: var(--background-100);
  align-items: center;
}
.tb-btn {
  min-width: 28px;
  height: 26px;
  padding: 0 8px;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: var(--text-700);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.tb-btn:hover { background: var(--card); border-color: var(--border); }
.tb-btn.active { background: var(--brand-100, #e6f0ff); color: var(--brand-700); border-color: var(--brand-200, #c7d7fe); }
.tb-sep {
  width: 1px;
  height: 16px;
  background: var(--border);
  margin: 0 4px;
}

.rte-textarea {
  width: 100%;
  border: none;
  outline: none;
  resize: vertical;
  padding: 10px 12px;
  font-size: 13px;
  font-family: inherit;
  line-height: 1.6;
  background: transparent;
  color: var(--text-900);
  box-sizing: border-box;
}
.rte-preview {
  padding: 12px;
  min-height: 80px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-900);
}
.rte-preview :deep(img) { max-width: 100%; height: auto; border-radius: 4px; }
.rte-preview :deep(a) { color: var(--brand-600); }
.rte-preview :deep(ul),
.rte-preview :deep(ol) { padding-left: 24px; }

.rte-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 12px;
  border-top: 1px solid var(--border);
  background: var(--background-100);
  font-size: 11px;
  color: var(--text-400);
}
</style>