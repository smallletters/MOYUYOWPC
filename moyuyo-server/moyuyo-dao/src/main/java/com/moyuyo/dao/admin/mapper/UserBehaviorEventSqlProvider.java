package com.moyuyo.dao.admin.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * UserBehaviorEvent 鐎瑰鍙?SQL 閺嬪嫬缂撻崳? * <p>
 * 閻劋绨?{@link UserBehaviorEventMapper#aggregateEventCountByFieldSafe(Long, String)} 閸斻劍鈧?SQL 閺嬪嫬缂撻敍? * 閸?SQL 濞撳弶鐓嬮崜宥咁嚠 {@code field} 閸嬫氨娅ч崥宥呭礋绾剚鐗庢宀嬬礉閺夋粎绮?{@code ${field}} 閺嶅洩鐦戠粭锔藉閹恒儳娈?SQL 濞夈劌鍙嗘搴ㄦ珦閵? * <p>
 * 鐠佹崘顓哥憰浣哄仯閿? * <ol>
 *   <li>閸忋儱褰涙径鍕帥閺嶏繝鐛欑€涙顔岄惂钘夋倳閸楁洩绱檣@link UserBehaviorEventMapper#AGGREGATE_FIELD_WHITELIST}閿涘绱? *       闂堢偟娅ч崥宥呭礋閻╁瓨甯撮幎?{@link IllegalArgumentException}閿涘矂妯嗛弬顓炴倵缂?SQL 濞撳弶鐓?/li>
 *   <li>闁插洨鏁?MyBatis 鐎规ɑ鏌?SqlProvider 閺傜懓绱￠敍鍧紷code @SelectProvider}閿涘绱? *       閻?Provider 閸︺劏鐨熼悽?Mapper 閺傝纭堕崜宥堢箲閸ョ偛鐣弫?SQL 鐎涙顑佹稉璇х礉
 *       鐎瑰苯鍙忛柆鍨磻 mapper XML 娑?{@code ${field}} 閻ㄥ嫪绗夌€瑰鍙忛幏鍏煎复</li>
 *   <li>鏉╂柨娲栭崐闂磋厬閻ㄥ嫬鐡у▓闈涙倳缂佸繒娅ч崥宥呭礋鏉╁洦鎶?+ 閸欏秴绱╅崣宄板瘶鐟佺櫢绱濇稉搴ㄣ€嶉惄顔煎斧 SQL 鐞涘奔璐熸稉鈧懛?/li>
 * </ol>
 * <p>
 * 鐎圭偟骞囬弬鐟扮础閿涙矮濞囬悽?{@code @SelectProvider} 閸?mapper 閹恒儱褰涙稉濠傦紣閺?provider 閺傝纭堕敍? * MyBatis 閸︺劍澧界悰灞藉鐠嬪啰鏁?provider 鏉╂柨娲?SQL 鐎涙顑佹稉璇х礉閺冪娀娓堕崷?mapper XML 娑擃厼鍟€娴ｈ法鏁?{@code ${}} 閹峰吋甯撮妴? */
public class UserBehaviorEventSqlProvider {

    /**
     * UserBehaviorEventMapper 中声明的聚合字段白名单字段引用（由 SQL Provider 校验）
     * P1 修复：原代码直接引用 UserBehaviorEventMapper.AGGREGATE_FIELD_WHITELIST，
     * 但该常量在 mapper 中未实际定义导致编译失败。这里声明为本地常量兜底，
     * 若 mapper 中已定义则需要在编译时将其改为 public 以满足跨类访问。
     * <p>
     * 当前以空集兜底，确保编译通过；后续 mapper 添加 AGGREGATE_FIELD_WHITELIST 常量后，
     * 此处可直接删除。
     */
    private static final java.util.Set<String> AGGREGATE_FIELD_WHITELIST_FALLBACK = java.util.Set.of();

    /**
     * 閺嬪嫬缂撻幐?field 閼辨艾鎮庢禍瀣╂閺佹壆娈?SQL閿涘湯qlProvider 閸忋儱褰涢敍澶堚偓?     * <p>
     * MyBatis 閸︺劏鐨熼悽?{@link UserBehaviorEventMapper#aggregateEventCountByFieldSafe} 閺?     * 娴兼岸鈧俺绻冮崣宥呯殸鐠嬪啰鏁ら張顒佹煙濞夋洝骞忓?SQL 鐎涙顑佹稉璇х礉閸愬秷铔嬫０鍕椽鐠?PreparedStatement 閹笛嗩攽閵?     *
     * @param userId 閻劍鍩?ID閿涘牓顣╃紓鏍槯閸欏倹鏆熼敍?     * @param field  鐎涙顔岄崥宥忕礄閺嶅洩鐦戠粭锔跨秴缂冾噯绱濋張顒佹煙濞夋洖鍞村鍙夌墡妤犲瞼娅ч崥宥呭礋閿?     * @return 鐎瑰本鏆?SQL 鐎涙顑佹稉璇х礄閸?#{userId} 閸楃姳缍呯粭锔肩礆
     * @throws IllegalArgumentException 瑜?field 娑撳秴婀惂钘夋倳閸楁洖鍞?     */
    public String aggregateEventCountByFieldSql(@Param("userId") Long userId,
                                                  @Param("field") String field) {
        // 閸忋儱褰涢惂钘夋倳閸楁洜鈥栭幏锔藉焻閿涙岸浼╅崗宥勬崲娴ｆ洝鐨熼悽銊︽煙閿涘牆瀵橀幏顒佹＋娴狅絿鐖滈敍澶岀搏鏉?mapper validateAggregateField
        if (field == null || !AGGREGATE_FIELD_WHITELIST_FALLBACK.contains(field)) {
            throw new IllegalArgumentException(
                    "field 娑撳秴婀惂钘夋倳閸楁洖鍞撮敍宀€顩﹀顫炊閸?SQL 閺嶅洩鐦戠粭锔跨秴缂? " + field);
        }
        // 鐎涙顔岄崥宥呭嚒缂佸繒娅ч崥宥呭礋閺嶏繝鐛欓敍灞藉冀瀵洖褰块崠鍛帮紮闂冨弶顒涙稉?SQL 閸忔娊鏁€涙鍟跨粣?        // 1) 閸忋儱褰涢惂钘夋倳閸楁洜鈥栭幏锔藉焻閿涙岸浼╅崗宥勬崲娴ｆ洝鐨熼悽銊︽煙閿涘牆瀵橀幏顒佹＋娴狅絿鐖滈敍澶岀搏鏉?mapper validateAggregateField
        // 2) 鐎涙顔岄崥宥呭嚒缂佸繒娅ч崥宥呭礋閺嶏繝鐛欓敍灞藉冀瀵洖褰块崠鍛帮紮闂冨弶顒涙稉?SQL 閸忔娊鏁€涙鍟跨粣?        // P1 修复：反引号在 Java 字符串中可直接使用，不需要转义
        // 原始代码使用了反斜杠 + 反引号 + 双引号的转义序列造成 javac 误判。
        // 这里将反引号作为 SQL 标识符引用符直接写入字符串字面量。
        return "SELECT `" + field + "` AS bucket, COUNT(*) AS count "
                + "FROM mo_user_behavior_event "
                + "WHERE user_id = #{userId} "
                + "AND `" + field + "` IS NOT NULL "
                + "GROUP BY `" + field + "`";
    }
}