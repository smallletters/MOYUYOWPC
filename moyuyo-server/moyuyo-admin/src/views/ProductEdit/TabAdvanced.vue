<template>
  <!-- 高级选项卡（对齐 WC advanced_product_data） -->
  <div class="tab-advanced">
    <div class="options-group">
      <div class="group-title">购买须知 / 元数据</div>
      <div class="form-row">
        <div class="form-group full">
          <label>购买须知 (purchase_note)</label>
          <textarea v-model="form.purchaseNote" rows="3" placeholder="订单确认页 / 邮件中展示的购买须知"></textarea>
          <span class="field-hint">WooCommerce: purchase_note</span>
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>菜单排序 (menu_order)</label>
          <input v-model.number="form.menuOrder" type="number" min="0" placeholder="0" />
          <span class="field-hint">自定义商品排序，数字越小越靠前</span>
        </div>
        <div class="form-group">
          <label>启用评论 (reviews_allowed)</label>
          <div class="status-toggle" style="margin-top:4px">
            <label class="toggle-switch">
              <input type="checkbox" v-model="form.reviewsAllowed" />
              <span class="toggle-slider"></span>
            </label>
            <span class="status-text">{{ form.reviewsAllowed ? '允许评论' : '已关闭评论' }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="options-group">
      <div class="group-title">类型扩展</div>
      <div class="form-row">
        <div class="form-group">
          <label>商品类型 (product_type)</label>
          <select :value="form.productType" disabled>
            <option value="simple">Simple — 简单商品</option>
            <option value="variable">Variable — 可变商品</option>
            <option value="grouped">Grouped — 组合商品</option>
            <option value="external">External — 外部商品</option>
          </select>
          <span class="field-hint">在顶部 type_box 切换</span>
        </div>
        <div class="form-group">
          <label>虚拟商品 (virtual)</label>
          <div class="status-toggle" style="margin-top:4px">
            <label class="toggle-switch">
              <input type="checkbox" v-model="form.isVirtual" />
              <span class="toggle-slider"></span>
            </label>
            <span class="status-text">{{ form.isVirtual ? '虚拟（无需物流）' : '实物' }}</span>
          </div>
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>可下载商品 (downloadable)</label>
          <div class="status-toggle" style="margin-top:4px">
            <label class="toggle-switch">
              <input type="checkbox" v-model="form.isDownloadable" />
              <span class="toggle-slider"></span>
            </label>
            <span class="status-text">{{ form.isDownloadable ? '可下载文件' : '无下载' }}</span>
          </div>
        </div>
      </div>
    </div>
    <div class="options-group">
      <div class="group-title">积分兑换</div>
      <div class="form-row">
        <div class="form-group">
          <label>开启积分兑换</label>
          <div class="status-toggle" style="margin-top:4px">
            <label class="toggle-switch">
              <input type="checkbox" v-model="form.allowPointsExchange" />
              <span class="toggle-slider"></span>
            </label>
            <span class="status-text">{{ form.allowPointsExchange ? '积分商城可见' : '不参与' }}</span>
          </div>
        </div>
        <div class="form-group">
          <label>兑换所需积分</label>
          <input v-model.number="form.pointsPrice" type="number" min="0" placeholder="0"
                 :disabled="!form.allowPointsExchange" />
          <span class="field-hint">0 表示未配置，商品不会出现在积分商城</span>
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>积分兑换库存</label>
          <input v-model.number="form.pointsStock" type="number" min="-1" placeholder="0"
                 :disabled="!form.allowPointsExchange" />
          <span class="field-hint">与销售库存相互独立；-1 表示不限量</span>
        </div>
      </div>
    </div>
    <div class="options-group">
      <div class="group-title">积分抵扣（结算）</div>
      <div class="form-row">
        <div class="form-group">
          <label>允许使用积分抵扣</label>
          <div class="status-toggle" style="margin-top:4px">
            <label class="toggle-switch">
              <input type="checkbox" v-model="form.allowPointsDeduct" />
              <span class="toggle-slider"></span>
            </label>
            <span class="status-text">{{ form.allowPointsDeduct ? 'APP 结算页可展示积分抵扣' : 'APP 结算页隐藏积分抵扣' }}</span>
          </div>
          <span class="field-hint">关闭后，订单中包含该商品时结算页不展示积分抵扣</span>
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
.tab-advanced { display: flex; flex-direction: column; gap: 18px; }
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
.form-group { display: flex; flex-direction: column; gap: 4px; }
.form-group.full { grid-column: 1 / -1; }
.form-group label { font-size: 12px; font-weight: 500; color: var(--text-700); }
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
.form-group input:disabled,
.form-group select:disabled {
  background: var(--background-100);
  color: var(--text-500);
  cursor: not-allowed;
}
.field-hint { font-size: 11px; color: var(--text-400); }

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