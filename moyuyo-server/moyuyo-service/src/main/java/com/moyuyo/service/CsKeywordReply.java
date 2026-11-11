package com.moyuyo.service;

/**
 * 内置关键词自动回复
 * <p>
 * 两条链路复用同一套话术，避免两处维护出现口径不一致：
 * <ul>
 *   <li>AI 客服关闭时由 {@code CsService.sendUserMessage} 同步回复</li>
 *   <li>消息已入队、异步任务真正执行时 AI 却被后台关闭，由 {@code AiCsReplyService} 回退回复</li>
 * </ul>
 */
public final class CsKeywordReply {

  private CsKeywordReply() {
  }

  /**
   * 按关键词生成回复文案
   *
   * @param userText 用户原始问题
   * @return 兜底回复，永不为 null
   */
  public static String reply(String userText) {
    String text = userText == null ? "" : userText.toLowerCase();
    if (text.contains("物流") || text.contains("快递") || text.contains("发货")) {
      return "您的订单一般在支付后 24 小时内出库，可在「我的-订单」中查看物流。";
    }
    if (text.contains("退款") || text.contains("退货")) {
      return "退款申请请到「我的-订单-申请退款」，原路退回 1-7 个工作日。";
    }
    if (text.contains("发票")) {
      return "电子发票将在订单完成后 1-3 个工作日开具，请到「我的-发票」查看。";
    }
    if (text.contains("优惠券") || text.contains("积分")) {
      return "积分可在「积分商城」兑换礼品，结算时也可抵扣（100 积分 = ¥1）。";
    }
    if (text.contains("你好") || text.contains("hi")) {
      return "您好，我是 MOYUYO 智能客服小助手，请问需要什么帮助？";
    }
    return "已收到您的消息，客服将在工作时间尽快回复您。";
  }
}
