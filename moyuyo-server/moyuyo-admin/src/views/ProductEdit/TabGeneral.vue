<template>
  <!-- 常规选项卡（对齐 WC general_product_data + linked_product 部分） -->
  <div class="tab-general">
    <!-- 基础信息 -->
    <div class="options-group">
      <div class="group-title">基础信息</div>
      <div class="form-row">
        <div class="form-group full">
          <label>商品名称 (name) <span class="required">*</span></label>
          <input v-model="form.name" placeholder="输入商品名称" @input="emit('permalink-change', form.name)" />
          <span class="field-hint">WooCommerce: name</span>
        </div>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label>商品分类 <span class="required">*</span></label>
          <select v-model="form.categoryId">
            <option value="">请选择分类</option>
            <option v-for="cat in categories" :key="cat.id" :value="cat.id">{{ cat.name }}</option>
          </select>
          <span class="field-hint">WooCommerce: categories[0].id</span>
        </div>
        <div class="form-group">
          <label>商品标签</label>
          <input v-model="form.tags" placeholder="多个标签用逗号分隔" />
          <span class="field-hint">WooCommerce: tags[].name</span>
        </div>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label>Permanalink 短链 (slug)</label>
          <input v-model="form.slug" placeholder="自动从名称生成" />
          <span class="field-hint">WooCommerce: slug</span>
        </div>
        <div class="form-group">
          <label>品牌</label>
          <input :value="form.brandId || ''" disabled placeholder="待接入品牌选择器" />
          <span class="field-hint">brandId（占位字段）</span>
        </div>
      </div>

      <div class="form-row">
        <div class="form-group full">
          <label>商品描述 (description)</label>
          <RichTextEditor v-model="form.detail" :rows="6" />
          <span class="field-hint">支持 HTML / 图片插入；WooCommerce: description</span>
        </div>
      </div>

      <div class="form-row">
        <div class="form-group full">
          <label>简短描述 (short_description)</label>
          <textarea v-model="form.shortDetail" rows="2" placeholder="商品摘要"></textarea>
        </div>
      </div>

      <div class="form-row">
        <div class="form-group full">
          <label>商品图片库 (images[])</label>
          <ImageUploader v-model="form.images" />
          <span class="field-hint">WooCommerce: images[].src（首张为封面图 images[0]）</span>
        </div>
      </div>
    </div>

    <!-- 定价（show_if_simple / show_if_external） -->
    <div v-show="form.productType !== 'grouped'" class="options-group">
      <div class="group-title">定价</div>
      <div class="form-row">
        <div class="form-group">
          <label>原价 (regular_price) <span class="required">*</span></label>
          <input v-model="form.originalPrice" type="number" step="0.01" min="0" placeholder="0.00" />
        </div>
        <div class="form-group">
          <label>售价 (sale_price)</label>
          <input v-model="form.price" type="number" step="0.01" min="0" placeholder="0.00" />
          <span class="field-hint">留空则使用原价出售</span>
        </div>
      </div>

      <!-- 销售日期区间（仅 simple 可见） -->
      <div v-show="form.productType === 'simple' || form.productType === 'external'" class="form-row">
        <div class="form-group">
          <label>销售起始日期 (date_on_sale_from)</label>
          <input v-model="form.salePriceDatesFrom" type="date" />
        </div>
        <div class="form-group">
          <label>销售结束日期 (date_on_sale_to)</label>
          <input v-model="form.salePriceDatesTo" type="date" />
          <span class="field-hint">销售期从 00:00:00 到 23:59:59</span>
        </div>
      </div>
    </div>

    <!-- 外部商品 URL（show_if_external） -->
    <div v-show="form.productType === 'external'" class="options-group">
      <div class="group-title">外部商品</div>
      <div class="form-row">
        <div class="form-group">
          <label>产品 URL (product_url) <span class="required">*</span></label>
          <input v-model="form.productUrl" placeholder="https://" />
        </div>
        <div class="form-group">
          <label>按钮文本 (button_text)</label>
          <input v-model="form.buttonText" placeholder="Buy product" />
        </div>
      </div>
    </div>

    <!-- 税务（show_if_simple / show_if_variable / show_if_external） -->
    <div v-show="form.productType !== 'grouped'" class="options-group">
      <div class="group-title">税务</div>
      <div class="form-row">
        <div class="form-group">
          <label>税状态 (tax_status)</label>
          <select v-model="form.taxStatus">
            <option value="taxable">Taxable — 应税</option>
            <option value="shipping">Shipping only — 仅运费</option>
            <option value="none">None — 不计税</option>
          </select>
        </div>
        <div class="form-group">
          <label>税类 (tax_class)</label>
          <select v-model="form.taxClass">
            <option value="standard">Standard rate — 标准税率</option>
            <option value="reduced-rate">Reduced rate — 减免税率</option>
            <option value="zero-rate">Zero rate — 零税率</option>
          </select>
        </div>
      </div>
    </div>

    <!-- 发布状态 -->
    <div class="options-group">
      <div class="group-title">发布</div>
      <div class="form-row">
        <div class="form-group">
          <label>商品状态 (status)</label>
          <div class="status-toggle">
            <label class="toggle-switch">
              <input type="checkbox" v-model="form.onSale" />
              <span class="toggle-slider"></span>
            </label>
            <span class="status-text">{{ form.onSale ? '上架 — publish' : '下架 — draft' }}</span>
          </div>
        </div>
        <div class="form-group">
          <label>已售数量 (total_sales)</label>
          <input :value="form.sales || '0'" disabled />
          <span class="field-hint">系统自动累计，只读</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import RichTextEditor from './RichTextEditor.vue'
import ImageUploader from './ImageUploader.vue'

const props = defineProps({
  form: { type: Object, required: true },
  categories: { type: Array, default: () => [] }
})
const emit = defineEmits(['permalink-change'])
</script>

<style scoped>
.tab-general { display: flex; flex-direction: column; gap: 18px; }
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
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 12px; }
.form-row:last-child { margin-bottom: 0; }
.form-group { display: flex; flex-direction: column; gap: 4px; }
.form-group.full { grid-column: 1 / -1; }
.form-group label { font-size: 12px; font-weight: 500; color: var(--text-700); }
.form-group label .required { color: var(--state-error); }
.form-group input,
.form-group select,
.form-group textarea {
  padding: 7px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 13px;
  background: var(--card);
  font-family: inherit;
}
.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus {
  outline: none;
  border-color: var(--brand-500);
  box-shadow: 0 0 0 2px var(--brand-100, #e6f0ff);
}
.field-hint { font-size: 11px; color: var(--text-400); }

.image-preview {
  margin-top: 8px;
  padding: 8px;
  background: var(--background-100);
  border-radius: 6px;
  text-align: center;
}
.image-preview img { max-width: 200px; max-height: 200px; object-fit: contain; }

.status-toggle { display: flex; align-items: center; gap: 12px; padding-top: 4px; }
.toggle-switch { position: relative; display: inline-block; width: 40px; height: 22px; }
.toggle-switch input { opacity: 0; width: 0; height: 0; }
.toggle-slider {
  position: absolute; cursor: pointer; inset: 0;
  background: var(--background-300); border-radius: 999px;
  transition: 0.2s;
}
.toggle-slider:before {
  position: absolute; content: ''; height: 16px; width: 16px;
  left: 3px; top: 3px; background: #fff; border-radius: 50%;
  transition: 0.2s;
}
.toggle-switch input:checked + .toggle-slider { background: var(--brand-600); }
.toggle-switch input:checked + .toggle-slider:before { transform: translateX(18px); }
.status-text { font-size: 13px; color: var(--text-700); font-weight: 500; }
</style>