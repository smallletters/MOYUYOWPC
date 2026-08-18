<template>
  <!-- 物流选项卡（对齐 WC shipping_product_data） -->
  <div class="tab-shipping">
    <div class="options-group">
      <div class="group-title">重量与尺寸</div>

      <div class="form-row">
        <div class="form-group">
          <label>重量 (weight)</label>
          <input v-model="form.weight" type="number" step="0.001" min="0" placeholder="0.000" />
          <span class="field-hint">单位 kg</span>
        </div>
        <div class="form-group">
          <label>运费类 (shipping_class)</label>
          <select v-model="form.shippingClass">
            <option v-for="sc in shippingClasses" :key="sc.value" :value="sc.value">{{ sc.label }}</option>
          </select>
          <span class="field-hint">WooCommerce: shipping_class</span>
        </div>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label>长度 (length)</label>
          <input v-model="dimensions.length" type="number" step="0.01" min="0" placeholder="cm" />
        </div>
        <div class="form-group">
          <label>宽度 (width)</label>
          <input v-model="dimensions.width" type="number" step="0.01" min="0" placeholder="cm" />
        </div>
        <div class="form-group">
          <label>高度 (height)</label>
          <input v-model="dimensions.height" type="number" step="0.01" min="0" placeholder="cm" />
        </div>
      </div>
      <span class="field-hint">WooCommerce: dimensions {length, width, height}（单位 cm）</span>
    </div>

    <div class="options-group">
      <div class="group-title">物流提示</div>
      <div class="notice-line">
        实际运费计算由独立运费服务处理（详见 物流策略 模块）。此处填写的重量与尺寸用于运费估算。
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  form: { type: Object, required: true },
  dimensions: { type: Object, required: true },
  shippingClasses: { type: Array, default: () => [] }
})
</script>

<style scoped>
.tab-shipping { display: flex; flex-direction: column; gap: 18px; }
.options-group {
  padding: 14px;
  background: var(--background-50, #fafafa);
  border: 1px solid var(--border);
  border-radius: 8px;
}
.group-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-700);
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}
.form-row { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; margin-bottom: 12px; }
.form-row:first-of-type { grid-template-columns: 1fr 1fr; }
.form-group { display: flex; flex-direction: column; gap: 4px; }
.form-group label { font-size: 12px; font-weight: 500; color: var(--text-700); }
.form-group input,
.form-group select {
  padding: 7px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 13px;
  background: var(--card);
  font-family: inherit;
}
.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: var(--brand-500);
  box-shadow: 0 0 0 2px var(--brand-100, #e6f0ff);
}
.field-hint { font-size: 11px; color: var(--text-400); }
.notice-line {
  padding: 8px 10px;
  background: var(--brand-50, #f0f5ff);
  border-left: 3px solid var(--brand-500, #1d4ed8);
  border-radius: 4px;
  font-size: 12px;
  color: var(--text-700);
}
</style>