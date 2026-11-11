package com.moyuyo.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.dao.entity.ProductEntity;
import com.moyuyo.dao.entity.ProductSkuEntity;
import com.moyuyo.dao.mapper.ProductSkuMapper;
import com.moyuyo.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 客服商品上下文构造
 * <p>
 * 扣子侧的智能体无法访问商城数据库，只拿到用户一句话时容易凭空回答「没有该商品」。
 * 这里在把问题发给智能体之前，先按用户问题里的编号 / 名称检索在售商品，
 * 拼成一段纯文本资料随问题一起发送，让智能体基于真实数据回答。
 * <p>
 * 检索失败或无命中时返回空串，调用方按原问题发送，不影响正常对话。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsAiProductContextService {

  /** 单次最多注入的商品条数，避免 prompt 过长推高 token 成本 */
  private static final int MAX_PRODUCTS = 5;

  /** 每个商品最多列出的规格数 */
  private static final int MAX_SKUS = 8;

  /** 商品简介截断长度 */
  private static final int MAX_DETAIL = 120;

  /** 整个上下文的最大字符数 */
  private static final int MAX_LENGTH = 1800;

  /** 编号类关键词：字母数字组合，如 008、MY-008、SKU123 */
  private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{1,30}");

  /** 中文片段：连续中文，剔除问句常用词后作为商品名候选 */
  private static final Pattern CN_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,20}");

  /** HTML 标签：商品详情是富文本，转纯文本再截断 */
  private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

  /**
   * 问句常用词：从中文片段中剔除，剩下的才当作商品名候选。
   * 顺序上长词在前，避免「查一下」先被「查」拆掉。
   */
  private static final String[] STOP_WORDS = {
      "查一下", "有没有", "多少钱", "什么规格", "你好", "您好", "请问", "我想", "想要", "咨询",
      "查询", "帮我", "麻烦", "商品", "产品", "一下", "有货", "现货", "现在", "价格", "库存",
      "规格", "什么", "怎么", "可以", "是否", "这个", "那个", "的", "吗", "呢", "啊", "呀", "我", "问"
  };

  private final ProductService productService;

  private final ProductSkuMapper skuMapper;

  /**
   * 根据用户问题构造商品资料文本
   *
   * @param userText 用户本轮问题
   * @return 命中商品时返回资料文本；无关键词或无命中返回空串
   */
  public String build(String userText) {
    if (userText == null || userText.isBlank()) return "";
    try {
      List<String> keywords = extractKeywords(userText);
      if (keywords.isEmpty()) return "";
      List<ProductEntity> products = search(keywords);
      if (products.isEmpty()) return "";
      return render(products);
    } catch (Exception e) {
      // 商品检索属于增强能力，失败不能影响本轮回复
      log.warn("构造商品上下文失败：{}", e.getMessage());
      return "";
    }
  }

  /**
   * 提取检索关键词：编号（字母数字）优先，其次是从问句中剥离常用词后的中文片段
   */
  private List<String> extractKeywords(String userText) {
    Set<String> keywords = new LinkedHashSet<>();

    Matcher codeMatcher = CODE_PATTERN.matcher(userText);
    while (codeMatcher.find() && keywords.size() < MAX_PRODUCTS) {
      String code = codeMatcher.group();
      // 纯数字过短（如「1」）没有检索价值
      if (code.chars().allMatch(Character::isDigit) && code.length() < 2) continue;
      keywords.add(code);
    }

    Matcher cnMatcher = CN_PATTERN.matcher(userText);
    while (cnMatcher.find() && keywords.size() < MAX_PRODUCTS) {
      String segment = stripStopWords(cnMatcher.group());
      if (segment.length() >= 2) keywords.add(segment);
    }
    return new ArrayList<>(keywords);
  }

  /**
   * 从中文片段中剔除问句常用词
   */
  private String stripStopWords(String segment) {
    String result = segment;
    for (String word : STOP_WORDS) {
      result = result.replace(word, "");
    }
    return result.trim();
  }

  /**
   * 按关键词逐个检索在售商品，按商品 ID 去重
   */
  private List<ProductEntity> search(List<String> keywords) {
    Map<Long, ProductEntity> hits = new LinkedHashMap<>();
    for (String keyword : keywords) {
      if (hits.size() >= MAX_PRODUCTS) break;
      // 关键词里的 SQL 通配对是无效输入，去掉避免匹配到全部商品
      String safe = keyword.replace("%", "").replace("_", "").trim();
      if (safe.isEmpty()) continue;
      Page<ProductEntity> page = productService.listProducts(
          1, MAX_PRODUCTS, null, null, null, safe, "active", null, null);
      for (ProductEntity product : page.getRecords()) {
        if (hits.size() >= MAX_PRODUCTS) break;
        hits.putIfAbsent(product.getId(), product);
      }
    }
    return new ArrayList<>(hits.values());
  }

  /**
   * 渲染为智能体可读的纯文本资料
   */
  private String render(List<ProductEntity> products) {
    StringBuilder sb = new StringBuilder("【商城商品资料】（来自后台商品库，请优先依据以下信息回答，禁止编造商品）\n");
    int index = 1;
    for (ProductEntity product : products) {
      sb.append(index++).append(". 编号：").append(blankTo(product.getSpuCode(), "-"))
          .append("；名称：").append(blankTo(product.getName(), "-"))
          .append("；售价：").append(money(product.getPrice()));
      if (product.getOriginalPrice() != null) {
        sb.append("（原价 ").append(money(product.getOriginalPrice())).append("）");
      }
      sb.append("；库存：").append(product.getStock() == null ? "-" : product.getStock())
          .append("；状态：").append(Boolean.TRUE.equals(product.getOnSale()) ? "在售" : "下架")
          .append('\n');

      List<ProductSkuEntity> skus = listSkus(product.getId());
      if (!skus.isEmpty()) {
        sb.append("   规格：");
        for (int i = 0; i < skus.size(); i++) {
          ProductSkuEntity sku = skus.get(i);
          if (i > 0) sb.append("；");
          sb.append(blankTo(sku.getSpec(), blankTo(sku.getSkuCode(), "-")))
              .append(" ").append(money(sku.getPrice()))
              .append("（库存 ").append(sku.getStock() == null ? "-" : sku.getStock()).append("）");
        }
        sb.append('\n');
      }

      String detail = plainText(product.getShortDetail());
      if (detail.isEmpty()) detail = plainText(product.getDetail());
      if (!detail.isEmpty()) sb.append("   简介：").append(detail).append('\n');

      if (sb.length() >= MAX_LENGTH) break;
    }
    return sb.length() <= MAX_LENGTH ? sb.toString() : sb.substring(0, MAX_LENGTH);
  }

  /**
   * 查询商品规格（限制条数，避免大商品把 prompt 撑爆）
   */
  private List<ProductSkuEntity> listSkus(Long productId) {
    if (productId == null) return List.of();
    List<ProductSkuEntity> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSkuEntity>()
        .eq(ProductSkuEntity::getProductId, productId)
        .last("LIMIT " + MAX_SKUS));
    return skus == null ? List.of() : skus;
  }

  /**
   * 富文本转纯文本并截断
   */
  private String plainText(String html) {
    if (html == null || html.isBlank()) return "";
    String text = HTML_TAG.matcher(html).replaceAll(" ")
        .replace("&nbsp;", " ")
        .replaceAll("\\s+", " ")
        .trim();
    return text.length() <= MAX_DETAIL ? text : text.substring(0, MAX_DETAIL);
  }

  private String money(BigDecimal amount) {
    return amount == null ? "-" : "¥" + amount.stripTrailingZeros().toPlainString();
  }

  private String blankTo(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
