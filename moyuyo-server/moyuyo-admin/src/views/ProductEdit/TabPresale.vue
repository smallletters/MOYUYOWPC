<template>
  <!-- 预售选项卡：DISPLAY 展示型 / FULL 全款预售 / DEPOSIT 定金+尾款 -->
  <div class="tab-presale">
    <div class="options-group">
      <div class="group-title">预售开关</div>
      <div class="form-row">
        <div class="form-group">
          <label>启用预售</label>
          <div class="status-toggle" style="margin-top:4px">
            <label class="toggle-switch">
              <input type="checkbox" v-model="form.presale.enabled" />
              <span class="toggle-slider"></span>
            </label>
            <span class="status-text">{{ form.presale.enabled ? '已启用' : '未启用' }}</span>
            <span class="field-hint">关闭并保存后，该商品的预售配置会被删除</span>
          </div>
        </div>
        <div class="form-group">
          <label>预售状态</label>
          <input :value="statusText" disabled />
          <span class="field-hint">由后端按预售时间与库存自动计算</span>
        </div>
      </div>

      <div v-show="form.presale.enabled" class="form-row">
        <div class="form-group full">
          <label>预售类型 (presale_type)</label>
          <div class="radio-group">
            <label class="radio-item">
              <input type="radio" v-model="form.presale.presaleType" value="DISPLAY" />
              <span>展示型预售 — 只展示预售时间/发货时间，下单按现货价</span>
            </label>
            <label class="radio-item">
              <input type="radio" v-model="form.presale.presaleType" value="FULL" />
              <span>全款预售 — 预售期内一次性支付预售价</span>
            </label>
            <label class="radio-item">
              <input type="radio" v-model="form.presale.presaleType" value="DEPOSIT" />
              <span>定金预售 — 先付定金，尾款期支付剩余金额</span>
            </label>
          </div>
        </div>
      </div>
    </div>

    <div v-show="form.presale.enabled" class="options-group">
      <div class="group-title">预售时间</div>
      <div class="form-row">
        <div class="form-group">
          <label>预售开始时间 (start_time)</label>
          <el-date-picker
            v-model="form.presale.startTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择开始时间"
            style="width:100%"
          />
        </div>
        <div class="form-group">
          <label>预售结束时间 (end_time)</label>
          <el-date-picker
            v-model="form.presale.endTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择结束时间"
            style="width:100%"
          />
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>预计发货日期 (ship_date)</label>
          <el-date-picker
            v-model="form.presale.shipDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择发货日期"
            style="width:100%"
          />
          <span class="field-hint">APP 详情页展示「预计 X 月 X 日发货」</span>
        </div>
        <div class="form-group">
          <label>未付款自动取消（分钟）</label>
          <input v-model="form.presale.autoCancelMinutes" type="number" min="1" placeholder="留空用系统默认" />
          <span class="field-hint">预售订单超时未支付自动关闭</span>
        </div>
      </div>
    </div>

    <!-- 展示型不看价格，全款/定金需要预售价 -->
    <div v-show="form.presale.enabled && form.presale.presaleType !== 'DISPLAY'" class="options-group">
      <div class="group-title">预售价格</div>
      <div class="form-row">
        <div class="form-group">
          <label>预售价格 (presale_price)</label>
          <input v-model="form.presale.presalePrice" type="number" min="0" step="0.01" placeholder="不得高于商品售价" />
          <span class="field-hint">商品当前售价：{{ form.price || '未设置' }}</span>
        </div>
        <div class="form-group" v-if="form.presale.presaleType === 'FULL'">
          <label>下单支付金额</label>
          <input :value="form.presale.presalePrice || '0'" disabled />
          <span class="field-hint">全款预售：下单即支付预售价</span>
        </div>
      </div>
    </div>

    <!-- 定金预售专有字段 -->
    <div v-show="form.presale.enabled && form.presale.presaleType === 'DEPOSIT'" class="options-group">
      <div class="group-title">定金与尾款</div>
      <div class="form-row">
        <div class="form-group">
          <label>定金金额 (deposit_amount)</label>
          <input v-model="form.presale.depositAmount" type="number" min="0" step="0.01" placeholder="必须小于预售价格" />
        </div>
        <div class="form-group">
          <label>定金抵扣金额 (deposit_offset)</label>
          <input v-model="form.presale.depositOffset" type="number" min="0" step="0.01" placeholder="留空按定金全额抵扣" />
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>尾款金额（自动计算）</label>
          <input :value="balanceAmount" disabled />
          <span class="field-hint">尾款 = 预售价格 - 定金抵扣金额，必须大于 0</span>
        </div>
        <div class="form-group">
          <label>定金是否可退</label>
          <div class="status-toggle" style="margin-top:4px">
            <label class="toggle-switch">
              <input type="checkbox" v-model="form.presale.depositRefundable" />
              <span class="toggle-slider"></span>
            </label>
            <span class="status-text">{{ form.presale.depositRefundable ? '可退定金' : '定金不可退' }}</span>
          </div>
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>尾款支付开始时间 (balance_start)</label>
          <el-date-picker
            v-model="form.presale.balanceStart"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="须晚于预售结束时间"
            style="width:100%"
          />
        </div>
        <div class="form-group">
          <label>尾款支付结束时间 (balance_end)</label>
          <el-date-picker
            v-model="form.presale.balanceEnd"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择尾款结束时间"
            style="width:100%"
          />
        </div>
      </div>
    </div>

    <div v-show="form.presale.enabled" class="options-group">
      <div class="group-title">预售库存与限购</div>
      <div class="form-row">
        <div class="form-group">
          <label>预售库存上限 (stock_limit)</label>
          <input v-model="form.presale.stockLimit" type="number" min="1" placeholder="留空表示不限" />
        </div>
        <div class="form-group">
          <label>已售预售数量</label>
          <input :value="form.presale.soldCount ?? 0" disabled />
          <span class="field-hint">由预售订单累计，后台不可修改</span>
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>每人限购数量 (limit_per_user)</label>
          <input v-model="form.presale.limitPerUser" type="number" min="1" placeholder="留空表示不限" />
        </div>
        <div class="form-group">
          <label>延迟发货补偿（天）</label>
          <input v-model="form.presale.compensationDays" type="number" min="0" placeholder="例如 7" />
          <span class="field-hint">超过该天数未发货触发补偿</span>
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label>补偿比例 (%)</label>
          <input v-model="form.presale.compensationPercent" type="number" min="0" step="0.01" placeholder="例如 5" />
          <span class="field-hint">按预售价格比例补偿</span>
        </div>
      </div>
      <div class="form-row">
        <div class="form-group full">
          <label>预售须知 (notice_text)</label>
          <textarea v-model="form.presale.noticeText" rows="3" placeholder="APP 商品详情页展示的预售说明，例如：预计 12 月 20 日起按订单顺序发货"></textarea>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  form: { type: Object, required: true }
})

// 预售状态中文文案
const statusText = computed(() => {
  const map = {
    DRAFT: '草稿（未配置预售时间）',
    SCHEDULED: '待开始',
    ONGOING: '进行中',
    SOLD_OUT: '已售罄',
    ENDED: '已结束'
  }
  return map[props.form.presale.status] || '未启用预售'
})

// 尾款 = 预售价格 - 定金抵扣金额（未填抵扣时按定金全额抵扣）
const balanceAmount = computed(() => {
  const price = Number(props.form.presale.presalePrice)
  if (!price) return '0.00'
  const offset = props.form.presale.depositOffset !== ''
    ? Number(props.form.presale.depositOffset)
    : Number(props.form.presale.depositAmount || 0)
  const balance = price - offset
  return balance > 0 ? balance.toFixed(2) : '0.00'
})
</script>

<style scoped>
.tab-presale { display: flex; flex-direction: column; gap: 18px; }
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
.form-group input:disabled {
  background: var(--background-100);
  color: var(--text-500);
  cursor: not-allowed;
}
.field-hint { font-size: 11px; color: var(--text-400); }

.radio-group { display: flex; flex-wrap: wrap; gap: 16px; }
.radio-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-700); }

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
.status-text { font-size: 12px; color: var(--text-600); }
</style>
