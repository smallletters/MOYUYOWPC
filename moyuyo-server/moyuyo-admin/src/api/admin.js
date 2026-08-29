/**
 * 管理后台 API 模块
 * 映射所有后端 Admin Controller 端点
 */
import api, { API_BASE_URL } from './index'

/**
 * 共享 API 根地址重导出（定义见 ./index，避免循环依赖与多处定义）。
 * 供 el-upload 等不走 axios 的场景拼接完整 URL，避免被前端 base='/admin/' 干扰。
 */
export { API_BASE_URL }

/**
 * 函数式 API：返回后端 API 根地址。
 * <p>
 * 与常量 API_BASE_URL 等价；保留函数形式便于将来按需切换。
 */
export function getApiBaseUrl() {
  return API_BASE_URL
}

// ==================== 仪表盘 ====================
export function getDashboardStats() {
  return api.get('/dashboard/stats')
}

export function getRecentOrders() {
  return api.get('/dashboard/recent-orders')
}

export function getSalesTrend() {
  return api.get('/dashboard/sales-trend')
}

// ==================== RBAC 权限管理 ====================
export function getRbacRoles() {
  return api.get('/rbac/roles')
}

export function createRbacRole(data) {
  return api.post('/rbac/roles', data)
}

export function updateRbacRole(id, data) {
  return api.put(`/rbac/roles/${id}`, data)
}

export function deleteRbacRole(id) {
  return api.delete(`/rbac/roles/${id}`)
}

export function getRbacRoleMembers(id) {
  return api.get(`/rbac/roles/${id}/members`)
}

export function getRolePermissions(id) {
  return api.get(`/rbac/roles/${id}/permissions`)
}

export function updateRolePermissions(id, data) {
  return api.put(`/rbac/roles/${id}/permissions`, data)
}

export function getRbacUsers() {
  return api.get('/rbac/users')
}

export function createRbacUser(data) {
  return api.post('/rbac/users', data)
}

export function resetRbacUserPassword(id, data) {
  return api.post(`/rbac/users/${id}/reset-password`, data)
}

// 删除管理员账号（仅删除本人当前操作可访问的角色下的成员；后端会校验是否 SUPER_ADMIN）
export function deleteRbacUser(id) {
  return api.delete(`/rbac/users/${id}`)
}

export function updateRbacUser(id, data) {
  return api.put(`/rbac/users/${id}`, data)
}

// ==================== CMS 内容管理 ====================
export function getCmsList() {
  return api.get('/cms/list')
}

export function getCmsDetail(id) {
  return api.get(`/cms/${id}`)
}

export function createCms(data) {
  return api.post('/cms/create', data)
}

export function updateCms(data) {
  return api.put('/cms/update', data)
}

export function deleteCms(id) {
  return api.delete(`/cms/${id}`)
}

export function updateCmsStatus(id, data) {
  return api.put(`/cms/${id}/status`, data)
}

export function reorderCms(data) {
  return api.put('/cms/reorder', data)
}

// ==================== 财务 ====================
export function getFinanceOverview() {
  return api.get('/finance/overview')
}

export function getSettlements() {
  return api.get('/finance/settlements')
}

export function getSettlementDetail(id) {
  return api.get(`/finance/settlements/${id}`)
}

export function createSettlement(data) {
  return api.post('/finance/settlements', data)
}

export function updateSettlement(id, data) {
  return api.put(`/finance/settlements/${id}`, data)
}

export function deleteSettlement(id) {
  return api.delete(`/finance/settlements/${id}`)
}

export function getFinanceRecords(params) {
  return api.get('/finance/records', { params })
}

// 结算管理页面专用：按渠道汇总最近 Payout
export function getPayoutChannels() {
  return api.get('/finance/payout-channels')
}

// 结算管理页面专用：对账异常告警
export function getReconcileAlerts() {
  return api.get('/finance/reconcile-alerts')
}

// 结算管理页面专用：退款 KPI（总额/笔数/待处理/已完成）
export function getRefundKpi() {
  return api.get('/finance/refund-kpi')
}

// ==================== 退款管理 ====================
export function getRefundStats() {
  return api.get('/refunds/stats')
}

export function getRefundList(params) {
  return api.get('/refunds/list', { params })
}

export function getRefundReasonDistribution() {
  return api.get('/refunds/reason-distribution')
}

// 按 type 维度查询各状态的精确计数（用于管理后台 chip 角标）
// type 为可选参数，传 null/空表示统计所有类型
export function getRefundStatusCount(params) {
  return api.get('/refunds/status-count', { params })
}

export function approveRefund(id) {
  return api.put(`/refunds/${id}/approve`)
}

export function rejectRefund(id) {
  return api.put(`/refunds/${id}/reject`)
}

export function completeRefund(id, transactionId) {
  return api.put(`/refunds/${id}/complete`, null, { params: { transactionId } })
}

export function getRefundDetail(id) {
  return api.get(`/refunds/${id}`)
}

export function batchApproveRefund(data) {
  return api.put('/refunds/batch-approve', data)
}

// ==================== 库存 ====================
export function getInventoryOverview() {
  return api.get('/inventory/overview')
}

export function getInventoryAlerts() {
  return api.get('/inventory/alerts')
}

export function getInventoryList(params) {
  return api.get('/inventory/list', { params })
}

export function getInventoryBatches(params) {
  return api.get('/inventory/batches', { params })
}

export function updateStock(id, data) {
  return api.put(`/inventory/${id}/stock`, data)
}

export function checkInventory(data) {
  return api.post('/inventory/check', data)
}

// ==================== 推送管理 ====================
export function getPushStats() {
  return api.get('/push/stats')
}

export function getPushRecords() {
  return api.get('/push/records')
}

export function getPushScheduled() {
  return api.get('/push/scheduled')
}

export function createPush(data) {
  return api.post('/push/create', data)
}

export function sendPush(id) {
  return api.post(`/push/${id}/send`)
}

export function cancelPush(id) {
  return api.post(`/push/${id}/cancel`)
}

export function schedulePush(id, data) {
  return api.post(`/push/schedule`, { ...data, id })
}

export function getPushDetail(id) {
  return api.get(`/push/${id}`)
}

export function updatePush(id, data) {
  return api.put(`/push/${id}`, data)
}

export function cancelScheduledPush(id) {
  return api.post(`/push/scheduled/${id}/cancel`)
}

export function deletePush(id) {
  return api.delete(`/push/${id}`)
}

// ==================== 工单 ====================
export function getTicketList(params) {
  return api.get('/ticket/list', { params })
}

export function getTicketStats() {
  return api.get('/ticket/stats')
}

export function getTicketDetail(id) {
  return api.get(`/ticket/${id}`)
}

export function assignTicket(id, data) {
  // 后端 DTO 期望 assigneeId，前端传 assignee 时自动映射
  const payload = { assigneeId: data.assignee || data.assigneeId }
  return api.put(`/ticket/${id}/assign`, payload)
}

export function updateTicketStatus(id, data) {
  return api.put(`/ticket/${id}/status`, data)
}

export function replyTicket(id, data) {
  return api.post(`/ticket/${id}/reply`, data)
}

export function getTicketMessages(id) {
  return api.get(`/ticket/${id}/messages`)
}

// ==================== 营销 ====================
export function getCampaigns() {
  return api.get('/marketing/campaigns')
}

export function createCampaign(data) {
  return api.post('/marketing/campaigns', data)
}

export function getCampaignDetail(id) {
  return api.get(`/marketing/campaigns/${id}`)
}

export function updateCampaign(id, data) {
  return api.put(`/marketing/campaigns/${id}`, data)
}

export function deleteCampaign(id) {
  return api.delete(`/marketing/campaigns/${id}`)
}

export function getAbTests() {
  return api.get('/marketing/ab-tests')
}

export function createAbTest(data) {
  return api.post('/marketing/ab-tests', data)
}

export function updateAbTest(id, data) {
  return api.put('/marketing/ab-tests/' + id, data)
}

export function getMarketingEffects(params) {
  return api.get('/marketing/effects', { params })
}

// 营销效果 - 优惠券维度
export function getCouponEffects(params) {
  return api.get('/marketing/effects/coupon', { params })
}

// 营销效果 - 秒杀维度
export function getFlashEffects(params) {
  return api.get('/marketing/effects/flash', { params })
}

// 营销效果 - 分销维度
export function getDistributionEffects(params) {
  return api.get('/marketing/effects/distribution', { params })
}

// ==================== 投诉管理 ====================
export function getComplaintList() {
  return api.get('/complaint/list')
}

export function createComplaint(data) {
  return api.post('/complaint/create', data)
}

export function getComplaintDetail(id) {
  return api.get(`/complaint/${id}`)
}

export function startComplaintProcess(id) {
  return api.post(`/complaint/${id}/start-process`)
}

export function closeComplaint(id) {
  return api.post(`/complaint/${id}/close`)
}

export function assignComplaint(id, data) {
  return api.put(`/complaint/${id}/assign`, data)
}

// ==================== 评价审核 ====================
export function getReviewList() {
  return api.get('/review/list')
}

export function approveReview(id) {
  return api.put(`/review/${id}/approve`)
}

export function rejectReview(id) {
  return api.put(`/review/${id}/reject`)
}

export function replyReview(id, data) {
  return api.post(`/review/${id}/reply`, data)
}

// ==================== 商品管理 ====================
export function getProductList(params) {
  return api.get('/products/list', { params })
}

export function getProductDetail(id) {
  return api.get(`/products/${id}`)
}

export function createProduct(data) {
  return api.post('/products/create', data)
}

export function updateProduct(id, data) {
  return api.put(`/products/${id}`, data)
}

export function toggleProductStatus(id) {
  return api.put(`/products/${id}/status`)
}

export function batchProductAction(data) {
  return api.post('/products/batch', data)
}

export function deleteProduct(id) {
  return api.delete(`/products/${id}`)
}

// 商品 WooCommerce 双向同步：从 WC 拉取 / 推送到 WC / 批量推送
export function syncProductFromWoo() {
  return api.post('/products/sync-from-woo')
}

export function pushProductToWoo(id) {
  return api.post(`/products/${id}/push-to-woo`)
}

export function pushAllProductsToWoo() {
  return api.post('/products/push-all-to-woo')
}

export function pullProductFromWoo(id) {
  return api.post(`/products/${id}/pull-from-woo`)
}

export function syncProductStock(id) {
  return api.post(`/products/${id}/sync-stock`)
}

export function syncAllStocksFromWoo() {
  return api.post('/products/sync-stock-from-woo')
}

export function getCategoryList() {
  return api.get('/products/categories')
}

// 关联商品弹窗使用：按关键字搜索轻量商品列表（仅返回 id/name/sku/price/mainImage）
export function searchProductsLite(params) {
  return api.get('/products/lite', { params })
}

// ==================== 文件上传 ====================
// 单张图片上传：返回 { url, filename, originalName, size, contentType }
export function uploadImage(file) {
  const fd = new FormData()
  fd.append('file', file)
  return api.post('/upload/image', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}

// 批量图片上传：files 为 File[]
export function uploadImages(files) {
  const fd = new FormData()
  for (const f of files || []) fd.append('files', f)
  return api.post('/upload/images', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}

// 删除已上传图片：url 为后端返回的相对路径（如 /uploads/2026/08/18/abc.png）
export function deleteImage(url) {
  return api.delete('/upload/image', { params: { url } })
}

/**
 * 通过 axios 上传图片（绕过 el-upload 的 action 机制，避免 Vite base 干扰）。
 * <p>
 * 使用场景：组件需要自定义上传逻辑（如多文件、拖拽、粘贴），不适合用 el-upload action。
 */
export function uploadImagesViaAxios(files) {
  const fd = new FormData()
  for (const f of files) fd.append('files', f)
  return api.post('/upload/images', fd)
}

/**
 * 通过 axios 上传单张图片（用于富文本编辑器图片插入等场景）。
 */
export function uploadImageViaAxios(file) {
  const fd = new FormData()
  fd.append('file', file)
  return api.post('/upload/image', fd)
}

// ==================== 产品分析 ====================
export function getProductAnalysisKpi() {
  return api.get('/product-analysis/kpi')
}

export function getProductAnalysisList() {
  return api.get('/product-analysis/list')
}

export function getProductAnalysisReport() {
  return api.get('/product-analysis/report')
}

// ==================== 价格管理 ====================
export function getPriceList(params) {
  return api.get('/price/list', { params })
}

export function createPrice(data) {
  return api.post('/price/create', data)
}

export function updatePrice(data) {
  return api.put('/price/update', data)
}

export function deletePrice(id) {
  return api.delete(`/price/${id}`)
}

export function getPriceHistory(params) {
  return api.get('/price/history', { params })
}

export function togglePrice(id) {
  return api.put(`/price/${id}/toggle`)
}

// ==================== 订单运营 ====================
export function getOrderOpsStats() {
  return api.get('/order-ops/stats')
}

export function getOrderOpsExport(params) {
  return api.get('/order-ops/export', { params })
}

export function createOrderExport(data) {
  return api.post('/order-ops/export/create', data)
}

export function downloadExportFile(exportId) {
  return api.get(`/order-ops/export/download/${exportId}`)
}

export function batchShip(data) {
  return api.post('/order-ops/batch-ship', data)
}

export function updateOrderRemark(id, data) {
  return api.put(`/order-ops/${id}/remark`, data)
}

// ==================== 订单打印 ====================
export function getPrintList(params) {
  return api.get('/order-ops/print/list', { params })
}

export function recordPrint(data) {
  return api.post('/order-ops/print/record', data)
}

// ==================== 订单改价 ====================
export function getPriceModifyList(params) {
  return api.get('/order-ops/price-modify/list', { params })
}

export function createPriceModify(data) {
  return api.post('/order-ops/price-modify/create', data)
}

// ==================== 订单拦截 ====================
export function getInterceptList(params) {
  return api.get('/order-ops/intercept/list', { params })
}

export function createIntercept(data) {
  return api.post('/order-ops/intercept/create', data)
}

export function releaseIntercept(id, data) {
  return api.post(`/order-ops/intercept/release/${id}`, data)
}

// ==================== 订单监控 ====================
export function getMonitorData() {
  return api.get('/order-ops/monitor/data')
}

export function getAbnormalOrders(params) {
  return api.get('/order-ops/monitor/list', { params })
}

// ==================== 物流 ====================
// 物流CRUD工厂 — 统一生成 create/update/delete 函数
const logisticsCrud = (entity) => ({
  create: (data) => api.post(`/logistics/${entity}`, data),
  update: (id, data) => api.put(`/logistics/${entity}/${id}`, data),
  delete: (id) => api.delete(`/logistics/${entity}/${id}`)
})

export function getWarehouses() {
  return api.get('/logistics/warehouses')
}

export const { create: createWarehouse, update: updateWarehouse, delete: deleteWarehouse } = logisticsCrud('warehouses')

export function getOverseasWarehouse() {
  return api.get('/logistics/overseas')
}

export function getMergePackages() {
  return api.get('/logistics/merge-packages')
}

export function getSplitPackages() {
  return api.get('/logistics/split-packages')
}

export function getCarriers() {
  return api.get('/logistics/carriers')
}

export function getClearance(params = {}) {
  return api.get('/logistics/clearance/docs', { params })
}

export function getCustoms() {
  return api.get('/logistics/customs')
}

export function getShippingStrategies() {
  return api.get('/logistics/shipping-strategies')
}

export function getLogisticsKpi() {
  return api.get('/logistics/kpi')
}

export function getLogisticsPackages(params) {
  return api.get('/logistics/packages', { params })
}

// 物流商
export const { create: createCarrier, update: updateCarrier, delete: deleteCarrier } = logisticsCrud('carriers')
// 清关
export const { create: createClearance, update: updateClearance, delete: deleteClearance } = logisticsCrud('clearance')
// 海关
export const { create: createCustoms, update: updateCustoms, delete: deleteCustoms } = logisticsCrud('customs')
// 合并包裹
export const { create: createMergePackage, update: updateMergePackage, delete: deleteMergePackage } = logisticsCrud('merge-packages')
// 海外仓
export const { create: createOverseasWarehouse, update: updateOverseasWarehouse, delete: deleteOverseasWarehouse } = logisticsCrud('overseas')
// 配送策略
export const { create: createShippingStrategy, update: updateShippingStrategy, delete: deleteShippingStrategy } = logisticsCrud('shipping-strategies')
// 拆包
export const { create: createSplitPackage, update: updateSplitPackage, delete: deleteSplitPackage } = logisticsCrud('split-packages')

export function syncCustoms(id) {
  return api.post(`/logistics/${id}/customs/sync`)
}

// ==================== 短信 ====================
export function getSmsStats() {
  return api.get('/sms/stats')
}

export function getSmsRecords() {
  return api.get('/sms/records')
}

export function sendSms(data) {
  return api.post('/sms/send', data)
}

// ==================== 敏感词 ====================
export function getSensitiveList() {
  return api.get('/sensitive/list')
}

export function getSensitiveCategories() {
  return api.get('/sensitive/categories')
}

export function createSensitive(data) {
  return api.post('/sensitive/create', data)
}

export function updateSensitive(data) {
  return api.put('/sensitive/update', data)
}

export function deleteSensitive(id) {
  return api.delete(`/sensitive/${id}`)
}

export function batchDeleteSensitive(data) {
  return api.post('/sensitive/batch-delete', data)
}

// ==================== 风控 ====================
export function getRiskRules() {
  return api.get('/risk/rules')
}

export function createRiskRule(data) {
  return api.post('/risk/rules', data)
}

export function updateRiskRule(id, data) {
  return api.put(`/risk/rules/${id}`, data)
}

export function toggleRiskRule(id, data) {
  return api.put(`/risk/rules/${id}/status`, data)
}

export function deleteRiskRule(id) {
  return api.delete(`/risk/rules/${id}`)
}

export function getRiskEvents() {
  return api.get('/risk/events')
}

export function getRiskEventStats() {
  return api.get('/risk/event-stats')
}

// ==================== 满意度 ====================
export function getSatisfactionStats() {
  return api.get('/satisfaction/stats')
}

export function getSatisfactionList() {
  return api.get('/satisfaction/list')
}

export function createSatisfactionSurvey(data) {
  return api.post('/satisfaction/surveys', data)
}

export function replySatisfactionSurvey(id, data) {
  return api.put(`/satisfaction/${id}/reply`, data)
}

// ==================== GDPR ====================
export function getGdprConsentRecords() {
  return api.get('/gdpr/consent-records')
}

export function getGdprDataRequests() {
  return api.get('/gdpr/data-requests')
}

export function processGdprRequest(id, data) {
  return api.put(`/gdpr/${id}/process`, data)
}

export function createGdprPolicy(data) {
  return api.post('/gdpr/policy', data)
}

// 未成年人保护 - 真实后端
export function getGdprMinorStats() {
  return api.get('/gdpr/minor/stats')
}

export function getGdprMinorList(limit = 50) {
  return api.get('/gdpr/minor/list', { params: { limit } })
}

export function reverifyGdprMinor(userId) {
  return api.post(`/gdpr/minor/${userId}/reverify`)
}

export function getGdprConsentProofs(userId) {
  return api.get('/gdpr/minor/consent-proofs', { params: { userId } })
}

export function getGdprArchiveOverview() {
  return api.get('/gdpr/minor/archive-overview')
}

export function triggerGdprQuickAction(data) {
  return api.post('/gdpr/quick-action', data)
}

// 通知中心 - 真实后端
export function getAdminNotifications(limit = 20) {
  return api.get('/notifications', { params: { limit } })
}

export function getAdminUnreadCount() {
  return api.get('/notifications/unread-count')
}

export function markNotificationRead(id) {
  return api.post(`/notifications/${id}/read`)
}

export function markAllNotificationsRead() {
  return api.post('/notifications/read-all')
}

// 清关 / 仓库 KPI / 智能分配 / 拆包裹策略 - 真实后端
export function getClearanceOverview() {
  return api.get('/logistics/clearance/overview')
}

export function getClearanceExceptions(limit = 20) {
  return api.get('/logistics/clearance/exceptions', { params: { limit } })
}

export function getClearanceDocs(params = {}) {
  return api.get('/logistics/clearance/docs', { params })
}

export function getClearanceDailyDays(days = 7) {
  return api.get('/logistics/clearance/daily-days', { params: { days } })
}

export function getClearanceCountryCompare() {
  return api.get('/logistics/clearance/country-compare')
}

export function getWarehouseKpi() {
  return api.get('/logistics/warehouse/kpi')
}

export function getWarehouseAllocationSuggest() {
  return api.get('/logistics/warehouse/allocation-suggest')
}

export function applyWarehouseAllocation(id) {
  return api.post(`/logistics/warehouse/allocation-suggest/${id}/apply`)
}

export function getWarehouseCategoryStocks(warehouse) {
  return api.get('/logistics/warehouse/category-stocks', { params: { warehouse } })
}

export function getWarehouseSmartPicks(limit = 6) {
  return api.get('/logistics/warehouse/smart-picks', { params: { limit } })
}

export function getSplitPackageVersions() {
  return api.get('/logistics/split-package/versions')
}

export function getSplitPackageRules(versionId) {
  return api.get('/logistics/split-package/rules', { params: { versionId } })
}

// 直播监控规则 / 违规告警 - 真实后端
export function getLiveMonitorRules() {
  return api.get('/live/monitor-rules')
}

export function getLiveViolationAlerts(limit = 20) {
  return api.get('/live/violation-alerts', { params: { limit } })
}

export function handleLiveAlert(id) {
  return api.post(`/live/violation-alerts/${id}/handle`)
}

// 活动草稿 - 真实后端
export function saveCampaignDraft(data) {
  return api.post('/marketing/campaigns/draft', data)
}

// 用户画像 6 图表 - 真实后端
export function getUserProfileSpendTrend(userId) {
  return api.get(`/user-profile/${userId}/charts/spend-trend`)
}

export function getUserProfileFunnel(userId) {
  return api.get(`/user-profile/${userId}/charts/funnel`)
}

export function getUserProfileCategory(userId) {
  return api.get(`/user-profile/${userId}/charts/category`)
}

export function getUserProfileActiveHours(userId) {
  return api.get(`/user-profile/${userId}/charts/active-hours`)
}

export function getUserProfileDevice(userId) {
  return api.get(`/user-profile/${userId}/charts/device`)
}

export function getUserProfileChannel(userId) {
  return api.get(`/user-profile/${userId}/charts/channel`)
}

// ==================== 审计日志 ====================
export function getAuditLogList() {
  return api.get('/audit-log/list')
}

export function getAuditLogStats() {
  return api.get('/audit-log/stats')
}

// ==================== 分析 ====================
export function getFunnelAnalysis(params) {
  return api.get('/analysis/funnel', { params })
}

export function getChurnAnalysis(params) {
  return api.get('/analysis/churn', { params })
}

export function getRepurchaseAnalysis(params) {
  return api.get('/analysis/repurchase', { params })
}

export function getRfmAnalysis() {
  return api.get('/analysis/rfm')
}

export function getSearchAnalysis() {
  return api.get('/analysis/search')
}

export function getTrafficAnalysis() {
  return api.get('/analysis/traffic')
}

// ==================== App版本 ====================
export function getAppVersionList() {
  return api.get('/app-version/list')
}

export function createAppVersion(data) {
  return api.post('/app-version/create', data)
}

export function updateAppVersion(data) {
  return api.put('/app-version/update', data)
}

export function publishAppVersion(id) {
  return api.post(`/app-version/${id}/publish`)
}

export function rollbackAppVersion(id) {
  return api.post(`/app-version/${id}/rollback`)
}

export function deleteAppVersion(id) {
  return api.delete(`/app-version/${id}`)
}

// ==================== 系统配置 ====================
export function getSystemConfig() {
  return api.get('/system/config')
}

export function saveSystemConfig(data) {
  return api.put('/system/config', data)
}

export function getSystemLogs() {
  return api.get('/system/logs')
}

// ==================== 系统设置（角色/权限/管理员信息等）====================
// getRbacRoles 是主入口，位于上方 RBAC 区域

// 权限管理（获取权限列表）
export function getPermissions() {
  return api.get('/rbac/permissions')
}

// 注意：getAdminInfo 定义在 auth.js 中，从此模块导入时请从 auth.js 引入
export function getSecurityConfig() {
  return api.get('/system-info/security-config')
}

export function saveSecurityConfig(data) {
  return api.put('/system-info/security-config', data)
}

export function getSystemInfo() {
  return api.get('/system-info/info')
}

export function getPaymentMethods() {
  return api.get('/settings/payment-methods')
}

export function getActivePolicy() {
  return api.get('/gdpr/policy')
}

// ==================== 批量导入 ====================
export function getImportRecords(params) {
  return api.get('/batch-import/records', { params })
}

export function submitImport(data) {
  return api.post('/batch-import/import', data)
}

export function getImportTemplate(type) {
  return api.get(`/batch-import/template/${type}`)
}

export function getImportErrors(id) {
  return api.get(`/batch-import/${id}/errors`)
}

export function deleteImportRecord(id) {
  return api.delete(`/batch-import/${id}`)
}

// ==================== 直播 ====================
export function getLiveRooms() {
  return api.get('/live/rooms')
}

export function createLiveRoom(data) {
  return api.post('/live/rooms', data)
}

export function getLiveRoomDetail(id) {
  return api.get(`/live/rooms/${id}`)
}

export function updateLiveRoom(id, data) {
  return api.put(`/live/rooms/${id}`, data)
}

export function updateLiveRoomStatus(id, data) {
  return api.put(`/live/rooms/${id}/status`, data)
}

export function deleteLiveRoom(id) {
  return api.delete(`/live/rooms/${id}`)
}

// ==================== 知识库 ====================
export function getKnowledgeList() {
  return api.get('/knowledge-base/list')
}

export function createKnowledge(data) {
  return api.post('/knowledge-base/create', data)
}

export function updateKnowledge(data) {
  return api.put('/knowledge-base/update', data)
}

export function deleteKnowledge(id) {
  return api.delete(`/knowledge-base/${id}`)
}

// ==================== 用户画像 ====================
export function getUserProfile(userId) {
  return api.get(`/user-profile/${userId}`)
}

export function getUserBehaviors(userId) {
  return api.get(`/user-profile/${userId}/behaviors`)
}

export function getUserOrderHistory(userId) {
  return api.get(`/user-profile/${userId}/orders`)
}

// 用户访问过的商品列表
export function getUserVisitedProducts(userId, size = 50) {
  return api.get(`/user-profile/${userId}/visited-products`, { params: { size } })
}

// 用户访问过的页面列表
export function getUserVisitedPages(userId, size = 50) {
  return api.get(`/user-profile/${userId}/visited-pages`, { params: { size } })
}

// 管理员手动调整用户积分
export function adjustUserPoints(userId, amount, reason) {
  return api.post(`/points/users/${userId}/adjust`, { amount, reason })
}

// ==================== 客服 ====================
export function getCsPerformance() {
  return api.get('/crm/cs-performance')
}

// 客服列表（不分页）：用于工单转交下拉选择
export function getCsStaff() {
  return api.get('/crm/cs-staff')
}

export function getCsDetail(agentId) {
  return api.get(`/crm/${agentId}/cs-detail`)
}

export function getRealtimeData() {
  return api.get('/crm/realtime')
}

export function getRealtimeOrderFlow() {
  return api.get('/crm/realtime-order-flow')
}

export function getRealtimeTopProducts() {
  return api.get('/crm/realtime/top-products')
}

// ==================== 商品审核 ====================
export function getProductApprovalList(params) { return api.get('/product-approval/list', { params }) }
export function getProductApprovalDetail(id) { return api.get(`/product-approval/${id}`) }
export function approveProductApproval(id) { return api.put(`/product-approval/${id}/approve`) }
export function rejectProductApproval(id, data) { return api.put(`/product-approval/${id}/reject`, data) }
export function setProductApprovalUrgent(id) { return api.put(`/product-approval/${id}/urgent`) }

// ==================== 内容审核 ====================
export function getContentReviewList(params) { return api.get('/content-review/list', { params }) }
export function getContentReviewDetail(id) { return api.get(`/content-review/${id}`) }
export function approveContentReview(id) { return api.put(`/content-review/${id}/approve`) }
export function rejectContentReview(id, data) { return api.put(`/content-review/${id}/reject`, data) }
export function hideContentReview(id) { return api.put(`/content-review/${id}/hide`) }
export function deleteContentReview(id) { return api.delete(`/content-review/${id}`) }
export function banContentReview(id, data) { return api.put(`/content-review/${id}/ban`, data) }
export function getContentReviewStats() { return api.get('/content-review/stats') }
export function getContentReviewTrend(params) { return api.get('/content-review/trend', { params }) }

/**
 * 灌入审核测试数据(用于演示违规类型筛选)。仅 dev 环境使用。
 */
export function seedContentReview() { return api.post('/content-review/seed') }

// ==================== 优惠券管理 ====================
export function getCouponList() { return api.get('/coupons/list') }
export function createCoupon(data) { return api.post('/coupons/create', data) }
export function updateCoupon(data) { return api.put('/coupons/update', data) }
export function deleteCoupon(id) { return api.delete(`/coupons/${id}`) }
export function getCouponStats() { return api.get('/coupons/stats') }

// ==================== 秒杀管理 ====================
export function getFlashSaleList() { return api.get('/flash-sales/list') }
export function createFlashSale(data) { return api.post('/flash-sales/create', data) }
export function updateFlashSale(data) { return api.put('/flash-sales/update', data) }
export function deleteFlashSale(id) { return api.delete(`/flash-sales/${id}`) }
export function updateFlashSaleStatus(id, data) { return api.put(`/flash-sales/${id}/status`, data) }
export function getFlashSaleStats() { return api.get('/flash-sales/stats') }

// ==================== 积分管理 ====================
export function getPointsActivities() { return api.get('/points/activities') }
export function createPointsActivity(data) { return api.post('/points/activities/create', data) }
export function deletePointsActivity(id) {
  return api.delete(`/points/activities/${encodeURIComponent(id)}`)
}
export function getPointsLogs(params) { return api.get('/points/logs', { params }) }
export function getPointsStats() { return api.get('/points/stats') }

// ==================== 黑名单 ====================
export function getBlacklistList(params) { return api.get('/blacklist/list', { params }) }
export function createBlacklist(data) { return api.post('/blacklist/create', data) }
export function batchCreateBlacklist(data) { return api.post('/blacklist/batch-create', data) }
export function deleteBlacklist(id) { return api.delete(`/blacklist/${id}`) }
export function updateBlacklist(id, data) { return api.put(`/blacklist/${id}`, data) }

// ==================== 关税管理 ====================
export function getTariffConfigs(params) { return api.get('/tariff/configs', { params }) }
export function createTariffConfig(data) { return api.post('/tariff/configs/create', data) }
export function updateTariffConfig(data) { return api.put('/tariff/configs/update', data) }
export function deleteTariffConfig(id) { return api.delete(`/tariff/configs/${id}`) }
export function calculateTariff(data) { return api.post('/tariff/calculate', data) }

// ==================== 客服会话 ====================
export function getCsSessionList(params) { return api.get('/cs-sessions/list', { params }) }
export function getCsSessionDetail(id) { return api.get(`/cs-sessions/${id}`) }
export function getCsSessionStats() { return api.get('/cs-sessions/stats') }

// ===== 客服在线聊天 =====
export function getCsSessionMessages(id) { return api.get(`/cs-sessions/${id}/messages`) }
export function pollCsSessionMessages(id, since) {
  return api.get(`/cs-sessions/${id}/messages/poll`, { params: since ? { since } : {} })
}
export function sendCsSessionMessage(id, payload) {
  return api.post(`/cs-sessions/${id}/messages`, payload)
}
export function markCsSessionRead(id) {
  return api.post(`/cs-sessions/${id}/messages/read`)
}
export function closeCsSession(id) {
  return api.post(`/cs-sessions/${id}/close`)
}
export function transferCsSession(id, payload) {
  return api.post(`/cs-sessions/${id}/transfer`, payload)
}

// ==================== 风控告警 ====================
export function getRiskAlertConfigs() { return api.get('/risk-alert/configs') }
export function createRiskAlertConfig(data) { return api.post('/risk-alert/configs/create', data) }
export function updateRiskAlertConfig(data) { return api.put('/risk-alert/configs/update', data) }
export function deleteRiskAlertConfig(id) { return api.delete(`/risk-alert/configs/${id}`) }
export function getRiskAlertHistory(params) { return api.get('/risk-alert/history', { params }) }

// ==================== 订单标签 ====================
export function getOrderTagList() { return api.get('/order-tags/list') }
export function createOrderTag(data) { return api.post('/order-tags/create', data) }
export function updateOrderTag(data) { return api.put('/order-tags/update', data) }
export function deleteOrderTag(id) { return api.delete(`/order-tags/${id}`) }
export function setOrderTags(orderId, data) { return api.post(`/order-tags/${orderId}/tags`, data) }
export function getOrderTags(orderId) { return api.get(`/order-tags/${orderId}/tags`) }

// ==================== 库存调拨 ====================
export function getInventoryTransferList(params) { return api.get('/inventory-transfer/list', { params }) }
export function createInventoryTransfer(data) { return api.post('/inventory-transfer/create', data) }
export function approveInventoryTransfer(id) { return api.put(`/inventory-transfer/${id}/approve`) }
export function rejectInventoryTransfer(id, data) { return api.put(`/inventory-transfer/${id}/reject`, data) }
export function completeInventoryTransfer(id) { return api.put(`/inventory-transfer/${id}/complete`) }

// ==================== 用户管理 ====================
export function getUserStats() { return api.get('/users/stats') }
export function getUserList(params) { return api.get('/users/list', { params }) }
export function getUserDetail(id) { return api.get(`/users/${id}`) }
export function updateUserStatus(id, data) { return api.put(`/users/${id}/status`, data) }
export function createUser(data) { return api.post('/users/create', data) }
export function updateUser(id, data) { return api.put(`/users/${id}`, data) }
export function deleteUser(id) { return api.delete(`/users/${id}`) }
export function resetUserPassword(id, data) { return api.post(`/users/${id}/reset-password`, data) }

// ==================== 订单管理（基础CRUD） ====================
export function getOrderList(params) { return api.get('/orders/list', { params }) }
export function getOrderDetail(id) { return api.get(`/orders/${id}`) }
export function updateOrderAddress(id, data) { return api.put(`/orders/${id}/address`, data) }
export function shipOrder(id, data) { return api.put(`/orders/${id}/ship`, data) }

// 管理后台订单列表"物流"弹窗：查询订单物流基础信息+轨迹 / 更新承运商运单号 / 获取承运商列表
export function getOrderLogistics(id) { return api.get(`/orders/${id}/logistics`) }
export function updateOrderLogistics(id, data) { return api.put(`/orders/${id}/logistics`, data) }
export function getCarrierOptions() { return getCarrierList() }

// 订单 WooCommerce 手动重推
export function syncOrderToWoo(id) { return api.post(`/orders/${id}/sync-to-woo`) }

// ==================== 订单监控规则 ====================
const monitorCrud = (base) => ({
  list: (params) => api.get(`${base}/rules`, { params }),
  create: (data) => api.post(`${base}/rules`, data),
  update: (id, data) => api.put(`${base}/rules/${id}`, data),
  toggle: (id, data) => api.put(`${base}/rules/${id}/status`, data),
  remove: (id) => api.delete(`${base}/rules/${id}`)
})

export const orderMonitorRuleApi = monitorCrud('/order-ops/monitor')


