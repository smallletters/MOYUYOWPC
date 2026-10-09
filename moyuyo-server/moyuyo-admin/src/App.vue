<template>
  <div>
    <div v-if="routeError" class="app-error">
      <h2>页面渲染错误</h2>
      <p>请截图发送给开发者，或刷新页面重试。</p>
      <pre>{{ routeError }}</pre>
      <button @click="reload">刷新页面</button>
    </div>
    <router-view v-else />
  </div>
</template>

<script setup>
import { ref, onErrorCaptured } from 'vue'

const routeError = ref(null)

// 捕获子组件渲染错误，防止白屏
// 注意：onErrorCaptured 必须返回 false 才能阻止错误继续向上传播
onErrorCaptured((err, instance, info) => {
  console.error('[App errorCaptured]', err, info)
  routeError.value = (err && (err.stack || err.message)) || String(err)
  // 返回 false 表示已处理，阻止继续向上冒泡
  return false
})

function reload() {
  location.reload()
}
</script>

<style>
.app-error {
  padding: 40px;
  text-align: center;
}
.app-error h2 {
  color: #ff3b30;
  margin-bottom: 12px;
}
.app-error pre {
  background: #fff5f5;
  border: 1px solid #ffcdd2;
  padding: 12px;
  border-radius: 6px;
  text-align: left;
  font-size: 12px;
  max-width: 800px;
  margin: 16px auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.app-error button {
  background: #1677ff;
  color: #fff;
  border: none;
  padding: 8px 20px;
  border-radius: 6px;
  cursor: pointer;
}
</style>