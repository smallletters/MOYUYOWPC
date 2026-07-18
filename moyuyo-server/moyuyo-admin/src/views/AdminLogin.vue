<template>
  <div class="login-page">
    <div class="login-card">
      <!-- 品牌区域 -->
      <div class="login-brand">
        <div class="brand-icon-box">
          <!-- 使用内联 SVG 作为品牌图标（56px 蓝色圆角方块） -->
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
            <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
            <line x1="12" y1="22.08" x2="12" y2="12" />
          </svg>
        </div>
        <h1 class="brand-title">管理后台</h1>
        <p class="brand-subtitle">MOYUYO Admin Console</p>
      </div>

      <!-- 登录表单 -->
      <el-form class="login-form" @submit.prevent="handleLogin">
        <!-- 邮箱输入框 -->
        <el-form-item>
          <el-input
            v-model="form.email"
            type="email"
            placeholder="管理员邮箱"
            autocomplete="email"
            size="large"
          >
            <!-- 使用内联 SVG Mail 图标 -->
            <template #prefix>
              <svg class="input-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="2" y="4" width="20" height="16" rx="2" />
                <path d="M22 4l-10 8L2 4" />
              </svg>
            </template>
          </el-input>
        </el-form-item>

        <!-- 密码输入框 -->
        <el-form-item>
          <el-input
            v-model="form.password"
            :type="showPassword ? 'text' : 'password'"
            placeholder="密码"
            autocomplete="current-password"
            size="large"
            show-password
            @click:show-password="showPassword = !showPassword"
          >
            <!-- 使用内联 SVG Lock 图标 -->
            <template #prefix>
              <svg class="input-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                <path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </svg>
            </template>
          </el-input>
        </el-form-item>

        <!-- 记住我 + 忘记密码 -->
        <div class="form-options">
          <label class="remember-me">
            <el-checkbox v-model="form.remember" size="default" />
            <span>记住我</span>
          </label>
          <a href="#" class="forgot-link" @click.prevent>忘记密码？</a>
        </div>

        <!-- 登录按钮 -->
        <el-button
          type="primary"
          native-type="submit"
          class="login-btn"
          :loading="loading"
          :disabled="loading"
        >
          {{ loading ? '登录中...' : '登录' }}
        </el-button>

        <!-- 分隔线 -->
        <div class="divider">
          <span>或</span>
        </div>

        <!-- SSO 登录按钮 -->
        <el-button class="sso-btn" @click="handleSSO">
          使用企业SSO登录
        </el-button>
      </el-form>

      <!-- 底部版本信息 -->
      <p class="login-version">v2.1.0</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'

const router = useRouter()

const form = reactive({
  email: '',
  password: '',
  remember: false
})

const showPassword = ref(false)
const loading = ref(false)

async function handleLogin() {
  if (!form.email || !form.password) {
    return
  }
  loading.value = true
  try {
    const res = await login({ email: form.email, password: form.password })
    if (res && res.token) {
      localStorage.setItem('admin_token', res.token)
      ElMessage.success('登录成功')
      router.push('/dashboard')
    } else {
      ElMessage.error(res?.message || '登录失败')
    }
  } catch (e) {
    ElMessage.error('网络错误，请检查后端服务是否正常运行')
  } finally {
    loading.value = false
  }
}

function handleSSO() {
  console.log('SSO login triggered')
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--background-200);
  padding: 20px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  background: var(--background-50);
  border-radius: calc(var(--radius) * 1.4);
  padding: 40px 32px 32px;
  box-shadow: var(--shadow-xl);
  animation: cardEnter 0.5s cubic-bezier(0.32, 0.72, 0, 1) forwards;
}

/* 品牌区域 */
.login-brand {
  text-align: center;
  margin-bottom: 32px;
}

.brand-icon-box {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: var(--brand-500);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  color: #fff;
  box-shadow: 0 4px 12px rgba(0, 122, 255, 0.3);
}

.brand-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  letter-spacing: 0.04em;
  margin: 0 0 4px;
}

.brand-subtitle {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-400);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  margin: 0;
}

/* 表单 */
.login-form {
  display: flex;
  flex-direction: column;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.login-form :deep(.el-input__wrapper) {
  height: 48px;
  padding: 0 14px;
  border-radius: var(--radius);
  border: 1px solid var(--input);
  box-shadow: none;
  background: var(--background-100);
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: none;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  border-color: var(--ring);
  box-shadow: 0 0 0 1px var(--ring);
  background: var(--background-50);
}

.login-form :deep(.el-input__inner) {
  font-size: 14px;
  color: var(--text-800);
  background: transparent;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: var(--text-400);
}

.input-icon {
  color: var(--text-400);
  flex-shrink: 0;
}

/* 记住我 + 忘记密码 */
.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.remember-me {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-500);
  cursor: pointer;
}

.forgot-link {
  font-size: 13px;
  color: var(--brand-500);
  font-weight: 500;
  text-decoration: none;
  transition: opacity 0.18s ease;
}

.forgot-link:hover {
  opacity: 0.7;
}

/* 登录按钮 */
.login-btn {
  width: 100%;
  height: 48px;
  border-radius: 999px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.login-btn :deep(.el-button__inner) {
  letter-spacing: 0.02em;
}

/* 分隔线 */
.divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 24px 0;
  color: var(--text-400);
  font-size: 12px;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--background-300);
}

/* SSO 登录按钮 */
.sso-btn {
  width: 100%;
  height: 48px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 500;
  border: 1px solid var(--background-300);
  background: var(--background-100);
  color: var(--text-800);
}

.sso-btn:hover {
  border-color: var(--background-400);
  background: var(--background-200);
}

/* 底部版本信息 */
.login-version {
  text-align: center;
  margin-top: 32px;
  font-size: 11px;
  color: var(--text-400);
  letter-spacing: 0.02em;
}

/* 入场动画 */
@keyframes cardEnter {
  from {
    opacity: 0;
    transform: translateY(24px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-card {
    animation: none;
  }
}
</style>
