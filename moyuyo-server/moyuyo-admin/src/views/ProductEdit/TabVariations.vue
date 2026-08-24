<template>
  <!-- 变体选项卡（对齐 WC variable_product_options） -->
  <div class="tab-variations">
    <div class="options-group">
      <div class="group-title">变体管理</div>

      <!-- 空态：提示先去属性面板 -->
      <div v-if="!form.variations.length" class="empty-state">
        <p>
          暂无变体。请在「属性」选项卡中添加用于变体的属性（如颜色 / 尺寸），勾选「用于变体」后点击「生成变体」。
        </p>
      </div>

      <!-- 工具栏 -->
      <div v-show="form.variations.length" class="toolbar">
        <button class="btn btn-primary" @click="addVariationManually">+ 添加手动变体</button>
        <button class="btn btn-outline" @click="expandAll(true)">展开全部</button>
        <button class="btn btn-outline" @click="expandAll(false)">收起全部</button>
        <div class="bulk-actions">
          <el-popover placement="bottom" :width="240" trigger="click">
            <template #reference>
              <button class="btn btn-outline">批量设置价格 ▾</button>
            </template>
            <div class="popover-form">
              <label>批量设置价格</label>
              <input v-model="bulkPrice" type="number" step="0.01" placeholder="例如 99.00" />
              <button class="btn btn-primary btn-block" @click="applyBulk('regularPrice')">应用到所有变体</button>
            </div>
          </el-popover>
          <el-popover placement="bottom" :width="240" trigger="click">
            <template #reference>
              <button class="btn btn-outline">批量设置库存 ▾</button>
            </template>
            <div class="popover-form">
              <label>批量设置库存</label>
              <input v-model="bulkStock" type="number" min="0" placeholder="例如 100" />
              <button class="btn btn-primary btn-block" @click="applyBulk('stockQuantity')">应用到所有变体</button>
            </div>
          </el-popover>
          <el-popover placement="bottom" :width="240" trigger="click">
            <template #reference>
              <button class="btn btn-outline">批量设置 SKU ▾</button>
            </template>
            <div class="popover-form">
              <label>批量设置 SKU</label>
              <input v-model="bulkSkuPrefix" placeholder="前缀，例如 SKU-" />
              <input v-model="bulkSkuStartNum" type="number" min="0" placeholder="起始编号（默认 1）" />
              <span class="popover-hint">留空前缀则仅按编号生成；编号可作为变体序号后缀</span>
              <button class="btn btn-primary btn-block" @click="applyBulkSku">应用到所有变体</button>
            </div>
          </el-popover>
          <el-popover placement="bottom" :width="240" trigger="click">
            <template #reference>
              <button class="btn btn-outline">批量设置销售价 ▾</button>
            </template>
            <div class="popover-form">
              <label>批量设置销售价</label>
              <input v-model="bulkSalePrice" type="number" step="0.01" placeholder="例如 79.00" />
              <span class="popover-hint">设为空可清空所有销售价，恢复按原价销售</span>
              <button class="btn btn-primary btn-block" @click="applyBulk('salePrice')">应用到所有变体</button>
            </div>
          </el-popover>
        </div>
      </div>

      <!-- 变体表格 -->
      <div v-show="form.variations.length" class="variation-table">
        <div class="vtable-header">
          <span class="col-toggle">▾</span>
          <span class="col-name">变体（属性组合）</span>
          <span class="col-image">图片</span>
          <span class="col-sku">SKU</span>
          <span class="col-price">价格</span>
          <span class="col-stock">库存</span>
          <span class="col-status">状态</span>
          <span class="col-ops">操作</span>
        </div>
        <div v-for="(v, idx) in form.variations" :key="v.id" class="vtable-row" :class="{ disabled: !v.enabled }">
          <div class="vrow-summary" @click="v.expanded = !v.expanded">
            <span class="col-toggle">{{ v.expanded ? '▼' : '▶' }}</span>
            <span class="col-name">
              <span v-for="(a, i) in v.attributes" :key="a.name">
                <span v-if="i > 0" class="attr-sep"> / </span>
                <strong>{{ a.name }}:</strong> {{ a.value }}
              </span>
            </span>
            <span class="col-image">
              <img v-if="v.image && v.image.src" :src="v.image.src" :alt="v.image.alt || ''" class="vrow-thumb" />
              <span v-else class="muted">—</span>
            </span>
            <span class="col-sku">{{ v.sku || '—' }}</span>
            <span class="col-price">{{ v.regularPrice || '—' }}</span>
            <span class="col-stock">
              <span v-if="v.manageStock">{{ v.stockQuantity || 0 }}</span>
              <span v-else class="muted">未启用</span>
            </span>
            <span class="col-status">
              <span v-if="v.enabled" class="tag tag-green">启用</span>
              <span v-else class="tag tag-gray">禁用</span>
            </span>
            <span class="col-ops">
              <button class="btn-mini" @click.stop="toggleEnabled(v)">{{ v.enabled ? '禁用' : '启用' }}</button>
              <button class="btn-mini btn-mini-danger" @click.stop="removeVariation(idx)">删除</button>
            </span>
          </div>

          <div v-show="v.expanded" class="vrow-detail">
            <div class="form-row">
              <div class="form-group">
                <label>SKU</label>
                <input v-model="v.sku" placeholder="变体级别 SKU" />
              </div>
              <div class="form-group">
                <label>常规价格 (regular_price)</label>
                <input v-model="v.regularPrice" type="number" step="0.01" placeholder="0.00" />
              </div>
              <div class="form-group">
                <label>售价 (sale_price)</label>
                <input v-model="v.salePrice" type="number" step="0.01" placeholder="0.00" />
              </div>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label>管理库存</label>
                <div class="status-toggle" style="margin-top:4px">
                  <label class="toggle-switch">
                    <input type="checkbox" v-model="v.manageStock" />
                    <span class="toggle-slider"></span>
                  </label>
                  <span class="status-text">{{ v.manageStock ? '已启用' : '未启用' }}</span>
                </div>
              </div>
              <div class="form-group" v-show="v.manageStock">
                <label>库存数量</label>
                <input v-model="v.stockQuantity" type="number" min="0" />
              </div>
              <div class="form-group">
                <label>库存状态</label>
                <select v-model="v.stockStatus">
                  <option value="instock">有货 instock</option>
                  <option value="outofstock">缺货 outofstock</option>
                  <option value="onbackorder">预售 onbackorder</option>
                </select>
              </div>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label>重量 (kg)</label>
                <input v-model="v.weight" type="number" step="0.001" />
              </div>
              <div class="form-group">
                <label>长度 (cm)</label>
                <input v-model="v.dimensions.length" type="number" step="0.01" />
              </div>
              <div class="form-group">
                <label>宽度 (cm)</label>
                <input v-model="v.dimensions.width" type="number" step="0.01" />
              </div>
              <div class="form-group">
                <label>高度 (cm)</label>
                <input v-model="v.dimensions.height" type="number" step="0.01" />
              </div>
            </div>

            <!-- 变体图片（WC: image { id, src, name, alt }） -->
            <div class="form-row">
              <div class="form-group full">
                <label>变体图片 (image)</label>
                <div class="vimage-row">
                  <div class="vimage-preview-wrap">
                    <img v-if="v.image && v.image.src" :src="absImageUrl(v.image.src)" :alt="v.image.alt" class="vimage-preview" />
                    <div v-else class="vimage-empty">未设置</div>
                  </div>
                  <div class="vimage-fields">
                    <!-- 上传组件：点击 / 拖拽 / 粘贴 触发 -->
                    <el-upload
                      :show-file-list="false"
                      :before-upload="(file) => beforeVariationUpload(v, file)"
                      :http-request="(opts) => customVariationUpload(v, opts)"
                      accept="image/png,image/jpeg,image/jpg,image/gif,image/webp"
                      drag
                      class="vimage-uploader"
                    >
                      <div class="vimage-uploader-inner">
                        <span class="vimage-uploader-icon">⤴</span>
                        <span>点击 / 拖拽 / 粘贴上传变体图片</span>
                      </div>
                    </el-upload>
                    <!-- URL 输入回退：兼容外链 / 已有路径 -->
                    <input
                      :value="v.image?.src || ''"
                      placeholder="图片 URL（粘贴地址或 /uploads/xxx 路径）"
                      @input="onVariationImageUrl(v, $event.target.value)"
                    />
                    <input
                      :value="v.image?.alt || ''"
                      placeholder="替代文本（alt，便于 SEO/无障碍）"
                      @input="onVariationImageAlt(v, $event.target.value)"
                    />
                    <div class="vimage-actions">
                      <button class="btn-mini" type="button" @click="fillVariationImageFromMain(v)">使用商品主图</button>
                      <button v-if="v.image?.src" class="btn-mini btn-mini-danger" type="button" @click="clearVariationImage(v)">清除</button>
                      <span v-if="v._imageUploading" class="vimage-uploading">上传中...</span>
                    </div>
                  </div>
                </div>
                <span class="field-hint">每个变体可独立设置一张图片（WC: variations[].image）</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadImageViaAxios, API_BASE_URL } from '../../api/admin'

const props = defineProps({
  form: { type: Object, required: true }
})
const emit = defineEmits(['open-upsell', 'remove-variation'])

const bulkPrice = ref('')
const bulkStock = ref('')
const bulkSalePrice = ref('')
const bulkSkuPrefix = ref('')
const bulkSkuStartNum = ref('')

function addVariationManually() {
  props.form.variations.push({
    id: 'tmp_' + Math.random().toString(36).slice(2, 10),
    attributes: [{ name: '自定义', value: '手动变体' }],
    sku: '',
    regularPrice: '',
    salePrice: '',
    manageStock: false,
    stockQuantity: 0,
    stockStatus: 'instock',
    weight: '',
    dimensions: { length: '', width: '', height: '' },
    enabled: true,
    expanded: true,
    image: { id: null, src: '', alt: '' }
  })
}

// 变体图片辅助方法（WC: image { id, src, name, alt }）
function ensureImage(v) {
  if (!v.image) v.image = { id: null, src: '', alt: '' }
  return v.image
}
function onVariationImageUrl(v, url) {
  const img = ensureImage(v)
  img.src = url || ''
  // alt 默认取属性组合名，方便无障碍
  if (!img.alt) img.alt = (v.attributes || []).map(a => a.value).join(' ')
}
function onVariationImageAlt(v, alt) {
  ensureImage(v).alt = alt || ''
}
function fillVariationImageFromMain(v) {
  const main = props.form.images?.[0]?.url || props.form.mainImage || ''
  const img = ensureImage(v)
  img.src = main
  if (!img.alt) img.alt = (v.attributes || []).map(a => a.value).join(' ')
}
function clearVariationImage(v) {
  v.image = { id: null, src: '', alt: '' }
}

// 拼接图片绝对 URL：相对路径走当前 origin（避开 Vite /api 前缀干扰），
// 外链直接返回
function absImageUrl(src) {
  if (!src) return ''
  if (/^https?:\/\//i.test(src)) return src
  if (src.startsWith('//')) return window.location.protocol + src
  // 相对路径：用当前 origin 作为基础
  // dev: http://localhost:5173/uploads/... → Vite 不代理 /uploads/，需要指到 8080
  // prod: 当前 origin (8080) + /uploads/... → 直连后端静态资源
  const origin = window.location.origin
  // dev 环境特判：5173 端口走的是 Vite，/uploads/ 需要绕到 8080
  if (origin.includes(':5173') || import.meta.env.DEV) {
    const apiBase = (API_BASE_URL && API_BASE_URL.startsWith('http')) ? API_BASE_URL : 'http://localhost:8080'
    return apiBase.replace(/\/$/, '') + (src.startsWith('/') ? src : '/' + src)
  }
  return origin + (src.startsWith('/') ? src : '/' + src)
}

// el-upload 钩子：拦截选择，校验类型/大小，返回 false 阻止默认 action
// EP 2.x 的 before-upload 回调直接拿到原始 File 对象（不是包装）
function beforeVariationUpload(v, file) {
  // 简单校验：仅允许图片类型；大小限制由后端 multipart.max-file-size 控制
  const isImage = /^image\//.test(file.type || '')
  if (!isImage) {
    ElMessage.error('仅支持图片文件')
    return false
  }
  v._imageUploading = true
  return true
}

// 自定义上传：调用 /api/admin/upload/image，成功后回填 v.image
// http-request 钩子的 opts.file 是原始 File 对象
async function customVariationUpload(v, opts) {
  try {
    const res = await uploadImageViaAxios(opts.file)
    if (res && res.url) {
      const img = ensureImage(v)
      img.src = res.url
      img.alt = img.alt || (v.attributes || []).map(a => a.value).join(' ')
      ElMessage.success('变体图片上传成功')
    } else {
      ElMessage.error('上传失败：返回数据格式异常')
    }
  } catch (e) {
    // axios 响应拦截器已统一 toast；此处仅记录日志
    console.error('[variation-image] upload failed:', e)
  } finally {
    v._imageUploading = false
  }
}

function removeVariation(idx) {
  emit('remove-variation', idx)
}

function toggleEnabled(v) {
  v.enabled = !v.enabled
}

function expandAll(state) {
  props.form.variations.forEach(v => { v.expanded = state })
}

function applyBulk(field) {
  if (field === 'regularPrice') {
    if (bulkPrice.value === '') return
    props.form.variations.forEach(v => { v.regularPrice = bulkPrice.value })
    bulkPrice.value = ''
  } else if (field === 'stockQuantity') {
    if (bulkStock.value === '') return
    props.form.variations.forEach(v => {
      v.manageStock = true
      v.stockQuantity = Number(bulkStock.value)
    })
    bulkStock.value = ''
  } else if (field === 'salePrice') {
    // 销售价允许清空：留空字符串就置空字符串
    props.form.variations.forEach(v => { v.salePrice = bulkSalePrice.value })
    bulkSalePrice.value = ''
  }
}

// 批量设置 SKU：支持「前缀 + 序号」拼接，也可只填前缀或只填编号
function applyBulkSku() {
  const prefix = (bulkSkuPrefix.value || '').trim()
  const startNum = bulkSkuStartNum.value === '' ? 1 : Math.max(0, Number(bulkSkuStartNum.value))
  if (!prefix && bulkSkuStartNum.value === '') return
  props.form.variations.forEach((v, i) => {
    const num = startNum + i
    v.sku = prefix ? `${prefix}${num}` : String(num)
  })
  bulkSkuPrefix.value = ''
  bulkSkuStartNum.value = ''
}
</script>

<style scoped>
.tab-variations { display: flex; flex-direction: column; gap: 18px; }
.options-group {
  padding: 14px;
  background: var(--background-50, #fafafa);
  border: 1px solid var(--border);
  border-radius: 8px;
}
.group-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-700);
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}

.empty-state {
  padding: 30px 20px;
  text-align: center;
  color: var(--text-500);
  background: var(--card);
  border: 1px dashed var(--border);
  border-radius: 6px;
  font-size: 13px;
}

.toolbar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; align-items: center; }
.bulk-actions { display: flex; gap: 8px; margin-left: auto; }
.popover-form { display: flex; flex-direction: column; gap: 6px; }
.popover-form label { font-size: 12px; font-weight: 500; color: var(--text-700); }
.popover-form input {
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 13px;
}
.popover-hint {
  font-size: 11px;
  color: var(--text-400);
  line-height: 1.4;
}

.variation-table {
  border: 1px solid var(--border);
  border-radius: 6px;
  overflow: hidden;
  background: var(--card);
}
.vtable-header,
.vrow-summary {
  display: grid;
  grid-template-columns: 30px 1.5fr 56px 100px 80px 80px 80px 130px;
  gap: 8px;
  padding: 8px 12px;
  align-items: center;
  font-size: 12px;
}
.vtable-header {
  background: var(--background-100);
  font-weight: 600;
  color: var(--text-700);
  border-bottom: 1px solid var(--border);
}
.vtable-row { border-bottom: 1px solid var(--border); }
.vtable-row.disabled .vrow-summary { opacity: 0.6; }
.vrow-summary { cursor: pointer; }
.vrow-summary:hover { background: var(--background-100); }
.col-name strong { color: var(--text-700); margin-right: 2px; }
.attr-sep { color: var(--text-400); margin: 0 2px; }
.col-sku, .col-price, .col-stock, .col-status {
  font-size: 12px;
  color: var(--text-900);
  overflow: hidden;
  text-overflow: ellipsis;
}
.col-ops { display: flex; gap: 4px; }
.muted { color: var(--text-400); }

.vrow-detail {
  padding: 14px;
  background: var(--background-50, #fafafa);
  border-top: 1px dashed var(--border);
}
.form-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 10px; }
.form-group { display: flex; flex-direction: column; gap: 4px; }
.form-group label { font-size: 12px; font-weight: 500; color: var(--text-700); }
.form-group input,
.form-group select {
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 12px;
  background: var(--card);
  font-family: inherit;
}

.status-toggle { display: flex; align-items: center; gap: 8px; }
.toggle-switch { position: relative; display: inline-block; width: 36px; height: 20px; }
.toggle-switch input { opacity: 0; width: 0; height: 0; }
.toggle-slider {
  position: absolute; cursor: pointer; inset: 0;
  background: var(--background-300); border-radius: 999px;
  transition: 0.2s;
}
.toggle-slider:before {
  position: absolute; content: ''; height: 14px; width: 14px;
  left: 3px; top: 3px; background: #fff; border-radius: 50%;
  transition: 0.2s;
}
.toggle-switch input:checked + .toggle-slider { background: var(--brand-600); }
.toggle-switch input:checked + .toggle-slider:before { transform: translateX(16px); }

.tag {
  display: inline-flex;
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
}
.tag-green { background: var(--state-success-surface); color: var(--state-success); }
.tag-gray { background: var(--background-200); color: var(--text-500); }

.btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  border: 1px solid transparent;
  font-family: inherit;
}
.btn-primary { background: var(--brand-600); color: #fff; border-color: var(--brand-600); }
.btn-primary:hover { background: var(--brand-700); }
.btn-outline { background: var(--card); color: var(--text-700); border-color: var(--border); }
.btn-outline:hover { background: var(--background-100); }
.btn-block { width: 100%; margin-top: 4px; }

.btn-mini {
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text-700);
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  cursor: pointer;
}
.btn-mini:hover { background: var(--background-100); }
.btn-mini-danger { color: var(--state-error); border-color: var(--state-error-surface, #fecaca); }
.btn-mini-danger:hover { background: var(--state-error-surface); }

/* 变体图片 */
.vrow-thumb {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid var(--border);
  background: var(--background-100);
}
.col-image { display: flex; align-items: center; }
.vimage-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.vimage-preview {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: var(--background-100);
  flex-shrink: 0;
}
.vimage-preview-wrap { flex-shrink: 0; }
.vimage-empty {
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--border);
  border-radius: 6px;
  color: var(--text-400);
  font-size: 11px;
  flex-shrink: 0;
  background: var(--background-100);
}
.vimage-fields { flex: 1; display: flex; flex-direction: column; gap: 6px; }
.vimage-fields input {
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 12px;
  background: var(--card);
  font-family: inherit;
}
.vimage-actions { display: flex; gap: 6px; align-items: center; }
.vimage-uploading {
  font-size: 11px;
  color: var(--primary);
  margin-left: 4px;
}
/* el-upload 拖拽区样式覆盖：缩小内边距与字体 */
.vimage-uploader {
  display: block;
}
.vimage-uploader :deep(.el-upload) { width: 100%; }
.vimage-uploader :deep(.el-upload-dragger) {
  padding: 10px 14px;
  border-radius: 6px;
}
.vimage-uploader-inner {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-500);
}
.vimage-uploader-icon {
  font-size: 14px;
  color: var(--primary);
}
</style>