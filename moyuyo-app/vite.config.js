import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// Vite 配置：uni-app 项目入口指向 src/main.js
export default defineConfig({
  plugins: [uni()],
  // 路径别名
  resolve: {
    alias: {
      '@': '/src'
    }
  }
})
