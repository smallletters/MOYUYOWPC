package com.moyuyo.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 燕文物流电子面单 API 配置项。
 * <p>
 * 绑定 application.yml 中的 {@code moyuyo.logistics.yanwen.*}。
 * 通过 {@link org.springframework.boot.context.properties.EnableConfigurationProperties}
 * 在 AdminOrderOpsController 所在的 api 模块启用。
 */
@Data
@ConfigurationProperties(prefix = "moyuyo.logistics.yanwen")
public class YanWenProperties {

    /** 是否启用（true 时才允许调用 SDK） */
    private boolean enabled = false;

    /** 燕文客户号 userId（默认账号，表里 mo_carrier 没配则用此值） */
    private String userId = "";

    /** 燕文秘钥 apitoken */
    private String apiToken = "";

    /** 接口地址（默认正式环境） */
    private String baseUrl = "https://open.yw56.com.cn/api/order";

    /**
     * 全局默认渠道/产品编码（可选）。
     * <p>
     * 燕文 createOrder 必填 channelId（不同承运商产品 ID 不同，如美国燕文专线是 1615）。
     * 优先级：mo_carrier.channelId > 本字段。
     * <p>
     * 不在 yml 配置时，运营必须在「承运商管理」里给具体承运商记录配置 channelId。
     */
    private String channelId = "";
}