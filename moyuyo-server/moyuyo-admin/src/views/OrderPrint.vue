<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>订单打印</h2>
    </div>

    <!-- ===== 打印模板区块 ===== -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-header">
          <span class="section-title">打印模板</span>
          <span class="section-tip">支持拣货单、打包单、发货单、配货标签四种模板，点击卡片选择，打印时按所选模板输出</span>
        </div>
      </template>
      <div class="template-grid">
        <div
          v-for="tpl in printTemplates"
          :key="tpl.id"
          class="template-card"
          :class="{ selected: selectedTemplateId === tpl.id }"
          @click="selectTemplate(tpl)"
        >
          <div class="template-thumb" :style="{ background: tpl.gradient }">
            <el-icon :size="30" :color="tpl.color"><component :is="tpl.icon" /></el-icon>
            <span v-if="tpl.isDefault" class="badge default-badge">默认</span>
            <span v-if="selectedTemplateId === tpl.id" class="badge selected-badge">
              <el-icon :size="12"><Check /></el-icon>已选
            </span>
            <span v-if="tpl.requiresShipping && !yanwenEnabled" class="badge unavailable-badge">需配置</span>
          </div>
          <div class="template-name">{{ tpl.name }}</div>
          <div class="template-meta">
            <el-tag size="small" :type="tpl.tagType" effect="light">{{ tpl.type }}</el-tag>
            <span class="template-paper">{{ tpl.paper }}</span>
          </div>
          <div class="template-desc">{{ tpl.desc }}</div>
          <div class="template-actions" @click.stop>
            <el-button size="small" text type="primary" @click="handleEditTemplate(tpl)">
              <el-icon :size="12" style="margin-right:2px"><Edit /></el-icon>编辑
            </el-button>
            <el-button size="small" text :disabled="tpl.isDefault" @click="handleSetDefault(tpl)">
              <el-icon :size="12" style="margin-right:2px"><Star /></el-icon>设为默认
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- ===== 打印设置区块 ===== -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-header">
          <span class="section-title">打印设置</span>
          <span class="section-tip">默认纸张、份数、方向与边距，保存后本地生效</span>
        </div>
      </template>
      <el-form :model="printSettings" label-width="96px" class="settings-form">
        <el-row :gutter="24">
          <el-col :span="8">
            <el-form-item label="默认纸张">
              <el-select v-model="printSettings.paperSize" style="width: 200px">
                <el-option label="A4" value="A4" />
                <el-option label="A5" value="A5" />
                <el-option label="热敏纸 80x80mm" value="thermal-80" />
                <el-option label="热敏纸 100x150mm" value="thermal-100" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="打印份数">
              <el-input-number v-model="printSettings.copies" :min="1" :max="99" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="打印方向">
              <el-radio-group v-model="printSettings.orientation">
                <el-radio value="portrait">纵向</el-radio>
                <el-radio value="landscape">横向</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="24">
          <el-col :span="8">
            <el-form-item label="双面打印">
              <el-switch v-model="printSettings.duplex" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="上下边距">
              <el-input-number v-model="printSettings.marginY" :min="0" :max="50" />
              <span class="unit-text">mm</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="左右边距">
              <el-input-number v-model="printSettings.marginX" :min="0" :max="50" />
              <span class="unit-text">mm</span>
            </el-form-item>
          </el-col>
        </el-row>
        <div class="settings-footer">
          <el-button type="primary" @click="handleSaveSettings">保存设置</el-button>
        </div>
      </el-form>
    </el-card>

    <!-- ===== 待打印订单列表（保留原功能） ===== -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-header">
          <span class="section-title">待打印订单</span>
          <el-button type="primary" size="small" @click="handleBatchPrint" :disabled="!tableData.length">
            <el-icon :size="12" style="margin-right:2px"><Printer /></el-icon>批量打印
          </el-button>
        </div>
      </template>
      <el-form :model="filters" inline>
        <el-form-item label="订单编号">
          <el-input v-model="filters.keyword" placeholder="请输入订单编号" clearable />
        </el-form-item>
        <el-form-item label="打印状态">
          <el-select v-model="filters.printStatus" placeholder="全部" clearable style="width:140px">
            <el-option label="全部" value="" />
            <el-option label="待打印" value="待打印" />
            <el-option label="已打印" value="已打印" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderNo" label="订单编号" width="170" />
        <el-table-column prop="productInfo" label="商品信息" min-width="200" show-overflow-tooltip />
        <el-table-column prop="receiver" label="收件人" width="120" />
        <el-table-column prop="printStatus" label="打印状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.printStatus === '已打印' ? 'success' : 'warning'" size="small">{{ row.printStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="printCount" label="打印次数" width="90" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handlePrint(row)">
              <el-icon :size="12" style="margin-right:2px"><Printer /></el-icon>打印
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;padding:16px 0 0">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @change="loadData"
        />
      </div>
    </el-card>

    <!-- 模板编辑对话框 -->
    <el-dialog v-model="templateDialogVisible" title="编辑打印模板" width="460px" append-to-body>
      <el-form :model="editingTemplate" label-width="80px">
        <el-form-item label="模板名称">
          <el-input v-model="editingTemplate.name" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="纸张规格">
          <el-select v-model="editingTemplate.paper" style="width: 200px">
            <el-option label="A4" value="A4" />
            <el-option label="A5" value="A5" />
            <el-option label="热敏纸 80x80mm" value="thermal-80" />
            <el-option label="热敏纸 100x150mm" value="thermal-100" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板说明">
          <el-input v-model="editingTemplate.desc" type="textarea" :rows="2" placeholder="请输入模板说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveTemplate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 打印内容区：Teleport 至 body，仅打印时可见，触发 window.print() 输出 -->
    <Teleport to="body">
      <!-- 通用模板：拣货单/打包单/发货单/配货标签 -->
      <div class="print-area" v-if="printingRow && !isShippingLabelMode">
        <div class="print-sheet" :style="printSheetStyle">
          <div class="print-header">
            <h2>{{ currentTemplate ? currentTemplate.name : '打印单' }}</h2>
            <span class="print-time">打印时间：{{ printTime }}</span>
          </div>
          <table class="print-table">
            <tbody>
              <tr><th>订单编号</th><td>{{ printingRow.orderNo }}</td></tr>
              <tr><th>收件人</th><td>{{ printingRow.receiver }}</td></tr>
              <tr><th>商品信息</th><td>{{ printingRow.productInfo }}</td></tr>
              <tr><th>下单时间</th><td>{{ printingRow.createTime }}</td></tr>
              <tr><th>纸张 / 份数</th><td>{{ printSettings.paperSize }} / {{ printSettings.copies }} 份</td></tr>
            </tbody>
          </table>
          <div class="print-footer">MOYUYO 订单打印系统</div>
        </div>
      </div>
      <!-- 快递面单模板：渲染燕文等承运商返回的 PDF/PNG base64。
           支持单条 + 批量（shippingLabels 是数组）。 -->
      <div class="print-area print-area--shipping" v-if="isShippingLabelMode && shippingLabels.length">
        <!-- 批量打单：每个订单渲染一份面单，每份按 printSettings.copies 复制多联 -->
        <div
          v-for="label in shippingLabels"
          :key="label.orderId"
          class="print-batch-group">
          <div v-for="(_, idx) in printSettings.copies" :key="label.orderId + '-copy-' + idx" class="print-sheet print-sheet--shipping">
            <!-- 燕文取号成功：用 iframe 嵌入 PDF 或 img 嵌入 PNG -->
            <iframe
              v-if="label.dataUrl && label.contentType === 'application/pdf'"
              :src="label.dataUrl"
              class="shipping-iframe"
              :title="'燕文快递面单 ' + label.orderNo"
            ></iframe>
            <img
              v-else-if="label.dataUrl"
              :src="label.dataUrl"
              class="shipping-image"
              :alt="'快递面单 ' + label.orderNo"
            />
            <!-- 燕文未启用 / 取号失败：使用通用面单模板（手动打印兜底） -->
            <div v-else class="shipping-fallback">
              <div class="shipping-fallback-title">快递面单（兜底）</div>
              <table class="print-table">
                <tbody>
                  <tr><th>订单号</th><td>{{ label.orderNo }}</td></tr>
                  <tr><th>承运商</th><td>{{ printingRow?.shippingCarrier || '—' }}</td></tr>
                  <tr><th>运单号</th><td>{{ printingRow?.trackingNumber || '—' }}</td></tr>
                  <tr><th>收件人</th><td>{{ printingRow?.receiver || '—' }}</td></tr>
                  <tr><th>商品</th><td>{{ printingRow?.productInfo || '—' }}</td></tr>
                  <tr><th>取号失败</th><td>{{ label.error || '—' }}</td></tr>
                  <tr><th>打印时间</th><td>{{ printTime }}</td></tr>
                </tbody>
              </table>
              <div class="print-footer">MOYUYO · 手动打印（请配置燕文 SDK 后获取电子面单）</div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 快递面单打印预览面板：常驻显示，方便用户预检后再触发 window.print() -->
    <el-card v-if="showShippingPreview" shadow="never" class="section-card shipping-preview">
      <template #header>
        <div class="section-header">
          <span class="section-title">📦 燕文快递面单预览（{{ shippingLabels.length }} 张）</span>
          <div style="display:flex;gap:8px;align-items:center;">
            <span v-if="shippingLabelError" class="shipping-error">{{ shippingLabelError }}</span>
            <el-button size="small" type="primary" :disabled="!shippingLabels.length" @click="triggerBrowserPrint">
              <el-icon :size="12" style="margin-right:2px"><Printer /></el-icon>浏览器打印
            </el-button>
            <el-button size="small" @click="closeShippingPreview">关闭</el-button>
          </div>
        </div>
      </template>
      <div class="shipping-preview-body">
        <!-- 批量打单时使用 Tab 切换展示每张面单 -->
        <template v-if="shippingLabels.length > 1">
          <el-tabs v-model="activeShippingTab" type="card" class="shipping-tabs">
            <el-tab-pane
              v-for="label in shippingLabels"
              :key="label.orderId"
              :name="String(label.orderId)">
              <template #label>
                <span :class="{ 'shipping-tab-error': label.error }">
                  {{ label.orderNo || '#' + label.orderId }}
                  <span v-if="label.error" style="color:var(--state-error);">⚠</span>
                </span>
              </template>
              <div class="shipping-pane">
                <div v-if="label.error" class="shipping-error-banner">取号失败：{{ label.error }}</div>
                <iframe
                  v-if="label.dataUrl && label.contentType === 'application/pdf'"
                  :src="label.dataUrl"
                  class="shipping-preview-iframe"
                ></iframe>
                <img
                  v-else-if="label.dataUrl"
                  :src="label.dataUrl"
                  class="shipping-preview-image"
                  :alt="'快递面单 ' + label.orderNo"
                />
              </div>
            </el-tab-pane>
          </el-tabs>
        </template>
        <!-- 单张面单直接占满预览区 -->
        <template v-else-if="shippingLabels.length === 1">
          <div class="shipping-pane">
            <div v-if="shippingLabels[0].error" class="shipping-error-banner">取号失败：{{ shippingLabels[0].error }}</div>
            <iframe
              v-if="shippingLabels[0].dataUrl && shippingLabels[0].contentType === 'application/pdf'"
              :src="shippingLabels[0].dataUrl"
              class="shipping-preview-iframe"
            ></iframe>
            <img
              v-else-if="shippingLabels[0].dataUrl"
              :src="shippingLabels[0].dataUrl"
              class="shipping-preview-image"
              :alt="'快递面单 ' + shippingLabels[0].orderNo"
            />
          </div>
        </template>
        <!-- 加载中或无数据 -->
        <div v-else-if="shippingLabelLoading" class="shipping-loading">正在调取燕文面单…</div>
        <div v-else class="shipping-empty">
          <div style="font-size:32px;margin-bottom:6px;">📭</div>
          <div>未取到面单数据</div>
          <div style="font-size:11px;color:var(--text-400);margin-top:4px;">请检查后端 moyuyo.logistics.yanwen.* 配置与运单号</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { List, Box, DocumentChecked, PriceTag, Check, Edit, Star, Printer, Promotion } from '@element-plus/icons-vue'
import {
  getPrintList, recordPrint, getPrintDetail, fetchShippingLabel, getYanwenStatus
} from '../api/admin'
import { toArray } from '../utils/safeArray'

const route = useRoute()
const router = useRouter()

const page = ref(1)
const pageSize = ref(10)
const printType = ref('order')
const total = ref(0)

const filters = reactive({
  keyword: '',
  printStatus: ''
})

const tableData = ref([])

// ==================== 打印模板（示例数据：后端暂无模板接口，先用结构化示例数据展示） ====================
const printTemplates = ref([
  { id: 1, name: '拣货单', type: '拣货单', paper: 'A4', desc: '按商品汇总，含货位 / SKU / 数量', gradient: 'linear-gradient(135deg, #e8f2ff, #cfe5ff)', color: '#2e8dff', icon: List, tagType: 'primary', isDefault: true, requiresShipping: false },
  { id: 2, name: '打包单', type: '打包单', paper: 'A5', desc: '按订单展示商品明细，放入包裹', gradient: 'linear-gradient(135deg, #f7f7fa, #e5e5ea)', color: '#8e8e93', icon: Box, tagType: 'info', isDefault: false, requiresShipping: false },
  { id: 3, name: '发货单', type: '发货单', paper: 'A4', desc: '含收件人信息 / 订单号 / 商品清单', gradient: 'linear-gradient(135deg, #fff7ed, #ffedd5)', color: '#c2410c', icon: DocumentChecked, tagType: 'warning', isDefault: false, requiresShipping: false },
  { id: 4, name: '配货标签', type: '配货标签', paper: '热敏 100x150mm', desc: '地址标签，可粘贴至包裹', gradient: 'linear-gradient(135deg, #f0fdf4, #dcfce7)', color: '#16a34a', icon: PriceTag, tagType: 'success', isDefault: false, requiresShipping: false },
  { id: 5, name: '快递面单', type: '快递面单', paper: '热敏 100x150mm', desc: '燕文等承运商电子面单 PDF，启用 SDK 后可直接调取并打印', gradient: 'linear-gradient(135deg, #fdf2f8, #fce7f3)', color: '#db2777', icon: Promotion, tagType: 'danger', isDefault: false, requiresShipping: true }
])
const selectedTemplateId = ref(1)

// 当前选中的模板
const currentTemplate = computed(() => printTemplates.value.find(t => t.id === selectedTemplateId.value))

// 燕文 SDK 是否启用（控制"快递面单"模板的可用性）
const yanwenEnabled = ref(false)

// 快递面单模板标识（id=5）：仅在当前选中快递面单模板时启用
const isShippingLabelMode = computed(() => selectedTemplateId.value === 5)

// 快递面单预览面板状态
const showShippingPreview = ref(false)
const shippingLabelLoading = ref(false)
// 改为数组：单条 / 多条场景统一处理
// 单条订单时 length=1，多条时 length=N（用于"批量打单"）
const shippingLabels = ref([])
// 当前查看中面单对应的 orderId（用于定位错误信息）
const shippingLabelError = ref('')

// 当前订单详情（从 query 传入或列表行传入）
const currentOrderDetail = ref(null)

// 标记"已在 loadOrderFromQuery 中主动拉过面单"，避免 watch 重复触发
const isLoadingFromQuery = ref(false)

// 兼容旧模板引用（仍按"第一条面单"渲染打印内容区）
const shippingLabelDataUrl = computed(() => shippingLabels.value[0]?.dataUrl || '')
const shippingLabelContentType = computed(() => shippingLabels.value[0]?.contentType || 'application/pdf')

// 当前预览面板激活的 Tab（批量打单时切换查看哪张面单）
const activeShippingTab = ref('')

// 选择打印模板
function selectTemplate(tpl) {
  selectedTemplateId.value = tpl.id
}

// 模板编辑（示例数据：仅修改本地数据，待后端模板接口接入）
const templateDialogVisible = ref(false)
const editingTemplate = ref({})

function handleEditTemplate(tpl) {
  editingTemplate.value = { ...tpl }
  templateDialogVisible.value = true
}

function handleSaveTemplate() {
  const target = printTemplates.value.find(t => t.id === editingTemplate.value.id)
  if (target) {
    target.name = editingTemplate.value.name
    target.paper = editingTemplate.value.paper
    target.desc = editingTemplate.value.desc
  }
  templateDialogVisible.value = false
  ElMessage.success('模板已保存（示例数据，未持久化到后端）')
}

// 设为默认模板
function handleSetDefault(tpl) {
  printTemplates.value.forEach(t => { t.isDefault = t.id === tpl.id })
  ElMessage.success('已将「' + tpl.name + '」设为默认模板')
}

// ==================== 打印设置（示例数据：本地默认值，保存后写入 localStorage） ====================
const printSettings = reactive({
  paperSize: 'A4',
  copies: 1,
  orientation: 'portrait',
  duplex: false,
  marginY: 10,
  marginX: 15
})

const SETTINGS_STORAGE_KEY = 'orderPrintSettings'

// 保存打印设置（示例数据实现，待接入后端设置接口）
function handleSaveSettings() {
  try {
    localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify({ ...printSettings }))
    ElMessage.success('打印设置已保存')
  } catch (error) {
    console.error('保存打印设置失败:', error)
    ElMessage.warning('保存失败：浏览器本地存储不可用')
  }
}

// 读取本地保存的打印设置
function loadSettings() {
  try {
    const saved = JSON.parse(localStorage.getItem(SETTINGS_STORAGE_KEY))
    if (saved && typeof saved === 'object') {
      Object.assign(printSettings, saved)
    }
  } catch (error) {
    // 本地无历史设置或解析失败时，使用默认值即可
  }
}

// ==================== 待打印订单列表 ====================
// 加载打印列表数据
async function loadData() {
  try {
    const res = await getPrintList({ printType: printType.value, page: page.value, size: pageSize.value })
    // 响应结构：{ list: [], total: number }
    const list = toArray(res)
    // 客户端关键字过滤
    const kw = filters.keyword.toLowerCase()
    let filtered = list
    if (kw) {
      filtered = filtered.filter(d => (d.orderNo || '').toLowerCase().includes(kw))
    }
    if (filters.printStatus) {
      filtered = filtered.filter(d => d.printStatus === filters.printStatus)
    }
    total.value = res && res.total != null ? res.total : filtered.length
    tableData.value = filtered
  } catch (error) {
    console.error('获取打印数据失败:', error)
    ElMessage.error('获取打印数据失败')
  }
}

function handleSearch() { page.value = 1; loadData() }

function handleReset() { filters.keyword = ''; filters.printStatus = ''; handleSearch() }

// ==================== 打印相关 ====================
// 当前待打印订单（用于打印内容区渲染）
const printingRow = ref(null)
const printTime = ref('')

// 按纸张规格计算打印内容区宽度（热敏纸窄幅，A4/A5 自适应）
const printSheetStyle = computed(() => {
  if (printSettings.paperSize === 'thermal-80') return { width: '80mm' }
  if (printSettings.paperSize === 'thermal-100') return { width: '100mm' }
  return {}
})

// 打印订单：先记录打印（保留原 recordPrint API 调用），再调用 window.print() 触发浏览器真实打印
async function handlePrint(row) {
  try {
    const tpl = currentTemplate.value
    // 兼容：如果选中"快递面单"模板，且行有运单号，自动调燕文取号
    if (isShippingLabelMode.value) {
      await fetchShippingLabelForOrder(row)
      // 取号失败不阻断，仍然可走通用模板兜底
      if (!shippingLabelDataUrl.value) {
        ElMessage.warning('燕文取号失败，使用通用面单模板打印（请检查 SDK 配置 / 运单号）')
      }
    }
    // 后端 recordPrint 只读取 orderId/printType/templateName/paperSize，其余字段不提交
    await recordPrint({
      orderId: row.id,
      printType: printType.value,
      templateName: tpl ? tpl.name : '默认模板',
      paperSize: printSettings.paperSize
    })
    ElMessage.success('打印任务已记录，订单：' + row.orderNo)
    // 准备打印内容并进入打印模式
    printingRow.value = row
    printTime.value = formatPrintTime()
    document.body.classList.add('print-mode')
    await nextTick()
    // 触发浏览器真实打印对话框（同步阻塞，关闭后继续执行）
    window.print()
  } catch (error) {
    console.error('提交打印任务失败:', error)
    ElMessage.error('提交打印任务失败')
  } finally {
    // 退出打印模式并刷新列表
    document.body.classList.remove('print-mode')
    printingRow.value = null
    loadData()
  }
}

// 批量打印：依次记录并打印全部待打印订单（示例数据简化实现）
async function handleBatchPrint() {
  const list = [...tableData.value]
  if (!list.length) return
  try {
    const tpl = currentTemplate.value
    for (const row of list) {
      await recordPrint({ orderId: row.id, printType: printType.value, templateName: tpl ? tpl.name : '批量打印', paperSize: printSettings.paperSize })
    }
    ElMessage.success('已记录 ' + list.length + ' 单打印任务，开始打印')
    printingRow.value = list[0]
    printTime.value = formatPrintTime()
    document.body.classList.add('print-mode')
    await nextTick()
    window.print()
  } catch (error) {
    console.error('批量提交打印任务失败:', error)
    ElMessage.error('批量提交打印任务失败')
  } finally {
    document.body.classList.remove('print-mode')
    printingRow.value = null
    loadData()
  }
}

// 拉取燕文 SDK 启用状态（页面加载时调用一次）
async function loadYanwenStatus() {
  try {
    const res = await getYanwenStatus()
    const data = res?.data || res
    yanwenEnabled.value = !!(data && data.enabled)
  } catch (e) {
    // 静默失败：状态用于 UI 提示
    yanwenEnabled.value = false
  }
}

// 从 query 加载订单详情（OrderList "打印快递单" 跳转时携带 orderId）
async function loadOrderFromQuery() {
  // 同时支持单条（?orderId=1）和批量（?ids=1,2,3）两种入口
  const singleId = Number(route.query.orderId)
  const idsStr = (route.query.ids || '').toString().trim()
  const batchIds = idsStr
    ? idsStr.split(',').map(s => Number(s.trim())).filter(n => !isNaN(n) && n > 0)
    : []
  const allIds = singleId ? [singleId] : batchIds
  if (!allIds.length) return

  // 标记"已经在加载时主动拉过面单"，避免 watch 监听 selectedTemplateId=5 重复触发
  isLoadingFromQuery.value = true
  // 自动选快递面单模板
  selectedTemplateId.value = 5

  // 把当前激活 Tab 切到第一张，方便用户切换
  activeShippingTab.value = String(allIds[0])

  // 单条场景：复用原有逻辑（保存到 currentOrderDetail 便于兼容兜底渲染）
  if (singleId) {
    try {
      const res = await getPrintDetail(singleId)
      const data = res?.data || res
      if (data && data.id) {
        currentOrderDetail.value = data
        await fetchShippingLabelForOrder({
          id: data.id,
          orderNo: data.orderNo,
          receiver: data.receiverName,
          productInfo: (data.items || []).map(it => `${it.productName} x${it.quantity}`).join('; '),
          createTime: data.createTime,
          shippingCarrier: data.shippingCarrier,
          trackingNumber: data.trackingNumber
        })
      }
    } catch (e) {
      console.warn('加载订单打印详情失败：', e?.message || e)
    } finally {
      isLoadingFromQuery.value = false
    }
    return
  }

  // 批量场景：循环并发拉取（Promise.allSettled 防止单条失败影响全部）
  try {
    const results = await Promise.allSettled(
      allIds.map(async (id) => {
        const res = await getPrintDetail(id)
        const data = res?.data || res
        if (!data || !data.id) throw new Error('订单 ' + id + ' 详情为空')
        return {
          id: data.id,
          orderNo: data.orderNo,
          receiver: data.receiverName,
          productInfo: (data.items || []).map(it => `${it.productName} x${it.quantity}`).join('; '),
          createTime: data.createTime,
          shippingCarrier: data.shippingCarrier,
          trackingNumber: data.trackingNumber
        }
      })
    )
    // 对拉取成功的订单挨个调燕文 SDK（fetchShippingLabelForOrder 内部已经 try/catch）
    for (const r of results) {
      if (r.status === 'fulfilled' && r.value) {
        await fetchShippingLabelForOrder(r.value)
      }
    }
  } catch (e) {
    console.warn('批量加载订单打印详情失败：', e?.message || e)
  } finally {
    isLoadingFromQuery.value = false
  }
}

// 取订单的快递电子面单（燕文），追加到数组（支持批量）
async function fetchShippingLabelForOrder(row) {
  if (!row || !row.id) return
  showShippingPreview.value = true
  shippingLabelLoading.value = true
  // 单条/批量场景：根据是否已有"该 orderId 的条目"决定是覆盖还是新增
  const idx = shippingLabels.value.findIndex(s => s.orderId === row.id)
  try {
    const res = await fetchShippingLabel(row.id, undefined)
    const data = res?.data || res
    // P1-2：后端返回 base64String + contentType（不再返回 dataUrl），前端自己拼。
    // 这样响应体小了约 33%，批量 5 张从 ~1.5MB 降到 ~1MB。
    if (data && data.base64String) {
      const contentType = data.contentType || 'application/pdf'
      const item = {
        orderId: row.id,
        orderNo: row.orderNo || row.id,
        dataUrl: 'data:' + contentType + ';base64,' + data.base64String,
        contentType,
        sizeBytes: data.sizeBytes || 0,
        error: ''
      }
      if (idx >= 0) shippingLabels.value.splice(idx, 1, item)
      else shippingLabels.value.push(item)
      shippingLabelError.value = ''
    } else {
      const err = (data && data.message) || '燕文未返回面单数据'
      if (idx >= 0) {
        shippingLabels.value[idx] = { ...shippingLabels.value[idx], error: err }
      }
      shippingLabelError.value = err
    }
  } catch (e) {
    const err = e?.message || '取面单失败，请确认后端 SDK 配置'
    if (idx >= 0) {
      shippingLabels.value[idx] = { ...shippingLabels.value[idx], error: err }
    }
    shippingLabelError.value = err
  } finally {
    shippingLabelLoading.value = false
  }
}

// 关闭面单预览面板
function closeShippingPreview() {
  showShippingPreview.value = false
  shippingLabels.value = []
  shippingLabelError.value = ''
}

// 直接触发浏览器打印（不依赖 printingRow，适合在预览面板中触发）
async function triggerBrowserPrint() {
  // 兜底：如果没有当前行，先用 currentOrderDetail 渲染
  if (!printingRow.value && currentOrderDetail.value) {
    const d = currentOrderDetail.value
    printingRow.value = {
      id: d.id,
      orderNo: d.orderNo,
      receiver: d.receiverName,
      productInfo: (d.items || []).map(it => `${it.productName} x${it.quantity}`).join('; '),
      createTime: d.createTime,
      shippingCarrier: d.shippingCarrier,
      trackingNumber: d.trackingNumber
    }
  }
  if (!printingRow.value) {
    ElMessage.warning('请先选择要打印的订单')
    return
  }
  // P1-3：固定 YYYY-MM-DD HH:mm:ss，规避浏览器/locale 差异
  printTime.value = formatPrintTime()
  document.body.classList.add('print-mode')
  await nextTick()
  window.print()
  setTimeout(() => {
    document.body.classList.remove('print-mode')
  }, 500)
}

/**
 * P1-3：把打印时间固定为 YYYY-MM-DD HH:mm:ss。
 * 之前用 new Date().toLocaleString()，不同浏览器的 locale 会输出
 *   "2026/9/29 14:23:45"、"9/29/2026, 2:23:45 PM" 等，
 * 导致打印输出不一致。统一格式化便于审计。
 */
function formatPrintTime() {
  const d = new Date()
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// 当切换到"快递面单"模板时，若已有当前订单则自动取号
// 跳过 silent 期间跳过 isLoadingFromQuery=true 的触发（避免 loadOrderFromQuery 已拉过的二次取）
watch(selectedTemplateId, (val) => {
  if (val === 5
      && !isLoadingFromQuery.value
      && currentOrderDetail.value
      && !shippingLabelDataUrl.value
      && !shippingLabelLoading.value) {
    fetchShippingLabelForOrder({
      id: currentOrderDetail.value.id,
      orderNo: currentOrderDetail.value.orderNo,
      shippingCarrier: currentOrderDetail.value.shippingCarrier,
      trackingNumber: currentOrderDetail.value.trackingNumber
    })
  }
})

onMounted(() => {
  loadSettings()
  loadData()
  loadYanwenStatus()
  // 如果路由 query 带 orderId，从 OrderList 跳过来则自动加载订单详情
  loadOrderFromQuery()
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.section-card { margin-bottom: 16px; }
.section-header { display: flex; align-items: center; justify-content: space-between; }
.section-title { font-size: 14px; font-weight: 600; color: var(--text-800); }
.section-tip { font-size: 12px; color: var(--text-400); }

/* ===== 打印模板卡片 ===== */
.template-grid { display: flex; gap: 16px; flex-wrap: wrap; }
.template-card {
  width: 200px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 14px;
  cursor: pointer;
  background: var(--card);
  transition: border-color .18s ease, box-shadow .18s ease;
}
.template-card:hover { border-color: var(--brand-300); box-shadow: var(--shadow-md); }
.template-card.selected { border-color: var(--primary); box-shadow: 0 0 0 2px var(--primary); }
.template-thumb {
  position: relative;
  width: 100%;
  height: 80px;
  border-radius: calc(var(--radius) - 4px);
  margin-bottom: 12px;
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
}
.badge {
  position: absolute;
  top: 8px;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
}
.default-badge { left: 8px; background: var(--brand-50); color: var(--brand-700); }
.selected-badge { right: 8px; background: var(--primary); color: var(--primary-foreground); }
.unavailable-badge {
  left: 8px;
  top: 32px;
  background: rgba(255, 59, 48, 0.1);
  color: var(--state-error);
  border: 1px solid rgba(255, 59, 48, 0.3);
}
.template-name { font-size: 14px; font-weight: 600; color: var(--text-800); margin-bottom: 6px; }
.template-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.template-paper { font-size: 12px; color: var(--text-400); }
.template-desc { font-size: 12px; color: var(--text-400); line-height: 1.4; margin-bottom: 8px; min-height: 34px; }
.template-actions { display: flex; gap: 4px; }

/* ===== 打印设置表单 ===== */
.settings-form { padding-top: 4px; }
.unit-text { margin-left: 6px; font-size: 12px; color: var(--text-400); }
.settings-footer { display: flex; justify-content: flex-end; margin-top: 4px; padding-top: 14px; border-top: 1px solid var(--border); }

/* ===== 打印内容区：屏幕隐藏，仅打印时显示（Teleport 至 body） ===== */
.print-area { display: none; }
.print-sheet { background: #fff; padding: 20px; font-family: var(--font-sans); color: var(--text-800); }
.print-header { display: flex; align-items: center; justify-content: space-between; border-bottom: 2px solid var(--text-800); padding-bottom: 12px; margin-bottom: 16px; }
.print-header h2 { font-size: 18px; font-weight: 700; margin: 0; }
.print-time { font-size: 12px; color: var(--text-400); }
.print-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.print-table th, .print-table td { border: 1px solid var(--border); padding: 8px 12px; text-align: left; }
.print-table th { background: var(--background-100); font-weight: 600; width: 110px; }
.print-footer { margin-top: 24px; text-align: center; font-size: 12px; color: var(--text-400); }

@media print {
  .print-area { display: block !important; }
  /* 快递面单打印：去掉页边距，PDF/PNG 自带 100mm 宽度 */
  body.print-mode { @page { size: 100mm 150mm; margin: 0; } }
}

/* ===== 快递面单预览 ===== */
.shipping-preview { border-color: var(--brand-200); }
.shipping-preview-body {
  min-height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--background-100);
  border-radius: var(--radius);
  overflow: hidden;
}
.shipping-preview-iframe {
  width: 100%;
  height: 540px;
  border: none;
  background: #fff;
}
.shipping-preview-image {
  max-width: 100%;
  max-height: 540px;
  background: #fff;
  padding: 12px;
  box-sizing: border-box;
}
.shipping-loading {
  padding: 60px 20px;
  font-size: 13px;
  color: var(--text-500);
}
.shipping-tabs {
  width: 100%;
}
.shipping-tabs :deep(.el-tabs__header) { margin-bottom: 8px; }
.shipping-tabs :deep(.el-tabs__item) { font-size: 12px; padding: 0 12px; }
.shipping-tab-error { color: var(--state-error); font-weight: 600; }
.shipping-pane { padding: 4px; }
.shipping-error-banner {
  padding: 6px 12px;
  margin-bottom: 8px;
  background: var(--state-error-surface);
  color: var(--state-error);
  border-radius: 6px;
  font-size: 12px;
}
.print-batch-group {
  /* 批量打单：每个订单独立分组，按订单分页（page-break-after） */
  page-break-after: always;
}
.print-batch-group:last-child { page-break-after: auto; }
.shipping-empty {
  padding: 60px 20px;
  font-size: 14px;
  color: var(--text-500);
  text-align: center;
}
.shipping-error {
  font-size: 12px;
  color: var(--state-error);
  background: var(--state-error-surface);
  padding: 2px 10px;
  border-radius: 999px;
}

/* ===== 打印区（Teleport 到 body） ===== */
.print-area--shipping { display: none; }
.print-sheet--shipping {
  width: 100mm;
  height: 150mm;
  padding: 0;
  margin: 0 auto;
  background: #fff;
  box-sizing: border-box;
  page-break-after: always;
}
.shipping-iframe {
  width: 100mm;
  height: 150mm;
  border: none;
  display: block;
}
.shipping-image {
  width: 100mm;
  height: 150mm;
  object-fit: contain;
  display: block;
}
.shipping-fallback {
  padding: 8mm;
  font-family: var(--font-sans);
  color: var(--text-800);
  height: 100%;
  box-sizing: border-box;
}
.shipping-fallback-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 8mm;
  border-bottom: 2px solid var(--text-800);
  padding-bottom: 4mm;
}

@media print {
  .print-area { display: block !important; }
  /* 快递面单打印：去掉页边距，PDF/PNG 自带 100mm 宽度 */
  body.print-mode.printing-shipping @page { size: 100mm 150mm; margin: 0; }
}
</style>

<style>
/* 打印模式（非 scoped）：隐藏后台整体布局，仅保留 Teleport 到 body 的打印内容区 */
@media print {
  body.print-mode .admin-layout { display: none !important; }
  body.print-mode #app { display: none !important; }
  @page { size: A4; margin: 10mm; }
}
/* 快递面单打印时切换纸张 */
@media print {
  body.print-mode.printing-shipping { @page { size: 100mm 150mm; margin: 0; } }
}
</style>
