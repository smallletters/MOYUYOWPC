package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moyuyo.dao.entity.GiftCardEntity;
import com.moyuyo.dao.entity.GiftCardTransactionEntity;
import com.moyuyo.dao.mapper.GiftCardMapper;
import com.moyuyo.dao.mapper.GiftCardTransactionMapper;
import com.moyuyo.service.GiftCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GiftCardServiceImpl implements GiftCardService {

  private final GiftCardMapper giftCardMapper;
  private final GiftCardTransactionMapper giftCardTransactionMapper;

  @Override
  public List<GiftCardEntity> listByUserId(Long userId) {
    return giftCardMapper.selectList(
        new LambdaQueryWrapper<GiftCardEntity>()
            .eq(GiftCardEntity::getUserId, userId)
            .orderByDesc(GiftCardEntity::getCreateTime));
  }

  @Override
  @Transactional
  public GiftCardEntity bind(Long userId, String cardNo, String pin) {
    GiftCardEntity card = giftCardMapper.selectOne(
        new LambdaQueryWrapper<GiftCardEntity>()
            .eq(GiftCardEntity::getCardNo, cardNo));
    if (card == null) {
      throw new IllegalArgumentException("礼品卡不存在");
    }
    if (!"ACTIVE".equals(card.getStatus())) {
      throw new IllegalArgumentException("礼品卡已使用或已过期");
    }
    // 验证 PIN 码
    if (pin != null && !pin.isEmpty() && !pin.equals(card.getPin())) {
      throw new IllegalArgumentException("PIN码不正确");
    }
    card.setUserId(userId);
    card.setStatus("USED");
    giftCardMapper.updateById(card);
    log.info("GiftCard bound: cardId={}, userId={}", card.getId(), userId);
    return card;
  }

  @Override
  @Transactional
  public List<GiftCardEntity> purchase(Long userId, Integer amount, Integer quantity) {
    List<GiftCardEntity> cards = new ArrayList<>();
    for (int i = 0; i < quantity; i++) {
      GiftCardEntity card = new GiftCardEntity();
      card.setUserId(userId);
      card.setCardNo(maskCardNo(UUID.randomUUID().toString().replace("-", "").substring(0, 16)));
      card.setTotalAmount(BigDecimal.valueOf(amount));
      card.setBalance(BigDecimal.valueOf(amount));
      card.setStatus("ACTIVE");
      giftCardMapper.insert(card);
      cards.add(card);
    }
    log.info("GiftCard purchased: userId={}, amount={}, quantity={}", userId, amount, quantity);
    return cards;
  }

  @Override
  public List<GiftCardTransactionEntity> getTransactions(Long cardId, Long userId) {
    GiftCardEntity card = giftCardMapper.selectById(cardId);
    if (card == null || !card.getUserId().equals(userId)) {
      throw new IllegalArgumentException("礼品卡不存在或无权访问");
    }
    return giftCardTransactionMapper.selectList(
        new LambdaQueryWrapper<GiftCardTransactionEntity>()
            .eq(GiftCardTransactionEntity::getCardId, cardId)
            .orderByDesc(GiftCardTransactionEntity::getCreateTime));
  }

  private String maskCardNo(String cardNo) {
    if (cardNo.length() > 4) {
      return "****" + cardNo.substring(cardNo.length() - 4);
    }
    return cardNo;
  }
}
