<template>
  <div class="after-sales-manage">
    <h2 class="page-title">售后管理</h2>

    <!-- 筛选 -->
    <div class="filter-bar">
      <div class="filter-item">
        <span class="filter-label">状态</span>
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 160px" @change="reload">
          <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </div>
      <div class="filter-item">
        <span class="filter-label">类型</span>
        <el-select v-model="filters.type" placeholder="全部类型" clearable style="width: 150px" @change="reload">
          <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </div>
      <el-button :icon="Refresh" @click="reload">刷新</el-button>
    </div>

    <!-- 列表 -->
    <el-table :data="records" v-loading="loading" stripe style="width: 100%">
      <el-table-column label="售后单号" prop="afterSaleNo" min-width="180">
        <template #default="{ row }">
          <span class="table-link" @click="openDetail(row)">{{ row.afterSaleNo }}</span>
        </template>
      </el-table-column>
      <el-table-column label="订单号" min-width="160">
        <template #default="{ row }">
          <span class="table-link" @click="openOrder(row.orderId)">
            {{ row.orderNo || row.orderId }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="商品" min-width="240">
        <template #default="{ row }">
          <div class="product-cell">
            <img
              v-if="row.mainImage"
              :src="resolveImageUrl(row.mainImage)"
              class="product-thumb"
              @error="onImageError"
            />
            <div class="product-meta">
              <div class="product-name">{{ row.productName || '-' }}</div>
              <div class="product-sub">{{ row.skuSpec || '' }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">{{ typeLabel(row.type) }}</template>
      </el-table-column>
      <el-table-column label="数量" prop="quantity" width="80" />
      <el-table-column label="退款金额" width="110">
        <template #default="{ row }">
          <span v-if="row.type === 'EXCHANGE'">无需退款</span>
          <span v-else>¥{{ row.amount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="退货物流" min-width="160">
        <template #default="{ row }">
          <div v-if="row.returnTrackingNo">
            <div>{{ row.returnCompany }}</div>
            <div class="tracking-no">{{ row.returnTrackingNo }}</div>
          </div>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" prop="createTime" width="170" />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button link type="primary" @click="handleApprove(row)">同意</el-button>
            <el-button link type="danger" @click="handleReject(row)">拒绝</el-button>
          </template>
          <template v-else-if="row.status === 'RETURNING'">
            <el-button link type="primary" @click="handleReceive(row)">确认收货</el-button>
            <el-button link @click="openLogistics(row)">补录运单</el-button>
          </template>
          <template v-else-if="row.status === 'APPROVED'">
            <el-button
              v-if="row.deliveryType === 'RETURN_SHIP'"
              link
              type="primary"
              @click="openLogistics(row)"
            >补录运单</el-button>
          </template>
          <el-button
            v-else-if="row.status === 'RECEIVED'"
            link
            type="primary"
            @click="openComplete(row)"
          >{{ row.type === 'EXCHANGE' ? '完成换货' : '完成退款' }}</el-button>
          <el-button link @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadList"
        @size-change="reload"
      />
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="售后详情" width="680px">
      <div v-if="detail" class="detail-body">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="售后单号">{{ detail.afterSaleNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail) }}</el-descriptions-item>
          <el-descriptions-item label="订单号">
            <span class="table-link" @click="openOrder(detail.orderId)">
              {{ detail.orderNo || detail.orderId }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="类型">{{ typeLabel(detail.type) }}</el-descriptions-item>
          <el-descriptions-item label="商品">{{ detail.productName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="规格">{{ detail.skuSpec || '-' }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ detail.quantity }}</el-descriptions-item>
          <el-descriptions-item label="退款金额">
            {{ detail.type === 'EXCHANGE' ? '无需退款' : `¥${detail.amount}` }}
          </el-descriptions-item>
          <el-descriptions-item label="售后原因" :span="2">{{ detail.reason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="问题描述" :span="2">
            {{ detail.description || '（无）' }}
          </el-descriptions-item>
          <el-descriptions-item label="退货方式" :span="2">
            {{ detail.deliveryType === 'RETURN_SHIP' ? '寄回商品' : '无需寄回' }}
          </el-descriptions-item>
          <el-descriptions-item label="退货物流" :span="2">
            <template v-if="detail.returnTrackingNo">
              {{ detail.returnCompany }} / {{ detail.returnTrackingNo }}
            </template>
            <template v-else>未填写</template>
          </el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ detail.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ detail.completeTime || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.refundNo" label="退款单号" :span="2">
            {{ detail.refundNo }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.rejectReason" label="拒绝原因" :span="2">
            <span class="rejected">{{ detail.rejectReason }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="detailImages.length" class="evidence">
          <div class="evidence-title">用户凭证</div>
          <div class="evidence-list">
            <el-image
              v-for="(img, idx) in detailImages"
              :key="idx"
              :src="resolveImageUrl(img)"
              :preview-src-list="detailImages.map(resolveImageUrl)"
              :initial-index="idx"
              fit="cover"
              class="evidence-img"
            />
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 补录/修正退货运单号（客服兜底） -->
    <el-dialog v-model="logisticsVisible" title="补录退货运单号" width="460px">
      <el-form label-width="90px">
        <el-form-item label="物流公司">
          <el-input v-model="logisticsForm.company" placeholder="如 UPS / USPS / FedEx" />
        </el-form-item>
        <el-form-item label="退货运单号">
          <el-input v-model="logisticsForm.trackingNo" placeholder="请输入退货运单号" />
        </el-form-item>
      </el-form>
      <div class="complete-tip">补录后售后单将进入"已寄回待收货"，用户可在 APP 查看并收到通知。</div>
      <template #footer>
        <el-button @click="logisticsVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitLogistics">保存</el-button>
      </template>
    </el-dialog>

    <!-- 完成退款/换货弹窗 -->
    <el-dialog v-model="completeVisible" title="完成售后" width="480px">
      <div v-if="completeRow" class="complete-body">
        <p class="complete-tip">
          {{ completeRow.type === 'EXCHANGE'
            ? '确认换货商品已寄出？确认后售后单将标记为已完成。'
            : `将为订单退款 ¥${completeRow.amount}，请选择退款方式。` }}
        </p>
        <template v-if="completeRow.type !== 'EXCHANGE'">
          <el-radio-group v-model="completeMode">
            <el-radio value="AUTO">自动原路退回</el-radio>
            <el-radio value="MANUAL">手动录入流水号</el-radio>
          </el-radio-group>
          <el-input
            v-if="completeMode === 'MANUAL'"
            v-model="completeTransactionId"
            placeholder="请输入第三方退款流水号"
            style="margin-top: 12px"
          />
        </template>
      </div>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitComplete">确认完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getAfterSalesList,
  getAfterSalesDetail,
  approveAfterSales,
  rejectAfterSales,
  receiveAfterSales,
  completeAfterSales,
  submitAfterSalesLogistics
} from '../api/admin'

const router = useRouter()

const statusOptions = [
  { value: 'PENDING', label: '待审核' },
  { value: 'APPROVED', label: '待寄回' },
  { value: 'RETURNING', label: '已寄回待收货' },
  { value: 'RECEIVED', label: '已收货/待退款' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'CANCELLED', label: '已取消' }
]

const typeOptions = [
  { value: 'RETURN_REFUND', label: '退货退款' },
  { value: 'REFUND_ONLY', label: '仅退款' },
  { value: 'EXCHANGE', label: '换货' }
]

const filters = reactive({ status: '', type: '' })
const page = ref(1)
const size = ref(10)
const total = ref(0)
const records = ref([])
const loading = ref(false)

const detailVisible = ref(false)
const detail = ref(null)
const completeVisible = ref(false)
const completeRow = ref(null)
const completeMode = ref('AUTO')
const completeTransactionId = ref('')
const submitting = ref(false)

const logisticsVisible = ref(false)
const logisticsRow = ref(null)
const logisticsForm = reactive({ company: '', trackingNo: '' })

const detailImages = computed(() => {
  if (!detail.value || !detail.value.images) return []
  try {
    const parsed = JSON.parse(detail.value.images)
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
})

function typeLabel(type) {
  if (type === 'RETURN_REFUND') return '退货退款'
  if (type === 'REFUND_ONLY') return '仅退款'
  if (type === 'EXCHANGE') return '换货'
  return type || '-'
}

function statusLabel(row) {
  if (!row) return '-'
  // 仅退款无寄回/收货环节，审核通过后统一显示"待退款"
  if (
    row.type === 'REFUND_ONLY' &&
    (row.status === 'RECEIVED' ||
      (row.status === 'APPROVED' && row.deliveryType === 'NO_RETURN'))
  ) {
    return '待退款'
  }
  const hit = statusOptions.find((s) => s.value === row.status)
  return hit ? hit.label : row.status || '-'
}

function statusTagType(status) {
  if (status === 'PENDING') return 'warning'
  if (status === 'COMPLETED') return 'success'
  if (status === 'REJECTED' || status === 'CANCELLED') return 'info'
  return 'primary'
}

function resolveImageUrl(path) {
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  return path.startsWith('/') ? path : `/${path}`
}

function onImageError(e) {
  if (e?.target) e.target.style.display = 'none'
}

async function loadList() {
  loading.value = true
  try {
    const res = await getAfterSalesList({
      page: page.value,
      size: size.value,
      status: filters.status || undefined,
      type: filters.type || undefined
    })
    records.value = (res && res.records) || []
    total.value = (res && res.total) || 0
  } catch (e) {
    // axios 拦截器已提示错误
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  loadList()
}

function openOrder(orderId) {
  if (orderId) router.push(`/orders/${orderId}`)
}

async function openDetail(row) {
  detailVisible.value = true
  detail.value = row
  try {
    detail.value = await getAfterSalesDetail(row.id)
  } catch (e) {
    // 保留列表行的基础信息
  }
}

async function handleApprove(row) {
  const content =
    row.type === 'REFUND_ONLY'
      ? '同意后该申请将进入待退款（无需寄回商品），确认同意？'
      : '同意后用户需寄回商品，确认同意该售后申请？'
  try {
    await ElMessageBox.confirm(content, '审核售后', { type: 'warning' })
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    await approveAfterSales(row.id, {
      deliveryType: row.type === 'REFUND_ONLY' ? 'NO_RETURN' : 'RETURN_SHIP'
    })
    ElMessage.success('已同意售后申请')
    loadList()
  } catch (e) {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

async function handleReject(row) {
  try {
    const result = await ElMessageBox.prompt('请输入拒绝原因', '拒绝售后', {
      inputPlaceholder: '例如：不符合退货条件',
      inputValidator: (value) => (value && value.trim() ? true : '请填写拒绝原因')
    })
    submitting.value = true
    await rejectAfterSales(row.id, result.value.trim())
    ElMessage.success('已拒绝售后申请')
    loadList()
  } catch (e) {
    // 用户取消或请求失败
  } finally {
    submitting.value = false
  }
}

async function handleReceive(row) {
  try {
    await ElMessageBox.confirm('确认已收到用户寄回的商品？', '确认收货', { type: 'warning' })
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    await receiveAfterSales(row.id)
    ElMessage.success('已确认收货')
    loadList()
  } catch (e) {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

/** 打开补录运单弹窗，默认带出已有物流信息便于修正 */
function openLogistics(row) {
  logisticsRow.value = row
  logisticsForm.company = row.returnCompany || ''
  logisticsForm.trackingNo = row.returnTrackingNo || ''
  logisticsVisible.value = true
}

async function submitLogistics() {
  if (!logisticsRow.value) return
  if (!logisticsForm.company.trim() || !logisticsForm.trackingNo.trim()) {
    ElMessage.warning('请填写物流公司与运单号')
    return
  }
  submitting.value = true
  try {
    await submitAfterSalesLogistics(logisticsRow.value.id, {
      company: logisticsForm.company.trim(),
      trackingNo: logisticsForm.trackingNo.trim()
    })
    ElMessage.success('退货运单号已保存')
    logisticsVisible.value = false
    loadList()
  } catch (e) {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

function openComplete(row) {
  completeRow.value = row
  completeMode.value = 'AUTO'
  completeTransactionId.value = ''
  completeVisible.value = true
}

async function submitComplete() {
  if (!completeRow.value) return
  const isExchange = completeRow.value.type === 'EXCHANGE'
  if (!isExchange && completeMode.value === 'MANUAL' && !completeTransactionId.value.trim()) {
    ElMessage.warning('请填写第三方退款流水号')
    return
  }
  submitting.value = true
  try {
    await completeAfterSales(completeRow.value.id, {
      mode: isExchange ? undefined : completeMode.value,
      transactionId:
        !isExchange && completeMode.value === 'MANUAL'
          ? completeTransactionId.value.trim()
          : undefined
    })
    ElMessage.success(isExchange ? '换货已完成' : '售后退款已完成')
    completeVisible.value = false
    loadList()
  } catch (e) {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.after-sales-manage {
  padding: 20px;
}

.page-title {
  margin: 0 0 16px;
  font-size: 20px;
  color: #1f2937;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  margin-bottom: 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 13px;
  color: #6b7280;
}

.product-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.product-thumb {
  width: 44px;
  height: 44px;
  border-radius: 6px;
  object-fit: cover;
  border: 1px solid #ebeef5;
  flex-shrink: 0;
}

.product-meta {
  min-width: 0;
}

.product-name {
  font-size: 13px;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-sub {
  font-size: 12px;
  color: #9ca3af;
}

.tracking-no {
  font-size: 12px;
  color: #6b7280;
}

.muted {
  color: #9ca3af;
}

.table-link {
  color: #2563eb;
  cursor: pointer;
}

.table-link:hover {
  text-decoration: underline;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-body {
  max-height: 62vh;
  overflow-y: auto;
}

.evidence {
  margin-top: 20px;
}

.evidence-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
  color: #1f2937;
}

.evidence-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.evidence-img {
  width: 96px;
  height: 96px;
  border-radius: 6px;
}

.rejected {
  color: #dc2626;
}

.complete-tip {
  margin: 0 0 16px;
  color: #4b5563;
  line-height: 1.6;
}
</style>
