/**
 * 主题页面 mixin（全局注册于 main.js）
 *
 * 解决的问题：使用 pages.json 默认导航栏（未设 navigationStyle:custom）的页面，
 * 顶部标题栏是客户端原生视图，**不参与 CSS 变量 / .theme-dark 的计算**，
 * 深色模式下仍会保持 pages.json globalStyle 里写死的白底。
 * 切主题时由 store 的 applyTheme() 改当前页，但每次导航到新页面时
 * H5/APP 都会按 pages.json 重建 head —— 必须在页面自己的生命周期里再同步一次。
 *
 * 为什么用 Vue 的 mounted 而不是 uni 的 onShow：
 * 全局 mixin 的非数组选项（onLoad/onShow 等 uni 页面钩子）会被页面自身定义的同名
 * 钩子「子覆盖父」挤掉，导致混入逻辑从未执行；Vue 生命周期钩子会合并为数组，安全。
 *
 * 为什么用 $options.__mpType 判别页面：
 * 全局 mixin 会混入所有组件，必须只对「页面」执行，避免每挂载一个子组件就调用一次。
 * 实测 uni-app 编译产物把页面标记放在 `__mpType`（H5 运行时 `$options.mpType` 为 undefined，
 * 只有 `__mpType === 'page'`），故两个字段都取一遍作为兼容。
 */
import { useThemeStore } from '@/store/theme'

export const themePageMixin = {
  mounted() {
    const mpType = this.$options.__mpType || this.$options.mpType
    if (mpType !== 'page') return
    // mounted 时页面 head DOM 已就绪，setNavigationBarColor 生效
    try {
      useThemeStore().syncNavigationBar()
    } catch (e) {
      /* pinia 未就绪或 custom navbar 页面，忽略 */
    }
  },
}
