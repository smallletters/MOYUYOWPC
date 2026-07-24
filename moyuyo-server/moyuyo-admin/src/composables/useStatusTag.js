/**
 * 通用状态标签辅助 composable
 * @param {Object} statusMap - 状态值到展示文本的映射
 * @param {Object} colorMap - 状态值到 el-tag type 的映射
 * @returns {{ getStatusText, getStatusType }}
 */
export function useStatusTag(statusMap = {}, colorMap = {}) {
  const defaultColorMap = { ...colorMap }

  function getStatusText(status) {
    return statusMap[status] || status || '未知'
  }

  function getStatusType(status) {
    return defaultColorMap[status] || 'info'
  }

  return { getStatusText, getStatusType }
}
