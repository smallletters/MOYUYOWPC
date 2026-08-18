package com.moyuyo.common.utils;

/**
 * 分页参数统一守卫（防御 OOM / 全表扫描）
 * <p>
 * 关键修复：原多个 Controller 直接将 @RequestParam int page / int size 透传至 Service，
 * 攻击者传入 size=100000 会触发：
 * 1) MyBatis-Plus 一次性查询 10W 行 → 数据库 IO 风暴
 * 2) Jackson 序列化 10W 行 → OOM
 * <p>
 * 本工具类在 Controller 入口对分页参数做统一归一化：
 * - page 最小 1（≤0 一律归为 1）
 * - size 范围 [1, MAX_PAGE_SIZE]，超出上限截断
 * - size 默认值通过 defaultSize 参数注入，便于不同业务场景配置（订单列表 10、运营列表 20）
 * <p>
 * 与 README 73 项"分页参数统一守卫"承诺对齐：
 * <ul>
 *   <li>硬上限 100：覆盖绝大多数业务场景，规避 size=100000 攻击</li>
 *   <li>归一化策略：page<1 视为 1（前端传 0/负数不报错）</li>
 *   <li>无副作用：返回 int[] 仅做参数修正，不抛业务异常</li>
 * </ul>
 */
public final class PageParamGuard {

    /** 单页硬上限：超过此值截断到上限，防止 OOM 与全表扫描 */
    public static final int MAX_PAGE_SIZE = 100;

    private PageParamGuard() {}

    /**
     * 归一化分页参数
     *
     * @param page        原始页码（≤0 一律视为 1）
     * @param size        原始单页大小（<1 视为 defaultSize，>MAX_PAGE_SIZE 截断）
     * @param defaultSize 默认单页大小（业务侧按需选择 10/20/50）
     * @return [normalizedPage, normalizedSize]
     */
    public static int[] normalize(int page, int size, int defaultSize) {
        int safeDefault = (defaultSize >= 1 && defaultSize <= MAX_PAGE_SIZE) ? defaultSize : 10;
        int normalizedPage = (page >= 1) ? page : 1;
        int normalizedSize;
        if (size < 1) {
            normalizedSize = safeDefault;
        } else if (size > MAX_PAGE_SIZE) {
            normalizedSize = MAX_PAGE_SIZE;
        } else {
            normalizedSize = size;
        }
        return new int[] { normalizedPage, normalizedSize };
    }
}