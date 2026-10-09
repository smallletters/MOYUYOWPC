<template>
  <view class="order-list">
    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.value"
        class="tab"
        :class="{ active: activeTab === t.value }"
        @click="onTabChange(t.value)"
      >
        {{ t.label }}
      </view>
    </view>

    <scroll-view
      scroll-y
      class="scroll"
      @scrolltolower="onLoadMore"
      @click="closeAllSwipe">
      <view v-for="o in orders" :key="o.id" class="swipe-wrapper">
        <!-- 搴曞眰鎿嶄綔鎸夐挳锛堣鍗＄墖瑕嗙洊锛涘乏婊戝悗闇插嚭锛?-->
        <view class="swipe-actions">
          <view v-if="canDelete(o)" class="swipe-btn btn-delete" @click.stop="handleDelete(o)">
            {{ $t('orderList.swipe.delete') }}
          </view>
          <view v-if="canCancel(o)" class="swipe-btn btn-cancel" @click.stop="handleCancel(o)">
            {{ $t('orderList.swipe.cancel') }}
          </view>
        </view>

        <!-- 涓婂眰鍗＄墖 -->
        <view
          class="card order-card"
          :class="{ 'is-swiping': isSwiping }"
          :style="{ transform: `translateX(${o.swipeOffset || 0}px)` }"
          @touchstart="onTouchStart($event, o)"
          @touchmove="onTouchMove($event, o)"
          @touchend="onTouchEnd($event, o)"
          @touchcancel="onTouchEnd($event, o)"
          @click="onCardClick(o, $event)"
        >
          <view class="card-header">
            <text class="order-no">#{{ o.orderNo }}</text>
            <text class="order-status" :class="`status-${o.status}`">
              {{ statusText(o.status) }}
            </text>
          </view>
          <view class="order-items">
            <view v-for="item in (o.items || []).slice(0, 2)" :key="item.id" class="item-row">
              <image :src="item.mainImage || ''" lazy-load class="item-image" />
              <view class="item-info">
                <text class="item-name text-ellipsis-2">{{ item.productName }}</text>
                <text class="item-qty">x {{ item.quantity }}</text>
              </view>
            </view>
            <view v-if="(o.items || []).length > 2" class="more">
              {{ $t('orderList.moreItems', { count: o.items.length - 2 }) }}
            </view>
          </view>
          <view class="card-footer">
            <text class="total">
              {{ $t('orderList.totalLabel', { amount: `${currencySymbol}${o.payAmount}` }) }}
            </text>
            <view class="btn btn-primary action-btn" @click.stop="onAction(o)">
              {{ o.status === 'RECEIVED' ? receivedActionText(o) : actionText(o.status) }}
            </view>
          </view>
        </view>
      </view>

      <view v-if="loading" class="loading">{{ $t('common.loading') }}</view>
      <view v-else-if="noMore && orders.length === 0" class="empty">
        {{ $t('orderList.empty') }}
      </view>
      <view v-else-if="noMore" class="loading">{{ $t('orderList.noMore') }}</view>
    </scroll-view>
  </view>
</template>

<script>
import { orderApi, reviewApi } from '@/api'
import { i18n, t } from '@/i18n'
import { getPendingOrders, removePendingOrder } from '@/utils/storage'

export default {
  pageTitleKey: 'pageTitle.orderList',

  data() {
    return {
      activeTab: 'all',
      orders: [],
      loading: false,
      noMore: false,
      page: 1,
      localeVersion: 0,

      // --- 宸︽粦鍒犻櫎鐘舵€?---
      // 琚Е鎽哥殑閭ｄ竴寮犺鍗曠殑寮曠敤锛堢敤浜?touchmove 涓疄鏃舵洿鏂?offset锛?      touchingOrder: null,
      // 瑙︽懜璧峰 X 鍧愭爣锛坧x锛?      touchStartX: 0,
      touchStartY: 0,
      // 瑙︽懜寮€濮嬫椂鏄惁宸插睍寮€锛堝睍寮€浜嗗氨璁颁负 btnWidth锛屾病灞曞紑灏辫涓?0锛?      touchStartOffset: 0,
      // 褰撳墠鏄惁澶勪簬"姝ｅ湪婊戝姩"鐘舵€侊紙鐢ㄤ簬缁欏崱鐗囧姞 is-swiping class锛屽幓鎺?transition锛?      isSwiping: false,
      // 鍒氱粨鏉熸粦鍔ㄧ殑閭ｄ竴鍒伙紝闃绘 click 鍐掓场锛坱ouch 缁撴潫鍚?50ms 鍐咃級
      justTouchedAt: 0,
      // 婊戝姩鎸夐挳瀹藉害锛坧x锛夛紝H5 鐢?rpx 鎹㈢畻锛?rpx 鈮?0.5px @750px 璁捐绋?      btnWidthPx: 60,

      // 宸叉彁浜よ繃璇勪环鐨勮鍗?id 闆嗗悎锛堣鍗曚富鐘舵€佷粛涓?RECEIVED锛?      // 鍥犱负 admin 瀹℃牳閫氳繃鍚庢墠浼氭帹杩涘埌 COMPLETED锛涗负浜嗛伩鍏嶇敤鎴峰湪
      // "寰呰瘎浠? tab 閲嶅鐐瑰嚮宸叉彁浜ょ殑璁㈠崟璺冲埌璇勪环椤?杩欓噷缁存姢涓€涓紦瀛橈級
      reviewedOrderIds: new Set(),

      // 棣栨杩涘叆(onLoad 宸茬粡鎷夎繃璇勪环闆嗗悎),鐢ㄤ簬閬垮厤 onShow 棣栨閲嶅璇锋眰
      hasLoadedReviewedIds: false,
    }
  },

  computed: {
    tabs() {
      void this.localeVersion
      // 鐘舵€佸€煎榻愬悗绔?OrderStatusEnum:
      // - PENDING_PAY 鈫?寰呮敮浠?      // - PAID,PENDING_SHIP 鈫?鏀粯鎴愬姛鍚庛€佸彂璐у墠锛坅dmin 鍙戣揣鍓嶇姸鎬佸彲鑳芥槸 PAID 鎴?PENDING_SHIP锛?      // - SHIPPED 鈫?宸插彂璐с€佸緟鏀惰揣
      // - RECEIVED 鈫?宸叉敹璐э紙寰呰瘎浠凤級
      // - COMPLETED 鈫?宸插畬鎴愶紙宸茶瘎浠凤級
      // 6 涓?tab,璇箟瀵归綈鐢ㄦ埛瑙嗚:
      // - RECEIVED (寰呰瘎浠? 涓?COMPLETED (宸插畬鎴? 鎷嗗垎,閬垮厤鍚屼竴 tab 鍐呮寜閽竴浼?鍘昏瘎浠?涓€浼?鏌ョ湅"
      // - 寰呭彂璐?PAID/PENDING_SHIP 浠嶅悎骞?鍚庣 listOrders 宸叉敮鎸侀€楀彿鍒嗛殧澶氱姸鎬?in 鏌ヨ
      return [
        { value: 'all', label: i18n.t('orderList.statusTabs.all') },
        { value: 'PENDING_PAY', label: i18n.t('orderList.statusTabs.pendingPay') },
        { value: 'PAID,PENDING_SHIP', label: i18n.t('orderList.statusTabs.pendingShip') },
        { value: 'SHIPPED', label: i18n.t('orderList.statusTabs.pendingReceive') },
        { value: 'RECEIVED', label: i18n.t('orderList.statusTabs.toReview') },
        { value: 'COMPLETED', label: i18n.t('orderList.statusTabs.completed') },
      ]
    },
    currencySymbol() {
      void this.localeVersion
      return i18n.currencySymbol
    },
  },

  onLoad(query) {
    if (query.type && query.type !== 'all') {
      // 鍏滃簳:濡傛灉浼犲叆鐨?type 涓嶅湪 tabs 鍒楄〃閲?鍏稿瀷渚嬪瓙:鍘嗗彶鍊?PENDING_RECEIVE / COMPLETED,
      // 涓庡悗绔灇涓惧拰 tab value 宸蹭笉瀵归綈),闈欓粯鍥為€€鍒?'all',
      // 閬垮厤 activeTab 钀藉埌涓€涓?tabs 鏁扮粍閲屾病鏈夌殑 value,瀵艰嚧鎵€鏈?tab 閮芥病鐐逛寒銆侀〉闈㈢湅涓婂幓绌虹櫧銆?      // 闈欓粯鍥為€€鑰屼笉鏄?toast:鏃?URL 鍙兘鏄敤鎴锋敹钘?鍒嗕韩,寮圭獥浣撻獙宸€?      const known = this.tabs.some((t) => t.value === query.type)
      if (known) {
        this.activeTab = query.type
      } else {
        this.activeTab = 'all'
      }
    }
    this.loadOrders(true)
    this._unsubLocale = i18n.subscribe(() => {
      this.localeVersion += 1
    })
  },

  onUnload() {
    if (this._unsubLocale) this._unsubLocale()
  },

  /**
   * 鐢ㄦ埛鍦ㄨ瘎浠烽〉鎻愪氦鍚?navigateBack 杩斿洖鍒楄〃,uni-app 涓嶄細鍐嶈Е鍙?onLoad,
   * 鍙Е鍙?onShow銆傝繖閲屽彧閲嶆媺"鎴戝凡鎻愪氦鐨勮瘎浠?闆嗗悎,閬垮厤鍐嶆鎷夎鍗曞垎椤点€?   */
  onShow() {
    // 浠呭綋鍒楄〃娓叉煋杩?orders 涓嶄负绌?鎵嶆湁鎰忎箟;棣栨杩涘叆浼氱敱 loadOrders 瑙﹀彂
    // hasLoadedReviewedIds 閬垮厤 onLoad + 绱ч殢鍏跺悗鐨?onShow 閲嶅鎷変竴娆?    if (this.orders.length > 0 && this.hasLoadedReviewedIds) {
      this.loadReviewedOrderIds()
    }
  },

  methods: {
    async loadOrders(reset = false) {
      if (reset) {
        this.page = 1
        this.noMore = false
        this.orders = []
      }
      this.loading = true
      try {
        const params = { page: this.page, size: 10 }
        if (this.activeTab !== 'all') params.status = this.activeTab
        // request.js 宸茶В鍖呭灞?envelope,result 鍗?IPage { records, total, size, current, ... }
        const result = await orderApi.getOrderList(params)
        let list = Array.isArray(result?.records)
          ? result.records
          : Array.isArray(result)
            ? result
            : []

        // 鍚堝苟鏈湴鏈敮浠樿鍗?all 涓?寰呬粯娆?tab 涓?鎶婁笅鍗曟湭鏀粯/鏀粯澶辫触鏆傚瓨鏈湴鐨勮褰曞苟鍏ュ垪琛?鎸?id 鍘婚噸
        if (this.activeTab === 'all' || this.activeTab === 'PENDING_PAY') {
          const pending = getPendingOrders()
          if (pending.length > 0) {
            const seen = new Set(list.map((o) => o.id))
            const merged = [...pending.filter((o) => !seen.has(o.id)), ...list]
            list = merged.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
          }
        }

        list = Array.isArray(list) ? list : []
        // 姣忔潯鍔?swipeOffset: 0锛堝乏婊戝睍寮€鏍囪锛?        const normalized = list.map((o) => ({ ...o, swipeOffset: 0 }))
        this.orders.push(...normalized)
        // 浼樺厛浣跨敤鍚庣 total;鑰佹帴鍙ｈ繑鍥炴暟缁勬椂,鏍规嵁褰撳墠娆℃媺鍙栨暟閲忓垽瀹?        const total = Number(result?.total)
        if (Number.isFinite(total) && total >= 0) {
          this.noMore = this.orders.length >= total
        } else {
          this.noMore = list.length < 10
        }
        if (list.length > 0) this.page += 1
        // 鎷夊畬棣栧睆鍚?寮傛琛ヤ竴娆?鎴戝凡鎻愪氦杩囪瘎浠风殑璁㈠崟 id"闆嗗悎,鐢ㄤ簬鍦?RECEIVED tab 鎶?        // 宸叉彁浜ょ殑璇勪环鎸夐挳鏂囨鎹㈡垚"宸茶瘎浠仿峰緟瀹℃牳"骞舵敼璺宠鎯?閬垮厤閲嶅杩涜瘎浠烽〉銆?        if (reset && this.orders.length > 0) {
          this.loadReviewedOrderIds()
        }
      } catch (e) {
        console.error('[order-list] error', e)
      } finally {
        this.loading = false
      }
    },

    /**
     * 鎷夊彇褰撳墠鐢ㄦ埛鍏ㄩ儴璇勪环,鏋勫缓宸茶瘎浠疯鍗?id 闆嗗悎銆?     * - 鍚庣鍒嗛〉 size=100 澶熻鐩栨櫘閫氱敤鎴烽噺绾?鑻ヨ秴杩?100,缈婚〉鐩村埌鎷垮叏銆?     * - 浠绘剰涓€椤靛け璐ラ兘涓嶅奖鍝嶅垪琛ㄦ覆鏌?闃插尽鎬?try/catch)銆?     */
    async loadReviewedOrderIds() {
      this.hasLoadedReviewedIds = true
      try {
        const set = new Set()
        let page = 1
        const size = 100
        // 闃插尽缈婚〉寰幆涓婇檺(閬垮厤鍚庣 total 寮傚父瀵艰嚧姝诲惊鐜?
        for (let i = 0; i < 20; i++) {
          const res = await reviewApi.getMyReviews({ page, size })
          const records = Array.isArray(res?.records) ? res.records : []
          for (const r of records) {
            if (r && r.orderId != null) set.add(r.orderId)
          }
          // MyBatis-Plus IPage 缈婚〉缁堟鍒ゆ柇:
          //   - records.length < size: 鏈〉娌″～婊?宸叉槸鏈€鍚庝竴椤?          //   - records.length === size: 鏈〉濉弧,鍙兘杩樻湁涓嬩竴椤?          // 娉ㄦ剰:set.size 鏄幓閲嶅悗鐨勮鍗曟暟,< 瀹為檯璇勪环鏉℃暟,
          // 涓嶈兘鐢?set.size >= total 鍒ゆ柇鏄惁鎷夊畬(鍚屽崟澶?item 璇勪环浼氶噸澶?銆?          if (records.length < size) break
          page += 1
        }
        this.reviewedOrderIds = set
      } catch (e) {
        // 闈欓粯澶辫触:涓嶅奖鍝嶅垪琛ㄥ睍绀?鍙槸鎸夐挳鏂囨璧伴粯璁?璇勪环鏅掑崟"
        console.warn('[order-list] load reviewed orderIds failed', e)
      }
    },

    /** 鍒ゆ柇鏌愯鍗曟槸鍚﹀凡缁忔彁浜よ繃璇勪环(PENDING/APPROVED/REJECTED 閮界畻"宸叉彁浜?) */
    isReviewed(order) {
      return this.reviewedOrderIds.has(order.id)
    },

    onTabChange(value) {
      this.activeTab = value
      this.loadOrders(true)
    },

    onLoadMore() {
      if (this.loading || this.noMore) return
      this.loadOrders(false)
    },

    statusText(status) {
      void this.localeVersion
      // 鐘舵€佹枃妗堢粺涓€浠庡瓧鍏歌:key 涓庡悗绔姸鎬佺爜瀵归綈
      const key = `orderStatus.${status}`
      const v = i18n.t(key)
      // i18n.t 鍦?key 缂哄け鏃惰繑鍥炲師 key,杩欓噷鍥為€€鍒?status 鏈韩
      return v === key ? status : v
    },

    actionText(status) {
      void this.localeVersion
      // 瀵归綈鍚庣 OrderStatusEnum 鐪熷疄鍊?      const map = {
        PENDING_PAY: i18n.t('orderDetail.pay'),
        PAID: i18n.t('orderList.actionWaitShip'), // 宸叉敮浠樼瓑寰呭彂璐?        PENDING_SHIP: i18n.t('orderList.actionWaitShip'), // 寰呭彂璐?        SHIPPED: i18n.t('orderList.actionConfirmReceive'), // 宸插彂璐?鈫?纭鏀惰揣
        RECEIVED: i18n.t('orderList.actionReview'), // 宸叉敹璐?鈫?鍘昏瘎浠?榛樿;瀹為檯娓叉煋浼氭寜鏄惁宸叉彁浜よ繃璇勪环鍐嶈鐩?
        COMPLETED: i18n.t('orderList.actionReviewed'), // 宸插畬鎴?鈫?鏌ョ湅璇勪环(閬垮厤鍜?RECEIVED 鍚屾枃妗?
      }
      return map[status] || i18n.t('orderList.actionView')
    },

    /**
     * RECEIVED 璁㈠崟涓撶敤鎸夐挳鏂囨:
     * - 宸叉彁浜よ繃璇勪环 鈫?"宸茶瘎浠仿峰緟瀹℃牳"锛堥伩鍏嶇粰鐢ㄦ埛"鏄笉鏄病鎻愪氦鎴愬姛"鐨勯敊瑙夛級
     * - 鏈彁浜よ繃璇勪环 鈫?"璇勪环鏅掑崟"
     */
    receivedActionText(order) {
      if (this.isReviewed(order)) return i18n.t('orderList.actionReviewSubmitted')
      return i18n.t('orderList.actionReview')
    },

    onAction(order) {
      const s = order.status
      if (s === 'PENDING_PAY') {
        uni.navigateTo({
          url: `/pages/order/pay?orderId=${order.id}&amount=${order.payAmount}`,
        })
      } else if (s === 'PAID' || s === 'PENDING_SHIP') {
        // 宸叉敮浠?寰呭彂璐?鈫?鏆傛棤 APP 渚ф搷浣滄寜閽紙绛?admin 鍙戣揣锛?        this.goDetail(order.id)
      } else if (s === 'SHIPPED') {
        // 宸插彂璐?鈫?鏀寔"鏌ョ湅鐗╂祦"鍜?纭鏀惰揣"
        uni.showActionSheet({
          itemList: [t('orderList.sheet.track'), t('orderList.sheet.confirmReceive')],
          success: (res) => {
            if (res.tapIndex === 0) {
              uni.navigateTo({ url: `/pages/order/logistics?id=${order.id}` })
            } else if (res.tapIndex === 1) {
              this.doConfirmReceive(order)
            }
          },
          fail: () => uni.navigateTo({ url: `/pages/order/logistics?id=${order.id}` }),
        })
      } else if (s === 'RECEIVED') {
        // 宸叉敹璐?鈫?鍘昏瘎浠烽〉;鑻ュ凡鎻愪氦杩囪瘎浠?璁㈠崟鐘舵€佷粛鏄?RECEIVED 鐩村埌瀹℃牳閫氳繃),
        // 鏀逛负璺宠鎯呴〉,閬垮厤閲嶅杩涘叆璇勪环椤垫彁浜ゃ€?        if (this.isReviewed(order)) {
          this.goDetail(order.id)
        } else {
          uni.navigateTo({ url: `/pages/order/review?orderId=${order.id}` })
        }
      } else if (s === 'COMPLETED') {
        // 宸插畬鎴愯鍗?璺宠鍗曡鎯呴〉(璇︽儏椤甸噷鏈夋煡鐪嬭瘎浠?鐢宠鍞悗绛夊叆鍙?,淇濇寔鍘熻涓?        uni.navigateTo({ url: `/pages/order/detail?id=${order.id}` })
      } else {
        this.goDetail(order.id)
      }
    },

    /** 纭鏀惰揣锛圫HIPPED 鈫?RECEIVED/COMPLETED锛?*/
    async doConfirmReceive(order) {
      const confirmed = await new Promise((resolve) => {
        uni.showModal({
          title: t('orderList.modal.confirmReceiveTitle'),
          content: t('orderList.modal.confirmReceiveContent', { orderNo: order.orderNo }),
          success: (res) => resolve(res.confirm),
          fail: () => resolve(false),
        })
      })
      if (!confirmed) return
      try {
        await orderApi.confirmReceived(order.id)
        // 浠?SHIPPED tab 绉诲埌宸叉敹璐?tab
        order.status = 'RECEIVED'
        order.swipeOffset = 0
        uni.showToast({ title: t('orderList.toast.receiveSuccess'), icon: 'success' })
      } catch (e) {
        uni.showToast({
          title: e?.data?.msg || e?.message || t('orderList.toast.operationFailed'),
          icon: 'none',
        })
      }
    },

    goDetail(id) {
      uni.navigateTo({ url: `/pages/order/detail?id=${id}` })
    },

    // ========== 宸︽粦鍒犻櫎鐩稿叧 ==========

    /** 鍒ゆ柇鏌愯鍗曟槸鍚﹀瓨鍦ㄥ彲婊戝嚭鐨勬搷浣滄寜閽紙鑷冲皯涓€涓級 */
    hasSwipeAction(o) {
      return this.canDelete(o) || this.canCancel(o)
    },
    /** 寰呬粯娆?/ 寰呭彂璐?/ 宸插彇娑?鐨勮鍗曞彲鐗╃悊鍒犻櫎 */
    canDelete(o) {
      return ['PENDING_PAY', 'PENDING_SHIP', 'CANCELLED'].includes(o.status)
    },
    /** 浠呭緟浠樻璁㈠崟鍙洿鎺ュ彇娑堬紱宸蹭粯娆炬湭鍙戣揣璁㈠崟闇€璧伴€€娆炬祦绋嬶紝閬垮厤涓庡悗绔櫧鍚嶅崟涓嶄竴鑷?*/
    canCancel(o) {
      return ['PENDING_PAY'].includes(o.status)
    },

    /** 鏍规嵁鎸夐挳鏁伴噺璁＄畻灞曞紑鏃跺簲婊戝姩鍒扮殑 px 鍊?*/
    computeBtnPx(o) {
      let count = 0
      if (this.canDelete(o)) count++
      if (this.canCancel(o)) count++
      // 鎸夐挳瀹界敱 CSS 鎺у埗锛?swipe-btn { width: 120rpx }
      // 鐢?uni.upx2px 鎸夊綋鍓嶇獥鍙ｅ搴︽崲绠?淇濊瘉涓?CSS 鐨?rpx 鎹㈢畻瑙勫垯涓€鑷?
      // 鍐欐 60px 鍙湪 375px 瑙嗗彛涓嬫垚绔?鏇村鐨勮鍙ｄ細闇插嚭涓嶅叏
      return count * uni.upx2px(120)
    },

    /** 瑙︽懜寮€濮嬶細璁板綍璧峰鍧愭爣 & 璧峰 offset */
    onTouchStart(e, order) {
      if (!this.hasSwipeAction(order)) return
      this.touchingOrder = order
      const t = e.touches[0] || e.changedTouches[0]
      this.touchStartX = t.clientX
      this.touchStartY = t.clientY
      this.touchStartOffset = order.swipeOffset || 0
    },

    /** 瑙︽懜绉诲姩锛氬疄鏃舵洿鏂?translateX锛堝姞 clamp 闃茶秺鐣岋級 */
    onTouchMove(e, order) {
      if (!this.touchingOrder) return
      const t = e.touches[0] || e.changedTouches[0]
      const dx = t.clientX - this.touchStartX
      const dy = t.clientY - this.touchStartY
      // 姘村钩浣嶇Щ蹇呴』澶т簬鍨傜洿浣嶇Щ鐨?1.5 鍊嶆墠绠?婊戝姩鍗＄墖"锛屽惁鍒欒鐖舵粴鍔ㄨ鍥炬帴绠?      if (!this.isSwiping && Math.abs(dx) > 10 && Math.abs(dx) > Math.abs(dy) * 1.5) {
        this.isSwiping = true
      }
      if (!this.isSwiping) return

      const full = this.computeBtnPx(order)
      let next = this.touchStartOffset + dx
      // 鍙屽悜 clamp锛氭渶灏?-full锛屾渶澶?0锛堢姝㈠悜鍙抽湶鍑?绌虹櫧"锛?      next = Math.max(-full, Math.min(0, next))
      // 娉ㄦ剰锛氳繖閲?order 鏄垪琛ㄤ腑鐨勫紩鐢紝鏀?swipeOffset 浼氱洿鎺ヨЕ鍙戞ā鏉?transform 鏇存柊
      this.touchingOrder.swipeOffset = next
    },

    /** 瑙︽懜缁撴潫锛氭牴鎹渶缁堜綅缃喅瀹?寮瑰洖鍘?杩樻槸"灞曞紑" */
    onTouchEnd(e, order) {
      if (!this.touchingOrder) return
      this.justTouchedAt = Date.now()
      const full = this.computeBtnPx(order)
      const current = this.touchingOrder.swipeOffset || 0
      // 瓒呰繃涓€鍗婂氨灞曞紑鍒板簳锛屽惁鍒欏脊鍥?      let target = 0
      if (current < -full / 2) target = -full
      else target = 0
      this.touchingOrder.swipeOffset = target
      this.touchingOrder = null
      this.isSwiping = false
    },

    /** 鐐瑰嚮鍗＄墖锛氳嫢鍒氭粦鍔ㄨ繃锛?0ms 鍐咃級锛屾嫤鎴烦杞?*/
    onCardClick(order, _e) {
      if (Date.now() - this.justTouchedAt < 50) return
      this.goDetail(order.id)
    },

    /** 鍏抽棴鎵€鏈夊凡灞曞紑鐨?swipe锛坰croll 鎴栫偣鍑诲叾浠栧尯鍩熸椂璋冪敤锛?*/
    closeAllSwipe() {
      let changed = false
      for (const o of this.orders) {
        if (o.swipeOffset !== 0) {
          o.swipeOffset = 0
          changed = true
        }
      }
      if (changed) {
        // 寮哄埗璁╂ā鏉块噸娓叉煋 transform
        this.orders = [...this.orders]
      }
    },

    /** 鐐瑰嚮銆屽垹闄ゃ€嶆寜閽細鏈湴鏈敮浠樿鍗?鈫?removePendingOrder锛涘叾浠栬蛋鍚庣 delete */
    async handleDelete(order) {
      const confirmed = await new Promise((resolve) => {
        uni.showModal({
          title: t('orderList.modal.deleteTitle'),
          content: t('orderList.modal.deleteContent', { orderNo: order.orderNo }),
          success: (res) => resolve(res.confirm),
          fail: () => resolve(false),
        })
      })
      if (!confirmed) {
        this.closeAllSwipe()
        return
      }
      try {
        // 鏈湴鏈敮浠樿鍗曪細鐩存帴浠?storage 绉婚櫎锛屼笉鐢ㄨ皟鍚庣锛堝彲鑳借繕娌℃寔涔呭寲锛?        const isLocal = order.id && String(order.id).startsWith('local-')
        if (isLocal) {
          removePendingOrder(order.id)
        } else {
          await orderApi.deleteOrder(order.id)
        }
        // 浠庡垪琛ㄤ腑绉婚櫎
        this.orders = this.orders.filter((o) => o.id !== order.id)
        uni.showToast({ title: t('orderList.toast.deleted'), icon: 'success' })
      } catch (e) {
        console.error('[order-list] delete fail', e)
        uni.showToast({
          title: e?.data?.msg || e?.message || t('orderList.toast.deleteFailed'),
          icon: 'none',
        })
        this.closeAllSwipe()
      }
    },

    /** 鐐瑰嚮銆屽彇娑堣鍗曘€嶆寜閽?*/
    async handleCancel(order) {
      const confirmed = await new Promise((resolve) => {
        uni.showModal({
          title: t('orderList.modal.cancelTitle'),
          content: t('orderList.modal.cancelContent', { orderNo: order.orderNo }),
          success: (res) => resolve(res.confirm),
          fail: () => resolve(false),
        })
      })
      if (!confirmed) {
        this.closeAllSwipe()
        return
      }
      try {
        await orderApi.cancelOrder(order.id, t('orderList.cancelReason'))
        // 鍙栨秷鎴愬姛鍚庯細濡傛灉褰撳墠 tab 涓嶅惈 CANCELLED锛屾妸璇ヨ鍗曚粠褰撳墠鍒楄〃闅愯棌
        // 绛?tab 涓嶆槸 ALL / CANCELLED 鏃堕殣钘?        if (this.activeTab !== 'all' && this.activeTab !== 'CANCELLED') {
          this.orders = this.orders.filter((o) => o.id !== order.id)
        } else {
          // 鍒锋柊璇ヨ鍗曟樉绀虹姸鎬?          order.status = 'CANCELLED'
          order.swipeOffset = 0
        }
        uni.showToast({ title: t('orderList.toast.cancelled'), icon: 'success' })
      } catch (e) {
        console.error('[order-list] cancel fail', e)
        uni.showToast({
          title: e?.data?.msg || e?.message || t('orderList.toast.cancelFailed'),
          icon: 'none',
        })
        this.closeAllSwipe()
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.order-list {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.tabs {
  display: flex;
  background: var(--color-surface);
  border-bottom: 1rpx solid var(--color-divider);
}

.tab {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  font-size: 26rpx;
  color: var(--color-text-secondary);
  position: relative;

  &.active {
    color: var(--color-primary);
    font-weight: 600;

    &::after {
      content: '';
      position: absolute;
      bottom: 0;
      left: 50%;
      transform: translateX(-50%);
      width: 60rpx;
      height: 4rpx;
      background: var(--color-primary);
      border-radius: 2rpx;
    }
  }
}

.scroll {
  flex: 1;
  padding: 20rpx;
  // uni-app H5 涓?uni-scroll-view 榛樿涓?content-box,
  // 鑻ヤ笉鍔?border-box,20rpx 鐨勫乏鍙冲唴杈硅窛浼氭拺鍑鸿鍙ｅ鑷村崱鐗囧彸渚ц瑁佸垏
  box-sizing: border-box;
}

.order-card {
  margin-bottom: 0; // 鐢卞灞?swipe-wrapper 鎺у埗
  position: relative;
  z-index: 2;
  background: var(--color-surface);
  border-radius: 16rpx;
  transition: transform 0.22s cubic-bezier(0.25, 0.46, 0.45, 0.94);
  will-change: transform;
  // 姝ｅ湪琚墜鎸囨嫋鍔ㄦ椂,鍘绘帀 transition 淇濊瘉璺熸墜鎰?  &.is-swiping {
    transition: none;
  }
}

/* ---------- 宸︽粦鍒犻櫎缁撴瀯 ---------- */
.swipe-wrapper {
  position: relative;
  overflow: hidden;
  margin-bottom: 20rpx;
  border-radius: 16rpx;
}

.swipe-actions {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: stretch;
  z-index: 1; // 姣斿崱鐗囦綆,琚伄鎸?}

.swipe-btn {
  width: 120rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 26rpx;
  font-weight: 500;
  letter-spacing: 2rpx;
  cursor: pointer;
  user-select: none;

  &.btn-delete {
    background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  }
  &.btn-cancel {
    background: linear-gradient(135deg, #a8a29e 0%, #78716c 100%);
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.order-no {
  font-size: 26rpx;
  color: var(--color-text-secondary);
}

.order-status {
  font-size: 26rpx;
  font-weight: 600;

  &.status-PENDING_PAY {
    color: var(--color-warning);
  }
  &.status-PENDING_SHIP {
    color: var(--color-info);
  }
  &.status-PENDING_RECEIVE {
    color: var(--color-primary);
  }
  &.status-COMPLETED {
    color: var(--color-success);
  }
  &.status-CANCELLED {
    color: var(--color-text-tertiary);
  }
}

.order-items {
  .item-row {
    display: flex;
    align-items: center;
    margin-bottom: 16rpx;
  }

  .item-image {
    width: 120rpx;
    height: 120rpx;
    border-radius: 12rpx;
    margin-right: 16rpx;
    background: var(--color-background);
  }

  .item-info {
    flex: 1;
  }

  .item-name {
    font-size: 26rpx;
    display: block;
  }

  .item-qty {
    font-size: 24rpx;
    color: var(--color-text-tertiary);
  }
}

.more {
  text-align: center;
  font-size: 24rpx;
  color: var(--color-text-tertiary);
  padding: 8rpx;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid var(--color-divider);
}

.total {
  font-size: 28rpx;
  font-weight: 600;
}

.action-btn {
  padding: 12rpx 32rpx;
  font-size: 24rpx;
}

.loading {
  text-align: center;
  padding: 40rpx;
  color: var(--color-text-tertiary);
  font-size: 26rpx;
}

.empty {
  text-align: center;
  padding: 100rpx 40rpx;
  color: var(--color-text-tertiary);
  font-size: 28rpx;
}
</style>

