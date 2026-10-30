package com.moyuyo.common.utils;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * XSS 净化工具类
 * 基于 OWASP HTML Sanitizer，使用白名单策略净化用户提交的富文本。
 * 默认策略仅允许常见的无害标签和属性，移除所有脚本、事件处理器与危险属性。
 */
public final class XssSanitizer {

    private XssSanitizer() {
    }

    /**
     * 富文本策略：允许常见的格式化标签与图片，禁止脚本、内联事件、javascript: 协议等。
     * 适用场景：社区帖子、评论、反馈等用户可输入富文本的场景。
     */
    private static final PolicyFactory RICH_TEXT_POLICY = new HtmlPolicyBuilder()
            .allowElements(
                    "a", "b", "i", "em", "strong", "u", "br", "p", "span", "div",
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "ul", "ol", "li",
                    "blockquote", "pre", "code",
                    "img", "hr", "table", "thead", "tbody", "tr", "th", "td"
            )
            // <a> 仅允许 href 与 title，强制 rel=nofollow 防止 SEO 滥用
            .allowAttributes("href", "title").onElements("a")
            .requireRelNofollowOnLinks()
            // <img> 仅允许 src、alt、width、height，禁止 srcset 等可能被滥用的属性
            .allowAttributes("src", "alt", "width", "height").onElements("img")
            // 允许通用样式属性（仅 style 中的 color/text-align/font-weight 等会被保留，sanitizer 会过滤危险值）
            .allowAttributes("style").onElements("span", "div", "p")
            .allowAttributes("colspan", "rowspan").onElements("th", "td")
            // 允许 http/https/mailto 协议，禁止 javascript:、data: 等危险协议
            .allowUrlProtocols("http", "https", "mailto")
            // 保留换行与空白
            .allowTextIn("p", "span", "div")
            .toFactory();

    /**
     * 纯文本策略：转义所有 HTML 标签，仅保留文本内容。
     * 适用场景：用户名、昵称、标题等不应包含任何 HTML 的字段。
     */
    private static final PolicyFactory PLAIN_TEXT_POLICY = new HtmlPolicyBuilder()
            .toFactory();

    /**
     * 净化富文本：保留允许的标签和属性，移除脚本、事件处理器等危险内容。
     *
     * @param input 用户输入的原始文本，可能为 null
     * @return 净化后的安全 HTML；输入为 null 时返回空字符串
     */
    public static String sanitizeRichText(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return RICH_TEXT_POLICY.sanitize(input);
    }

    /**
     * 净化纯文本：转义所有 HTML 标签，返回纯文本内容。
     *
     * <p><b>注意</b>：OWASP HTML Sanitizer 会把所有非 ASCII 字符输出成数字实体
     * （{@code ，} → {@code &#xff0c;}、{@code @} → {@code &#64;}）。
     * 该结果只适合再用 HTML 渲染的场景；若字段最终由
     * {@code <text>}（uni-app）或 {@code {{ }}}（Vue）当纯文本显示，
     * 实体会原样暴露成乱码 —— 这类字段请用 {@link #sanitizeText(String)}。
     *
     * @param input 用户输入的原始文本，可能为 null
     * @return 转义后的纯文本；输入为 null 时返回空字符串
     */
    public static String sanitizePlainText(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return PLAIN_TEXT_POLICY.sanitize(input);
    }

    /**
     * 保真净化纯文本（不做 HTML 实体转义）。
     *
     * <p>适用场景：内容只会以文本节点方式渲染的字段，如意见反馈正文、联系方式、
     * 备注等。Vue 的 {@code {{ }}} 与 uni-app 的 {@code <text>} 都按文本渲染，
     * 天然不会执行 HTML，因此无需转义；只需去掉控制字符与首尾空白，保证入库内容
     * 与用户输入一致。
     *
     * <p><b>禁止</b>把该方法的返回值交给 {@code v-html} / {@code rich-text} 渲染。
     *
     * @param input 用户输入的原始文本，可能为 null
     * @return 去掉控制字符并 trim 的原文；输入为 null 时返回空字符串
     */
    public static String sanitizeText(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        // 仅保留 \t \n \r，其余 C0/C1 控制字符（含 \u0000）全部剔除
        String cleaned = input.replaceAll("[\\p{Cntrl}&&[^\\t\\n\\r]]", "");
        return cleaned.trim();
    }

    /**
     * URL 净化：仅允许 http/https/mailto 协议，禁用 javascript:、data: 等危险协议，
     * 防止攻击者通过 video / img 等 src 属性注入脚本。
     *
     * @param input 原始 URL（可能为 null）
     * @return 安全 URL；不安全或空时返回 null（让上层以 null 表示"未上传"）
     */
    public static String sanitizeUrl(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return null;
        String lower = trimmed.toLowerCase();
        // 白名单协议头检测
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("/")) {
            return trimmed;
        }
        // 不允许的协议（javascript: / data: / vbscript: / file: ...）一律返回 null
        return null;
    }
}
