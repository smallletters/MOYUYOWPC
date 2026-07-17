<template>
  <div class="page-wrapper">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">系统配置</h1>
    </div>

    <!-- Tab 切换 -->
    <div class="config-tab-bar">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        :class="['config-tab', { active: activeTab === tab.key }]"
        @click="activeTab = tab.key"
      >{{ tab.label }}</button>
    </div>

    <!-- ====== 基本设置 ====== -->
    <div v-show="activeTab === 'basic'" class="tab-content">
      <div class="section-title">站点信息</div>
      <div class="config-card">
        <div class="config-section">
          <div class="config-row">
            <div>
              <div class="config-label">站点名称</div>
              <div class="config-desc">将显示在页面标题和导航栏中</div>
            </div>
            <input v-model="form.siteName" type="text" class="config-input" placeholder="请输入站点名称">
          </div>
          <div class="config-row">
            <div>
              <div class="config-label">站点 Logo</div>
              <div class="config-desc">建议上传 512x512 像素的 PNG 图片</div>
            </div>
            <div class="logo-upload-area" @click="handleUploadLogo">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
              <span class="logo-upload-text">上传</span>
            </div>
          </div>
          <div class="config-row">
            <div>
              <div class="config-label">备案号</div>
              <div class="config-desc">网站备案 ICP 编号</div>
            </div>
            <input v-model="form.icpNumber" type="text" class="config-input" placeholder="京ICP备XXXXXXXX号" style="max-width: 180px;">
          </div>
        </div>
      </div>
    </div>

    <!-- ====== 订单设置 ====== -->
    <div v-show="activeTab === 'order'" class="tab-content">
      <div class="section-title">订单规则</div>
      <div class="config-card">
        <div class="config-section">
          <div class="config-label">自动取消时间</div>
          <div class="config-desc">未支付订单超过设定时间后自动取消</div>
          <div class="radio-group">
            <button
              v-for="opt in cancelOptions"
              :key="opt.value"
              :class="['radio-option', { selected: form.autoCancel === opt.value }]"
              @click="form.autoCancel = opt.value"
            >{{ opt.label }}</button>
          </div>
        </div>
        <div class="config-section">
          <div class="config-row">
            <div>
              <div class="config-label">自动确认收货</div>
              <div class="config-desc">发货后超过设定天数自动确认收货</div>
            </div>
            <div
              class="toggle-track"
              role="switch"
              :aria-checked="String(form.autoConfirm)"
              tabindex="0"
              @click="form.autoConfirm = !form.autoConfirm"
              @keydown.enter.prevent="form.autoConfirm = !form.autoConfirm"
              @keydown.space.prevent="form.autoConfirm = !form.autoConfirm"
            >
              <div class="toggle-thumb"></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ====== 支付设置 ====== -->
    <div v-show="activeTab === 'payment'" class="tab-content">
      <div class="section-title">支付方式</div>
      <div class="config-card">
        <div v-for="(method, idx) in paymentMethods" :key="method.key" class="payment-method-item">
          <div class="payment-left">
            <div class="payment-drag-handle">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
            </div>
            <div>
              <div class="config-label">{{ method.name }}</div>
              <div class="config-desc">{{ method.desc }}</div>
            </div>
          </div>
          <div
            class="toggle-track"
            role="switch"
            :aria-checked="String(method.enabled)"
            tabindex="0"
            @click="method.enabled = !method.enabled"
            @keydown.enter.prevent="method.enabled = !method.enabled"
            @keydown.space.prevent="method.enabled = !method.enabled"
          >
            <div class="toggle-thumb"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- ====== 通知设置 ====== -->
    <div v-show="activeTab === 'notification'" class="tab-content">
      <div class="section-title">通知渠道</div>
      <div class="config-card">
        <div class="notification-item">
          <div>
            <div class="config-label">邮件通知</div>
            <div class="config-desc">通过邮件发送系统通知和营销信息</div>
          </div>
          <div
            class="toggle-track"
            role="switch"
            :aria-checked="String(form.emailNotify)"
            tabindex="0"
            @click="form.emailNotify = !form.emailNotify"
            @keydown.enter.prevent="form.emailNotify = !form.emailNotify"
            @keydown.space.prevent="form.emailNotify = !form.emailNotify"
          >
            <div class="toggle-thumb"></div>
          </div>
        </div>
        <div class="notification-item">
          <div>
            <div class="config-label">短信通知</div>
            <div class="config-desc">通过短信发送系统通知和验证码</div>
          </div>
          <div
            class="toggle-track"
            role="switch"
            :aria-checked="String(form.smsNotify)"
            tabindex="0"
            @click="form.smsNotify = !form.smsNotify"
            @keydown.enter.prevent="form.smsNotify = !form.smsNotify"
            @keydown.space.prevent="form.smsNotify = !form.smsNotify"
          >
            <div class="toggle-thumb"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部保存栏 -->
    <div class="save-bar">
      <button class="save-btn" @click="handleSave">保存配置</button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'

const activeTab = ref('basic')

const tabs = [
  { key: 'basic', label: '基本设置' },
  { key: 'order', label: '订单设置' },
  { key: 'payment', label: '支付设置' },
  { key: 'notification', label: '通知设置' }
]

const cancelOptions = [
  { label: '30 分钟', value: 30 },
  { label: '1 小时', value: 60 },
  { label: '2 小时', value: 120 }
]

const form = reactive({
  siteName: 'MOYUYO',
  icpNumber: '京ICP备2025XXXXXX号',
  autoCancel: 30,
  autoConfirm: true,
  emailNotify: true,
  smsNotify: false
})

const paymentMethods = reactive([
  { key: 'stripe', name: 'Stripe', desc: '信用卡 / 借记卡支付', enabled: true },
  { key: 'paypal', name: 'PayPal', desc: 'PayPal 账户支付', enabled: true },
  { key: 'applepay', name: 'Apple Pay', desc: 'Apple 设备快捷支付', enabled: false }
])

function handleUploadLogo() {
  console.log('上传 Logo')
}

function handleSave() {
  console.log('保存配置:', { ...form, paymentMethods: paymentMethods.map(m => ({ ...m })) })
}
</script>

<style scoped>
.page-wrapper {
  padding: 0 0 100px;
}
.page-header {
  padding: 24px 24px 0;
  margin-bottom: 4px;
}
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-800);
  margin: 0;
}

/* ===== Tab 切换 ===== */
.config-tab-bar {
  display: flex;
  overflow-x: auto;
  gap: 4px;
  padding: 12px 24px 16px;
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.config-tab-bar::-webkit-scrollbar { display: none; }
.config-tab {
  flex-shrink: 0;
  height: 36px;
  padding: 0 16px;
  border-radius: 18px;
  border: none;
  background: transparent;
  color: var(--text-500);
  font-size: 13px;
  font-weight: 600;
  font-family: var(--font-sans);
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;
  white-space: nowrap;
}
.config-tab.active {
  background: var(--primary);
  color: var(--primary-foreground);
}
.config-tab:not(.active):hover {
  background: var(--background-200);
}

/* ===== Tab 内容 ===== */
.tab-content {
  padding: 0 24px;
}

/* 分组标题 */
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-400);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  padding: 0 0 8px;
}

/* 配置卡片 */
.config-card {
  background: var(--card);
  border-radius: var(--radius);
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
  overflow: hidden;
  margin-bottom: 20px;
}
.config-section {
  padding: 16px;
}
.config-section + .config-section {
  border-top: 1px solid var(--background-200);
}
.config-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
}
.config-row + .config-row {
  margin-top: 14px;
}
.config-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-800);
  flex-shrink: 0;
}
.config-desc {
  font-size: 12px;
  color: var(--text-400);
  margin-top: 2px;
}
.config-input {
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--input);
  border-radius: 10px;
  background: var(--background);
  color: var(--text-800);
  font-size: 14px;
  font-family: var(--font-sans);
  outline: none;
  transition: border-color 0.18s ease;
  max-width: 200px;
  text-align: right;
}
.config-input:focus {
  border-color: var(--ring);
  box-shadow: 0 0 0 1px var(--ring);
}

/* Logo 上传区 */
.logo-upload-area {
  width: 64px;
  height: 64px;
  border-radius: 14px;
  border: 2px dashed var(--background-400);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease;
  background: var(--background-200);
  flex-shrink: 0;
}
.logo-upload-area:hover {
  border-color: var(--primary);
  background: var(--brand-50);
}
.logo-upload-text {
  font-size: 9px;
  font-weight: 600;
  color: var(--text-400);
}

/* 单选按钮组 */
.radio-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.radio-option {
  height: 36px;
  padding: 0 16px;
  border-radius: 18px;
  border: 1px solid var(--input);
  background: var(--background);
  color: var(--text-500);
  font-size: 13px;
  font-weight: 500;
  font-family: var(--font-sans);
  cursor: pointer;
  transition: all 0.2s ease;
}
.radio-option.selected {
  border-color: var(--primary);
  background: var(--brand-50);
  color: var(--primary);
}

/* Toggle 开关 */
.toggle-track {
  position: relative;
  width: 51px;
  height: 31px;
  border-radius: 16px;
  background: var(--background-400);
  cursor: pointer;
  transition: background-color 0.25s ease;
  flex-shrink: 0;
}
.toggle-track[aria-checked="true"] {
  background: var(--state-success);
}
.toggle-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 27px;
  height: 27px;
  border-radius: 50%;
  background: var(--background-50);
  box-shadow: 0 2px 4px rgba(0,0,0,0.15), 0 1px 1px rgba(0,0,0,0.1);
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.toggle-track[aria-checked="true"] .toggle-thumb {
  transform: translateX(20px);
}

/* 支付方式项 */
.payment-method-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
}
.payment-method-item + .payment-method-item {
  border-top: 1px solid var(--background-200);
}
.payment-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.payment-drag-handle {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-400);
  cursor: grab;
}

/* 通知项 */
.notification-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
}
.notification-item + .notification-item {
  border-top: 1px solid var(--background-200);
}

/* 底部保存栏 */
.save-bar {
  position: fixed;
  bottom: 0;
  left: 240px;
  right: 0;
  z-index: 40;
  padding: 12px 24px;
  padding-bottom: calc(12px + env(safe-area-inset-bottom, 0px));
  background: rgba(255,255,255,0.88);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-top: 1px solid var(--border);
}
.save-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 50px;
  border: none;
  border-radius: 14px;
  background: var(--primary);
  color: var(--primary-foreground);
  font: 600 15px/1 var(--font-sans);
  cursor: pointer;
  transition: filter 0.18s ease;
}
.save-btn:hover {
  filter: brightness(0.96);
}
</style>
