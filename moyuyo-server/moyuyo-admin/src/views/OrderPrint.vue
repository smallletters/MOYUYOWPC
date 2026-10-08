<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>订单打印</h2>
    </div>

    <!-- ===== 打印模板区块 ===== -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-header">
          <span class="section-title">打印模板</span>
          <span class="section-tip">支持拣货单、打包单、发货单、配货标签四种模板，点击卡片选择，打印时按所选模板输出</span>
        </div>
      </template>
      <div class="template-grid">
        <div
          v-for="tpl in printTemplates"
          :key="tpl.id"
          class="template-card"
          :class="{ selected: selectedTemplateId === tpl.id }"
          @click="selectTemplate(tpl)"
        >
          <div class="template-thumb" :style="{ background: tpl.gradient }">
            <el-icon :size="30" :color="tpl.color"><component :is="tpl.icon" /></el-icon>
            <span v-if="tpl.isDefault" class="badge default-badge">默认</span>
            <span v-if="selectedTemplateId === tpl.id" class="badge selected-badge">
              <el-icon :size="12"><Check /></el-icon>已选
            </span>
            <span v-if="tpl.requiresShipping && !yanwenEnabled" class="badge unavailable-badge">需配置</span>
          </div>
          <div class="template-name">{{ tpl.name }}</div>
          <div class="template-meta">
            <el-tag size="small" :type="tpl.tagType" effect="light">{{ tpl.type }}</el-tag>
            <span class="template-paper">{{ tpl.paper }}</span>
          </div>
          <div class="template-desc">{{ tpl.desc }}</div>
          <div class="template-actions" @click.stop>
            <el-button size="small" text type="primary" @click="handleEditTemplate(tpl)">
              <el-icon :size="12" style="margin-right:2px"><Edit /></el-icon>编辑
            </el-button>
            <el-button size="small" text :disabled="tpl.isDefault" @click="handleSetDefault(tpl)">
              <el-icon :size="12" style="margin-right:2px"><Star /></el-icon>设为默认
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- ===== 打印设置区块 ===== -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-header">
          <span class="section-title">打印设置</span>
          <span class="section-tip">默认纸张、份数、方向与边距，保存后本地生效</span>
        </div>
      </template>
      <el-form :model="printSettings" label-width="96px" class="settings-form">
        <el-row :gutter="24">
          <el-col :span="8">
            <el-form-item label="默认纸张">
              <el-select v-model="printSettings.paperSize" style="width: 200px">
                <el-option label="A4 (210×297mm)" value="a4" />
                <el-option label="A5 (148×210mm)" value="a5" />
                <el-option label="热敏纸 80×80mm" value="thermal-80" />
                <el-option label="热敏纸 100×150mm" value="thermal-100" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="打印份数">
              <el-input-number v-model="printSettings.copies" :min="1" :max="99" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="打印方向">
              <el-radio-group v-model="printSettings.orientation">
                <el-radio value="portrait">纵向</el-radio>
                <el-radio value="landscape">横向</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="24">
          <el-col :span="8">
            <el-form-item label="双面打印">
              <el-switch v-model="printSettings.duplex" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="上下边距">
              <el-input-number v-model="printSettings.marginY" :min="0" :max="50" />
              <span class="unit-text">mm</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="左右边距">
              <el-input-number v-model="printSettings.marginX" :min="0" :max="50" />
              <span class="unit-text">mm</span>
            </el-form-item>
          </el-col>
        </el-row>
        <div class="settings-footer">
          <el-button type="primary" @click="handleSaveSettings">保存设置</el-button>
        </div>
      </el-form>
    </el-card>

    <!-- ===== 待打印订单列表（保留原功能） ===== -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-header">
          <span class="section-title">待打印订单</span>
          <el-button type="primary" size="small" @click="handleBatchPrint" :disabled="!tableData.length">
            <el-icon :size="12" style="margin-right:2px"><Printer /></el-icon>批量打印
          </el-button>
        </div>
      </template>
      <el-form :model="filters" inline>
        <el-form-item label="订单编号">
          <el-input v-model="filters.keyword" placeholder="请输入订单编号" clearable />
        </el-form-item>
        <el-form-item label="打印状态">
          <el-select v-model="filters.printStatus" placeholder="全部" clearable style="width:140px">
            <el-option label="全部" value="" />
            <el-option label="未打印" value="未打印" />
            <el-option label="已打印" value="已打印" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板已选">
          <!-- 仅"曾用此模板打印过"的订单过滤。
               后端 controller 已对 'order' / 空值归一为不过滤；这里额外把"全部"映射为 'order' 兜底。 -->
          <el-select v-model="filters.printType" placeholder="全部" clearable style="width:160px">
            <el-option label="全部" value="order" />
            <el-option label="拣货单 (PICK)" value="PICK" />
            <el-option label="打包单 (PACK)" value="PACK" />
            <el-option label="发货单 (SHIP)" value="SHIP" />
            <el-option label="配货标签 (LABEL)" value="LABEL" />
            <el-option label="快递面单 (SHIPPING_LABEL)" value="SHIPPING_LABEL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderNo" label="订单编号" width="170" />
        <!-- P1：订单状态标签 —— 异常件（HOLD / 退款中）在打单前要看到，避免打印后才发现问题 -->
        <el-table-column prop="statusLabel" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="orderStatusTagType(row.status)" size="small">{{ row.statusLabel || orderStatusFallback(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <!-- P0：收件人 / 电话 / 地址三件套（地址用 tooltip + 折叠展示，宽度 200） -->
        <el-table-column label="收件人" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.receiverName || row.receiver || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="联系电话" width="130">
          <template #default="{ row }">
            <span style="font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;">{{ row.receiverPhone || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="收货地址" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.receiverAddress || '—' }}</span>
          </template>
        </el-table-column>
        <!-- P0：商品三件套 —— 用 popover 嵌套表格展示 SKU / 名称 / 规格 / 数量 -->
        <el-table-column label="商品信息" min-width="200">
          <template #default="{ row }">
            <el-popover
              placement="right"
              :width="420"
              trigger="hover"
              :show-after="200"
            >
              <template #reference>
                <span class="product-cell">{{ row.productInfo || '—' }}</span>
              </template>
              <template #default>
                <div class="product-popover">
                  <div class="product-popover-title">订单商品明细（{{ (row.items || []).length }} 件）</div>
                  <table class="product-popover-table">
                    <thead>
                      <tr>
                        <th style="width:22%">商品SKU</th>
                        <th style="width:34%">商品名称</th>
                        <th style="width:26%">规格</th>
                        <th style="width:18%;text-align:right">数量</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(it, idx) in (row.items || [])" :key="idx">
                        <td style="font-family:Consolas,monospace;">{{ it.skuCode || '—' }}</td>
                        <td>{{ it.productName || '—' }}</td>
                        <td>{{ it.skuSpec || '—' }}</td>
                        <td style="text-align:right">×{{ it.quantity }}</td>
                      </tr>
                      <tr v-if="!(row.items || []).length">
                        <td colspan="4" style="text-align:center;color:var(--text-400);">暂无商品明细</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </template>
            </el-popover>
          </template>
        </el-table-column>
        <!-- P0：实付金额（核对金额用） -->
        <el-table-column label="实付金额" width="110" align="right">
          <template #default="{ row }">
            <span style="font-weight:600;">{{ formatAmount(row.payAmount) }}</span>
          </template>
        </el-table-column>
        <!-- P2：运费 -->
        <el-table-column label="运费" width="90" align="right">
          <template #default="{ row }">
            <span>{{ formatAmount(row.freight) }}</span>
          </template>
        </el-table-column>
        <!-- P0：买家备注 —— 用 el-tag 显眼展示，"易碎/发顺丰"等关键信息打单前必看 -->
        <el-table-column label="买家备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.remark" type="warning" size="small" effect="light">{{ row.remark }}</el-tag>
            <span v-else style="color:var(--text-400);">—</span>
          </template>
        </el-table-column>
        <!-- P1：物流公司 + 运单号（已有运单时显示，避免重复打单/发货） -->
        <el-table-column label="物流" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="row.trackingNumber">
              <div style="display:flex;flex-direction:column;line-height:1.3;">
                <span style="font-size:11px;color:var(--text-400);">{{ shippingCarrierName(row.shippingCarrier) }}</span>
                <span style="font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;font-size:12px;">{{ row.trackingNumber }}</span>
              </div>
            </template>
            <span v-else style="color:var(--text-400);">未分配</span>
          </template>
        </el-table-column>
        <el-table-column prop="printStatus" label="打印" width="100">
          <template #default="{ row }">
            <el-tag :type="row.printStatus === '已打印' ? 'success' : 'warning'" size="small">{{ row.printStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="printCount" label="次数" width="70" align="center" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handlePrint(row)">
              <el-icon :size="12" style="margin-right:2px"><Printer /></el-icon>打印
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;padding:16px 0 0">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @change="loadData"
        />
      </div>
    </el-card>

    <!-- 模板编辑对话框：宽度 760px 容纳"基本信息 + HTML 编辑器 + 占位符帮助"三段式布局 -->
    <el-dialog v-model="templateDialogVisible" title="编辑打印模板" width="760px" append-to-body>
      <el-form :model="editingTemplate" label-width="80px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模板名称">
              <el-input v-model="editingTemplate.name" placeholder="请输入模板名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纸张规格">
              <el-select v-model="editingTemplate.paper" style="width: 100%">
                <el-option label="A4 (210×297mm)" value="a4" />
                <el-option label="A5 (148×210mm)" value="a5" />
                <el-option label="热敏纸 80×80mm" value="thermal-80" />
                <el-option label="热敏纸 100×150mm" value="thermal-100" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="模板说明">
          <el-input v-model="editingTemplate.desc" type="textarea" :rows="2" placeholder="请输入模板说明" />
        </el-form-item>
        <el-form-item label="模板内容">
          <div class="tpl-editor-toolbar">
            <span class="tpl-editor-tip">支持 HTML + 占位符，点击插入</span>
            <el-dropdown trigger="click" @command="insertPlaceholder">
              <el-button size="small" type="primary" plain>
                <el-icon :size="12" style="margin-right:2px"><Plus /></el-icon>插入占位符
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="p in PLACEHOLDERS" :key="p.token" :command="p.token">
                    <span class="ph-token">{{ p.token }}</span>
                    <span class="ph-desc">{{ p.desc }}</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button size="small" plain @click="resetToDefaultTemplate">
              <el-icon :size="12" style="margin-right:2px"><RefreshRight /></el-icon>恢复默认模板
            </el-button>
            <el-button size="small" plain @click="showTemplatePreview = !showTemplatePreview">
              <el-icon :size="12" style="margin-right:2px"><View /></el-icon>{{ showTemplatePreview ? '隐藏预览' : '预览效果' }}
            </el-button>
          </div>
          <el-input
            v-model="editingTemplate.contentTemplate"
            type="textarea"
            :rows="14"
            placeholder="支持 HTML 模板，使用 {{order.orderNo}} / {{items}} 等占位符。留空则使用代码默认模板。"
            class="tpl-editor-textarea"
            spellcheck="false"
          />
        </el-form-item>
        <!-- 占位符语法说明（折叠展示，避免太占空间） -->
        <el-collapse class="tpl-editor-help">
          <el-collapse-item title="占位符语法 & 使用说明" name="help">
            <div class="help-section">
              <p><strong>支持的占位符（点击上方"插入占位符"自动写入光标位置）：</strong></p>
              <ul>
                <li v-for="p in PLACEHOLDERS" :key="p.token">
                  <code>{{ p.token }}</code> — {{ p.desc }}
                </li>
              </ul>
              <p><strong>语法说明：</strong></p>
              <ul>
                <li>占位符区分大小写，必须严格使用双花括号 <code v-pre>{{ }}</code> 包裹。</li>
                <li><code v-pre>{{items}}</code> 会在打印时整段替换为商品明细 HTML（自动生成表格）。</li>
                <li>其他 <code v-pre>{{order.xxx}}</code> 占位符会做 HTML 转义后注入，杜绝 XSS。</li>
                <li>建议用 <code>&lt;style&gt;</code> 块内联 CSS，避免依赖后台布局样式。</li>
                <li>支持 <code>@media print</code> 媒体查询，打印时自动生效。</li>
              </ul>
            </div>
          </el-collapse-item>
        </el-collapse>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="改名将重置打印次数累加"
          description="模板名称变更后,旧记录将以旧名为唯一键、新记录以新名为唯一键,不会合并 print_count。"
          style="margin-top: 8px;"
        />
      </el-form>
      <!-- 预览面板（仅在用户点击"预览效果"时显示） -->
      <div v-if="showTemplatePreview" class="tpl-preview-wrapper">
        <div class="tpl-preview-title">预览效果（基于示例订单数据）</div>
        <div class="tpl-preview-frame" v-html="renderedPreviewHtml"></div>
      </div>
      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveTemplate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 打印内容区：Teleport 至 body，仅打印时可见，触发 window.print() 输出 -->
    <Teleport to="body">
      <!-- 通用模板：拣货单/打包单/发货单/配货标签 -->
      <div class="print-area" v-if="printingRow && !isShippingLabelMode">
        <div class="print-sheet" :style="printSheetStyle">
          <!-- P1：自定义模板优先 —— 当前模板若在数据库存了 contentTemplate，
               整页用 v-html 渲染运营自定义的 HTML（已做占位符替换 + XSS 转义）。
               否则走下面的"内置默认模板"块（向后兼容历史运营体验）。 -->
          <div v-if="currentTemplate && currentTemplate.contentTemplate" class="print-custom" v-html="renderedPrintHtml"></div>
          <template v-else>
          <div class="print-header">
            <h2>{{ currentTemplate ? currentTemplate.name : '打印单' }}</h2>
            <span class="print-time">打印时间：{{ printTime }}</span>
          </div>
          <!-- P0：打印纸头部 —— 订单状态 / 实付金额 / 买家备注 并列展示（运营打单一眼看到关键信息） -->
          <div class="print-meta">
            <span class="print-meta-item">
              <strong>状态：</strong>
              <span :class="'print-status print-status--' + orderStatusTagType(printingRow.status)">
                {{ printingRow.statusLabel || orderStatusFallback(printingRow.status) }}
              </span>
            </span>
            <span class="print-meta-item">
              <strong>实付金额：</strong>{{ formatAmount(printingRow.payAmount) }}
            </span>
            <span class="print-meta-item" v-if="printingRow.freight">
              <strong>运费：</strong>{{ formatAmount(printingRow.freight) }}
            </span>
          </div>
          <table class="print-table">
            <tbody>
              <!-- P0：订单编号 / 下单时间 -->
              <tr><th>订单编号</th><td>{{ printingRow.orderNo }}</td></tr>
              <tr><th>下单时间</th><td>{{ printingRow.createTime }}</td></tr>
              <!-- P0：收件人三件套（姓名 / 电话 / 地址）—— 主流电商"发货单"的标配 -->
              <tr><th>收件人</th><td>{{ printingRow.receiverName || printingRow.receiver || '—' }}</td></tr>
              <tr><th>联系电话</th><td>{{ printingRow.receiverPhone || '—' }}</td></tr>
              <tr><th>收货地址</th><td>{{ printingRow.receiverAddress || '—' }}</td></tr>
              <!-- P0：商品明细 —— 拆成多行表格（名称 / 规格 / 单价 / 数量），替换旧的拼接字符串 -->
              <tr>
                <th>商品明细</th>
                <td>
                  <table class="print-items" v-if="printingRow.items && printingRow.items.length">
                    <thead>
                      <tr>
                        <th style="width:22%">商品SKU</th>
                        <th style="width:38%">商品名称</th>
                        <th style="width:22%">规格</th>
                        <th style="width:18%;text-align:right">数量</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(it, idx) in printingRow.items" :key="idx">
                        <td style="font-family:Consolas,monospace;">{{ it.skuCode || '—' }}</td>
                        <td>{{ it.productName || '—' }}</td>
                        <td>{{ it.skuSpec || '—' }}</td>
                        <td style="text-align:right">×{{ it.quantity }}</td>
                      </tr>
                    </tbody>
                  </table>
                  <span v-else>{{ printingRow.productInfo || '—' }}</span>
                </td>
              </tr>
              <!-- P0：买家备注（醒目展示，避免漏掉"易碎/发顺丰"） -->
              <tr v-if="printingRow.remark">
                <th>买家备注</th>
                <td class="print-remark">{{ printingRow.remark }}</td>
              </tr>
              <!-- P1：物流信息（已有运单号时打印，避免重复打单/重复发货） -->
              <tr v-if="printingRow.trackingNumber">
                <th>物流</th>
                <td>{{ shippingCarrierName(printingRow.shippingCarrier) }} / {{ printingRow.trackingNumber }}</td>
              </tr>
              <tr><th>纸张 / 份数</th><td>{{ paperSizeLabel }} / {{ printSettings.copies }} 份</td></tr>
            </tbody>
          </table>
          <div class="print-footer">MOYUYO 订单打印系统</div>
          </template>
        </div>
      </div>
      <!-- 快递面单模板：渲染燕文等承运商返回的 PDF/PNG base64。
           支持单条 + 批量（shippingLabels 是数组）。 -->
      <div class="print-area print-area--shipping" v-if="isShippingLabelMode && shippingLabels.length">
        <!-- 批量打单：每个订单渲染一份面单，每份按 printSettings.copies 复制多联 -->
        <div
          v-for="label in shippingLabels"
          :key="label.orderId"
          class="print-batch-group">
          <div v-for="(_, idx) in printSettings.copies" :key="label.orderId + '-copy-' + idx" class="print-sheet print-sheet--shipping" :style="printSheetStyle">
            <!-- 燕文取号成功：用 iframe 嵌入 PDF 或 img 嵌入 PNG -->
            <iframe
              v-if="label.dataUrl && label.contentType === 'application/pdf'"
              :src="label.dataUrl"
              class="shipping-iframe"
              :title="'燕文快递面单 ' + label.orderNo"
            ></iframe>
            <img
              v-else-if="label.dataUrl"
              :src="label.dataUrl"
              class="shipping-image"
              :alt="'快递面单 ' + label.orderNo"
            />
            <!-- 燕文未启用 / 取号失败：使用通用面单模板（手动打印兜底） -->
            <div v-else class="shipping-fallback">
              <div class="shipping-fallback-title">快递面单（兜底）</div>
              <table class="print-table">
                <tbody>
                  <tr><th>订单号</th><td>{{ label.orderNo }}</td></tr>
                  <tr><th>承运商</th><td>{{ printingRow?.shippingCarrier || '—' }}</td></tr>
                  <tr><th>运单号</th><td>{{ printingRow?.trackingNumber || '—' }}</td></tr>
                  <tr><th>收件人</th><td>{{ printingRow?.receiverName || printingRow?.receiver || '—' }}</td></tr>
              <tr><th>商品</th><td>{{ printingRow?.productInfo || '—' }}</td></tr>
                  <tr><th>取号失败</th><td>{{ label.error || '—' }}</td></tr>
                  <tr><th>打印时间</th><td>{{ printTime }}</td></tr>
                </tbody>
              </table>
              <div class="print-footer">MOYUYO · 手动打印（请配置燕文 SDK 后获取电子面单）</div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 快递面单打印预览面板：常驻显示，方便用户预检后再触发 window.print() -->
    <el-card v-if="showShippingPreview" shadow="never" class="section-card shipping-preview">
      <template #header>
        <div class="section-header">
          <span class="section-title">📦 燕文快递面单预览（{{ shippingLabels.length }} 张）</span>
          <div style="display:flex;gap:8px;align-items:center;">
            <span v-if="shippingLabelError" class="shipping-error">{{ shippingLabelError }}</span>
            <el-button size="small" type="primary" :disabled="!shippingLabels.length" @click="triggerBrowserPrint">
              <el-icon :size="12" style="margin-right:2px"><Printer /></el-icon>浏览器打印
            </el-button>
            <el-button size="small" @click="closeShippingPreview">关闭</el-button>
          </div>
        </div>
      </template>
      <div class="shipping-preview-body">
        <!-- 批量打单时使用 Tab 切换展示每张面单 -->
        <template v-if="shippingLabels.length > 1">
          <el-tabs v-model="activeShippingTab" type="card" class="shipping-tabs">
            <el-tab-pane
              v-for="label in shippingLabels"
              :key="label.orderId"
              :name="String(label.orderId)">
              <template #label>
                <span :class="{ 'shipping-tab-error': label.error }">
                  {{ label.orderNo || '#' + label.orderId }}
                  <span v-if="label.error" style="color:var(--state-error);">⚠</span>
                </span>
              </template>
              <div class="shipping-pane">
                <div v-if="label.error" class="shipping-error-banner">取号失败：{{ label.error }}</div>
                <iframe
                  v-if="label.dataUrl && label.contentType === 'application/pdf'"
                  :src="label.dataUrl"
                  class="shipping-preview-iframe"
                ></iframe>
                <img
                  v-else-if="label.dataUrl"
                  :src="label.dataUrl"
                  class="shipping-preview-image"
                  :alt="'快递面单 ' + label.orderNo"
                />
              </div>
            </el-tab-pane>
          </el-tabs>
        </template>
        <!-- 单张面单直接占满预览区 -->
        <template v-else-if="shippingLabels.length === 1">
          <div class="shipping-pane">
            <div v-if="shippingLabels[0].error" class="shipping-error-banner">取号失败：{{ shippingLabels[0].error }}</div>
            <iframe
              v-if="shippingLabels[0].dataUrl && shippingLabels[0].contentType === 'application/pdf'"
              :src="shippingLabels[0].dataUrl"
              class="shipping-preview-iframe"
            ></iframe>
            <img
              v-else-if="shippingLabels[0].dataUrl"
              :src="shippingLabels[0].dataUrl"
              class="shipping-preview-image"
              :alt="'快递面单 ' + shippingLabels[0].orderNo"
            />
          </div>
        </template>
        <!-- 加载中或无数据 -->
        <div v-else-if="shippingLabelLoading" class="shipping-loading">正在调取燕文面单…</div>
        <div v-else class="shipping-empty">
          <div style="font-size:32px;margin-bottom:6px;">📭</div>
          <div>未取到面单数据</div>
          <div style="font-size:11px;color:var(--text-400);margin-top:4px;">请检查后端 moyuyo.logistics.yanwen.* 配置与运单号</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { List, Box, DocumentChecked, PriceTag, Check, Edit, Star, Printer, Promotion, Plus, RefreshRight, View } from '@element-plus/icons-vue'
import {
  getPrintList, recordPrint, getPrintDetail, fetchShippingLabel, getYanwenStatus, shipOrder,
  getPrintTemplates, updatePrintTemplate, setDefaultPrintTemplate,
  recordShippingLabelLog,
  getPrintSettings, savePrintSettings
} from '../api/admin'
import { toArray } from '../utils/safeArray'

const route = useRoute()
const router = useRouter()

const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  keyword: '',
  printStatus: '',
  // 模板已选过滤：
  //   'order' 兜底值 → 后端归一为 null，返回全部待打印订单
  //   其他 5 个模板 code → 后端按"曾用此模板打印"过滤（EXISTS 子查询）
  printType: 'order'
})

const tableData = ref([])

// 从 query.ids 进来的批量订单（如从 OrderList 顶部"批量打单"跳转）。
// 存在这里而不是直接用 tableData —— 因为 OrderPrint 自己的待打印列表
// (tableData) 可能与跳转目标不一致。本字段为 handleBatchPrint 提供"本次要打哪些订单"的权威数据源。
const currentBatchOrders = ref([])

// ==================== 打印模板（从后端持久化加载） ====================
// 5 个固定 code 的展示元数据（渐变色、图标、tag 类型），与数据库 mo_print_template.code 一一对应。
// 数据库只保存可编辑字段（name/paperSize/description/isDefault），UI 装饰信息前端维护。
const TEMPLATE_META = {
  PICK:           { name: '拣货单', type: '拣货单', gradient: 'linear-gradient(135deg, #e8f2ff, #cfe5ff)', color: '#2e8dff', icon: List, tagType: 'primary', requiresShipping: false },
  PACK:           { name: '打包单', type: '打包单', gradient: 'linear-gradient(135deg, #f7f7fa, #e5e5ea)', color: '#8e8e93', icon: Box, tagType: 'info', requiresShipping: false },
  SHIP:           { name: '发货单', type: '发货单', gradient: 'linear-gradient(135deg, #fff7ed, #ffedd5)', color: '#c2410c', icon: DocumentChecked, tagType: 'warning', requiresShipping: false },
  LABEL:          { name: '配货标签', type: '配货标签', gradient: 'linear-gradient(135deg, #f0fdf4, #dcfce7)', color: '#16a34a', icon: PriceTag, tagType: 'success', requiresShipping: false },
  SHIPPING_LABEL: { name: '快递面单', type: '快递面单', gradient: 'linear-gradient(135deg, #fdf2f8, #fce7f3)', color: '#db2777', icon: Promotion, tagType: 'danger', requiresShipping: true }
}
// 初始空数组，由 onMounted → loadPrintTemplates() 异步加载后填充
const printTemplates = ref([])
const selectedTemplateId = ref(null)

// 加载模板列表（持久化版本：从 mo_print_template 表读取，merge TEMPLATE_META 渲染）
async function loadPrintTemplates() {
  try {
    const list = await getPrintTemplates()
    const records = Array.isArray(list) ? list : []
    // 后端字段 + 前端展示元数据合并
    printTemplates.value = records.map(r => {
      const meta = TEMPLATE_META[r.code] || {}
      return {
        id: r.id,
        code: r.code,
        name: r.name || meta.name || r.code,
        type: meta.type || r.code,
        paper: r.paperSize || meta.paper || 'A4',
        desc: r.description || '',
        // 模板 HTML 正文：null 当空字符串处理；空字符串表示"使用代码默认模板"。
        // 后端 Service.listPrintTemplates 已统一空字符串返回，前端不再需要兼容 null。
        contentTemplate: r.contentTemplate || '',
        isDefault: !!r.isDefault,
        sortOrder: r.sortOrder || 0,
        gradient: meta.gradient || 'linear-gradient(135deg, #f3f4f6, #e5e7eb)',
        color: meta.color || '#6b7280',
        icon: meta.icon || List,
        tagType: meta.tagType || 'info',
        requiresShipping: !!meta.requiresShipping
      }
    })
    // 优先选中"默认模板"，其次选第一个
    const def = printTemplates.value.find(t => t.isDefault)
    selectedTemplateId.value = def ? def.id : (printTemplates.value[0]?.id || null)
  } catch (e) {
    // 加载失败：用 emoji 提示 + 静默使用内置示例（不阻塞主流程）
    console.warn('加载打印模板失败，使用本地兜底：', e?.message || e)
    ElMessage.warning('加载打印模板失败，已使用本地默认模板')
    printTemplates.value = Object.entries(TEMPLATE_META).map(([code, m], idx) => ({
      id: idx + 1, code, name: m.name, type: m.type, paper: 'A4', desc: '',
      isDefault: code === 'PICK', sortOrder: (idx + 1) * 10,
      gradient: m.gradient, color: m.color, icon: m.icon, tagType: m.tagType,
      requiresShipping: m.requiresShipping
    }))
    selectedTemplateId.value = printTemplates.value[0]?.id || null
  }
}

// 当前选中的模板
const currentTemplate = computed(() => printTemplates.value.find(t => t.id === selectedTemplateId.value))

// 燕文 SDK 是否启用（控制"快递面单"模板的可用性）
const yanwenEnabled = ref(false)

// 快递面单模板标识：当前选中模板的 code 是否为 SHIPPING_LABEL
// 改用 code 而非 id 比较，因为现在 id 来自数据库自增，已不再固定为 5。
const isShippingLabelMode = computed(() => {
  const t = printTemplates.value.find(x => x.id === selectedTemplateId.value)
  return t ? t.code === 'SHIPPING_LABEL' : false
})

// 快递面单预览面板状态
const showShippingPreview = ref(false)
const shippingLabelLoading = ref(false)
// 改为数组：单条 / 多条场景统一处理
// 单条订单时 length=1，多条时 length=N（用于"批量打单"）
const shippingLabels = ref([])
// 当前查看中面单对应的 orderId（用于定位错误信息）
const shippingLabelError = ref('')

// 当前订单详情（从 query 传入或列表行传入）
const currentOrderDetail = ref(null)

// 标记"已在 loadOrderFromQuery 中主动拉过面单"，避免 watch 重复触发
const isLoadingFromQuery = ref(false)

// 兼容旧模板引用（仍按"第一条面单"渲染打印内容区）
const shippingLabelDataUrl = computed(() => shippingLabels.value[0]?.dataUrl || '')
const shippingLabelContentType = computed(() => shippingLabels.value[0]?.contentType || 'application/pdf')

// 当前预览面板激活的 Tab（批量打单时切换查看哪张面单）
const activeShippingTab = ref('')

// 选择打印模板
function selectTemplate(tpl) {
  selectedTemplateId.value = tpl.id
}

// 模板编辑对话框状态
const templateDialogVisible = ref(false)
const editingTemplate = ref({})
// 控制"预览效果"面板显示
const showTemplatePreview = ref(false)

// P0：打印模板占位符字典 —— 单一真相源，编辑对话框的"插入占位符"下拉 + 帮助文档共用。
// 与渲染时 renderTemplate() 的白名单严格对齐（添加新占位符需同步两边）。
// 字段说明：
//   - token: 占位符字面量（双花括号包裹），与 HTML 模板里出现的一致
//   - desc:  中文说明，运营在 UI 上能看懂
const PLACEHOLDERS = [
  { token: '{{order.orderNo}}',         desc: '订单编号' },
  { token: '{{order.createTime}}',      desc: '下单时间' },
  { token: '{{order.receiverName}}',    desc: '收件人姓名' },
  { token: '{{order.receiverPhone}}',   desc: '收件人电话' },
  { token: '{{order.receiverAddress}}', desc: '完整收货地址' },
  { token: '{{order.payAmount}}',       desc: '实付金额（含币种）' },
  { token: '{{order.freight}}',         desc: '运费' },
  { token: '{{order.remark}}',          desc: '买家备注' },
  { token: '{{order.statusLabel}}',     desc: '订单状态（中文）' },
  { token: '{{order.shippingCarrier}}', desc: '承运商名称' },
  { token: '{{order.trackingNumber}}',  desc: '运单号' },
  { token: '{{template.name}}',         desc: '当前模板名称' },
  { token: '{{time}}',                  desc: '打印时间（YYYY-MM-DD HH:mm:ss）' },
  { token: '{{paper}}',                 desc: '纸张规格（中文）' },
  { token: '{{items}}',                 desc: '商品明细表格（整段 HTML）' }
]

function handleEditTemplate(tpl) {
  editingTemplate.value = { ...tpl }
  // 打开对话框时关闭预览面板，避免上次预览结果残留
  showTemplatePreview.value = false
  templateDialogVisible.value = true
}

// 在编辑器的 textarea 当前光标位置插入占位符。
// 未取到光标位置时（textarea 未聚焦）直接追加到末尾。
function insertPlaceholder(token) {
  const area = document.querySelector('.tpl-editor-textarea textarea')
  const current = editingTemplate.value.contentTemplate || ''
  if (!area) {
    editingTemplate.value.contentTemplate = current + token
    return
  }
  const start = area.selectionStart || 0
  const end = area.selectionEnd || 0
  // 在光标处插入，并保留原有选区文本
  const next = current.slice(0, start) + token + current.slice(end)
  editingTemplate.value.contentTemplate = next
  // 让 el-input 的 v-model 感知到变化后，再把光标定位到插入文本之后
  nextTick(() => {
    try {
      area.focus()
      const pos = start + token.length
      area.setSelectionRange(pos, pos)
    } catch (_) { /* 忽略聚焦异常 */ }
  })
}

// "恢复默认模板"按钮：用当前模板 code 对应的内置兜底 HTML 重置 contentTemplate。
// 这里直接把新版（优化样式）的默认 HTML 写一份在脚本里，让运营在不依赖后端的情况下也能"重置"。
// 反向操作（从 SQL 拉默认）需要新增 /print/templates/{code}/default 接口，超出本期需求。
function resetToDefaultTemplate() {
  const code = editingTemplate.value.code
  const html = DEFAULT_TEMPLATE_HTML[code]
  if (!html) {
    ElMessage.warning('该模板类型无内置默认 HTML（如快递面单走燕文 PDF）')
    return
  }
  editingTemplate.value.contentTemplate = html
  ElMessage.success('已恢复「' + (editingTemplate.value.name || code) + '」默认模板')
}

// ==================== 占位符渲染 ====================
// 渲染单个占位符的"示例值"（用于编辑对话框里的实时预览）。
// 与渲染时 renderTemplate() 共用同一套转义函数，保证预览与最终打印一致。
const renderedPreviewHtml = computed(() => {
  const tpl = editingTemplate.value
  if (!tpl || !tpl.contentTemplate) {
    return '<div class="tpl-preview-empty">（空模板：将使用代码内置默认模板）</div>'
  }
  // 用一份示例订单做"伪渲染"——运营编辑时能马上看到效果
  const sampleOrder = {
    orderNo: 'TEST_US_YANWEN_20260930001',
    createTime: '2026-09-30 14:23:45',
    receiverName: '张三',
    receiverPhone: '+1 415-555-0100',
    receiverAddress: '123 Main St, San Francisco, CA 94105, US',
    payAmount: '¥128.00',
    freight: '¥15.00',
    remark: '请轻拿轻放，谢谢',
    statusLabel: '待发货',
    shippingCarrier: '燕文物流',
    trackingNumber: 'YW2026093000123'
  }
  const sampleItemsHtml = '<table style="width:100%;border-collapse:collapse;font-size:11px;">' +
    '<thead><tr style="background:#f0f0f0;"><th style="border:1px solid #999;padding:4px 6px;">商品SKU</th>' +
    '<th style="border:1px solid #999;padding:4px 6px;">商品名称</th>' +
    '<th style="border:1px solid #999;padding:4px 6px;">规格</th>' +
    '<th style="border:1px solid #999;padding:4px 6px;">数量</th></tr></thead>' +
    '<tbody><tr><td style="border:1px solid #999;padding:4px 6px;font-family:Consolas,monospace;">SKU-A001</td>' +
    '<td style="border:1px solid #999;padding:4px 6px;">示例商品 A</td>' +
    '<td style="border:1px solid #999;padding:4px 6px;">默认规格</td>' +
    '<td style="border:1px solid #999;padding:4px 6px;">×2</td></tr>' +
    '<tr><td style="border:1px solid #999;padding:4px 6px;font-family:Consolas,monospace;">SKU-B002</td>' +
    '<td style="border:1px solid #999;padding:4px 6px;">示例商品 B</td>' +
    '<td style="border:1px solid #999;padding:4px 6px;">大号</td>' +
    '<td style="border:1px solid #999;padding:4px 6px;">×1</td></tr></tbody></table>'
  return renderTemplate(tpl.contentTemplate, {
    order: sampleOrder,
    template: { name: tpl.name || tpl.code || '拣货单' },
    time: '2026-09-30 14:23:45',
    paper: paperSizeLabel.value || 'A4 (210×297mm)',
    items: sampleItemsHtml
  })
})

/**
 * 渲染自定义模板：把所有 {{token}} 替换成实际值。
 * 设计要点：
 *   1) 简单占位符（order.xxx / template.name / time / paper）：HTML 转义后注入，防 XSS。
 *   2) {{items}}：整段替换为商品明细 HTML（不再二次转义，因为明细 HTML 由我们生成，可信）。
 *   3) 白名单之外的占位符：原样保留，让运营看到"未替换"提示，避免静默失败。
 *   4) 同步支持 {{ token }}（带空格）—— \s* 容忍运营手抖多打空格。
 */
function renderTemplate(html, ctx) {
  if (!html) return ''
  let out = html
  // items 整段替换：单独的 regex 处理，避免被字段替换提前匹配到
  out = out.replace(/\{\{\s*items\s*\}\}/g, ctx.items || '')
  // 字段占位符：白名单 key → 值
  // 注意：ctx.order.* / ctx.template.* 是嵌套对象，这里手动展开，避免 eval
  const lookup = (key) => {
    if (key === 'time') return ctx.time || ''
    if (key === 'paper') return ctx.paper || ''
    if (key.startsWith('order.')) {
      const v = ctx.order ? ctx.order[key.slice(6)] : null
      return v == null ? '' : String(v)
    }
    if (key.startsWith('template.')) {
      const v = ctx.template ? ctx.template[key.slice(9)] : null
      return v == null ? '' : String(v)
    }
    return null // 不识别的 key
  }
  out = out.replace(/\{\{\s*([a-zA-Z][\w.]*)\s*\}\}/g, (m, key) => {
    const val = lookup(key)
    if (val === null) return m // 未识别的占位符原样返回，便于运营排查
    return escapeHtml(val)
  })
  return out
}

// HTML 实体转义（防 XSS）：将 & < > " ' 转义。
// 用于把用户数据插入到自定义 HTML 模板里，避免恶意订单数据触发脚本执行。
function escapeHtml(v) {
  return String(v)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

async function handleSaveTemplate() {
  const id = editingTemplate.value.id
  if (!id) return
  try {
    // 调用后端持久化：code/name/paperSize/description/contentTemplate/isDefault/sortOrder
    // 后端 PrintTemplateRequest.code 字段 @NotBlank 校验非空，必须传
    // contentTemplate 始终带上（即使为空串），便于运营主动"清空重置"操作
    await updatePrintTemplate(id, {
      code: editingTemplate.value.code,
      name: editingTemplate.value.name,
      paperSize: editingTemplate.value.paper,
      description: editingTemplate.value.desc,
      contentTemplate: editingTemplate.value.contentTemplate || ''
    })
    // 同步更新本地缓存，避免用户立即重新拉取
    const target = printTemplates.value.find(t => t.id === id)
    if (target) {
      target.name = editingTemplate.value.name
      target.paper = editingTemplate.value.paper
      target.desc = editingTemplate.value.desc
      target.contentTemplate = editingTemplate.value.contentTemplate || ''
    }
    templateDialogVisible.value = false
    ElMessage.success('模板已保存到数据库')
  } catch (e) {
    ElMessage.error('保存模板失败：' + (e?.message || '未知错误'))
  }
}

// 设为默认模板：调用后端，确保全库唯一默认
async function handleSetDefault(tpl) {
  try {
    await setDefaultPrintTemplate(tpl.id)
    // 同步本地缓存
    printTemplates.value.forEach(t => { t.isDefault = t.id === tpl.id })
    ElMessage.success('已将「' + tpl.name + '」设为默认模板')
  } catch (e) {
    ElMessage.error('设置默认模板失败：' + (e?.message || '未知错误'))
  }
}

// ==================== 打印设置（示例数据：本地默认值，保存后写入 localStorage） ====================
const printSettings = reactive({
  paperSize: 'a4',
  copies: 1,
  orientation: 'portrait',
  duplex: false,
  marginY: 5,
  marginX: 5
})

const SETTINGS_STORAGE_KEY = 'orderPrintSettings'

// 保存打印设置（P1：服务端持久化，替代 localStorage）
async function handleSaveSettings() {
  try {
    await savePrintSettings({ ...printSettings })
    // 同时保留 localStorage 备份（服务端不可用时降级）
    try {
      localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify({ ...printSettings }))
    } catch (_) { /* 忽略 localStorage 错误 */ }
    ElMessage.success('打印设置已保存到服务端')
  } catch (error) {
    console.error('保存打印设置失败：', error)
    // 服务端失败时尝试降级到 localStorage
    try {
      localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify({ ...printSettings }))
      ElMessage.warning('服务端保存失败，已保存到本地（浏览器缓存可能被清理时会丢失）')
    } catch (_) {
      ElMessage.error('保存失败：服务端不可用且浏览器本地存储不可用')
    }
  }
}

// 前端纸规格白名单（与后端 AdminOrderOpsServiceImpl.normalizePaperSize 字典一致）
const ALLOWED_PAPER_SIZES = ['a4', 'a5', 'thermal-80', 'thermal-100']

// 读取打印设置：优先服务端，失败时回退 localStorage
async function loadSettings() {
  let loaded = false
  try {
    const res = await getPrintSettings()
    const data = res?.data || res
    if (data && typeof data === 'object') {
      // 二次过滤：只接受白名单内的 paperSize / orientation 等字段
      Object.assign(printSettings, sanitizePrintSettings(data))
      loaded = true
    }
  } catch (error) {
    console.warn('读取服务端打印设置失败，回退到 localStorage：', error?.message || error)
  }
  if (!loaded) {
    // 降级到 localStorage
    try {
      const saved = JSON.parse(localStorage.getItem(SETTINGS_STORAGE_KEY))
      if (saved && typeof saved === 'object') {
        Object.assign(printSettings, sanitizePrintSettings(saved))
      }
    } catch (error) {
      // 本地无历史设置或解析失败时，使用默认值即可
    }
  }
}

// 前端兜底校验：防御历史脏数据 / 旧版本 localStorage 写入的非法字段。
// 后端虽然也有 normalizePaperSize，但前端的 printSheetStyle / PRINT_PAGE_TEMPLATES / el-option
// 都依赖纸规格白名单，提前过滤避免传非法值后切换纸张失效。
function sanitizePrintSettings(raw) {
  const out = { ...raw }
  if (!ALLOWED_PAPER_SIZES.includes(out.paperSize)) {
    out.paperSize = 'a4'
  }
  if (!['portrait', 'landscape'].includes(out.orientation)) {
    out.orientation = 'portrait'
  }
  // 数值字段：兼容数字与数字字符串（如 "5"），非法值回退默认值。
  // 注意：拷贝 loadSettings 链路可能从 localStorage（旧版本）取到字符串数字。
  out.copies = toIntInRange(out.copies, 1, 99, 1)
  out.marginY = toIntInRange(out.marginY, 0, 50, 5)
  out.marginX = toIntInRange(out.marginX, 0, 50, 5)
  // duplex 强转：true/false/0/1/"yes" 等 → boolean
  out.duplex = toBool(out.duplex)
  return out
}

// 把任意值归一为 [min, max] 区间内的整数；非法值返回 defaultVal。
//   接受 Number / 数字字符串；其他类型（含 undefined/null/NaN）一律回退。
function toIntInRange(v, min, max, defaultVal) {
  let n
  if (typeof v === 'number' && !Number.isNaN(v)) {
    n = Math.trunc(v)
  } else if (typeof v === 'string' && v.trim() !== '') {
    const parsed = parseInt(v.trim(), 10)
    n = Number.isNaN(parsed) ? defaultVal : parsed
  } else {
    return defaultVal
  }
  if (n < min) return min
  if (n > max) return max
  return n
}

// 把任意值归一为 boolean。
//   接受 boolean / number（0 false，非 0 true）/ 字符串（"true"/"1"/"yes" → true，其他 → false）。
function toBool(v) {
  if (typeof v === 'boolean') return v
  if (typeof v === 'number') return v !== 0
  if (typeof v === 'string') {
    const t = v.trim().toLowerCase()
    if (t === 'true' || t === '1' || t === 'yes') return true
    if (t === 'false' || t === '0' || t === 'no') return false
  }
  return false
}

// ==================== 待打印订单列表 ====================
// 加载列表（待打印订单）
  async function loadData() {
    try {
      // printType 兜底：filters.printType 清空时用 'order'（后端归一为不过滤）
      const filterPrintType = filters.printType || 'order'
      const res = await getPrintList({ printType: filterPrintType, page: page.value, size: pageSize.value })
      // 注意：后端按 printType 过滤"曾用此类型打印过的订单"。
      //   'order' 兜底值 → 后端忽略过滤，返回全部待打印订单。
      //   PICK / PACK / SHIP / LABEL / SHIPPING_LABEL → 按具体模板 code 过滤。
    // 响应结构：MyBatis-Plus Page 对象 { records: [], total: number, size, current }
    // toArray 兜底从 records / list / data / items 多个常见字段中取数组
    const list = toArray(res)
    // 客户端关键字过滤
    const kw = filters.keyword.toLowerCase()
    let filtered = list
    if (kw) {
      filtered = filtered.filter(d => (d.orderNo || '').toLowerCase().includes(kw))
    }
    if (filters.printStatus) {
      filtered = filtered.filter(d => d.printStatus === filters.printStatus)
    }
    // total 字段：Page 对象有 .total，兜底用过滤后长度
    total.value = res && res.total != null ? res.total : filtered.length
    tableData.value = filtered
  } catch (error) {
    console.error('获取打印数据失败:', error)
    ElMessage.error('获取打印数据失败')
  }
}

function handleSearch() { page.value = 1; loadData() }

function handleReset() { filters.keyword = ''; filters.printStatus = ''; handleSearch() }

// ==================== 打印相关 ====================
// 当前待打印订单（用于打印内容区渲染）
const printingRow = ref(null)
const printTime = ref('')

// P1：自定义模板的"打印时"渲染 —— 把 printingRow + 模板元数据 + 时间 + 纸张塞进
// 运营自定义的 HTML，做占位符替换。
// 与 renderedPreviewHtml 走同一套 renderTemplate()，保证预览和打印一致
const renderedPrintHtml = computed(() => {
  const tpl = currentTemplate.value
  if (!tpl || !tpl.contentTemplate || !printingRow.value) return ''
  return renderTemplate(tpl.contentTemplate, {
    order: printingRow.value,
    template: { name: tpl.name || tpl.code },
    time: printTime.value || formatPrintTime(),
    paper: paperSizeLabel.value || 'A4 (210×297mm)',
    items: buildItemsHtml(printingRow.value)
  })
})

// 把订单商品明细序列化为 HTML 表格，注入到 {{items}} 占位符。
// 与打印内容区原"商品明细"格保持视觉一致：4 列（SKU/名称/规格/数量），细边框。
// 商品SKU：取自后端 OrderItemEntity.skuCode（LEFT JOIN mo_product_sku.sku_code 填充）。
function buildItemsHtml(row) {
  if (!row || !row.items || !row.items.length) {
    const fallback = (row && row.productInfo) ? String(row.productInfo) : '—'
    return '<div class="items-empty">' + escapeHtml(fallback) + '</div>'
  }
  let html = '<table class="items-table" style="width:100%;border-collapse:collapse;font-size:11px;">'
  html += '<thead><tr style="background:#f0f0f0;">'
  html += '<th style="border:1px solid #999;padding:4px 6px;text-align:left;width:22%;">商品SKU</th>'
  html += '<th style="border:1px solid #999;padding:4px 6px;text-align:left;width:38%;">商品名称</th>'
  html += '<th style="border:1px solid #999;padding:4px 6px;text-align:left;width:22%;">规格</th>'
  html += '<th style="border:1px solid #999;padding:4px 6px;text-align:right;width:18%;">数量</th>'
  html += '</tr></thead><tbody>'
  for (const it of row.items) {
    html += '<tr>'
    html += '<td style="border:1px solid #999;padding:4px 6px;font-family:Consolas,monospace;">' + escapeHtml(it.skuCode || '—') + '</td>'
    html += '<td style="border:1px solid #999;padding:4px 6px;">' + escapeHtml(it.productName || '—') + '</td>'
    html += '<td style="border:1px solid #999;padding:4px 6px;">' + escapeHtml(it.skuSpec || '—') + '</td>'
    html += '<td style="border:1px solid #999;padding:4px 6px;text-align:right;">×' + escapeHtml(String(it.quantity || 0)) + '</td>'
    html += '</tr>'
  }
  html += '</tbody></table>'
  return html
}

// 按纸张规格计算打印内容区尺寸（4 档：A4 / A5 / 热敏 80x80 / 热敏 100x150）。
// 这里决定的是"打印内容框"的物理大小，CSS 里也要配套改 @page 与边距。
const printSheetStyle = computed(() => {
  switch (printSettings.paperSize) {
    case 'thermal-80':
      return { width: '80mm', minHeight: '80mm' }
    case 'thermal-100':
      return { width: '100mm', minHeight: '150mm' }
    case 'a5':
      return { width: '148mm', minHeight: '210mm' }
    case 'a4':
    default:
      return { width: '190mm', minHeight: '277mm' }
  }
})

// 打印模板里"纸张"那一格的展示文本。paperSize 是小写枚举值，需要翻译成中文 + 物理尺寸
const paperSizeLabel = computed(() => {
  switch (printSettings.paperSize) {
    case 'thermal-80': return '热敏 80×80mm'
    case 'thermal-100': return '热敏 100×150mm'
    case 'a5': return 'A5 (148×210mm)'
    case 'a4':
    default: return 'A4 (210×297mm)'
  }
})

// 内置默认模板 HTML（新版：参考电商后台最佳实践重设计样式）
// SHIPPING_LABEL 走燕文 PDF 渲染，不提供 HTML 默认值。
// 设计参考：
//   - Attribute 拣货单最佳实践（https://www.getattribute.com/blog/packing-slip-best-practices）
//   - 拼多多 商家发货 SOP（拣货单带复选框 + 行号 + SKU + 加急标签）
//   - Shopify Order Printer（HTML + 占位符语法）
const DEFAULT_TEMPLATE_HTML = {
  // ========== 拣货单 A4：横向分区，拣货员视角，重点是商品核对 + 签字栏 ==========
  PICK: '<div class="moyu-tpl tpl-pick">' +
    '<div class="tpl-brand">' +
      '<div class="tpl-brand-name">MOYUYO 海外仓</div>' +
      '<div class="tpl-brand-contact">拣货单 · 客服：support@moyuyo.com</div>' +
    '</div>' +
    '<div class="tpl-order-id">' +
      '<div class="tpl-order-no"><span class="lbl">订单号</span><span class="val">{{order.orderNo}}</span></div>' +
      '<div class="tpl-order-meta">' +
        '<div><span class="lbl">下单时间</span><span class="val">{{order.createTime}}</span></div>' +
        '<div><span class="lbl">打印时间</span><span class="val">{{time}}</span></div>' +
      '</div>' +
    '</div>' +
    '<div class="tpl-address-row">' +
      '<div class="tpl-addr-block to">' +
        '<div class="tpl-addr-label">收件人</div>' +
        '<div class="tpl-addr-name">{{order.receiverName}}</div>' +
        '<div class="tpl-addr-phone">{{order.receiverPhone}}</div>' +
        '<div class="tpl-addr-text">{{order.receiverAddress}}</div>' +
      '</div>' +
    '</div>' +
    '<h3 class="tpl-section-title">拣货明细</h3>' +
    '<div class="items">{{items}}</div>' +
    '<div class="tpl-footer">' +
      '<div class="tpl-sign">拣货员：__________</div>' +
      '<div class="tpl-sign">复核：__________</div>' +
      '<div class="tpl-paper">{{paper}}</div>' +
    '</div>' +
  '</div>' +
  '<style>' +
    '.tpl-pick{font-family:"Helvetica Neue","Microsoft YaHei",sans-serif;color:#000;font-size:11px;line-height:1.4;}' +
    '.tpl-pick .tpl-brand{display:flex;justify-content:space-between;align-items:center;border-bottom:2px solid #000;padding-bottom:6px;margin-bottom:10px;}' +
    '.tpl-pick .tpl-brand-name{font-size:14px;font-weight:700;letter-spacing:.5px;}' +
    '.tpl-pick .tpl-brand-contact{font-size:10px;color:#444;}' +
    '.tpl-pick .tpl-order-id{display:flex;justify-content:space-between;align-items:flex-end;border-bottom:1px solid #000;padding-bottom:8px;margin-bottom:12px;}' +
    '.tpl-pick .tpl-order-no .lbl{display:block;font-size:10px;color:#666;letter-spacing:1px;}' +
    '.tpl-pick .tpl-order-no .val{display:block;font-size:20px;font-weight:700;font-family:"Courier New",monospace;margin-top:2px;}' +
    '.tpl-pick .tpl-order-meta{display:flex;gap:18px;}' +
    '.tpl-pick .tpl-order-meta>div .lbl{display:block;font-size:9px;color:#666;}' +
    '.tpl-pick .tpl-order-meta>div .val{font-family:"Courier New",monospace;font-size:11px;}' +
    '.tpl-pick .tpl-address-row{display:flex;gap:10px;margin-bottom:10px;}' +
    '.tpl-pick .tpl-addr-block{flex:1;border:1px solid #000;padding:8px 10px;}' +
    '.tpl-pick .tpl-addr-label{font-size:9px;color:#666;letter-spacing:1px;border-bottom:1px solid #ddd;padding-bottom:3px;margin-bottom:4px;}' +
    '.tpl-pick .tpl-addr-name{font-size:14px;font-weight:700;}' +
    '.tpl-pick .tpl-addr-phone{font-size:11px;font-family:"Courier New",monospace;color:#333;margin-top:1px;}' +
    '.tpl-pick .tpl-addr-text{font-size:11px;margin-top:3px;line-height:1.4;}' +
    '.tpl-pick .tpl-section-title{font-size:13px;font-weight:700;margin:10px 0 6px;padding-bottom:3px;border-bottom:1px solid #000;}' +
    '.tpl-pick table{width:100%;border-collapse:collapse;}' +
    '.tpl-pick table th{background:#000;color:#fff;font-size:10px;text-align:left;padding:5px 8px;letter-spacing:.5px;}' +
    '.tpl-pick table td{border-bottom:1px solid #999;padding:6px 8px;font-size:11px;vertical-align:top;}' +
    '.tpl-pick table tbody tr:nth-child(even) td{background:#f7f7f7;}' +
    '.tpl-pick .tpl-footer{display:flex;justify-content:space-between;align-items:center;margin-top:14px;padding-top:8px;border-top:1px solid #000;font-size:10px;color:#444;}' +
    '.tpl-pick .tpl-sign{flex:1;}' +
  '</style>',

  // ========== 打包单 A5：纵向紧凑，打包员视角，重点是核对 + 签字 ==========
  PACK: '<div class="moyu-tpl tpl-pack">' +
    '<div class="tpl-header">' +
      '<div class="tpl-title">打包单</div>' +
      '<div class="tpl-subtitle">Packing Slip · MOYUYO</div>' +
    '</div>' +
    '<div class="tpl-meta-row">' +
      '<div><span class="lbl">订单号</span><strong>{{order.orderNo}}</strong></div>' +
      '<div><span class="lbl">打印</span>{{time}}</div>' +
    '</div>' +
    '<div class="tpl-addr-block">' +
      '<div class="tpl-addr-label">TO</div>' +
      '<div class="tpl-addr-name">{{order.receiverName}}</div>' +
      '<div class="tpl-addr-phone">{{order.receiverPhone}}</div>' +
      '<div class="tpl-addr-text">{{order.receiverAddress}}</div>' +
    '</div>' +
    '<h3 class="tpl-section-title">商品清单（核对无误后打勾）</h3>' +
    '<div class="items">{{items}}</div>' +
    '<div class="tpl-footer">' +
      '<div class="tpl-sign-row"><span>打包员</span><span class="line"></span></div>' +
      '<div class="tpl-sign-row"><span>复核</span><span class="line"></span></div>' +
    '</div>' +
  '</div>' +
  '<style>' +
    '.tpl-pack{font-family:"Helvetica Neue","Microsoft YaHei",sans-serif;color:#000;font-size:11px;line-height:1.4;}' +
    '.tpl-pack .tpl-header{text-align:center;border-bottom:2px solid #000;padding-bottom:6px;margin-bottom:10px;}' +
    '.tpl-pack .tpl-title{font-size:18px;font-weight:700;letter-spacing:2px;}' +
    '.tpl-pack .tpl-subtitle{font-size:9px;color:#666;letter-spacing:1px;margin-top:2px;}' +
    '.tpl-pack .tpl-meta-row{display:flex;justify-content:space-between;font-size:11px;margin-bottom:10px;}' +
    '.tpl-pack .tpl-meta-row .lbl{display:inline-block;width:42px;color:#666;font-size:10px;}' +
    '.tpl-pack .tpl-addr-block{border:1px solid #000;padding:8px 10px;margin-bottom:8px;}' +
    '.tpl-pack .tpl-addr-label{font-size:9px;color:#666;letter-spacing:2px;margin-bottom:3px;}' +
    '.tpl-pack .tpl-addr-name{font-size:15px;font-weight:700;}' +
    '.tpl-pack .tpl-addr-phone{font-size:11px;font-family:"Courier New",monospace;margin-top:1px;}' +
    '.tpl-pack .tpl-addr-text{font-size:11px;margin-top:3px;line-height:1.4;}' +
    '.tpl-pack .tpl-section-title{font-size:12px;font-weight:700;margin:8px 0 4px;}' +
    '.tpl-pack table{width:100%;border-collapse:collapse;}' +
    '.tpl-pack table th{background:#000;color:#fff;font-size:10px;text-align:left;padding:4px 6px;}' +
    '.tpl-pack table td{border-bottom:1px solid #999;padding:5px 6px;font-size:11px;}' +
    '.tpl-pack .tpl-footer{margin-top:12px;border-top:1px dashed #999;padding-top:8px;}' +
    '.tpl-pack .tpl-sign-row{display:flex;align-items:center;gap:8px;font-size:11px;margin-top:6px;}' +
    '.tpl-pack .tpl-sign-row .lbl{width:42px;}' +
    '.tpl-pack .tpl-sign-row .line{flex:1;border-bottom:1px solid #000;height:14px;}' +
  '</style>',

  // ========== 发货单 A4：发件方 + 收件方 双块对称，重点是金额 + 物流条 ==========
  SHIP: '<div class="moyu-tpl tpl-ship">' +
    '<div class="tpl-header">' +
      '<div class="tpl-brand-block">' +
        '<div class="tpl-brand-name">MOYUYO</div>' +
        '<div class="tpl-brand-tag">海外仓直发 · 全球速运</div>' +
      '</div>' +
      '<div class="tpl-doc-title">' +
        '<div class="tpl-title">发货单</div>' +
        '<div class="tpl-subtitle">SHIPPING ORDER</div>' +
      '</div>' +
    '</div>' +
    '<div class="tpl-status-bar">' +
      '<span>订单状态：<strong>{{order.statusLabel}}</strong></span>' +
      '<span>实付金额：<strong>{{order.payAmount}}</strong></span>' +
    '</div>' +
    '<div class="tpl-address-row">' +
      '<div class="tpl-addr-block from">' +
        '<div class="tpl-addr-label">FROM · 发件人</div>' +
        '<div class="tpl-addr-name">MOYUYO 海外仓</div>' +
        '<div class="tpl-addr-text">中国 · 跨境物流集散中心</div>' +
        '<div class="tpl-addr-text" style="margin-top:3px;">客服：support@moyuyo.com</div>' +
      '</div>' +
      '<div class="tpl-addr-arrow">▶</div>' +
      '<div class="tpl-addr-block to">' +
        '<div class="tpl-addr-label">TO · 收件人</div>' +
        '<div class="tpl-addr-name">{{order.receiverName}}</div>' +
        '<div class="tpl-addr-phone">{{order.receiverPhone}}</div>' +
        '<div class="tpl-addr-text">{{order.receiverAddress}}</div>' +
      '</div>' +
    '</div>' +
    '<h3 class="tpl-section-title">商品明细</h3>' +
    '<div class="items">{{items}}</div>' +
    '<div class="tpl-footer">' +
      '<div class="tpl-sign">发货人：__________</div>' +
      '<div class="tpl-sign">承运签字：__________</div>' +
      '<div class="tpl-sign">客户签收：__________</div>' +
    '</div>' +
    '<div class="tpl-bottom-line">{{template.name}} · {{paper}} · 打印：{{time}}</div>' +
  '</div>' +
  '<style>' +
    '.tpl-ship{font-family:"Helvetica Neue","Microsoft YaHei",sans-serif;color:#000;font-size:11px;line-height:1.4;}' +
    '.tpl-ship .tpl-header{display:flex;justify-content:space-between;align-items:center;border-bottom:3px double #000;padding-bottom:8px;margin-bottom:10px;}' +
    '.tpl-ship .tpl-brand-name{font-size:20px;font-weight:900;letter-spacing:3px;}' +
    '.tpl-ship .tpl-brand-tag{font-size:9px;color:#666;letter-spacing:1px;margin-top:2px;}' +
    '.tpl-ship .tpl-doc-title{text-align:right;}' +
    '.tpl-ship .tpl-title{font-size:24px;font-weight:900;letter-spacing:4px;}' +
    '.tpl-ship .tpl-subtitle{font-size:9px;color:#666;letter-spacing:2px;margin-top:2px;}' +
    '.tpl-ship .tpl-status-bar{display:flex;gap:24px;background:#fff7ed;border:1px solid #fed7aa;padding:6px 12px;margin-bottom:10px;font-size:11px;border-radius:2px;}' +
    '.tpl-ship .tpl-status-bar strong{color:#c2410c;font-size:13px;}' +
    '.tpl-ship .tpl-address-row{display:flex;align-items:stretch;gap:8px;margin-bottom:10px;}' +
    '.tpl-ship .tpl-addr-block{flex:1;border:1px solid #000;padding:8px 10px;}' +
    '.tpl-ship .tpl-addr-block.from{background:#f7f7f7;}' +
    '.tpl-ship .tpl-addr-block.to{background:#fff;border-width:2px;}' +
    '.tpl-ship .tpl-addr-arrow{display:flex;align-items:center;font-size:18px;color:#999;font-weight:700;}' +
    '.tpl-ship .tpl-addr-label{font-size:9px;color:#666;letter-spacing:2px;margin-bottom:3px;border-bottom:1px solid #ddd;padding-bottom:2px;}' +
    '.tpl-ship .tpl-addr-name{font-size:15px;font-weight:700;margin-top:2px;}' +
    '.tpl-ship .tpl-addr-phone{font-size:11px;font-family:"Courier New",monospace;margin-top:1px;}' +
    '.tpl-ship .tpl-addr-text{font-size:11px;line-height:1.4;}' +
    '.tpl-ship .tpl-section-title{font-size:13px;font-weight:700;margin:10px 0 6px;padding:3px 8px;background:#000;color:#fff;letter-spacing:1px;}' +
    '.tpl-ship table{width:100%;border-collapse:collapse;}' +
    '.tpl-ship table th{background:#f0f0f0;border:1px solid #999;padding:5px 8px;text-align:left;font-size:10px;font-weight:700;}' +
    '.tpl-ship table td{border:1px solid #999;padding:6px 8px;font-size:11px;}' +
    '.tpl-ship .tpl-footer{display:flex;justify-content:space-between;margin-top:14px;padding-top:8px;border-top:1px dashed #999;font-size:11px;color:#444;}' +
    '.tpl-ship .tpl-sign{flex:1;}' +
    '.tpl-ship .tpl-bottom-line{margin-top:6px;text-align:center;font-size:9px;color:#888;letter-spacing:1px;}' +
  '</style>',

  // ========== 配货标签 100×150 热敏：分拣用，大字收货人 + 条码 + 醒目地址 ==========
  LABEL: '<div class="moyu-tpl tpl-label">' +
    '<div class="tpl-brand-row">' +
      '<div class="tpl-brand-name">MOYUYO</div>' +
      '<div class="tpl-brand-tag">SHIPMENT LABEL</div>' +
    '</div>' +
    '<div class="tpl-to-block">' +
      '<div class="tpl-to-label">SHIP TO</div>' +
      '<div class="tpl-to-name">{{order.receiverName}}</div>' +
      '<div class="tpl-to-phone">{{order.receiverPhone}}</div>' +
    '</div>' +
    '<div class="tpl-addr-block">' +
      '<div class="tpl-addr-text">{{order.receiverAddress}}</div>' +
    '</div>' +
    '<div class="tpl-barcode">' +
      '<div class="tpl-barcode-label">ORDER NO.</div>' +
      '<div class="tpl-barcode-no">{{order.orderNo}}</div>' +
      '<div class="tpl-barcode-stripes">||||||||||||||||||||||||||||||||||||</div>' +
    '</div>' +
    '<div class="tpl-from-row">' +
      '<div class="tpl-from-label">FROM</div>' +
      '<div class="tpl-from-name">MOYUYO 海外仓</div>' +
    '</div>' +
    '<div class="tpl-bottom">{{time}}</div>' +
  '</div>' +
  '<style>' +
    '.tpl-label{font-family:"Helvetica Neue","Microsoft YaHei",sans-serif;color:#000;font-size:11px;line-height:1.3;padding:4mm;}' +
    '.tpl-label .tpl-brand-row{display:flex;justify-content:space-between;align-items:center;border-bottom:1.5px solid #000;padding-bottom:3px;margin-bottom:6px;}' +
    '.tpl-label .tpl-brand-name{font-size:13px;font-weight:900;letter-spacing:2px;}' +
    '.tpl-label .tpl-brand-tag{font-size:8px;color:#666;letter-spacing:1px;font-weight:700;}' +
    '.tpl-label .tpl-to-block{margin-bottom:6px;}' +
    '.tpl-label .tpl-to-label{font-size:9px;color:#666;letter-spacing:2px;font-weight:700;margin-bottom:2px;}' +
    '.tpl-label .tpl-to-name{font-size:24px;font-weight:900;line-height:1.1;letter-spacing:1px;}' +
    '.tpl-label .tpl-to-phone{font-size:13px;font-family:"Courier New",monospace;font-weight:700;margin-top:2px;}' +
    '.tpl-label .tpl-addr-block{border:1.5px solid #000;padding:5px 6px;margin-bottom:8px;min-height:18mm;}' +
    '.tpl-label .tpl-addr-text{font-size:13px;font-weight:600;line-height:1.35;}' +
    '.tpl-label .tpl-barcode{text-align:center;border:1px solid #000;padding:4px;margin-bottom:6px;background:#fff;}' +
    '.tpl-label .tpl-barcode-label{font-size:8px;letter-spacing:2px;color:#666;}' +
    '.tpl-label .tpl-barcode-no{font-family:"Courier New",monospace;font-size:14px;font-weight:700;letter-spacing:1px;margin:1px 0;}' +
    '.tpl-label .tpl-barcode-stripes{font-family:monospace;font-size:14px;letter-spacing:0;color:#000;line-height:1;overflow:hidden;}' +
    '.tpl-label .tpl-from-row{border-top:1px dashed #666;padding-top:3px;display:flex;align-items:center;gap:6px;}' +
    '.tpl-label .tpl-from-label{font-size:8px;color:#666;letter-spacing:1px;font-weight:700;}' +
    '.tpl-label .tpl-from-name{font-size:10px;font-weight:600;}' +
    '.tpl-label .tpl-bottom{text-align:right;font-size:8px;color:#666;font-family:"Courier New",monospace;margin-top:2px;}' +
  '</style>'
}

// 打印订单：先记录打印（保留原 recordPrint API 调用），再调用 window.print() 触发浏览器真实打印
async function handlePrint(row) {
  try {
    const tpl = currentTemplate.value
    // P1-4：进入打印前先把不属于本次的 shippingLabels 修剪掉，
    // 防止上一次打印残留的面单混入本次模板渲染（之前独立打印过 A，再打印 B 时，
    // shippingLabels 里仍有 A，模板 v-for 会把 A 一起打到 B 的输出里）
    pruneShippingLabels(new Set([row.id]))
    // 兼容：如果选中"快递面单"模板，且行有运单号，自动调燕文取号
    if (isShippingLabelMode.value) {
      await fetchShippingLabelForOrder(row)
      // 取号失败不阻断，仍然可走"面单兜底表格"（带运单号 / 错误信息的简化版）
      // 这里不要再说"通用面单模板"，那会让用户去找拣货/打包/发货单的打印单
      if (!shippingLabelDataUrl.value) {
        ElMessage.warning('燕文取号失败，将按面单兜底表格打印（请检查 SDK 配置 / 运单号）')
      }
    }
    // 后端 recordPrint 只读取 orderId/printType/templateName/paperSize，其余字段不提交
    // 注意：paperSize 由 Service.normalizePaperSize() 统一归一化（白名单字典，
    // 不是简单 toUpperCase —— 'thermal-100' 必须保持小写与模板表一致），前端无需处理
    // P0：recordPrint 失败必须阻断 window.print()，否则会出现"打印成功但未记账"的脏数据。
    // 之前的实现是 catch 里只弹错误提示，window.print() 仍会触发，订单被打印但 print_count 未累加。
    // 已知设计取舍：recordPrint 在点击打印按钮时立即记录，而非在 afterprint 里。
    //   浏览器没有"打印任务已发送"的回调，afterprint 也包括"用户在对话框里点了取消"两种情形。
    //   业务上：用户主动点击"打印"即视为打印意图（即便取消，由运营负责），
    //   电商后台普遍采用此策略。如果未来要严格区分"成功打印 vs 取消"，
    //   需要在 afterprint 里拿打印队列的 orderId 二次调用 recordPrint（或单独加 confirmPrint）。
    // 注意：printType 必须使用当前选中模板的 code（PICK/PACK/SHIP/LABEL/SHIPPING_LABEL），
    //   而不是前端 printType（用于 list 查询的过滤参数）。后端 printType 白名单校验
    //   会拒绝非法值并返回 400。
    await recordPrint({
      orderId: row.id,
      printType: tpl && tpl.code ? tpl.code : 'PICK',
      templateName: tpl ? tpl.name : '默认模板',
      paperSize: printSettings.paperSize
    })
    // P1-2：面单模式下，单独累加一次 SHIPPING_LOG 维度日志（之前 fetchShippingLabel 会自动累加，
    // 但预览一次就被记一次打印是错误的；现在改为只统计用户实际点击"打印"的次数）。
    // 即便 fetchShippingLabel 取号失败、recordShippingLabelLog 也失败，也不影响 window.print() 流程。
    // P1：守卫——仅在"取号成功"（无 error）时才记录 SHIPPING_LABEL 日志，
    // 避免燕文取号失败却累加了成功打印次数。
    if (isShippingLabelMode.value) {
      if (isShippingLabelSucceeded(row.id)) {
        try {
          await recordShippingLabelLog(row.id)
        } catch (e) {
          console.warn(`订单 ${row.orderNo} 燕文面单日志累加失败：`, e?.message || e)
        }
      }
    }
    ElMessage.success('打印任务已记录，订单：' + row.orderNo)
    // P1-1：面单模式下取号成功后，自动调用 shipOrder 完成发货闭环。
    // 只在拿到有效运单号时触发（取号失败不发货，由运营走手动流程）。
    // shipOrder 失败不阻断打印 —— 后面 ElMessage 给出提示即可。
    if (isShippingLabelMode.value) {
      const label = findShippingLabel(row.id)
      if (label && label.waybillNumber && !label.error) {
        try {
          await shipOrder(row.id, {
            carrier: label.carrier || 'yanwen',
            trackingNo: label.waybillNumber
          })
          ElMessage.success(`订单 ${row.orderNo} 已自动发货（${label.carrier} ${label.waybillNumber}）`)
        } catch (shipErr) {
          // 区分"已发货"和"其它错误"，给运营更精确的提示
          const msg = shipErr?.message || ''
          if (msg.includes('已发货')) {
            // 已经是已发货状态，无需再次 shipOrder —— 提示一下避免运营误以为漏处理
            console.info(`订单 ${row.orderNo} 已是已发货状态，跳过自动发货:`, msg)
          } else {
            console.warn(`订单 ${row.orderNo} 自动发货失败:`, msg)
            ElMessage.warning(`订单 ${row.orderNo} 已打印但自动发货失败：${msg || '未知原因'}，请手动处理`)
          }
        }
      }
    }
    // 准备打印内容并进入打印模式
    printingRow.value = row
    printTime.value = formatPrintTime()
    // 给 body 加打印类 + 动态注入当前纸张的 @page（详见 applyPrintBodyClasses）。
    // 1) print-mode：通用打印开关（隐藏后台布局、显示打印内容区）
    // 2) print-paper-xxx：4 档纸张规格（A4 / A5 / 热敏 80 / 热敏 100）
    // 3) printing-shipping：兼容旧 CSS（100×150mm），后续可移除
    // 4) <style id="print-page-style">：动态注入当前 paperSize 对应的 @page
    applyPrintBodyClasses()
    await nextTick()
    // 触发浏览器打印对话框：window.print() 是 synchronous but non-blocking，
    // 调用立即返回 JS 控制权，对话框异步弹出。body class / @page 由 afterprint 事件清理。
    window.print()
  } catch (error) {
    console.error('提交打印任务失败:', error)
    // 把后端错误消息透传给用户（避免只看到笼统的"提交失败"）。
    // 例如后端 printType 白名单校验失败会返回 "printType 不合法：xxx"。
    const msg = (error && error.response && error.response.data && error.response.data.message)
      || (error && error.message)
      || '提交打印任务失败'
    ElMessage.error(msg)
  } finally {
    // body class / @page / printingRow 都由 window.afterprint 事件统一清理，
    // 这里只负责刷新列表（让"已打印"状态即时可见）。
    // 注意：故意不清空 shippingLabels / 预览面板 —— recordPrint 失败时
    // 用户仍可点预览面板的"浏览器打印"补救，强行清掉会丢失燕文已返回的面单数据。
    loadData()
    // P1-5：单条打印也清掉 currentBatchOrders。
    // 场景：用户从 OrderList 批量跳转过来（currentBatchOrders 已被填），
    // 但只想先打其中 1 张 —— 后续再点"批量打印"不应被旧的 batch 干扰。
    currentBatchOrders.value = []
  }
}

// 批量打印：依次记录并打印全部待打印订单（示例数据只处理了简化的批量逻辑）
async function handleBatchPrint() {
  // P1-5：优先用 currentBatchOrders（query.ids 进来的批量订单），
  // 避免 OrderPrint 内部待打印列表与跳转目标不一致时丢单/打错单。
  // 未从 query 进来时（如用户直接在 OrderPrint 里点"批量打印"），fallback 到 tableData。
  const list = currentBatchOrders.value.length
    ? [...currentBatchOrders.value]
    : [...tableData.value]
  if (!list.length) {
    ElMessage.warning('暂无可打印的订单')
    return
  }
  try {
    const tpl = currentTemplate.value
    // P1-4：进入批量打印前先把不属于本批次 orderId 的 shippingLabels 修剪掉，
    // 防止历史残留面单混入本次打印输出
    pruneShippingLabels(new Set(list.map(r => r.id)))
    // 面单模式：先把每张面单都拉下来，再一次性进入打印
    // 否则 window.print() 时 shippingLabels 还是空的，会打印出空表格
    // 修复 P0-3：用 Promise.allSettled 并发拉取，避免 N×5s 串行等待
    if (isShippingLabelMode.value) {
      await Promise.allSettled(list.map(row => fetchShippingLabelForOrder(row)))
    }
    // 修复 P0-4/P0-6：用 Promise.allSettled 并发累加 + 每条独立 try/catch，
    // 单条失败不影响其它订单累加
    // 注意：printType 用当前选中模板的 code（与后端白名单对齐：PICK/PACK/SHIP/LABEL/SHIPPING_LABEL）
    const batchPrintType = tpl && tpl.code ? tpl.code : 'PICK'
    await Promise.allSettled(list.map(async (row) => {
      try {
        await recordPrint({ orderId: row.id, printType: batchPrintType, templateName: tpl ? tpl.name : '批量打印', paperSize: printSettings.paperSize })
      } catch (e) {
        console.error(`订单 ${row.orderNo} recordPrint 失败：`, e?.message || e)
        throw e // 继续走 allSettled，但 ElMessage 不弹（避免批量弹一堆）
      }
    }))
    // P1-2：批量面单模式下，逐单累加 SHIPPING_LABEL 维度日志（与 recordPrint 并行）。
    // 注意：必须放在 recordPrint 之后，与 handlePrint 单条逻辑保持一致。
    // P1：守卫——仅在"取号成功"（无 error）时才记录 SHIPPING_LABEL 日志，
    // 避免燕文取号失败却累加了成功打印次数。
    // 累加失败不阻断打印流程，只记录 warn 日志便于排查。
    // 修复 P0-3：并发累加
    if (isShippingLabelMode.value) {
      await Promise.allSettled(list.map(async (row) => {
        if (!isShippingLabelSucceeded(row.id)) return // 取号失败 → 跳过
        try {
          await recordShippingLabelLog(row.id)
        } catch (e) {
          console.warn(`订单 ${row.orderNo} 燕文面单日志累加失败：`, e?.message || e)
        }
      }))
    }
    // P1-1：批量面单模式下，逐单自动 shipOrder 完成发货闭环。
    // 跳过取号失败的单子（label.error 非空），由运营在订单列表里手动发货。
    // P1：改为并发执行，避免 N 条订单串行等待接口响应；
    //   单条失败不影响其它订单（Promise.allSettled）。
    if (isShippingLabelMode.value) {
      const shipResults = await Promise.allSettled(list.map(async (row) => {
        const label = findShippingLabel(row.id)
        if (label && label.waybillNumber && !label.error) {
          try {
            await shipOrder(row.id, {
              carrier: label.carrier || 'yanwen',
              trackingNo: label.waybillNumber
            })
            return { row, ok: true }
          } catch (e) {
            // 区分"已发货"（属正常态）vs"其它错误"（需运营介入）
            const msg = e?.message || ''
            if (msg.includes('已发货')) {
              console.info(`订单 ${row.orderNo} 已是已发货状态，跳过:`, msg)
              return { row, ok: true, skipped: true }
            }
            console.warn(`订单 ${row.orderNo} 自动发货失败:`, msg)
            return { row, ok: false, err: msg }
          }
        }
        return { row, ok: false, err: '取号失败' }
      }))
      const shippedCount = shipResults.filter(r => r.status === 'fulfilled' && r.value && r.value.ok).length
      const failedCount = shipResults.filter(r => r.status === 'rejected' || (r.status === 'fulfilled' && r.value && !r.value.ok)).length
      if (shippedCount > 0) {
        ElMessage.success(`批量打印完成，已自动发货 ${shippedCount} 单${failedCount > 0 ? `，${failedCount} 单需手动发货` : ''}`)
      } else if (failedCount > 0) {
        // P1-1：批量取号全部失败时不能静默，否则运营以为已发货实际没有
        ElMessage.warning(`批量 ${list.length} 单均取号失败，已打印但未自动发货，请到订单列表手动处理`)
      }
    }
    ElMessage.success('已记录 ' + list.length + ' 单打印任务，开始打印')
    printingRow.value = list[0]
    printTime.value = formatPrintTime()
    applyPrintBodyClasses()
    await nextTick()
    window.print()
  } catch (error) {
    console.error('批量提交打印任务失败:', error)
    ElMessage.error('批量提交打印任务失败')
  } finally {
    // body class / @page / printingRow 都由 window.afterprint 事件统一清理，
    // 这里只负责刷新列表（让"已打印"状态即时可见）。
    loadData()
    // P1-5：批量任务结束就清掉 currentBatchOrders，避免影响后续单条打印
    // （下次进入时会按当前 row.id 修剪 shippingLabels，无残留风险）
    currentBatchOrders.value = []
  }
}

// 拉取燕文 SDK 启用状态（页面加载时调用一次）
async function loadYanwenStatus() {
  try {
    const res = await getYanwenStatus()
    const data = res?.data || res
    yanwenEnabled.value = !!(data && data.enabled)
  } catch (e) {
    // 静默失败：状态用于 UI 提示
    yanwenEnabled.value = false
  }
}

// 从 query 加载订单详情（OrderList "打印快递单" 跳转时携带 orderId）
async function loadOrderFromQuery() {
  // 同时支持单条（?orderId=1）和批量（?ids=1,2,3）两种入口
  const singleId = Number(route.query.orderId)
  const idsStr = (route.query.ids || '').toString().trim()
  const batchIds = idsStr
    ? idsStr.split(',').map(s => Number(s.trim())).filter(n => !isNaN(n) && n > 0)
    : []
  const allIds = singleId ? [singleId] : batchIds
  if (!allIds.length) return

  // 标记"已经在加载时主动拉过面单"，避免 watch 监听 selectedTemplateId=快递面单 重复触发
  isLoadingFromQuery.value = true
  // 自动选快递面单模板（按 code 而非 id 查找，因为 id 现在来自数据库自增）
  const shippingTpl = printTemplates.value.find(t => t.code === 'SHIPPING_LABEL')
  if (shippingTpl) {
    selectedTemplateId.value = shippingTpl.id
  }

  // 把当前激活 Tab 切到第一张，方便用户切换
  activeShippingTab.value = String(allIds[0])

  // 单条场景：复用原有逻辑（保存到 currentOrderDetail 便于兼容兜底渲染）
  if (singleId) {
    try {
      const res = await getPrintDetail(singleId)
      const data = res?.data || res
      if (data && data.id) {
        currentOrderDetail.value = data
        await fetchShippingLabelForOrder({
          id: data.id,
          orderNo: data.orderNo,
          receiver: data.receiverName,
          productInfo: (data.items || []).map(it => `${it.productName} x${it.quantity}`).join('; '),
          createTime: data.createTime,
          shippingCarrier: data.shippingCarrier,
          trackingNumber: data.trackingNumber
        })
      }
    } catch (e) {
      console.warn('加载订单打印详情失败：', e?.message || e)
    } finally {
      isLoadingFromQuery.value = false
    }
    return
  }

  // 批量场景：循环并发拉取（Promise.allSettled 防止单条失败影响全部）
  try {
    const results = await Promise.allSettled(
      allIds.map(async (id) => {
        const res = await getPrintDetail(id)
        const data = res?.data || res
        if (!data || !data.id) throw new Error('订单 ' + id + ' 详情为空')
        return {
          id: data.id,
          orderNo: data.orderNo,
          receiver: data.receiverName,
          productInfo: (data.items || []).map(it => `${it.productName} x${it.quantity}`).join('; '),
          createTime: data.createTime,
          shippingCarrier: data.shippingCarrier,
          trackingNumber: data.trackingNumber
        }
      })
    )
    // P1-5：把 query 里成功拉到的订单写入 currentBatchOrders，
    // 作为 handleBatchPrint 的权威数据源（避免与 OrderPrint 内部列表不一致时丢单）
    const fulfilledOrders = results
      .filter(r => r.status === 'fulfilled' && r.value)
      .map(r => r.value)
    currentBatchOrders.value = fulfilledOrders
    // 对拉取成功的订单挨个调燕文 SDK（fetchShippingLabelForOrder 内部已经 try/catch）
    for (const r of results) {
      if (r.status === 'fulfilled' && r.value) {
        await fetchShippingLabelForOrder(r.value)
      }
    }
  } catch (e) {
    console.warn('批量加载订单打印详情失败：', e?.message || e)
  } finally {
    isLoadingFromQuery.value = false
  }
}

// 取订单的快递电子面单（燕文），追加到数组（支持批量）

// 工具：根据 orderId 在 shippingLabels 中查找对应面单（handlePrint/handleBatchPrint 复用）
function findShippingLabel(orderId) {
  return shippingLabels.value.find(s => s.orderId === orderId)
}

// 工具：面单取号是否成功（label 存在且 error 为空）
function isShippingLabelSucceeded(orderId) {
  const label = findShippingLabel(orderId)
  return !!(label && !label.error)
}

async function fetchShippingLabelForOrder(row) {
  if (!row || !row.id) return
  showShippingPreview.value = true
  shippingLabelLoading.value = true
  // 单条/批量场景：根据是否已有"该 orderId 的条目"决定是覆盖还是新增
  const idx = shippingLabels.value.findIndex(s => s.orderId === row.id)
  try {
    const res = await fetchShippingLabel(row.id, undefined)
    const data = res?.data || res
    // P1-2：后端返回 base64String + contentType（不再返回 dataUrl），前端自己拼。
    // 这样响应体小了约 33%，批量 5 张从 ~1.5MB 降到 ~1MB。
    if (data && data.base64String) {
      const contentType = data.contentType || 'application/pdf'
      const item = {
        orderId: row.id,
        orderNo: row.orderNo || row.id,
        dataUrl: 'data:' + contentType + ';base64,' + data.base64String,
        contentType,
        sizeBytes: data.sizeBytes || 0,
        // P1-1：保存燕文返回的运单号，打印成功后用于自动调用 shipOrder 完成发货
        waybillNumber: data.waybillNumber || '',
        // 承运商：当前仅支持燕文；后续接入多家时由后端按 carrierId 返回
        carrier: 'yanwen',
        error: ''
      }
      if (idx >= 0) shippingLabels.value.splice(idx, 1, item)
      else shippingLabels.value.push(item)
      shippingLabelError.value = ''
    } else {
      // 取号失败：无论之前是否已有条目，都要确保 UI 上能看到"这一单失败"
      // 否则批量场景下某些订单静默失败，用户没法定位是哪一单
      const err = (data && data.message) || '燕文未返回面单数据'
      const placeholder = {
        orderId: row.id,
        orderNo: row.orderNo || row.id,
        dataUrl: '',
        contentType: '',
        sizeBytes: 0,
        error: err
      }
      if (idx >= 0) {
        shippingLabels.value.splice(idx, 1, placeholder)
      } else {
        shippingLabels.value.push(placeholder)
      }
      shippingLabelError.value = err
    }
  } catch (e) {
    const err = e?.message || '取面单失败，请确认后端 SDK 配置'
    const placeholder = {
      orderId: row.id,
      orderNo: row.orderNo || row.id,
      dataUrl: '',
      contentType: '',
      sizeBytes: 0,
      error: err
    }
    if (idx >= 0) {
      shippingLabels.value.splice(idx, 1, placeholder)
    } else {
      shippingLabels.value.push(placeholder)
    }
    shippingLabelError.value = err
  } finally {
    shippingLabelLoading.value = false
  }
}

// 关闭面单预览面板
function closeShippingPreview() {
  showShippingPreview.value = false
  shippingLabels.value = []
  shippingLabelError.value = ''
}

// 清掉 shippingLabels 中不属于当前 orderId 集合的条目，避免上一次打印残留
// 混入本次打印（之前独立打印过 A，再批量打印 B 时，A 的面单会被一起打到）。
// 不传参数 = 清空全部（用于批量打印入口或独立打印入口主动清场）。
function pruneShippingLabels(keepOrderIds) {
  if (!keepOrderIds || keepOrderIds.size === 0) {
    shippingLabels.value = []
    shippingLabelError.value = ''
    return
  }
  const kept = shippingLabels.value.filter(s => keepOrderIds.has(s.orderId))
  // 仅当确实清理了条目时重置错误提示，避免错误提示在打印中突然消失
  if (kept.length !== shippingLabels.value.length) {
    shippingLabels.value = kept
    // 若清理后面单条目变空，错误提示也清理（避免误导）
    if (kept.length === 0) shippingLabelError.value = ''
  }
}

// 直接触发浏览器打印（不依赖 printingRow，适合在预览面板中触发）
async function triggerBrowserPrint() {
  // 兜底：如果没有当前行，先用 currentOrderDetail 渲染
  if (!printingRow.value && currentOrderDetail.value) {
    const d = currentOrderDetail.value
    printingRow.value = {
      id: d.id,
      orderNo: d.orderNo,
      // 兼容老字段名 receiver —— 打印模板里的回退路径（printingRow.receiverName || printingRow.receiver）
      receiver: d.receiverName,
      receiverName: d.receiverName,
      receiverPhone: d.receiverPhone,
      receiverAddress: d.receiverAddress,
      remark: d.remark,
      payAmount: d.payAmount,
      freight: null,
      currency: null,
      status: d.status,
      statusLabel: d.statusLabel,
      shippingCarrier: d.shippingCarrier,
      trackingNumber: d.trackingNumber,
      // 关键：把结构化 items 也复制过来 —— 否则打印纸上的"商品明细"表格会回退到拼接字符串
      // skuCode：同步带过来，确保从 query 进入打印预览时也展示商品SKU
      items: (d.items || []).map(it => ({
        productName: it.productName,
        skuSpec: it.skuSpec,
        skuCode: it.skuCode,
        quantity: it.quantity,
        price: it.price
      })),
      productInfo: (d.items || []).map(it => `${it.productName} x${it.quantity}`).join('; '),
      createTime: d.createTime
    }
  }
  if (!printingRow.value) {
    ElMessage.warning('请先选择要打印的订单')
    return
  }
  // P1-3：固定 YYYY-MM-DD HH:mm:ss，规避浏览器/locale 差异
  printTime.value = formatPrintTime()
  applyPrintBodyClasses()
  await nextTick()
  window.print()
  // body class / @page 由 window.afterprint 事件统一清理（详见 onMounted）
}

/**
 * P1-3：把打印时间固定为 YYYY-MM-DD HH:mm:ss。
 * 之前用 new Date().toLocaleString()，不同浏览器的 locale 会输出
 *   "2026/9/29 14:23:45"、"9/29/2026, 2:23:45 PM" 等，
 * 导致打印输出不一致。统一格式化便于审计。
 */
function formatPrintTime() {
  const d = new Date()
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/**
 * P0：金额格式化 —— 兼容 null / 字符串数字 / Number，
 * 统一输出 ¥12.00 形式（与电商后台导出的"实付金额"对齐）。
 */
function formatAmount(v) {
  if (v == null || v === '') return '—'
  const n = typeof v === 'number' ? v : Number(v)
  if (!Number.isFinite(n)) return String(v)
  // 后端返回的 payAmount 已经是元为单位（数据库 DECIMAL(10,2)），直接 toFixed 即可
  return '¥' + n.toFixed(2)
}

/**
 * P1：订单状态 → el-tag type 映射。
 *   - warning：待发货（核心打单场景，主色提示）
 *   - success：已发货（已打印过，可能补打）
 *   - danger：HOLD / 退款中（异常件，不能打）
 *   - info：其它
 */
function orderStatusTagType(status) {
  if (!status) return 'info'
  switch (String(status).toUpperCase()) {
    case 'PENDING_SHIP': return 'warning'
    case 'SHIPPED':
    case 'RECEIVED':
    case 'COMPLETED': return 'success'
    case 'HOLD':
    case 'REFUNDING':
    case 'CANCELLED':
    case 'CANCELED': return 'danger'
    default: return 'info'
  }
}

/**
 * P1：状态 fallback 文本 —— 当后端没返回 statusLabel 时，按 enum 翻译为中文。
 */
function orderStatusFallback(status) {
  if (!status) return '未知'
  switch (String(status).toUpperCase()) {
    case 'PENDING_PAY': return '待付款'
    case 'PAID': return '已支付'
    case 'PENDING_SHIP': return '待发货'
    case 'SHIPPED': return '已发货'
    case 'RECEIVED': return '已收货'
    case 'COMPLETED': return '已完成'
    case 'CANCELLED':
    case 'CANCELED': return '已取消'
    case 'REFUNDING': return '退款中'
    case 'REFUNDED': return '已退款'
    case 'EXCHANGING': return '换货中'
    case 'EXCHANGED': return '已换货'
    case 'HOLD': return '已拦截'
    default: return status
  }
}

/**
 * P1：承运商 code → 中文名。当前主要识别燕文，后续接顺丰/中通时在此扩展。
 */
function shippingCarrierName(code) {
  if (!code) return '未指定'
  const c = String(code).toLowerCase()
  if (c === 'yanwen' || c === '燕文') return '燕文物流'
  if (c === 'sf' || c === 'shunfeng' || c === '顺丰') return '顺丰'
  if (c === 'zt' || c === 'zhongtong' || c === '中通') return '中通'
  if (c === 'yt' || c === 'yuantong' || c === '圆通') return '圆通'
  if (c === 'sto' || c === 'shentong' || c === '申通') return '申通'
  if (c === 'jd' || c === 'jingdong' || c === '京东') return '京东物流'
  return code
}

// 打印 body 类清单（每次打印都会完整重置，避免上一次残留影响本次）
const PRINT_PAPER_CLASSES = ['print-paper-a4', 'print-paper-a5', 'print-paper-thermal-80', 'print-paper-thermal-100']

// 4 档纸张的 @page CSS 模板。@page 不能按 body 类动态切换（CSS 规范不允许 @page 嵌套），
// 所以每张纸准备一份完整模板，运行时按需替换 <style id="print-page-style"> 内容。
const PRINT_PAGE_TEMPLATES = {
  'a4': '@media print { @page { size: A4; margin: 5mm; } }',
  'a5': '@media print { @page { size: A5; margin: 5mm; } }',
  'thermal-80': '@media print { @page { size: 80mm 80mm; margin: 0; } }',
  'thermal-100': '@media print { @page { size: 100mm 150mm; margin: 0; } }'
}

// 进入打印：根据当前 printSettings.paperSize 切换 @page，加 body 类隐藏后台布局。
// 注意：这里只负责"打印模式标记"，**不清 printingRow** —— printingRow 由调用方
// (handlePrint / handleBatchPrint / triggerBrowserPrint) 在调 apply 之前赋值，
// apply 内部不能再清，否则会把刚刚准备好的打印内容清空。
function applyPrintBodyClasses() {
  document.body.classList.add('print-mode')
  document.body.classList.add('print-paper-' + printSettings.paperSize)
  if (isShippingLabelMode.value) document.body.classList.add('printing-shipping')
  // 动态注入当前纸张对应的 @page（替换 #print-page-style 的内容）
  ensurePrintPageStyle(printSettings.paperSize)
}

// 退出打印：清掉所有打印相关 body 类、移除 @page、清掉打印内容源 printingRow，
// 恢复页面正常显示。注意：Chrome / Firefox / Edge / Safari 13.1+ 都支持 afterprint 事件，
// 这里通过顶层 addEventListener 让浏览器在打印对话框关闭（无论打印还是取消）时
// 自动调用此函数。这样能避免 finally 在打印快照生成前清掉状态导致预览不完整。
//
// printingRow 必须在打印快照生成后再清 —— 否则浏览器拍快照时打印区已经隐藏，
// 预览里就是空白。
function clearPrintBodyClasses() {
  document.body.classList.remove('print-mode')
  document.body.classList.remove('printing-shipping')
  for (const cls of PRINT_PAPER_CLASSES) document.body.classList.remove(cls)
  const styleEl = document.getElementById('print-page-style')
  if (styleEl) styleEl.textContent = ''
  // printingRow 是打印内容区的 v-if 条件，必须等到打印快照生成后再清，
  // 否则浏览器拍快照时打印区已经隐藏，预览就是空白。
  printingRow.value = null
}

// 把当前 paperSize 对应的 @page 写入 #print-page-style，幂等
function ensurePrintPageStyle(paperSize) {
  const tpl = PRINT_PAGE_TEMPLATES[paperSize] || PRINT_PAGE_TEMPLATES['a4']
  let styleEl = document.getElementById('print-page-style')
  if (!styleEl) {
    styleEl = document.createElement('style')
    styleEl.id = 'print-page-style'
    document.head.appendChild(styleEl)
  }
  if (styleEl.textContent !== tpl) styleEl.textContent = tpl
}

// 当切换到"快递面单"模板时，若已有当前订单则自动取号
// 跳过 silent 期间跳过 isLoadingFromQuery=true 的触发（避免 loadOrderFromQuery 已拉过的二次取）
watch(selectedTemplateId, (val) => {
  const tpl = printTemplates.value.find(t => t.id === val)
  if (tpl
      && tpl.code === 'SHIPPING_LABEL'
      && !isLoadingFromQuery.value
      && currentOrderDetail.value
      && !shippingLabelDataUrl.value
      && !shippingLabelLoading.value) {
    fetchShippingLabelForOrder({
      id: currentOrderDetail.value.id,
      orderNo: currentOrderDetail.value.orderNo,
      shippingCarrier: currentOrderDetail.value.shippingCarrier,
      trackingNumber: currentOrderDetail.value.trackingNumber
    })
  }
})

// 注册 afterprint 事件，让浏览器在打印对话框关闭（无论打印还是取消）时
// 自动清理 body class + 注入的 @page。Chrome / Firefox / Edge / Safari 13.1+ 都支持。
// 这样打印快照一定在 print-mode 类下生成，不会出现"对话框弹出后头部被清掉"的问题。
window.addEventListener('afterprint', clearPrintBodyClasses)

onMounted(async () => {
  loadSettings()
  loadData()
  loadYanwenStatus()
  // 关键：先加载打印模板，等模板列表就绪后再处理 query 跳转，
  // 否则 loadOrderFromQuery 里"按 code 找快递面单模板"会因 printTemplates 为空而选不中
  await loadPrintTemplates()
  // 如果路由 query 带 orderId，从 OrderList 跳过来则自动加载订单详情
  loadOrderFromQuery()
})

// 页面销毁时移除 afterprint 监听，避免组件卸载后还触发回调
onUnmounted(() => {
  window.removeEventListener('afterprint', clearPrintBodyClasses)
})
</script>

<style scoped>
.page-wrapper { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; font-weight: 700; color: var(--text-800); margin: 0; }
.section-card { margin-bottom: 16px; }
.section-header { display: flex; align-items: center; justify-content: space-between; }
.section-title { font-size: 14px; font-weight: 600; color: var(--text-800); }
.section-tip { font-size: 12px; color: var(--text-400); }

/* ===== 打印模板卡片 ===== */
.template-grid { display: flex; gap: 16px; flex-wrap: wrap; }
.template-card {
  width: 200px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 14px;
  cursor: pointer;
  background: var(--card);
  transition: border-color .18s ease, box-shadow .18s ease;
}
.template-card:hover { border-color: var(--brand-300); box-shadow: var(--shadow-md); }
.template-card.selected { border-color: var(--primary); box-shadow: 0 0 0 2px var(--primary); }
.template-thumb {
  position: relative;
  width: 100%;
  height: 80px;
  border-radius: calc(var(--radius) - 4px);
  margin-bottom: 12px;
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
}
.badge {
  position: absolute;
  top: 8px;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
}
.default-badge { left: 8px; background: var(--brand-50); color: var(--brand-700); }
.selected-badge { right: 8px; background: var(--primary); color: var(--primary-foreground); }
.unavailable-badge {
  left: 8px;
  top: 32px;
  background: rgba(255, 59, 48, 0.1);
  color: var(--state-error);
  border: 1px solid rgba(255, 59, 48, 0.3);
}
.template-name { font-size: 14px; font-weight: 600; color: var(--text-800); margin-bottom: 6px; }
.template-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.template-paper { font-size: 12px; color: var(--text-400); }
.template-desc { font-size: 12px; color: var(--text-400); line-height: 1.4; margin-bottom: 8px; min-height: 34px; }
.template-actions { display: flex; gap: 4px; }

/* ===== 打印设置表单 ===== */
.settings-form { padding-top: 4px; }
.unit-text { margin-left: 6px; font-size: 12px; color: var(--text-400); }

/* ===== P0：商品信息 popover ===== */
.product-cell {
  cursor: pointer;
  color: var(--primary);
  text-decoration: underline dotted;
  text-underline-offset: 2px;
}
.product-popover-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-800);
  margin-bottom: 6px;
}
.product-popover-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.product-popover-table th,
.product-popover-table td {
  border-bottom: 1px solid var(--border);
  padding: 6px 8px;
  text-align: left;
}
.product-popover-table th {
  background: var(--background-100);
  font-weight: 600;
  color: var(--text-600);
}
.product-popover-table tbody tr:last-child td { border-bottom: none; }
.settings-footer { display: flex; justify-content: flex-end; margin-top: 4px; padding-top: 14px; border-top: 1px solid var(--border); }

/* ===== 打印内容区：屏幕隐藏，仅打印时显示（Teleport 至 body） ===== */
.print-area { display: none; }
/* P1：自定义模板容器 —— 强制字体颜色为黑（防止后台布局变量串入） */
.print-custom { color: #000; font-family: var(--font-sans, sans-serif); }
/* 自定义模板里的 <style> 由内联方式注入；这里补容器兜底 */
.print-custom :deep(table) { border-collapse: collapse; }
.print-custom :deep(h1),
.print-custom :deep(h2) { margin: 8px 0; }
.print-sheet { background: #fff; padding: 20px; font-family: var(--font-sans); color: var(--text-800); }
.print-header { display: flex; align-items: center; justify-content: space-between; border-bottom: 2px solid var(--text-800); padding-bottom: 12px; margin-bottom: 16px; }
.print-header h2 { font-size: 18px; font-weight: 700; margin: 0; }
.print-time { font-size: 12px; color: var(--text-400); }
.print-table { width: 100%; border-collapse: collapse; font-size: 13px; }

/* ===== P0：打印纸头部 meta 行（状态 / 实付金额 / 运费）===== */
.print-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 8px 12px;
  margin-bottom: 12px;
  background: var(--background-100);
  border-radius: 4px;
  font-size: 12px;
}
.print-meta-item { display: inline-flex; align-items: center; gap: 4px; }
.print-status {
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 600;
  font-size: 11px;
}
.print-status--warning { background: #fff7ed; color: #c2410c; border: 1px solid #ffedd5; }
.print-status--success { background: #ecfdf5; color: #16a34a; border: 1px solid #d1fae5; }
.print-status--danger  { background: #fef2f2; color: #dc2626; border: 1px solid #fee2e2; }
.print-status--info    { background: var(--background-100); color: var(--text-600); border: 1px solid var(--border); }

/* ===== P0：打印纸"商品明细"嵌套表格 ===== */
.print-items {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.print-items th,
.print-items td {
  border-bottom: 1px solid var(--border);
  padding: 4px 6px;
  text-align: left;
}
.print-items th {
  background: var(--background-100);
  font-weight: 600;
  color: var(--text-600);
}
.print-items tbody tr:last-child td { border-bottom: none; }

/* ===== P0：买家备注醒目行 ===== */
.print-remark {
  color: #c2410c;
  font-weight: 600;
  background: #fff7ed;
  border-left: 3px solid #c2410c;
  padding-left: 8px !important;
}
.print-table th, .print-table td { border: 1px solid var(--border); padding: 8px 12px; text-align: left; }
.print-table th { background: var(--background-100); font-weight: 600; width: 110px; }
.print-footer { margin-top: 24px; text-align: center; font-size: 12px; color: var(--text-400); }

@media print {
  .print-area { display: block !important; }
}

/* ===== 快递面单预览 ===== */
.shipping-preview { border-color: var(--brand-200); }
.shipping-preview-body {
  min-height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--background-100);
  border-radius: var(--radius);
  overflow: hidden;
}
.shipping-preview-iframe {
  width: 100%;
  height: 540px;
  border: none;
  background: #fff;
}
.shipping-preview-image {
  max-width: 100%;
  max-height: 540px;
  background: #fff;
  padding: 12px;
  box-sizing: border-box;
}
.shipping-loading {
  padding: 60px 20px;
  font-size: 13px;
  color: var(--text-500);
}
.shipping-tabs {
  width: 100%;
}
.shipping-tabs :deep(.el-tabs__header) { margin-bottom: 8px; }
.shipping-tabs :deep(.el-tabs__item) { font-size: 12px; padding: 0 12px; }
.shipping-tab-error { color: var(--state-error); font-weight: 600; }
.shipping-pane { padding: 4px; }
.shipping-error-banner {
  padding: 6px 12px;
  margin-bottom: 8px;
  background: var(--state-error-surface);
  color: var(--state-error);
  border-radius: 6px;
  font-size: 12px;
}
.print-batch-group {
  /* 批量打单：每个订单独立分组，按订单分页（page-break-after） */
  page-break-after: always;
}
.print-batch-group:last-child { page-break-after: auto; }
.shipping-empty {
  padding: 60px 20px;
  font-size: 14px;
  color: var(--text-500);
  text-align: center;
}
.shipping-error {
  font-size: 12px;
  color: var(--state-error);
  background: var(--state-error-surface);
  padding: 2px 10px;
  border-radius: 999px;
}

/* ===== 打印区（Teleport 到 body）===== */
.print-area--shipping { display: none; }
/* 实际尺寸由父组件的 printSheetStyle 通过 :style 注入（按当前 paperSize）。
   这里只保留 padding / 背景 / 分页等"与纸张无关"的样式。 */
.print-sheet--shipping {
  padding: 0;
  margin: 0 auto;
  background: #fff;
  box-sizing: border-box;
  page-break-after: always;
}
.shipping-iframe {
  width: 100%;
  height: 100%;
  border: none;
  display: block;
}
.shipping-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}
.shipping-fallback {
  padding: 8mm;
  font-family: var(--font-sans);
  color: var(--text-800);
  height: 100%;
  box-sizing: border-box;
}
.shipping-fallback-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 8mm;
  border-bottom: 2px solid var(--text-800);
  padding-bottom: 4mm;
}

/* ===== P1：模板编辑对话框 —— 工具栏 / 编辑区 / 预览区 ===== */
.tpl-editor-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.tpl-editor-toolbar .tpl-editor-tip {
  font-size: 12px;
  color: var(--text-500);
  margin-right: auto;
}
.tpl-editor-textarea :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace !important;
  font-size: 12px !important;
  line-height: 1.55 !important;
  background: var(--background-50, #fafafa);
}
.tpl-editor-help :deep(.el-collapse-item__header) {
  font-size: 12px;
  color: var(--text-600);
}
.tpl-editor-help .help-section {
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-700);
  padding: 4px 4px 8px;
}
.tpl-editor-help .help-section ul {
  margin: 6px 0;
  padding-left: 20px;
}
.tpl-editor-help .help-section li {
  margin-bottom: 3px;
}
.tpl-editor-help .help-section code {
  background: var(--background-100, #f3f4f6);
  padding: 1px 5px;
  border-radius: 3px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  color: #c2410c;
}
.ph-token {
  display: inline-block;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--brand-700, #1d4ed8);
  font-size: 12px;
  min-width: 200px;
}
.ph-desc {
  color: var(--text-500);
  font-size: 11px;
  margin-left: 8px;
}
.tpl-preview-wrapper {
  margin-top: 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  overflow: hidden;
}
.tpl-preview-title {
  background: var(--background-100, #f3f4f6);
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-700);
  border-bottom: 1px solid var(--border);
}
.tpl-preview-frame {
  padding: 16px 20px;
  max-height: 480px;
  overflow-y: auto;
  background: #fff;
  color: #000;
}
.tpl-preview-empty {
  padding: 40px;
  text-align: center;
  color: var(--text-400);
  font-size: 13px;
}

@media print {
  .print-area { display: block !important; }
}
</style>

<style>
/* 打印模式（非 scoped）：隐藏后台整体布局，仅保留 Teleport 到 body 的打印内容区。
   4 档纸张的 @page 定义见 JS 中的 PRINT_PAGE_TEMPLATES，
   打印入口动态写入 <style id="print-page-style">（@page 不能按 body 类切换，CSS 规范禁止嵌套）。 */
@media print {
  /* 通用：所有打印态隐藏后台布局 */
  body.print-mode .admin-layout,
  body.print-mode.printing-shipping .admin-layout { display: none !important; }
  body.print-mode #app,
  body.print-mode.printing-shipping #app { display: none !important; }
}
</style>
