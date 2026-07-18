/**
 * 管理后台 API 模块
 * 映射所有后端 Admin Controller 端点
 */
import api from './index'

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

export function getFinanceRecords() {
  return api.get('/finance/records')
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

export function deletePush(id) {
  return api.delete(`/push/${id}`)
}

// ==================== 工单 ====================
export function getTicketList() {
  return api.get('/ticket/list')
}

export function getTicketStats() {
  return api.get('/ticket/stats')
}

export function getTicketDetail(id) {
  return api.get(`/ticket/${id}`)
}

export function assignTicket(id, data) {
  return api.put(`/ticket/${id}/assign`, data)
}

export function updateTicketStatus(id, data) {
  return api.put(`/ticket/${id}/status`, data)
}

export function replyTicket(id, data) {
  return api.post(`/ticket/${id}/reply`, data)
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

export function getMarketingEffects() {
  return api.get('/marketing/effects')
}

// ==================== 投诉管理 ====================
export function getComplaintList() {
  return api.get('/complaint/list')
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
export function getPriceList() {
  return api.get('/price/list')
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

export function togglePrice(id) {
  return api.put(`/price/${id}/toggle`)
}

// ==================== 订单运营 ====================
export function getOrderOpsStats() {
  return api.get('/order-ops/stats')
}

export function getOrderOpsExport() {
  return api.get('/order-ops/export')
}

export function batchShip(data) {
  return api.post('/order-ops/batch-ship', data)
}

export function updateOrderRemark(id, data) {
  return api.put(`/order-ops/${id}/remark`, data)
}

// ==================== 物流 ====================
export function getWarehouses() {
  return api.get('/logistics/warehouses')
}

export function createWarehouse(data) {
  return api.post('/logistics/warehouses', data)
}

export function updateWarehouse(id, data) {
  return api.put(`/logistics/warehouses/${id}`, data)
}

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

export function getClearance() {
  return api.get('/logistics/clearance')
}

export function getCustoms() {
  return api.get('/logistics/customs')
}

export function getShippingStrategies() {
  return api.get('/logistics/shipping-strategies')
}

export function createCarrier(data) {
  return api.post('/logistics/carriers', data)
}

export function updateCarrier(id, data) {
  return api.put(`/logistics/carriers/${id}`, data)
}

export function deleteCarrier(id) {
  return api.delete(`/logistics/carriers/${id}`)
}

export function createClearance(data) {
  return api.post('/logistics/clearance', data)
}

export function updateClearance(id, data) {
  return api.put(`/logistics/clearance/${id}`, data)
}

export function deleteClearance(id) {
  return api.delete(`/logistics/clearance/${id}`)
}

export function createCustoms(data) {
  return api.post('/logistics/customs', data)
}

export function updateCustoms(id, data) {
  return api.put(`/logistics/customs/${id}`, data)
}

export function deleteCustoms(id) {
  return api.delete(`/logistics/customs/${id}`)
}

export function createMergePackage(data) {
  return api.post('/logistics/merge-packages', data)
}

export function updateMergePackage(id, data) {
  return api.put(`/logistics/merge-packages/${id}`, data)
}

export function deleteMergePackage(id) {
  return api.delete(`/logistics/merge-packages/${id}`)
}

export function createOverseasWarehouse(data) {
  return api.post('/logistics/overseas', data)
}

export function updateOverseasWarehouse(id, data) {
  return api.put(`/logistics/overseas/${id}`, data)
}

export function deleteOverseasWarehouse(id) {
  return api.delete(`/logistics/overseas/${id}`)
}

export function createShippingStrategy(data) {
  return api.post('/logistics/shipping-strategies', data)
}

export function updateShippingStrategy(id, data) {
  return api.put(`/logistics/shipping-strategies/${id}`, data)
}

export function deleteShippingStrategy(id) {
  return api.delete(`/logistics/shipping-strategies/${id}`)
}

export function createSplitPackage(data) {
  return api.post('/logistics/split-packages', data)
}

export function updateSplitPackage(id, data) {
  return api.put(`/logistics/split-packages/${id}`, data)
}

export function deleteSplitPackage(id) {
  return api.delete(`/logistics/split-packages/${id}`)
}

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

// ==================== GDPR ====================
export function getGdprConsentRecords() {
  return api.get('/gdpr/consent-records')
}

export function getGdprDataRequests() {
  return api.get('/gdpr/data-requests')
}

export function processGdprRequest(id) {
  return api.put(`/gdpr/${id}/process`)
}

// ==================== 审计日志 ====================
export function getAuditLogList() {
  return api.get('/audit-log/list')
}

export function getAuditLogStats() {
  return api.get('/audit-log/stats')
}

// ==================== 分析 ====================
export function getFunnelAnalysis() {
  return api.get('/analysis/funnel')
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
export function getRoles() {
  return api.get('/rbac/roles')
}

export function getPermissions() {
  // 后端无独立权限端点，从角色权限中获取
  return api.get('/rbac/roles')
}

export function getAdminInfo() {
  // 后端无独立管理员信息端点
  return Promise.resolve({ name: 'Admin', email: 'admin@moyuyo.com', role: '超级管理员' })
}

export function getSecurityConfig() {
  // 后端无独立安全设置端点
  return Promise.resolve([])
}

export function getSystemInfo() {
  // 后端无独立系统信息端点
  return Promise.resolve({ version: '1.0.0', dbStatus: '正常', cacheStatus: '正常', lastBackup: '2026-07-18' })
}

export function getPaymentMethods() {
  // 后端无独立支付方式端点
  return Promise.resolve(['Stripe', 'PayPal'])
}

export function getActivePolicy() {
  // 后端无独立 GDPR 政策端点
  return Promise.resolve({ version: '3.0', effectiveDate: '2026-07-01', description: 'GDPR 隐私政策' })
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

// ==================== A/B 测试 ====================
export function updateAbTest(id, data) {
  return api.put(`/marketing/ab-tests/${id}`, data)
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

// ==================== 客服 ====================
export function getCsPerformance() {
  return api.get('/crm/cs-performance')
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

// ==================== 物流-CRUD ====================
export function deleteWarehouse(id) {
  return api.delete(`/logistics/warehouses/${id}`)
}

export function deleteOverseasWarehouse(id) {
  return api.delete(`/logistics/overseas/${id}`)
}

export function deleteMergePackage(id) {
  return api.delete(`/logistics/merge-packages/${id}`)
}

export function deleteSplitPackage(id) {
  return api.delete(`/logistics/split-packages/${id}`)
}

export function deleteCarrier(id) {
  return api.delete(`/logistics/carriers/${id}`)
}

export function deleteClearance(id) {
  return api.delete(`/logistics/clearance/${id}`)
}

export function deleteCustoms(id) {
  return api.delete(`/logistics/customs/${id}`)
}

export function deleteShippingStrategy(id) {
  return api.delete(`/logistics/shipping-strategies/${id}`)
}

export function createShippingStrategy(data) {
  return api.post('/logistics/shipping-strategies', data)
}

export function updateShippingStrategy(data) {
  return api.put('/logistics/shipping-strategies', data)
}

export function createCarrier(data) {
  return api.post('/logistics/carriers', data)
}

export function updateCarrier(data) {
  return api.put('/logistics/carriers', data)
}

export function createClearance(data) {
  return api.post('/logistics/clearance', data)
}

export function updateClearance(data) {
  return api.put('/logistics/clearance', data)
}

export function createCustoms(data) {
  return api.post('/logistics/customs', data)
}

export function updateCustoms(data) {
  return api.put('/logistics/customs', data)
}
