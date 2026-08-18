import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import compression from 'vite-plugin-compression'

export default defineConfig({
  plugins: [
    vue(),
    // 生产环境 gzip 压缩（nginx 配合 gzip_static 使用）
    compression({
      algorithm: 'gzip',
      ext: '.gz',
      threshold: 1024,
      deleteOriginFile: false
    }),
    // Brotli 压缩：相比 gzip 体积小 15~20%，现代浏览器（Chrome 50+ / Firefox 44+ / Edge 15+）原生支持
    // nginx 通过 `Brotli on;` 指令配合 `brotli_static on;` 优先返回 .br 文件
    // 体积优化对管理后台 SPA 尤为重要（首屏 JS 普遍 > 500KB）
    // 注意：vite-plugin-brotli 与 vite-plugin-compression 同名插件版本兼容性差异较大，
    // 这里使用同一插件族的 brotli 算法（vite-plugin-compression 自 1.x 起支持 algorithm='brotliCompress'）
    compression({
      algorithm: 'brotliCompress',
      ext: '.br',
      threshold: 1024,
      deleteOriginFile: false
    })
  ],
  base: '/admin/',
  build: {
    outDir: '../moyuyo-api/src/main/resources/static/admin/',
    emptyOutDir: true,
    // 生产环境去除 console / debugger 语句（防止调试代码泄漏）
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
        pure_funcs: ['console.log', 'console.info', 'console.debug']
      }
    },
    // 启用 rollup 源码泄漏防护（生产 sourcemap 仅生成 .map 但不出包）
    sourcemap: false,
    // 关闭 chunk 大小警告（按需 splitChunks）
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        // 拆分第三方库到独立 chunk，便于长期缓存
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus', '@element-plus/icons-vue']
        }
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // /uploads/ 静态资源代理：与后端 WebMvcConfig.addResourceHandlers 保持一致
      // 作用：dev 环境下前端可直接用相对 URL `/uploads/yyyy/MM/dd/xxx.png` 访问上传的图片
      // 生产环境由 nginx/1Panel 反代统一处理 /uploads/*
      '/uploads/': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})