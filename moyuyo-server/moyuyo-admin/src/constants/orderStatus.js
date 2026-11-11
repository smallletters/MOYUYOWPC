/**
 * 订单状态中文映射（管理端单一事实来源）
 *
 * 口径来源：
 * 1. 后端 OrderStatusEnum 的状态值与展示名；
 * 2. APP 端 i18n 的 orderStatus 文案。
 * 新增订单状态时只需改这里，避免各页面各写一套导致同一状态显示不同中文。
 */
export const ORDER_STATUS_TEXT = {
  PENDING_PAY: '待付款',
  PAID: '已支付',
  PENDING_SHIP: '待发货',
  SHIPPED: '已发货',
  IN_TRANSIT: '运输中',
  DELIVERED: '已送达',
  RECEIVED: '已收货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  // 历史数据存在 CANCELED 拼写，保留兼容
  CANCELED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
  EXCHANGING: '换货中',
  EXCHANGED: '已换货',
  HOLD: '已拦截'
}

/**
 * 状态值 → 中文标签。
 * 未知状态返回原文，便于发现后端新增状态未同步字典；空值返回 fallback。
 */
export function orderStatusText(status, fallback = '-') {
  if (!status) return fallback
  const code = String(status).toUpperCase()
  return ORDER_STATUS_TEXT[code] || status
}

/**
 * 状态语义色：success / warning / danger / info。
 * 各页面按自己的样式体系映射，例如 status-success / tag-green / el-tag type。
 * <p>
 * 划分口径：待处理类（待付款/待发货/退款中/换货中/拦截）用 warning；
 * 履约进行中（已支付/已发货/运输中）用 info；已签收/完成用 success；取消/退款完成用 danger。
 */
export function orderStatusTone(status) {
  const code = String(status || '').toUpperCase()
  if (code === 'COMPLETED' || code === 'RECEIVED' || code === 'DELIVERED'
      || code === 'EXCHANGED') {
    return 'success'
  }
  if (code === 'CANCELLED' || code === 'CANCELED' || code === 'REFUNDED') {
    return 'danger'
  }
  if (code === 'PENDING_PAY' || code === 'PENDING_SHIP'
      || code === 'REFUNDING' || code === 'EXCHANGING' || code === 'HOLD') {
    return 'warning'
  }
  return 'info'
}
