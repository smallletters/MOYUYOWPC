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
      <div class="print-area" v-if="printingRow">
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
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { List, Box, DocumentChecked, PriceTag, Check, Edit, Star, Printer } from '@element-plus/icons-vue'
import { getPrintList, recordPrint } from '../api/admin'
import { toArray } from '../utils/safeArray'

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
  { id: 1, name: '拣货单', type: '拣货单', paper: 'A4', desc: '按商品汇总，含货位 / SKU / 数量', gradient: 'linear-gradient(135deg, #e8f2ff, #cfe5ff)', color: '#2e8dff', icon: List, tagType: 'primary', isDefault: true },
  { id: 2, name: '打包单', type: '打包单', paper: 'A5', desc: '按订单展示商品明细，放入包裹', gradient: 'linear-gradient(135deg, #f7f7fa, #e5e5ea)', color: '#8e8e93', icon: Box, tagType: 'info', isDefault: false },
  { id: 3, name: '发货单', type: '发货单', paper: 'A4', desc: '含收件人信息 / 订单号 / 商品清单', gradient: 'linear-gradient(135deg, #fff7ed, #ffedd5)', color: '#c2410c', icon: DocumentChecked, tagType: 'warning', isDefault: false },
  { id: 4, name: '配货标签', type: '配货标签', paper: '热敏 100x150mm', desc: '地址标签，可粘贴至包裹', gradient: 'linear-gradient(135deg, #f0fdf4, #dcfce7)', color: '#16a34a', icon: PriceTag, tagType: 'success', isDefault: false }
])
const selectedTemplateId = ref(1)

// 当前选中的模板
const currentTemplate = computed(() => printTemplates.value.find(t => t.id === selectedTemplateId.value))

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
    printTime.value = new Date().toLocaleString()
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
    printTime.value = new Date().toLocaleString()
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

onMounted(() => { loadSettings(); loadData() })
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
}
</style>

<style>
/* 打印模式（非 scoped）：隐藏后台整体布局，仅保留 Teleport 到 body 的打印内容区 */
@media print {
  body.print-mode .admin-layout { display: none !important; }
  body.print-mode #app { display: none !important; }
  @page { size: A4; margin: 10mm; }
}
</style>
