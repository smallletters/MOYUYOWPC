<template>
  <!-- 库存选项卡（对齐 WC inventory_product_data） -->
  <div class="tab-inventory">
    <div class="options-group">
      <div class="group-title">基础标识</div>
      <div class="form-row">
        <div class="form-group">
          <label><abbr title="Stock Keeping Unit">SKU</abbr> 编码</label>
          <input v-model="form.spuCode" placeholder="唯一 SKU" />
          <span class="field-hint">WooCommerce: sku</span>
        </div>
        <div class="form-group">
          <label>
            <abbr title="Global Trade Item Number">GTIN</abbr> /
            <abbr title="Universal Product Code">UPC</abbr> /
            <abbr title="European Article Number">EAN</abbr> /
            <abbr title="International Standard Book Number">ISBN</abbr>
          </label>
          <input v-model="form.globalUniqueId" placeholder="全球唯一商品编码" />
          <span class="field-hint">WooCommerce: global_unique_id</span>
        </div>
      </div>
    </div>

    <div class="options-group">
      <div class="group-title">库存管理</div>

      <div class="form-row">
        <div class="form-group">
          <label>启用库存管理 (manage_stock)</label>
          <div class="status-toggle" style="margin-top:4px">
            <label class="toggle-switch">
              <input type="checkbox" v-model="form.manageStock" />
              <span class="toggle-slider"></span>
            </label>
            <span class="status-text">{{ form.manageStock ? '已启用' : '未启用' }}</span>
            <span class="field-hint">关闭后，库存数量仅按库存状态判断</span>
          </div>
        </div>
      </div>

      <div v-show="form.manageStock" class="form-row">
        <div class="form-group">
          <label>库存数量 (stock_quantity)</label>
          <input v-model="form.stock" type="number" min="0" placeholder="0" />
          <span class="field-hint">当前可售库存</span>
        </div>
        <div class="form-group">
          <label>低库存阈值 (low_stock_amount)</label>
          <input v-model="form.lowStockAmount" type="number" min="0" placeholder="例如 2" />
          <span class="field-hint">低于此值时发送邮件通知</span>
        </div>
      </div>

      <div v-show="form.manageStock" class="form-row">
        <div class="form-group full">
          <label>允许缺货订购 (backorders)</label>
          <div class="radio-group">
            <label class="radio-item">
              <input type="radio" v-model="form.backorders" value="no" />
              <span>不开启 No</span>
            </label>
            <label class="radio-item">
              <input type="radio" v-model="form.backorders" value="notify" />
              <span>通知 Notify</span>
            </label>
            <label class="radio-item">
              <input type="radio" v-model="form.backorders" value="yes" />
              <span>允许 Yes</span>
            </label>
          </div>
          <span class="field-hint">WooCommerce: backorders（无库存时是否继续接受订单）</span>
        </div>
      </div>

      <div class="form-row">
        <div class="form-group full">
          <label>库存状态 (stock_status)</label>
          <div class="radio-group">
            <label class="radio-item">
              <input type="radio" v-model="form.stockStatus" value="IN_STOCK" />
              <span>有货 in stock</span>
            </label>
            <label class="radio-item">
              <input type="radio" v-model="form.stockStatus" value="OUT_OF_STOCK" />
              <span>缺货 out of stock</span>
            </label>
            <label class="radio-item">
              <input type="radio" v-model="form.stockStatus" value="ON_BACKORDER" />
              <span>预售 on backorder</span>
            </label>
          </div>
        </div>
      </div>

      <div class="form-row">
        <div class="form-group full">
          <label>单独出售 (sold_individually)</label>
          <div class="status-toggle">
            <label class="toggle-switch">
              <input type="checkbox" v-model="form.soldIndividually" />
              <span class="toggle-slider"></span>
            </label>
            <span class="status-text">{{ form.soldIndividually ? '限制每个订单只能购买 1 件' : '允许单订单多件' }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  form: { type: Object, required: true }
})
</script>

<style scoped>
.tab-inventory { display: flex; flex-direction: column; gap: 18px; }
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
.form-group abbr { text-decoration: none; border-bottom: 1px dotted var(--text-400); }
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

.radio-group { display: flex; gap: 18px; padding: 6px 0; }
.radio-item { display: flex; align-items: center; gap: 4px; font-size: 13px; cursor: pointer; }

.status-toggle { display: flex; align-items: center; gap: 12px; }
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
.status-text { font-size: 13px; color: var(--text-700); }
</style>