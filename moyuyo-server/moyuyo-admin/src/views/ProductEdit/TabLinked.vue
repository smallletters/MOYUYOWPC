<template>
  <!-- 关联商品选项卡（对齐 WC linked_product_data） -->
  <div class="tab-linked">
    <div class="options-group">
      <div class="group-title">Upsell 加价销售 (upsell_ids)</div>
      <p class="hint-text">推荐给已加入购物车的客户的更高价值替代品。</p>
      <div class="linked-actions">
        <button class="btn btn-outline" @click="emit('open-upsell')">
          + 选择商品
        </button>
        <span class="linked-count">已选 {{ form.upsellIds.length }} 个</span>
      </div>
      <div v-if="form.upsellIds.length" class="linked-tag-list">
        <span v-for="id in form.upsellIds" :key="id" class="linked-tag">
          #{{ id }}
          <button class="tag-remove" @click="removeUpsell(id)">×</button>
        </span>
      </div>
    </div>

    <div class="options-group">
      <div class="group-title">Cross-sell 交叉销售 (cross_sell_ids)</div>
      <p class="hint-text">购物车页底部展示的关联推荐，提升客单价。</p>
      <div class="linked-actions">
        <button class="btn btn-outline" @click="emit('open-cross')">
          + 选择商品
        </button>
        <span class="linked-count">已选 {{ form.crossSellIds.length }} 个</span>
      </div>
      <div v-if="form.crossSellIds.length" class="linked-tag-list">
        <span v-for="id in form.crossSellIds" :key="id" class="linked-tag">
          #{{ id }}
          <button class="tag-remove" @click="removeCross(id)">×</button>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  form: { type: Object, required: true }
})
const emit = defineEmits(['open-upsell', 'open-cross'])

function removeUpsell(id) {
  props.form.upsellIds = props.form.upsellIds.filter(x => x !== id)
}
function removeCross(id) {
  props.form.crossSellIds = props.form.crossSellIds.filter(x => x !== id)
}
</script>

<style scoped>
.tab-linked { display: flex; flex-direction: column; gap: 18px; }
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
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}
.hint-text { font-size: 12px; color: var(--text-500); margin: 4px 0 10px; }
.linked-actions { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.linked-count { font-size: 12px; color: var(--text-500); }
.linked-tag-list { display: flex; flex-wrap: wrap; gap: 6px; }
.linked-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  background: var(--brand-50, #f0f5ff);
  border: 1px solid var(--brand-200, #c7d7fe);
  color: var(--brand-700, #1e40af);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}
.tag-remove {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  color: var(--brand-700, #1e40af);
  padding: 0 0 0 2px;
}
.btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text-700);
  font-family: inherit;
}
.btn:hover { background: var(--background-100); }
</style>