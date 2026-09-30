package com.moyuyo.common.dto.logistics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 燕文物流 — 查询通达国家列表响应参数
 * <p>
 * 对应接口：common.country.getlist
 * 文档：https://opendocs.yw56.com.cn/webfile/6993833547773513728/
 * <p>
 * 响应结构：{ success, code, message, data: [ { id, code, nameCh, nameEn }, ... ] }
 * <p>
 * 用于 CountryResolver 在地址解析时做精确匹配：
 *   - 业务地址里包含"加拿大" → 查燕文 nameCh="加拿大" 的记录 → code="CA"
 *   - 避免运营写错 yml 配置（漏写"加拿大"别名 → 误判到其他国家）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class YanWenCountryListResponse {

    private Boolean success;
    private String code;
    private String message;
    private List<CountryItem> data = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CountryItem {
        /** 国家 ID（燕文内部自增，业务上用不到） */
        private String id;
        /** 国家代码（ISO 3166-1 alpha-2 二字码） */
        private String code;
        /** 中文名（如 "加拿大"） */
        private String nameCh;
        /** 英文名（如 "CANADA"） */
        private String nameEn;
    }
}
