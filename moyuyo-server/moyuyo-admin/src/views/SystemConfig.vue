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

    <!-- ====== 语言与地区 ====== -->
    <div v-show="activeTab === 'locale'" class="tab-content">
      <div class="section-title">语言与地区</div>
      <div class="config-card">
        <div class="config-section">
          <div class="config-label">默认语言</div>
          <div class="config-desc">新用户的默认显示语言</div>
          <div class="radio-group">
            <button
              v-for="opt in languageOptions"
              :key="opt.value"
              :class="['radio-option', { selected: form.defaultLanguage === opt.value }]"
              @click="form.defaultLanguage = opt.value"
            >{{ opt.label }}</button>
          </div>
        </div>
        <div class="config-section">
          <div class="config-label">支持语言</div>
          <div class="config-desc">商城前台可切换的语言列表（多选）</div>
          <div class="checkbox-group">
            <label v-for="opt in languageOptions" :key="opt.value" class="checkbox-option">
              <input type="checkbox" :value="opt.value" v-model="form.supportedLanguages">
              <span>{{ opt.label }}</span>
            </label>
          </div>
        </div>
        <div class="config-section">
          <div class="config-row">
            <div>
              <div class="config-label">币种</div>
              <div class="config-desc">商品价格显示币种</div>
            </div>
            <select v-model="form.currency" class="config-select">
              <option v-for="opt in currencyOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </div>
          <div class="config-row">
            <div>
              <div class="config-label">时区</div>
              <div class="config-desc">影响订单时间、定时任务等</div>
            </div>
            <select v-model="form.timezone" class="config-select">
              <option v-for="opt in timezoneOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
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
              <div class="config-label">商品税率</div>
              <div class="config-desc">下单按商品税状态/税类计算税费，0 表示不计税</div>
            </div>
            <div class="number-input-wrap">
              <input v-model.number="form.productTaxRate" type="number" class="config-input" style="max-width: 100px;" min="0" step="0.01">
              <span class="input-suffix">%</span>
            </div>
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

    <!-- ====== 物流配置 ====== -->
    <div v-show="activeTab === 'shipping'" class="tab-content">
      <!-- 承运商与包装 -->
      <div class="section-title">承运商与包装</div>
      <div class="config-card">
        <div class="config-section">
          <div class="config-label">承运商列表</div>
          <div class="config-desc">可启用的物流承运商（来自承运商档案，状态在承运商管理中维护）</div>
          <div class="carrier-list">
            <div v-for="carrier in carriers" :key="carrier.key" class="carrier-item">
              <div class="carrier-left">
                <div>
                  <div class="config-label">{{ carrier.name }}</div>
                  <div class="config-desc">{{ carrier.desc }}</div>
                </div>
              </div>
              <el-tag :type="carrier.enabled ? 'success' : 'info'" size="small">
                {{ carrier.enabled ? '启用' : '停用' }}
              </el-tag>
            </div>
          </div>
        </div>
        <div class="config-section">
          <div class="config-row">
            <div>
              <div class="config-label">默认承运商</div>
              <div class="config-desc">创建发货单时的默认物流商</div>
            </div>
            <select v-model="form.defaultCarrier" class="config-select">
              <option v-for="c in carriers" :key="c.key" :value="c.key">{{ c.name }}</option>
            </select>
          </div>
          <div class="config-row">
            <div>
              <div class="config-label">默认包装类型</div>
              <div class="config-desc">用于自动计算运费体积重</div>
            </div>
            <select v-model="form.packageType" class="config-select">
              <option v-for="opt in packageTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </div>
          <div class="config-row">
            <div>
              <div class="config-label">单件最大重量</div>
              <div class="config-desc">单件包裹重量上限，超出将被拆分或拒收</div>
            </div>
            <div class="number-input-wrap">
              <input v-model="form.maxWeight" type="number" class="config-input" style="max-width: 100px;" min="0">
              <span class="input-suffix">kg</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 运费计算规则 -->
      <div class="section-title">运费计算规则</div>
      <div class="config-card">
        <div class="config-section">
          <div class="config-label">计算方式</div>
          <div class="config-desc">根据以下方式自动计算运费</div>
          <div class="radio-group">
            <button
              v-for="opt in shippingMethodOptions"
              :key="opt.value"
              :class="['radio-option', { selected: form.shippingMethod === opt.value }]"
              @click="form.shippingMethod = opt.value"
            >{{ opt.label }}</button>
          </div>
        </div>
        <div class="config-section">
          <div class="config-label">基础运费</div>
          <div class="config-desc">首重 / 首件 / 订单基础运费价格</div>
          <div class="number-input-wrap">
            <span class="input-prefix">¥</span>
            <input v-model="form.baseFee" type="number" class="config-input" style="max-width: 100px;" min="0">
          </div>
        </div>
        <div class="config-section">
          <div class="config-label">运费规则</div>
          <div class="config-desc">来自运费规则配置（只读，前往发货策略维护）</div>
          <table class="tier-table">
            <thead>
              <tr>
                <th>计费方式</th>
                <th>首费</th>
                <th>续费</th>
                <th>包邮门槛</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="rate in shippingRates" :key="rate.id">
                <td>{{ rate.chargeType }}</td>
                <td>{{ rate.currency }} {{ rate.firstCharge }}</td>
                <td>{{ rate.currency }} {{ rate.continueCharge }}</td>
                <td>{{ rate.freeThreshold }}</td>
              </tr>
              <tr v-if="shippingRates.length === 0">
                <td colspan="4" style="text-align:center;color:var(--text-400)">暂无运费规则</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 运费类系数 -->
      <div class="section-title">运费类系数</div>
      <div class="config-card">
        <div class="config-section">
          <div class="config-label">计费重量系数</div>
          <div class="config-desc">按商品运费类放大计费重量，1 = 不放大；用于冷链 / 大件 / 海外直邮等附加运费</div>
          <div class="multiplier-grid">
            <label v-for="item in shippingClassOptions" :key="item.value" class="multiplier-item">
              <span class="config-label">{{ item.label }}</span>
              <input
                v-model.number="form.shippingClassMultiplier[item.value]"
                type="number"
                class="config-input"
                min="0"
                step="0.01"
              >
            </label>
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

    <!-- ====== 私信安全 ====== -->
    <div v-show="activeTab === 'dm'" class="tab-content">
      <div class="section-title">陌生人私信限制</div>
      <div class="config-card">
        <div class="config-section">
          <div class="config-row">
            <div>
              <div class="config-label">首条消息上限</div>
              <div class="config-desc">对方未回复前，最多可发送的消息条数；对方回复后解除限制，0 表示不限制</div>
            </div>
            <input v-model="form.dmStrangerFirstLimit" type="number" class="config-input" style="max-width: 100px;" min="0">
          </div>
          <div class="config-row">
            <div>
              <div class="config-label">每日消息上限</div>
              <div class="config-desc">对方未回复的会话中，每天最多可发送的消息条数，0 表示不限制</div>
            </div>
            <input v-model="form.dmStrangerDailyLimit" type="number" class="config-input" style="max-width: 100px;" min="0">
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSystemConfig, saveSystemConfig, getPaymentMethods, getCarriers, getShippingRates } from '../api/admin'

const activeTab = ref('basic')

const tabs = [
  { key: 'basic', label: '基本设置' },
  { key: 'locale', label: '语言与地区' },
  { key: 'order', label: '订单设置' },
  { key: 'payment', label: '支付设置' },
  { key: 'shipping', label: '物流配置' },
  { key: 'notification', label: '通知设置' },
  { key: 'dm', label: '私信安全' }
]

const cancelOptions = [
  { label: '30 分钟', value: 30 },
  { label: '1 小时', value: 60 },
  { label: '2 小时', value: 120 }
]

// 语言选项（选项字典）
const languageOptions = [
  { label: '简体中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' },
  { label: '日本語', value: 'ja-JP' },
  { label: '한국어', value: 'ko-KR' }
]

// 币种选项（选项字典）
const currencyOptions = [
  { label: 'CNY (¥)', value: 'CNY' },
  { label: 'USD ($)', value: 'USD' },
  { label: 'EUR (€)', value: 'EUR' },
  { label: 'JPY (¥)', value: 'JPY' }
]

// 时区选项（选项字典）
const timezoneOptions = [
  { label: 'UTC+8（中国标准时间）', value: 'UTC+8' },
  { label: 'UTC+9（日本标准时间）', value: 'UTC+9' },
  { label: 'UTC+0（格林尼治标准时间）', value: 'UTC+0' },
  { label: 'UTC-5（美东时间）', value: 'UTC-5' }
]

// 包装类型选项（选项字典）
const packageTypeOptions = [
  { label: '小号包裹（30×20×15cm）', value: 'S' },
  { label: '中号包裹（40×30×20cm）', value: 'M' },
  { label: '大号包裹（60×40×35cm）', value: 'L' }
]

// 运费计算方式选项（选项字典）
const shippingMethodOptions = [
  { label: '按重量', value: 'weight' },
  { label: '按件数', value: 'count' },
  { label: '按金额', value: 'amount' }
]

// 运费类系数选项（与商品编辑页的运费类保持一致）
const shippingClassOptions = [
  { label: '标准配送', value: 'standard' },
  { label: '冷链配送', value: 'cold' },
  { label: '大件物流', value: 'bulky' },
  { label: '海外直邮', value: 'oversea' }
]

// 承运商列表（来自 /logistics/carriers 真实档案）
const carriers = ref([])

// 运费规则（来自 /logistics/shipping-rates 真实配置）
const shippingRates = ref([])

// 计费方式编码 → 中文
const CHARGE_TYPE_TEXT = { 1: '按件', 2: '按重', 3: '按金额' }

// 加载真实承运商列表
async function loadCarriers() {
  try {
    const list = await getCarriers()
    carriers.value = (list || []).map(c => ({
      key: c.code || String(c.id),
      name: c.name || '-',
      desc: [c.transportMode, c.avgDeliveryDays ? '平均 ' + c.avgDeliveryDays + ' 天' : ''].filter(Boolean).join(' · '),
      enabled: String(c.status || '').toUpperCase() === 'ACTIVE'
    }))
  } catch (e) {
    console.error('获取承运商列表失败', e)
    carriers.value = []
  }
}

// 加载真实运费规则
async function loadShippingRates() {
  try {
    const list = await getShippingRates()
    shippingRates.value = (list || []).map(r => ({
      id: r.id,
      chargeType: CHARGE_TYPE_TEXT[r.chargeType] || '按件',
      firstCharge: Number(r.firstCharge || 0),
      continueCharge: Number(r.continueCharge || 0),
      freeThreshold: r.freeThreshold == null ? '不包邮' : '￥' + r.freeThreshold,
      currency: r.currency || 'USD'
    }))
  } catch (e) {
    console.error('获取运费规则失败', e)
    shippingRates.value = []
  }
}

const form = reactive({
  siteName: 'MOYUYO',
  icpNumber: '京ICP备2025XXXXXX号',
  autoCancel: 30,
  autoConfirm: true,
  // 商品税率（百分比，0 = 不计税）
  productTaxRate: 0,
  emailNotify: true,
  smsNotify: false,
  // 语言与地区（示例数据）
  defaultLanguage: 'zh-CN',
  supportedLanguages: ['zh-CN', 'en-US', 'ja-JP'],
  currency: 'CNY',
  timezone: 'UTC+8',
  // 承运商与包装（示例数据）
  defaultCarrier: 'sf',
  packageType: 'M',
  maxWeight: 20,
  // 运费计算规则（示例数据）
  shippingMethod: 'weight',
  baseFee: 12,
  // 运费类计费重量系数（1 = 不放大）
  shippingClassMultiplier: { standard: 1, cold: 1, bulky: 1, oversea: 1 },
  // 私信安全（0 = 不限制）
  dmStrangerFirstLimit: 1,
  dmStrangerDailyLimit: 10
})

const paymentMethods = ref([
  { key: 'stripe', name: 'Stripe', desc: '信用卡 / 借记卡支付（含 Google Pay）', enabled: true },
  { key: 'paypal', name: 'PayPal', desc: 'PayPal 账户支付', enabled: true },
  // 默认开启：与后端「未配置的开关视为开启」保持一致，避免后台显示关闭但实际可用
  { key: 'applepay', name: 'Apple Pay', desc: 'Apple 设备快捷支付', enabled: true }
])

// 加载系统配置
async function loadConfig() {
  try {
    const res = await getSystemConfig()
    const configList = res || []
      // 将后端返回的配置项列表映射到 form 中
      configList.forEach(item => {
        if (item.key === 'siteName') form.siteName = item.value || form.siteName
        if (item.key === 'icpNumber') form.icpNumber = item.value || form.icpNumber
        if (item.key === 'autoCancel') form.autoCancel = Number(item.value) || form.autoCancel
        if (item.key === 'product_tax_rate') form.productTaxRate = Number(item.value) || 0
        if (item.key === 'shipping_class_multiplier') {
          try {
            const multipliers = JSON.parse(item.value)
            if (multipliers && typeof multipliers === 'object') {
              Object.assign(form.shippingClassMultiplier, multipliers)
            }
          } catch (e) { /* 忽略非法 JSON，保留默认系数 */ }
        }
        if (item.key === 'autoConfirm') form.autoConfirm = item.value === 'true' || item.value === true
        if (item.key === 'emailNotify') form.emailNotify = item.value === 'true' || item.value === true
        if (item.key === 'smsNotify') form.smsNotify = item.value === 'true' || item.value === true
        // 语言与地区
        if (item.key === 'defaultLanguage') form.defaultLanguage = item.value || form.defaultLanguage
        if (item.key === 'currency') form.currency = item.value || form.currency
        if (item.key === 'timezone') form.timezone = item.value || form.timezone
        if (item.key === 'supportedLanguages') {
          try {
            const list = JSON.parse(item.value)
            if (Array.isArray(list) && list.length > 0) form.supportedLanguages = list
          } catch (e) { /* 忽略解析失败，保留默认值 */ }
        }
        // 承运商与包装
        if (item.key === 'defaultCarrier') form.defaultCarrier = item.value || form.defaultCarrier
        if (item.key === 'packageType') form.packageType = item.value || form.packageType
        if (item.key === 'maxWeight') form.maxWeight = Number(item.value) || form.maxWeight
        // 运费计算规则
        if (item.key === 'shippingMethod') form.shippingMethod = item.value || form.shippingMethod
        if (item.key === 'baseFee') form.baseFee = Number(item.value) || form.baseFee
        // 私信安全：0 是合法值（表示不限制），不能用 || 回退默认值；空值保持表单默认
        if (item.key === 'dm.guard.stranger_first_limit'
            && item.value !== '' && item.value !== null && item.value !== undefined
            && !Number.isNaN(Number(item.value))) {
          form.dmStrangerFirstLimit = Math.max(0, Math.trunc(Number(item.value)))
        }
        if (item.key === 'dm.guard.stranger_daily_limit'
            && item.value !== '' && item.value !== null && item.value !== undefined
            && !Number.isNaN(Number(item.value))) {
          form.dmStrangerDailyLimit = Math.max(0, Math.trunc(Number(item.value)))
        }
        // 从配置中恢复支付方式状态
        if (item.key && item.key.startsWith('payment_')) {
          const payKey = item.key.replace('payment_', '')
          const method = paymentMethods.value.find(m => m.key === payKey)
          if (method) {
            method.enabled = item.value === 'true'
          }
        }
      })
    // 加载支付方式配置（名称 / 描述 / 开关状态均以后端为准，与 C 端收银台同源）
    try {
      const payRes = await getPaymentMethods()
      if (payRes && payRes.length > 0) {
        // 后端返回的开关状态与真实生效一致（数据源与 C 端收银台同源），以它为准覆盖本地状态
        payRes.forEach(apiMethod => {
          const existing = paymentMethods.value.find(m => m.key === (apiMethod.code || apiMethod.key))
          if (existing) {
            existing.name = apiMethod.name || existing.name
            existing.desc = apiMethod.desc || existing.desc
            if (typeof apiMethod.enabled === 'boolean') existing.enabled = apiMethod.enabled
          } else if (apiMethod.code) {
            paymentMethods.value.push({
              key: apiMethod.code,
              name: apiMethod.name,
              desc: apiMethod.desc || '',
              enabled: apiMethod.enabled === true
            })
          }
        })
      }
    } catch (e) {
      console.error('获取支付方式失败，使用默认值', e)
    }
  } catch (e) {
    ElMessage.error('获取配置失败')
  }
}

function handleUploadLogo() {
  // 触发隐藏的文件选择器
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/png,image/jpeg,image/webp'
  input.onchange = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    // Logo上传（当前以提示形式展示，后续可对接实际文件上传API）
    ElMessage.success(`已选择: ${file.name}`)
  }
  input.click()
}

// 保存系统配置
async function handleSave() {
  try {
    // 私信安全阈值：先归一化为非负整数，空值/非法值回退默认，避免保存空串后前后端理解不一致
    form.dmStrangerFirstLimit = normalizeLimit(form.dmStrangerFirstLimit, 1)
    form.dmStrangerDailyLimit = normalizeLimit(form.dmStrangerDailyLimit, 10)
    // 将表单数据转换为后端需要的配置项列表格式
    const configs = [
      { key: 'siteName', value: form.siteName, type: 'text', label: '站点名称' },
      { key: 'icpNumber', value: form.icpNumber, type: 'text', label: '备案号' },
      { key: 'autoCancel', value: String(form.autoCancel), type: 'text', label: '自动取消时间' },
      { key: 'product_tax_rate', value: String(form.productTaxRate || 0), type: 'number', label: '商品税率' },
      { key: 'autoConfirm', value: String(form.autoConfirm), type: 'text', label: '自动确认收货' },
      { key: 'emailNotify', value: String(form.emailNotify), type: 'text', label: '邮件通知' },
      { key: 'smsNotify', value: String(form.smsNotify), type: 'text', label: '短信通知' },
      // 语言与地区
      { key: 'defaultLanguage', value: form.defaultLanguage, type: 'text', label: '默认语言' },
      { key: 'supportedLanguages', value: JSON.stringify(form.supportedLanguages), type: 'json', label: '支持语言' },
      { key: 'currency', value: form.currency, type: 'text', label: '币种' },
      { key: 'timezone', value: form.timezone, type: 'text', label: '时区' },
      // 承运商与包装
      { key: 'defaultCarrier', value: form.defaultCarrier, type: 'text', label: '默认承运商' },
      { key: 'packageType', value: form.packageType, type: 'text', label: '默认包装类型' },
      { key: 'maxWeight', value: String(form.maxWeight), type: 'text', label: '单件最大重量' },
      // 运费计算规则
      { key: 'shippingMethod', value: form.shippingMethod, type: 'text', label: '运费计算方式' },
      { key: 'baseFee', value: String(form.baseFee), type: 'text', label: '基础运费' },
      { key: 'shipping_class_multiplier', value: JSON.stringify(form.shippingClassMultiplier || {}), type: 'json', label: '运费类系数' },
      // 私信安全（0 = 不限制）
      { key: 'dm.guard.stranger_first_limit', value: String(form.dmStrangerFirstLimit), type: 'number', label: '陌生人首条上限' },
      { key: 'dm.guard.stranger_daily_limit', value: String(form.dmStrangerDailyLimit), type: 'number', label: '陌生人每日上限' }
    ]
    // 将支付方式状态也加入配置保存
    paymentMethods.value.forEach(method => {
      configs.push({
        key: `payment_${method.key}`,
        value: String(method.enabled),
        type: 'toggle',
        label: method.name
      })
    })
    await saveSystemConfig(configs)
    ElMessage.success('配置保存成功')
  } catch (e) {
    ElMessage.error('保存配置失败')
  }
}

// 防骚扰阈值归一化：非负整数；空值或非法值回退默认
function normalizeLimit(value, defaultValue) {
  const num = Number(value)
  return Number.isFinite(num) && num >= 0 ? Math.trunc(num) : defaultValue
}

onMounted(() => {
  loadConfig()
  loadCarriers()
  loadShippingRates()
})
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

/* 下拉选择框 */
.config-select {
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--input);
  border-radius: 10px;
  background: var(--background);
  color: var(--text-800);
  font-size: 14px;
  font-family: var(--font-sans);
  outline: none;
  cursor: pointer;
  max-width: 240px;
}
.config-select:focus {
  border-color: var(--ring);
  box-shadow: 0 0 0 1px var(--ring);
}

/* 多选语言（Checkbox 组） */
.checkbox-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.checkbox-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: 18px;
  border: 1px solid var(--input);
  background: var(--background);
  color: var(--text-500);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
}
.checkbox-option input {
  width: 14px;
  height: 14px;
  accent-color: var(--primary);
  cursor: pointer;
}
.checkbox-option:has(input:checked) {
  border-color: var(--primary);
  background: var(--brand-50);
  color: var(--primary);
}

/* 承运商列表 */
.carrier-list {
  margin-top: 10px;
}
.carrier-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
}
.carrier-item + .carrier-item {
  border-top: 1px solid var(--background-200);
}
.carrier-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 数字输入（带前后缀） */
.number-input-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}
.input-prefix {
  font-size: 14px;
  color: var(--text-500);
  flex-shrink: 0;
}
.input-suffix {
  font-size: 13px;
  color: var(--text-400);
  white-space: nowrap;
  flex-shrink: 0;
}

/* 阶梯价格表 */
.tier-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 10px;
}
.tier-table th {
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-400);
  padding: 8px 10px;
  border-bottom: 1px solid var(--background-200);
}
.tier-table td {
  padding: 8px 10px;
  border-bottom: 1px solid var(--background-200);
}
.tier-input {
  width: 100px;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--input);
  border-radius: 8px;
  background: var(--background);
  color: var(--text-800);
  font-size: 14px;
  font-family: var(--font-sans);
  outline: none;
}
.tier-input:focus {
  border-color: var(--ring);
  box-shadow: 0 0 0 1px var(--ring);
}
.tier-actions {
  text-align: right;
}
.tier-remove {
  border: none;
  background: transparent;
  color: var(--text-400);
  font-size: 13px;
  font-family: var(--font-sans);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.15s ease;
}
.tier-remove:hover {
  color: #ff3b30;
  background: rgba(255, 59, 48, 0.08);
}
.add-tier-btn {
  margin-top: 12px;
  height: 36px;
  padding: 0 16px;
  border-radius: 18px;
  border: 1px dashed var(--input);
  background: var(--background);
  color: var(--text-500);
  font-size: 13px;
  font-weight: 500;
  font-family: var(--font-sans);
  cursor: pointer;
  transition: all 0.2s ease;
}
.add-tier-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: var(--brand-50);
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

/* 运费类系数 */
.multiplier-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 8px;
}
.multiplier-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
