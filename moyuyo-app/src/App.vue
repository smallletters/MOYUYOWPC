<script>
import { useThemeStore } from '@/store'
import { useUserStore } from '@/store'

export default {
  onLaunch() {
    console.log('[MOYUYO] App Launch')

    // 主题初始化
    const themeStore = useThemeStore()
    themeStore.applyTheme()

    // 校验已保存的 Token 是否有效
    const userStore = useUserStore()
    if (userStore.token) {
      // 静默拉取用户信息，失败则强制登出
      userStore.fetchProfile().catch(() => userStore.forceLogout())
    }
  },
  onShow() {
    // 每次回到前台
  },
  onHide() {
    // 进入后台
  }
}
</script>

<style lang="scss">
@import '@/styles/common.scss';

/* uView Plus 全局样式（可选） */
/* @import 'uview-plus/index.scss'; */
</style>
