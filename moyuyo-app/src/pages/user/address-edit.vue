<template>
  <view class="address-edit">
    <view class="header-bar">
      <view class="header-back" aria-label="返回" @click="goBack">
        <text class="luc luc-arrow-left" />
      </view>
      <text class="title">{{ form.id ? $t('address.editTitle') : $t('address.addTitle') }}</text>
      <view class="header-spacer" />
    </view>

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
          @change="onCountryChange"
        >
          <view class="input picker">
            <text>{{ countryLabel(form.country) || $t('address.countryPlaceholder') }}</text>
            <text class="luc luc-chevron-down picker-arrow" />
          </view>
        </picker>
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

<script>
import { addressApi } from '@/api'
import { i18n } from '@/i18n'

// 国家码（值与英文/本地化文案分离，便于在 picker 中显示本地化文本）
const COUNTRY_CODES = [
  'US',
  'CA',
  'GB',
  'DE',
  'FR',
  'ES',
  'IT',
  'NL',
  'AU',
  'JP',
  'CN',
  'HK',
  'TW',
  'SG',
  'MY',
]

export default {
  data() {
    return {
      localeVersion: 0,
      form: {
        id: null,
        receiver: '',
        phone: '',
        country: 'US',
        province: '',
        city: '',
        district: '',
        detail: '',
        zipCode: '',
        tag: '',
        isDefault: false,
      },
    }
  },

  computed: {
    // 依赖 localeVersion,语言切换时 picker 文本会重新渲染
    countryLabels() {
      void this.localeVersion
      return COUNTRY_CODES.map((code) => i18n.t(`address.countryCodes.${code}`))
    },
    tags() {
      void this.localeVersion
      return [
        { value: 'HOME', label: i18n.t('address.tagHome'), icon: 'luc-home' },
        { value: 'COMPANY', label: i18n.t('address.tagCompany'), icon: 'luc-briefcase' },
        { value: 'OTHER', label: i18n.t('address.tagOther'), icon: 'luc-map-pin' },
      ]
    },
    countryIndex() {
      const idx = COUNTRY_CODES.indexOf(this.form.country)
      return idx >= 0 ? idx : 0
    },
  },

  onLoad(query) {
    // 订阅语言切换
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
    if (query.id) {
      this.loadDetail(query.id)
    }
  },
  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  methods: {
    async loadDetail(id) {
      try {
        const addr = await addressApi.getAddressDetail(id)
        if (addr) {
          this.form = { ...this.form, ...addr }
          // 兼容后端可能不返回 tag 的情况
          if (typeof this.form.tag !== 'string') this.form.tag = ''
        }
      } catch (e) {
        console.warn('[address-edit] load failed', e)
        uni.showToast({ title: i18n.t('address.loadFailed'), icon: 'none' })
      }
    },

    countryLabel(code) {
      if (!code) return ''
      return i18n.t(`address.countryCodes.${code}`)
    },

    onCountryChange(e) {
      const idx = Number(e.detail.value)
      if (COUNTRY_CODES[idx]) this.form.country = COUNTRY_CODES[idx]
    },

    async onSave() {
      // 必填校验
      const required = [
        { key: 'receiver', field: i18n.t('address.fieldReceiver') },
        { key: 'phone', field: i18n.t('address.fieldPhone') },
        { key: 'detail', field: i18n.t('address.fieldDetail') },
        { key: 'city', field: i18n.t('address.fieldCity') },
      ]
      for (const r of required) {
        if (!this.form[r.key] || !String(this.form[r.key]).trim()) {
          uni.showToast({
            title: i18n.t('address.requiredField', { field: r.field }),
            icon: 'none',
          })
          return
        }
      }
      // 手机号简单格式校验（最少 6 位数字即可，海外号码格式差异大）
      if (!/^\d{6,20}$/.test(String(this.form.phone).replace(/\s+/g, ''))) {
        uni.showToast({ title: i18n.t('address.invalidPhone'), icon: 'none' })
        return
      }

      try {
        uni.showLoading({ title: i18n.t('address.saving') })
        if (this.form.id) {
          await addressApi.updateAddress(this.form.id, this.form)
        } else {
          await addressApi.createAddress(this.form)
        }
        uni.hideLoading()
        uni.showToast({ title: i18n.t('address.saved'), icon: 'success' })
        setTimeout(() => uni.navigateBack(), 600)
      } catch (e) {
        uni.hideLoading()
        console.warn('[address-edit] save failed', e)
        uni.showToast({ title: i18n.t('address.saveFailed'), icon: 'none' })
      }
    },

    goBack() {
      const pages = getCurrentPages()
      if (pages.length > 1) uni.navigateBack({ delta: 1 })
      else uni.switchTab({ url: '/pages/tabbar/user' })
    },
  },
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
.header-bar {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
  position: sticky;
  top: 0;
  z-index: 10;
}
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
