import { defineConfig, loadEnv } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// Vite 配置：uni-app 项目入口指向 src/main.js
export default defineConfig(({ mode }) => {
  // 手动加载当前 mode 下的环境变量（uni-app 默认 mode=production）
  // 拿到后用于下面的 define，把所有 process.env.VITE_* 在编译期字面量替换进 bundle
  // 这是因为 APP 端运行时 typeof process 通常是 undefined 或空对象，
  // 直接读 process.env.VITE_* 拿不到 .env 里写的值，必须靠 define 做静态替换。
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const processEnvDefine = Object.fromEntries(
    Object.entries(env).map(([k, v]) => [`process.env.${k}`, JSON.stringify(v)]),
  )

  return {
    plugins: [uni()],
    // 路径别名
    resolve: {
      alias: {
        '@': '/src',
      },
    },
    // 把 process.env.VITE_* 静态注入到 bundle；uni-app APP 端没有 dev server，
    // 运行时读 process.env 拿不到值，必须靠 define 在 build 期替换成字符串字面量
    define: {
      ...processEnvDefine,
    },
  // 强制 Sass 使用现代 JS API(sass.compile),消除 "legacy-js-api" DEPRECATION 警告
  // 同时传递 silenceDeprecations 给上游 sass:
  //   - import:项目里 uview-plus/index.scss 还在用 @import,这条以后会跟着 uview-plus 升级一起消
  //   - bogus-combinators:同一原因(uniapp 子组件 deep 选择器)
  // 这两条来自第三方依赖,我们改不动,只能从工具侧抹掉警告噪音
  css: {
    preprocessorOptions: {
      scss: {
        api: 'modern-compiler',
        silenceDeprecations: ['legacy-js-api', 'import', 'bogus-combinators'],
      },
    },
  },
  // dev 代理：所有 /api/v1/* 透明转发到 Spring Boot 8080，避免 5174 跨源访问 8080 的 CORS / 端口差异问题
  server: {
    port: 5174,
    host: '0.0.0.0',
    proxy: {
      // ===== C 端公开接口（无需鉴权） =====
      '/api/v1/cms': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/categories': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/products': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/community': { target: 'http://localhost:8080', changeOrigin: true },

      // ===== 用户域 =====
      '/api/v1/auth': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/users': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/member': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/points': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/missions': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/invites': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/coupons': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/feedback': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/addresses': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/wallet': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/notifications': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/browsing-history': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/invoices': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/gift-cards': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/favorites': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/pets': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/reviews': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/payments': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/refunds': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/after-sales': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/shares': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/recycle-bin': { target: 'http://localhost:8080', changeOrigin: true },

      // ===== 商品活动域 =====
      '/api/v1/cart': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/orders': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/file': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/lotteries': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/flash-sales': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/subscriptions': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/bargains': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/group-buys': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/bundle-deals': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/live-rooms': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/logistics': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/shipping': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/exchange': { target: 'http://localhost:8080', changeOrigin: true },

      // ===== 宠物 =====
      '/api/v1/pet-album': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/pet-diary': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/pet-weight': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/pet-dresser': { target: 'http://localhost:8080', changeOrigin: true },

      // ===== 静态资源 =====
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },

      // ===== 客服/帮助/设备/关注/黑名单/会员/营销 =====
      '/api/v1/cs': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/help': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/devices': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/follows': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/blocks': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/prime': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/affiliate': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/bookings': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/festivals': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/newuser': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/achievements': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/v1/reports': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  }
})
