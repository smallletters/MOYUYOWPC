<template>
  <!-- 属性选项卡（对齐 WC product_attributes） -->
  <div class="tab-attributes">
    <div class="options-group">
      <div class="group-title">商品属性</div>
      <p class="hint-text">
        添加可搜索 / 可过滤的商品属性（如颜色、尺寸、材质）。勾选「用于变体」后会在「变体」选项卡生成变体组合。
      </p>

      <div class="toolbar">
        <button class="btn btn-primary" @click="addAttribute">+ 添加自定义属性</button>
        <button class="btn btn-outline" @click="expandAll(true)">展开全部</button>
        <button class="btn btn-outline" @click="expandAll(false)">收起全部</button>
      </div>

      <div v-if="!form.customAttributes.length" class="empty-state">
        暂无属性，点击「添加自定义属性」创建第一个属性。
      </div>

      <div v-for="(attr, idx) in form.customAttributes" :key="attr.uid" class="attribute-row" :class="{ collapsed: !attr.expanded }">
        <div class="attr-header" @click="attr.expanded = !attr.expanded">
          <span class="attr-toggle">{{ attr.expanded ? '▼' : '▶' }}</span>
          <strong class="attr-name">{{ attr.name || '(未命名属性)' }}</strong>
          <span class="attr-values-preview">{{ attr.options.join(', ') || '(无值)' }}</span>
          <button class="btn-delete" @click.stop="removeAttribute(idx)">删除</button>
        </div>

        <div v-show="attr.expanded" class="attr-body">
          <div class="form-row">
            <div class="form-group">
              <label>名称 (name)</label>
              <input v-model="attr.name" placeholder="例如：颜色 / Size / Material" />
            </div>
            <div class="form-group">
              <label>值（多个用 | 分隔，或回车添加）</label>
              <el-select
                v-model="attr.options"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="输入值后回车添加"
                style="width:100%"
              />
              <span class="field-hint">WC: options[]</span>
            </div>
          </div>

          <div class="form-row">
            <div class="form-group full">
              <div class="checkbox-row">
                <label class="check-item">
                  <input type="checkbox" v-model="attr.visible" />
                  <span>在商品页面可见 (visible)</span>
                </label>
                <label class="check-item">
                  <input type="checkbox" v-model="attr.variation" :disabled="form.productType !== 'variable'" />
                  <span>用于变体 (variation)</span>
                  <span class="field-hint" v-if="form.productType !== 'variable'">仅 Variable 商品支持</span>
                </label>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="form.customAttributes.length" class="actions-row">
        <button class="btn btn-primary" @click="regenerate">生成变体（保存草稿）</button>
        <span class="field-hint">点击后基于「用于变体」的属性生成笛卡尔积变体列表</span>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  form: { type: Object, required: true }
})
const emit = defineEmits(['regenerate-variations'])

let _uid = 0
function nextUid() { return 'attr_' + (++_uid) + '_' + Math.random().toString(36).slice(2, 6) }

function addAttribute() {
  props.form.customAttributes.push({
    uid: nextUid(),
    name: '',
    options: [],
    visible: true,
    variation: false,
    expanded: true
  })
}

function removeAttribute(idx) {
  props.form.customAttributes.splice(idx, 1)
  emit('regenerate-variations')
}

function expandAll(state) {
  props.form.customAttributes.forEach(a => { a.expanded = state })
}

function regenerate() {
  // 切换到变体选项卡需要父组件配合：仅触发事件
  emit('regenerate-variations')
}
</script>

<style scoped>
.tab-attributes { display: flex; flex-direction: column; gap: 18px; }
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
.hint-text { font-size: 12px; color: var(--text-500); margin: 4px 0 12px; }

.toolbar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }

.empty-state {
  padding: 24px;
  text-align: center;
  color: var(--text-400);
  background: var(--card);
  border: 1px dashed var(--border);
  border-radius: 6px;
  font-size: 13px;
}

.attribute-row {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 6px;
  margin-bottom: 8px;
  overflow: hidden;
}
.attr-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  cursor: pointer;
  background: var(--background-100);
  font-size: 13px;
}
.attr-toggle { font-size: 10px; color: var(--text-500); width: 14px; }
.attr-name { flex: 0 0 auto; min-width: 100px; }
.attr-values-preview {
  flex: 1;
  font-size: 12px;
  color: var(--text-500);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.btn-delete {
  border: none;
  background: transparent;
  color: var(--state-error);
  cursor: pointer;
  font-size: 12px;
  padding: 2px 8px;
}
.btn-delete:hover { background: var(--state-error-surface); border-radius: 4px; }

.attr-body { padding: 12px; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 10px; }
.form-group { display: flex; flex-direction: column; gap: 4px; }
.form-group.full { grid-column: 1 / -1; }
.form-group label { font-size: 12px; font-weight: 500; color: var(--text-700); }
.form-group input {
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 13px;
  background: var(--card);
  font-family: inherit;
}
.checkbox-row { display: flex; gap: 24px; padding: 6px 0; }
.check-item { display: flex; align-items: center; gap: 4px; font-size: 13px; cursor: pointer; }
.field-hint { font-size: 11px; color: var(--text-400); margin-left: 6px; }

.actions-row { margin-top: 12px; display: flex; gap: 12px; align-items: center; }

.btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  border: 1px solid transparent;
  font-family: inherit;
}
.btn-primary { background: var(--brand-600); color: #fff; border-color: var(--brand-600); }
.btn-primary:hover { background: var(--brand-700); }
.btn-outline { background: var(--card); color: var(--text-700); border-color: var(--border); }
.btn-outline:hover { background: var(--background-100); }
</style>