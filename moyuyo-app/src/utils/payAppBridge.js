/**
 * APP（iOS/Android 原生打包）端专属：支付 WebView 控制桥 + 原生支付通道。
 *
 * uni-app 默认的 <web-view> 在 iOS 的 WKWebView / Android WebView 上对
 *   alipays:// / intent:// / paypal:// / cashme:// / itms-apps://
 * 这类自定义 scheme URL 会直接失败不跳转（系统 WebView 默认拦截非 http(s)），
 * 导致用户选了 Alipay / PayPal / Apple Pay / Cash App 时没任何反应。
 *
 * 本桥提供两条通道：
 *   A) WebView 通道：APP 端用 plus.webview 自建子 WebView，并通过 overrideUrlLoading
 *      拦截支付相关自定义 scheme → 用 plus.runtime.openURL 真正跳转到对方 APP。
 *      同时监听 moyuyo://pay/return?status=success&orderNo=xxx 回跳 URL：
 *      第三方 APP 付款后通过自定义 scheme 回到你的 Moyuyo APP，
 *      这里通过回调告知 pay.vue，pay.vue 随即关闭子 WebView 并显示订单状态。
 *
 *   B) 原生通道（优先尝试，失败自动回落 A）：
 *      - PayPal / Venmo：调用 uni.requestPayment({ provider: 'paypal', orderInfo })
 *        使用 uni-app 官方封装的 PayPal Android/iOS SDK，避免 WKWebView 跳转失败。
 *      - Apple Pay（iOS）：通过 Stripe SDK 原生插件或 PassKit 调起系统支付面板。
 *      - 任何原生通道不可用时 → 自动走 A) WebView 通道，不影响用户体验。
 *
 * H5 / 小程序端直接用 uni <web-view> 和中转页 return.html，不加载此模块。
 */

/**
 * 解析 URL 的 scheme 部分（冒号之前，全小写，不含 ://）。
 * 对 ?query / #fragment 友好，URL 非法时返回空串。
 */
function getScheme(url) {
  if (!url || typeof url !== 'string') return ''
  try {
    const i = url.indexOf(':')
    if (i <= 0) return ''
    return url.slice(0, i).toLowerCase()
  } catch (e) {
    return ''
  }
}

/**
 * 判断一个 URL 属于"要跳外部 APP 的自定义 scheme"。
 * 命中后一律调用 plus.runtime.openURL。
 * 注意：iOS 上 itms-apps://、itms-services://、applewebdata:// 也会进入这里；
 * applewebdata:// / blob: / about: 等直接放行由 WebView 内部处理。
 */
function isExternalAppScheme(url) {
  const scheme = getScheme(url)
  if (!scheme) return false
  // 已知要跳外 APP 的 scheme（持续收集扩展）
    // 美国市场方案：只保留实际支付链路需要的 scheme
    // - paypal / paypalme / paypalpay: PayPal 官方 SDK
    // - googlepay / gpay: Google Pay APP
    // - intent: Android intent:// URL（Stripe Checkout 跳 G Pay 用）
    // - applepay / com-apple-payment-pass: iOS Apple Pay PassKit scheme
    // - itms-*: App Store（用户未安装对应 APP 时可能跳到 App Store 提示下载）
    const open = [
      // PayPal 官方 SDK
      'paypal',
      'paypalme',
      'paypalpay',
      // Google Pay
      'googlepay',
      'gpay',
      // Android intent:// URLs
      'intent',
      // iOS Apple Pay
      'applepay',
      'com-apple-payment-pass',
      // App Store 兜底
      'itms-apps',
      'itms-services',
      'itms',
    ]
  if (open.indexOf(scheme) !== -1) return true
  // Android intent:// URLs
  if (url.indexOf('intent://') === 0) return true
  return false
}

/**
 * 这是 Moyuyo 自身注册的 scheme 回跳：
 * 第三方 APP / Stripe Checkout 支付完成后
 *   重定向到 moyuyo://pay/return?status=xxx&orderNo=yyy
 * 本方法命中时返回 { isReturn: true, status, orderNo }。
 */
function parseMoyuyoReturn(url) {
  if (!url || typeof url !== 'string') return null
  const scheme = getScheme(url)
  if (scheme !== 'moyuyo') return null
  const qIdx = url.indexOf('?')
  const query = qIdx === -1 ? '' : url.slice(qIdx + 1)
  const params = {}
  query.split('&').forEach(function (kv) {
    if (!kv) return
    const idx = kv.indexOf('=')
    const k = idx === -1 ? kv : decodeURIComponent(kv.slice(0, idx))
    const v = idx === -1 ? '' : decodeURIComponent(kv.slice(idx + 1))
    params[k] = v
  })
  return {
    isReturn: true,
    status: params.status || '',
    orderNo: params.orderNo || '',
    raw: url,
  }
}

/**
 * 在 APP 页面内创建一个子 WebView（全屏 + 覆盖页面内容）。
 * 调用方拿到后，通过 webview.loadURL 加载 Stripe/PayPal Checkout URL。
 *
 * @param {Object} opts
 * @param {Object} opts.page   当前页面 Vue 实例（用于绑定 plusReady 回调）
 * @param {Function} opts.onReturn   (returnInfo) => void，命中 moyuyo://pay/return 时调用
 * @param {Function} [opts.onMessage]  (event) => void，web-view @message 等效事件（H5 中转页 postMessage 可用）
 */
export function createPaymentWebView(opts) {
  const onReturn = opts.onReturn || function () {}
  const onMessage = opts.onMessage || function () {}
  let webview = null
  let pendingUrl = ''

  function attachOverride(wv) {
    if (!wv || typeof wv.overrideUrlLoading !== 'function') return
    wv.overrideUrlLoading(
      { mode: 'reject', match: '.*' },
      function (event) {
        const url = event.url
        if (!url) return

        // 调试日志：方便在真机排查 Stripe Checkout 触发的 scheme 类型
        console.log('[payAppBridge] intercept url:', url)

        // 1) 命中 Moyuyo 自定义 scheme 回跳 → 告诉调用方（关 WebView + 处理结果）
        const ret = parseMoyuyoReturn(url)
        if (ret) {
          console.log('[payAppBridge] moyuyo return, status=', ret.status, 'orderNo=', ret.orderNo)
          onReturn(ret)
          return
        }

        // 2) 支付自定义 scheme → 跳对方 APP
        if (isExternalAppScheme(url)) {
          console.log('[payAppBridge] external scheme, openURL:', url)
          openExternalApp(url)
          return
        }

        // 3) http(s) 正常继续加载
        try {
          wv.loadURL(url)
        } catch (e) {
          // ignore
        }
      },
      function (err) {
        console.error('[payAppBridge] overrideUrlLoading error:', err)
      },
    )
  }

  function openExternalApp(url) {
    if (typeof plus === 'undefined' || !plus.runtime) {
      // 开发时非 APP 环境不做处理，打印即可
      console.warn('[payAppBridge] skip openURL (非 APP 环境):', url)
      return
    }
    plus.runtime.openURL(url, function (err) {
      console.warn('[payAppBridge] openURL 失败，用户可能未安装对应 APP:', url, err)
      // 兜底：如果是 Android intent://，把 fallback URL 抽出来再加载回 WebView
      const fallback = parseIntentFallback(url)
      if (fallback && webview) webview.loadURL(fallback)
    })
  }

  function parseIntentFallback(intentUrl) {
    if (!intentUrl) return ''
    try {
      const match =
        intentUrl.match(/;S\.browser_fallback_url=([^;]+)/) ||
        intentUrl.match(/S\.browser_fallback_url%3D([^&;]+)/)
      if (match && match[1]) return decodeURIComponent(match[1])
    } catch (e) {
      /* ignore */
    }
    return ''
  }

  function ensureReady() {
    return new Promise(function (resolve) {
      const _init = function () {
        if (webview) return resolve(webview)
        const currentWebview =
          typeof plus !== 'undefined' && plus.webview && plus.webview.currentWebview
            ? plus.webview.currentWebview()
            : null
        const styles = {
          plusrequire: 'ahead',
          cache: { clear: false },
          statusbar: { background: '#FFFFFF' },
          // iOS 侧允许：applepay://、alipays:// 这类 scheme 跳转（overrideUrlLoading 再精细控制）
          allowsInlineMediaPlayback: true,
          allowFileAccessFromFileURLs: true,
          allowUniversalAccessFromFileURLs: true,
        }
        const current =
          typeof plus !== 'undefined' && plus.webview
            ? plus.webview.create('', 'pay-' + Date.now(), styles, { softinputNavBar: 'auto' })
            : null
        if (!current) {
          webview = null
          return resolve(null)
        }
        current.setStyle({
          top: '0px',
          bottom: '0px',
          left: '0px',
          right: '0px',
          width: '100%',
          height: '100%',
          position: 'absolute',
          zindex: 9999,
        })
        attachOverride(current)
        // H5 中转页 window.parent.postMessage 可通过 urlChange 事件 + evalJS 近似监听，
        // 这里也监听 URL 变化命中 return.html?status=xxx 就发一条模拟消息
        current.addEventListener(
          'urlChange',
          function (e) {
            const m = parseMoyuyoReturn(e.url)
            if (m) onReturn(m)
            else onMessage({ detail: { data: [{ type: 'urlChange', url: e.url }] } })
          },
          false,
        )
        // 加载中转页/支付页完成钩子
        current.addEventListener(
          'loaded',
          function () {
            // 把 localStorage 的 moyuyo_pay_result 抽出来（如果 H5 中转页写了）
            try {
              current.evalJS(
                "(function(){try{var r=localStorage.getItem('moyuyo_pay_result');if(r){return r;}}catch(e){}return '';})()",
                function (raw) {
                  if (raw && typeof raw === 'string' && raw.trim()) {
                    try {
                      var d = JSON.parse(raw)
                      if (d && d.type === 'pay_result') onMessage({ detail: { data: [d] } })
                    } catch (e) {
                      /* 解析失败忽略 */
                    }
                  }
                },
              )
            } catch (e) {
              /* evalJS 失败忽略 */
            }
          },
          false,
        )
        if (currentWebview && typeof currentWebview.append === 'function') {
          currentWebview.append(current)
        }
        webview = current
        resolve(webview)
      }
      if (typeof plus !== 'undefined') {
        _init()
      } else if (typeof document !== 'undefined') {
        document.addEventListener('plusready', _init, false)
      } else {
        // 非 APP 环境，resolve(null)，上层回落到 uni <web-view>
        resolve(null)
      }
    })
  }

  return {
    /**
     * 加载支付页 URL（Stripe session.url / PayPal approvalUrl 等）。
     * 如果 plus 还没 ready，会缓存 URL，plus ready 后自动 loadURL。
     */
    async loadURL(url) {
      const wv = await ensureReady()
      if (!wv) {
        pendingUrl = url
        return null
      }
      if (pendingUrl && !url) url = pendingUrl
      pendingUrl = ''
      wv.loadURL(url)
      return wv
    },
    /**
     * 关闭子 WebView。用户支付完成、取消、或者命中 moyuyo://return 时调用。
     */
    close() {
      if (!webview) return
      try {
        const wv = webview
        webview = null
        const parent =
          typeof plus !== 'undefined' && plus.webview && plus.webview.currentWebview
            ? plus.webview.currentWebview()
            : null
        if (parent && typeof parent.remove === 'function') {
          parent.remove(
            wv,
            function () {},
            function () {},
          )
        }
        if (typeof wv.close === 'function') {
          wv.close()
        }
      } catch (e) {
        console.warn('[payAppBridge] close error:', e)
      }
    },
    /**
     * 当 H5 中转页通过 postMessage 回传时，主动把 message 扔给调用方。
     * 也支持调用方直接 evalJS 读取支付页数据。
     */
    evalJS(script, cb) {
      if (!webview || typeof webview.evalJS !== 'function') return
      webview.evalJS(script, cb)
    },
  }
}

/**
 * 全局监听 moyuyo:// scheme：处理 APP 冷启动 / 从后台被 scheme 唤起的场景。
 *   - 冷启动：通过 plus.runtime.arguments 拿 URL
 *   - 热启动：通过 plus.runtime 的 newintent / resumed 事件
 *
 * 回调参数：同 parseMoyuyoReturn 的返回结构
 *
 * 使用：在 App.vue 的 onLaunch / onShow 中调用一次，再用事件总线转发到 pay.vue。
 */
export function registerMoyuyoScheme(callback) {
  if (typeof callback !== 'function') return function () {}
  if (typeof plus === 'undefined') return function () {}

  const handle = function (url) {
    const info = parseMoyuyoReturn(url)
    if (info) callback(info)
  }

  // 冷启动参数
  try {
    const args = plus.runtime.arguments
    if (args && typeof args === 'string') handle(args)
  } catch (e) {
    /* ignore */
  }

  const onNewIntent = function (intent) {
    try {
      let url = intent && intent.data ? intent.data : ''
      if (!url && intent) {
        if (typeof intent === 'string') url = intent
        else if (intent.url) url = intent.url
        else if (intent.action && intent.extras) {
          const extras = intent.extras || {}
          url = extras.url || extras.intentUrl || ''
        }
      }
      if (url) handle(url)
    } catch (e) {
      /* ignore */
    }
  }

  // Android：newintent
  try {
    plus.runtime.addEventListener('newintent', onNewIntent, false)
  } catch (e) {
    /* 注册失败忽略 */
  }
  // iOS：从后台被 scheme 打开会触发 resumed，arguments 会变
  try {
    plus.runtime.addEventListener(
      'resumed',
      function () {
        try {
          const args = plus.runtime.arguments
          if (args && typeof args === 'string') handle(args)
        } catch (e) {
          /* ignore */
        }
      },
      false,
    )
  } catch (e) {
    /* resumed 监听失败忽略 */
  }

  return function cleanup() {
    try {
      plus.runtime.removeEventListener('newintent', onNewIntent, false)
    } catch (e) {
      /* 移除失败忽略 */
    }
  }
}

/* ============================================================================
 * B) 原生支付通道（双通道策略：原生优先，失败回落 WebView）
 *   - 解决 iOS WKWebView 拦截 PayPal/Apple Pay 302 跳转造成的白屏问题
 *   - 同时 Android 也避免 WebView SSL pinning / User-Agent 被三方拦截
 * ========================================================================== */

/**
 * 判断当前 APP 是否已经启用了 uni-app 官方 PayPal 支付模块。
 * 对应 manifest.json → app-plus.modules.Payment.paypal
 */
export function isPayPalNativeAvailable() {
  if (typeof uni === 'undefined' || typeof uni.getProvider !== 'function') return false
  // 同步包装一层 Promise：避免回调地狱
  return new Promise((resolve) => {
    try {
      uni.getProvider({
        service: 'payment',
        success: (res) => {
          const list = res && res.provider ? res.provider : []
          resolve(Array.isArray(list) && list.indexOf('paypal') !== -1)
        },
        fail: () => resolve(false),
      })
    } catch (e) {
      resolve(false)
    }
  })
}

/**
 * PayPal / Venmo 原生支付调用（通过 uni.requestPayment 官方通道）。
 *
 * 适用条件：
 *   - 已在 manifest 配置 modules.Payment.paypal（含 returnURL_android/ios）
 *   - PayPal Developer Dashboard 已把 returnURL 加到 App Settings
 *
 * @param {Object} opts
 * @param {string} opts.clientId        PayPal App ClientID（从后端安全下发，不要前端硬编码）
 * @param {string} opts.orderId         PayPal v2 Orders API 返回的订单 ID（后端 /v2/checkout/orders 创建）
 * @param {string} [opts.environment]   sandbox | live，默认 sandbox
 * @param {string} [opts.currency]      USD
 * @param {string} [opts.userAction]    paynow | continue
 * @returns {Promise<{ success: boolean, orderId?: string, error?: Error, fallback?: boolean }>}
 *   success=true  ：原生通道成功，支付流程已交给 PayPal SDK
 *   fallback=true ：原生通道不可用/失败，调用方应回落 WebView 通道
 */
export function startPayPalNative(opts) {
  return new Promise((resolve) => {
    if (!opts || !opts.clientId || !opts.orderId) {
      return resolve({
        success: false,
        fallback: true,
        error: new Error('missing clientId or orderId'),
      })
    }
    if (typeof uni === 'undefined' || typeof uni.requestPayment !== 'function') {
      return resolve({
        success: false,
        fallback: true,
        error: new Error('uni.requestPayment unavailable'),
      })
    }
    const orderInfo = {
      clientId: opts.clientId,
      orderId: opts.orderId,
      environment: opts.environment || 'sandbox',
      currency: opts.currency || 'USD',
      userAction: opts.userAction || 'paynow',
    }
    try {
      uni.requestPayment({
        provider: 'paypal',
        orderInfo,
        success: (res) => {
          let pid = opts.orderId
          try {
            if (res && res.rawdata) {
              const raw = typeof res.rawdata === 'string' ? JSON.parse(res.rawdata) : res.rawdata
              if (raw && raw.orderId) pid = raw.orderId
            }
          } catch (ignore) {
            /* 解析 rawdata 失败忽略 */
          }
          resolve({ success: true, orderId: pid })
        },
        fail: (err) => {
          // 用户取消（err.code=-1 或类似）不应视为通道失败，但仍回落 WebView 给用户再选一次
          resolve({
            success: false,
            fallback: true,
            error: err || new Error('paypal native cancelled'),
          })
        },
      })
    } catch (e) {
      resolve({ success: false, fallback: true, error: e })
    }
  })
}

/**
 * iOS Apple Pay：通过系统 PassKit 调起支付面板。
 * 注：此实现依赖 Stripe 官方原生插件或自研原生插件；
 *     如果打包时未集成对应 nativePlugin，将直接返回 fallback=true 让上层走 WebView。
 *
 * @param {Object} opts
 * @param {string} opts.clientSecret   Stripe PaymentIntent client_secret（后端下发）
 * @param {string} opts.merchantId     Apple Pay Merchant ID（需与 entitlements & Stripe Dashboard 一致）
 * @param {string} opts.countryCode    US
 * @param {string} opts.currencyCode   USD
 * @returns {Promise<{ success: boolean, fallback?: boolean, error?: any }>}
 */
export function startApplePayNative(opts) {
  return new Promise((resolve) => {
    // 仅 iOS 且集成了 Stripe 原生插件时才会命中；否则直接回落 WebView（Stripe Checkout 页内自带 Apple Pay 按钮）
    if (typeof plus === 'undefined') {
      return resolve({ success: false, fallback: true, error: new Error('not APP environment') })
    }
    try {
      // 尝试调 MOYUYOPayment 原生插件（如果在 manifest.nativePlugins 中注册了）
      const paymentModule =
        uni && uni.requireNativePlugin ? uni.requireNativePlugin('MOYUYOPayment') : null
      if (!paymentModule || typeof paymentModule.startApplePay !== 'function') {
        return resolve({
          success: false,
          fallback: true,
          error: new Error('native plugin MOYUYOPayment.startApplePay unavailable'),
        })
      }
      paymentModule.startApplePay(
        {
          clientSecret: opts?.clientSecret || '',
          merchantId: opts?.merchantId || 'merchant.com.moyuyo.app',
          countryCode: opts?.countryCode || 'US',
          currencyCode: opts?.currencyCode || 'USD',
        },
        (res) => {
          if (res && res.success) {
            resolve({ success: true })
          } else {
            resolve({ success: false, fallback: true, error: res?.message || 'apple pay failed' })
          }
        },
      )
    } catch (e) {
      resolve({ success: false, fallback: true, error: e })
    }
  })
}
