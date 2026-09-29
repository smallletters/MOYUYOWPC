import { ref, computed, watch } from 'vue'
import { addressApi } from '@/api'
import { i18n } from '@/i18n'
import { getStorage, setStorage, STORAGE_KEYS } from '@/utils/storage'

/**
 * 加载运营配置的国家码（composable）。
 *
 * <p>提供三件事，address-edit / address-list / checkout 共享：
 *  1. <b>countryCodes</b> ref<string[]>，picker 用的源数据
 *  2. <b>countryLabels</b> computed<string[]>，picker 显示文案（按当前 locale 渲染）
 *  3. <b>isCountryShippable(code)</b> 方法，判断某国家是否在可发货集内
 *
 * <p>策略（两层缓存 + 自动重试）：
 *  - L1 in-memory <code>loaded</code> 状态：同会话多个页面共享一次拉取（每次调用 loadCountries 都重新查 force）
 *  - L2 storage cache：跨会话复用上次结果，APP 启动立即有 picker（弱网兜底）
 *  - 失败重试：onError 自动以 force=true 重试一次，避免 setup 异常导致 picker 永远空
 *
 * <p>@example
 *   const { countryCodes, countryLabels, loadCountries, isCountryShippable } =
 *     useAddressCountries()
 *   onMounted(loadCountries)
 */
export function useAddressCountries() {
  const countryCodes = ref([])
  // loaded 标记：true = 已经尝试过 L1 加载（不论成功失败）；force=true 时重置
  const loaded = ref(false)

  // ==================== L2: hydrate from storage（让 APP 启动立即可用） ====================
  hydrateFromStorage()

  function hydrateFromStorage() {
    try {
      const cached = getStorage(STORAGE_KEYS.SUPPORTED_COUNTRIES_CACHE)
      if (cached && Array.isArray(cached.countries) && cached.countries.length > 0) {
        countryCodes.value = cached.countries
      }
    } catch (e) {
      console.warn('[useAddressCountries] hydrate from storage failed', e)
    }
  }

  function persistToStorage(list) {
    try {
      setStorage(STORAGE_KEYS.SUPPORTED_COUNTRIES_CACHE, { countries: list, ts: Date.now() })
    } catch (e) {
      console.warn('[useAddressCountries] persist failed', e)
    }
  }

  /**
   * 主动加载；force=true 强制重拉（绕过 L1 loaded 状态）
   *
   * 防御：每次 force 调用都会重置 loaded=false，让下一次普通 loadCountries 也能再次发起请求，
   * 避免"force=true 之后 L1 永远命中"的 stale cache bug。
   *
   * 失败时自动以 force=true 重试一次（防止 setup 期异常后 picker 永远空）。
   */
  async function loadCountries(force = false) {
    if (loaded.value && !force) return countryCodes.value

    loaded.value = false // force 模式：强制下一行能进入分支
    try {
      const list = await addressApi.getSupportedCountries()
      if (Array.isArray(list) && list.length > 0) {
        countryCodes.value = list
        persistToStorage(list)
      } else if (countryCodes.value.length === 0) {
        // 接口返空 + storage 也空：兜底
        countryCodes.value = FALLBACK
      }
      loaded.value = true
      return countryCodes.value
    } catch (e) {
      // 第一次失败：loaded 保持 false（让后续 loadCountries 有机会再试）
      // 仅当 storage 也没有缓存时才用 fallback
      if (countryCodes.value.length === 0) {
        countryCodes.value = FALLBACK
      }
      if (!force) {
        // 自动重试一次：网络瞬时抖动 / 服务刚启动 race 时自动恢复
        console.warn('[useAddressCountries] load failed, retrying once with force=true', e)
        return loadCountries(true)
      }
      // 已经 force 重试还失败：放弃，loaded=false（仍可被下次手动 loadCountries 触发）
      console.error('[useAddressCountries] load failed twice (force retry exhausted)', e)
      return countryCodes.value
    }
  }

  /** picker 显示文案（依赖 i18n.locale 自动响应） */
  const countryLabels = computed(() =>
    countryCodes.value.map((code) => i18n.t(`address.countryCodes.${code}`, code)),
  )

  /** 判断某国家码是否在运营配置集内 */
  function isCountryShippable(code) {
    if (!code) return true
    return countryCodes.value.indexOf(code) >= 0
  }

  // 后台静默刷新：当 countryCodes 实际变化时持久化到 storage
  // 防御：只检测 length 变化（避免每次响应式触发都 setStorage 浪费 IO）
  watch(countryCodes, (newVal, oldVal) => {
    if (oldVal && newVal.length !== oldVal.length) {
      persistToStorage(newVal)
    }
  })

  return {
    countryCodes,
    countryLabels,
    isCountryShippable,
    loadCountries,
  }
}

// 与历史默认白名单对齐：US 排首位（最常见）
const FALLBACK = ['US', 'CA', 'GB', 'DE', 'FR', 'ES', 'IT', 'AU', 'JP', 'SG']
