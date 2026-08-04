/**
 * 前端 CSV 导出工具
 * 用于审计日志、流量分析等页面的"导出"按钮，纯前端生成 CSV 文件下载
 * @param {Array} rows 数据行（对象数组）
 * @param {Array} columns 列定义 [{ key, label }]
 * @param {string} filename 下载文件名
 * @returns {boolean} 是否成功导出
 */
export function exportCsv(rows, columns, filename) {
  if (!rows || rows.length === 0) {
    return false
  }
  // 生成表头
  const header = columns.map(c => c.label).join(',')
  // 生成数据行：逗号/引号/换行需转义，防止破坏 CSV 结构
  const body = rows.map(row =>
    columns.map(c => {
      const val = row[c.key] ?? ''
      const str = String(val).replace(/"/g, '""')
      return /[",\n]/.test(str) ? `"${str}"` : str
    }).join(',')
  ).join('\n')
  // 添加 BOM，保证 Excel 打开中文不乱码
  const blob = new Blob(['\uFEFF' + header + '\n' + body], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  link.click()
  URL.revokeObjectURL(link.href)
  return true
}
