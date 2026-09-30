package com.moyuyo.common.util;

/**
 * 国家目录查找接口 —— CountryResolver 用来按中文/英文国名查 ISO alpha-2。
 * <p>
 * 设计目标：让 CountryResolver（common 模块）能引用 service 模块的缓存，
 * 又不破坏模块依赖方向。做法是把接口放在 common，实现在 service。
 * <p>
 * 典型实现：从燕文 common.country.getlist 拉取并缓存到内存。
 */
public interface CountryDirectoryLookup {

    /**
     * 按国名（中/英）查 ISO 3166-1 alpha-2 二字码。
     *
     * @param nameZhOrEn 中文名（如 "加拿大"）或英文名（如 "CANADA"），大小写不敏感
     * @return ISO 二字码；查不到返回 null
     */
    String lookupByName(String nameZhOrEn);

    /**
     * 从 address 字符串中查找首个出现的国名并返回 code。
     * <p>
     * 遍历缓存所有国名（中/英），找"address 是否包含此国名"。
     * 用于 CountryResolver 主解析路径 —— 比逐条匹配 yml aliases 覆盖率更高。
     *
     * @param address 收货地址字符串
     * @return ISO 二字码；找不到返回 null
     */
    String findFirstCodeIn(String address);

    /**
     * 缓存大小（调试用）。
     */
    int size();
}
