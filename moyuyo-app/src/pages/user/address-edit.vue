<template>
  <view class="address-edit">
    <scroll-view scroll-y class="form">
      <view class="input-group">
        <text class="input-label">
          {{ $t('address.fieldReceiver') }}
          <text class="required">*</text>
        </text>
        <input
          v-model="form.receiver"
          class="input"
          :placeholder="$t('address.receiverPlaceholder')"
          maxlength="40"
        >
      </view>

      <view class="input-group">
        <text class="input-label">
          {{ $t('address.fieldPhone') }}
          <text class="required">*</text>
        </text>
        <input
          v-model="form.phone"
          class="input"
          type="number"
          :placeholder="$t('address.phonePlaceholderEdit')"
          maxlength="20"
        >
      </view>

      <view class="input-group">
        <text class="input-label">
          {{ $t('address.fieldCountry') }}
          <text class="required">*</text>
        </text>
        <picker
          mode="selector"
          :range="countryLabels"
          :value="countryIndex"
          :disabled="countryCodes.length === 0"
          @change="onCountryChange"
        >
          <view class="input picker">
            <text>{{ countryLabel(form.country) || $t('address.countryPlaceholder') }}</text>
            <text class="luc luc-chevron-down picker-arrow" />
          </view>
        </picker>
        <!-- 即时提示：当前所选国家不在运营配置中，下单会被拒 -->
        <text v-if="form.country && !isCountryShippable" class="picker-warn">
          {{ $t('address.unshippableCountryHint') }}
        </text>
      </view>

      <view class="row-group">
        <view class="input-group half">
          <text class="input-label">{{ $t('address.fieldProvince') }}</text>
          <input
            v-model="form.province"
            class="input"
            :placeholder="$t('address.provincePlaceholder')"
            maxlength="40"
          >
        </view>
        <view class="input-group half">
          <text class="input-label">
            {{ $t('address.fieldCity') }}
            <text class="required">*</text>
          </text>
          <input
            v-model="form.city"
            class="input"
            :placeholder="$t('address.cityPlaceholder')"
            maxlength="40"
          >
        </view>
      </view>

      <view class="input-group">
        <text class="input-label">{{ $t('address.fieldDistrict') }}</text>
        <input
          v-model="form.district"
          class="input"
          :placeholder="$t('address.districtPlaceholder')"
          maxlength="40"
        >
      </view>

      <view class="input-group">
        <text class="input-label">
          {{ $t('address.fieldDetail') }}
          <text class="required">*</text>
        </text>
        <input
          v-model="form.detail"
          class="input"
          :placeholder="$t('address.detailPlaceholder')"
          maxlength="120"
        >
      </view>

      <view class="input-group">
        <text class="input-label">{{ $t('address.fieldZip') }}</text>
        <input
          v-model="form.zipCode"
          class="input"
          :placeholder="$t('address.zipPlaceholder')"
          maxlength="20"
        >
      </view>

      <view class="input-group">
        <text class="input-label">{{ $t('address.fieldTag') }}</text>
        <view class="tag-options">
          <view
            v-for="t in tags"
            :key="t.value"
            class="tag-option"
            :class="{ active: form.tag === t.value }"
            @click="form.tag = form.tag === t.value ? '' : t.value"
          >
            <text class="luc" :class="t.icon" />
            <text>{{ t.label }}</text>
          </view>
        </view>
      </view>

      <view class="default-row" @click="form.isDefault = !form.isDefault">
        <view class="checkbox" :class="{ checked: form.isDefault }">
          <text v-if="form.isDefault" class="luc luc-check" />
        </view>
        <text>{{ $t('address.setDefaultLabel') }}</text>
      </view>
    </scroll-view>

    <view class="bottom-bar safe-area-bottom">
      <view class="btn btn-secondary cancel-btn" @click="goBack">{{ $t('common.cancel') }}</view>
      <view class="btn btn-primary save-btn" @click="onSave">{{ $t('address.save') }}</view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { addressApi } from '@/api'
import { i18n } from '@/i18n'
import { useAddressCountries } from '@/composables/useAddressCountries'

defineOptions({ name: 'UserAddressEdit' })

// ==================== 表单状态 ====================

const form = reactive({
  id: null,
  receiver: '',
  phone: '',
  country: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  zipCode: '',
  tag: '',
  isDefault: false,
})

// ==================== 国家码（composable） ====================

const {
  countryCodes,
  countryLabels,
  loadCountries,
  isCountryShippable: isCountryShippableFn,
} = useAddressCountries()
const countryIndex = computed(() => {
  const idx = countryCodes.value.indexOf(form.country)
  return idx >= 0 ? idx : 0
})
const isCountryShippable = computed(() => isCountryShippableFn(form.country))

// ==================== 标签（依赖 i18n 自动响应） ====================

const tags = computed(() => [
  { value: 'HOME', label: i18n.t('address.tagHome'), icon: 'luc-home' },
  { value: 'COMPANY', label: i18n.t('address.tagCompany'), icon: 'luc-briefcase' },
  { value: 'OTHER', label: i18n.t('address.tagOther'), icon: 'luc-map-pin' },
])

// ==================== 生命周期 ====================

// 与项目其他 setup 页面一致：用 getCurrentPages() 读 URL query 入参
const editId = ref(null)
onMounted(() => bootstrap())

async function bootstrap() {
  await loadCountries()
  // 读 URL ?id=xxx（uni-app 跳页约定）
  try {
    const pages = getCurrentPages()
    const cur = pages[pages.length - 1]
    const query = (cur && cur.options) || {}
    if (query.id) {
      editId.value = Number(query.id)
      await loadDetail(editId.value)
    }
  } catch (e) {
    console.warn('[address-edit] read page query failed', e)
  }
  // 编辑模式：保留已有国家；新增模式：默认选第一项（仅当用户没有历史 country 时）
  if (!form.country && countryCodes.value.length > 0) {
    form.country = countryCodes.value[0]
  }
}

async function loadDetail(id) {
  try {
    const addr = await addressApi.getAddressDetail(id)
    if (addr) {
      Object.assign(form, addr)
      if (typeof form.tag !== 'string') form.tag = ''
    }
  } catch (e) {
    console.warn('[address-edit] load failed', e)
    uni.showToast({ title: i18n.t('address.loadFailed'), icon: 'none' })
  }
}

function countryLabel(code) {
  if (!code) return ''
  return i18n.t(`address.countryCodes.${code}`, code)
}

function onCountryChange(e) {
  const idx = Number(e.detail.value)
  if (countryCodes.value[idx]) form.country = countryCodes.value[idx]
}

// ==================== 保存校验 ====================

async function onSave() {
  const required = [
    { key: 'receiver', field: i18n.t('address.fieldReceiver') },
    { key: 'phone', field: i18n.t('address.fieldPhone') },
    { key: 'detail', field: i18n.t('address.fieldDetail') },
    { key: 'city', field: i18n.t('address.fieldCity') },
  ]
  for (const r of required) {
    if (!form[r.key] || !String(form[r.key]).trim()) {
      uni.showToast({
        title: i18n.t('address.requiredField', { field: r.field }),
        icon: 'none',
      })
      return
    }
  }
  if (!/^\d{6,20}$/.test(String(form.phone).replace(/\s+/g, ''))) {
    uni.showToast({ title: i18n.t('address.invalidPhone'), icon: 'none' })
    return
  }
  // 国家必须在当前运营配置的 countryCodes 内，否则保存后下单会被后端拒
  if (!countryCodes.value || countryCodes.value.length === 0) {
    uni.showToast({ title: i18n.t('address.unshippableCountryHint'), icon: 'none' })
    return
  }
  if (!form.country || countryCodes.value.indexOf(form.country) < 0) {
    uni.showToast({ title: i18n.t('address.unshippableCountryHint'), icon: 'none' })
    return
  }

  try {
    uni.showLoading({ title: i18n.t('address.saving') })
    if (form.id) {
      await addressApi.updateAddress(form.id, form)
    } else {
      await addressApi.createAddress(form)
    }
    uni.hideLoading()
    uni.showToast({ title: i18n.t('address.saved'), icon: 'success' })
    setTimeout(() => uni.navigateBack(), 600)
  } catch (e) {
    uni.hideLoading()
    console.warn('[address-edit] save failed', e)
    uni.showToast({ title: i18n.t('address.saveFailed'), icon: 'none' })
  }
}

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) uni.navigateBack({ delta: 1 })
  else uni.switchTab({ url: '/pages/tabbar/user' })
}
</script>

<style lang="scss" scoped>
.address-edit {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--color-background);
}

/* ============ 顶部 ============ */
.header-back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  color: var(--color-text);
}
.title {
  flex: 1;
  text-align: center;
  font-size: 32rpx;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text);
}
.header-spacer {
  width: 64rpx;
  height: 64rpx;
}

/* ============ 表单 ============ */
.form {
  flex: 1;
  padding: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding-bottom: 32rpx;
}

.input-group {
  background: var(--color-surface);
  border-radius: var(--radius-lg, 20rpx);
  padding: 20rpx 24rpx;
}
.input-label {
  display: block;
  font-size: 24rpx;
  color: var(--color-text-secondary);
  margin-bottom: 8rpx;
}
.required {
  color: var(--color-danger);
  margin-left: 2rpx;
}
.input {
  width: 100%;
  font-size: 28rpx;
  color: var(--color-text);
  line-height: 1.5;
}
.picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44rpx;
  font-size: 28rpx;
  color: var(--color-text);
}
.picker-arrow {
  font-size: 28rpx;
  color: var(--color-text-tertiary);
}
/* picker 下方红色警告：当前国家不在运营配置中 */
.picker-warn {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--color-danger);
  line-height: 1.4;
}

/* 双列（省 / 市） */
.row-group {
  display: flex;
  gap: 12rpx;
}
.half {
  flex: 1;
}

/* 标签选择 */
.tag-options {
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
}
.tag-option {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 12rpx 20rpx;
  border-radius: 999px;
  background: var(--color-background);
  color: var(--color-text-secondary);
  font-size: 24rpx;
  border: 1rpx solid transparent;
}
.tag-option.active {
  background: var(--color-primary);
  color: #fff;
  border-color: var(--color-primary);
}

/* 设为默认 */
.default-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 24rpx;
  background: var(--color-surface);
  border-radius: var(--radius-lg, 20rpx);
  font-size: 28rpx;
  color: var(--color-text);
}
.checkbox {
  width: 36rpx;
  height: 36rpx;
  border: 2rpx solid var(--color-divider);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 22rpx;
  background: var(--color-surface);
  flex-shrink: 0;
}
.checkbox.checked {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

/* ============ 底部 ============ */
.bottom-bar {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  background: var(--color-surface);
  border-top: 1rpx solid var(--color-divider);
}
.btn {
  flex: 1;
  height: 88rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  font-size: 28rpx;
  font-weight: var(--font-weight-medium);
  border: 1rpx solid transparent;
}
.cancel-btn {
  background: var(--color-background);
  color: var(--color-text);
}
.save-btn {
  background: var(--color-primary);
  color: #fff;
  border-color: var(--color-primary);
}
</style>
