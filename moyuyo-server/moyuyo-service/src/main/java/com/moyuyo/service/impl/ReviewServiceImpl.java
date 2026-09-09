package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.review.CreateReviewRequest;
import com.moyuyo.common.dto.review.ReviewVO;
import com.moyuyo.common.enums.OrderStatusEnum;
import com.moyuyo.common.enums.ReviewStatusEnum;
import com.moyuyo.common.utils.JsonUtils;
import com.moyuyo.common.utils.PageUtils;
import com.moyuyo.common.utils.XssSanitizer;
import com.moyuyo.dao.entity.OrderEntity;
import com.moyuyo.dao.entity.OrderItemEntity;
import com.moyuyo.dao.entity.ProductReviewEntity;
import com.moyuyo.dao.entity.UserEntity;
import com.moyuyo.dao.mapper.OrderItemMapper;
import com.moyuyo.dao.mapper.OrderMapper;
import com.moyuyo.dao.mapper.ProductReviewMapper;
import com.moyuyo.dao.mapper.UserMapper;
import com.moyuyo.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ProductReviewMapper productReviewMapper;
    private final UserMapper userMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderMapper orderMapper;

    @Override
    public Page<ReviewVO> getProductReviews(Long productId, int page, int size) {
        Page<ProductReviewEntity> entityPage = productReviewMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<ProductReviewEntity>()
                .eq(ProductReviewEntity::getProductId, productId)
                .eq(ProductReviewEntity::getStatus, ReviewStatusEnum.APPROVED.name())
                .orderByDesc(ProductReviewEntity::getCreateTime)
        );
        return toReviewVOPage(entityPage);
    }

    @Override
    @Transactional
    public ReviewVO createReview(Long userId, CreateReviewRequest request) {
        // 归属校验：只能评价"自己的、已收货/已完成订单中的真实商品"，防止刷评价/刷积分
        if (request.getOrderId() == null || request.getOrderItemId() == null
            || request.getProductId() == null) {
            throw new IllegalArgumentException("缺少订单信息，无法评价");
        }
        OrderItemEntity item = orderItemMapper.selectById(request.getOrderItemId());
        if (item == null) {
            throw new IllegalArgumentException("评价的订单商品不存在");
        }
        if (!request.getOrderId().equals(item.getOrderId())) {
            throw new IllegalArgumentException("订单与商品明细不匹配");
        }
        if (!request.getProductId().equals(item.getProductId())) {
            throw new IllegalArgumentException("商品与订单明细不一致");
        }
        OrderEntity order = orderMapper.selectById(item.getOrderId());
        if (order == null || !userId.equals(order.getUserId())) {
            throw new IllegalArgumentException("无权评价该订单");
        }
        String status = order.getStatus();
        if (!OrderStatusEnum.RECEIVED.name().equals(status)
            && !OrderStatusEnum.COMPLETED.name().equals(status)) {
            throw new IllegalArgumentException("订单需确认收货/完成后才能评价");
        }
        // 防重复评价（唯一索引 user_id+order_item_id 兜底）
        Long duplicate = productReviewMapper.selectCount(
            new LambdaQueryWrapper<ProductReviewEntity>()
                .eq(ProductReviewEntity::getUserId, userId)
                .eq(ProductReviewEntity::getOrderItemId, request.getOrderItemId()));
        if (duplicate != null && duplicate > 0) {
            throw new IllegalArgumentException("该订单商品已评价");
        }

        ProductReviewEntity entity = new ProductReviewEntity();
        entity.setProductId(request.getProductId());
        entity.setUserId(userId);
        entity.setOrderId(request.getOrderId());
        entity.setOrderItemId(request.getOrderItemId());
        entity.setRating(request.getRating());
        // 净化评价内容，防止存储型 XSS
        entity.setContent(XssSanitizer.sanitizeRichText(request.getContent()));
        entity.setTags(JsonUtils.toJsonArray(request.getTags()));
        entity.setImages(JsonUtils.toJsonArray(request.getImages()));
        entity.setStatus(ReviewStatusEnum.PENDING.name());
        try {
            productReviewMapper.insert(entity);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发重复提交：唯一索引兜底
            throw new IllegalArgumentException("该订单商品已评价");
        }

        UserEntity user = userMapper.selectById(userId);
        if (user != null) {
            user.setPoints(user.getPoints() == null ? 10 : user.getPoints() + 10);
            userMapper.updateById(user);
        }

        ReviewVO vo = toReviewVO(entity);
        vo.setReviewerName(user != null ? user.getNickname() : null);
        return vo;
    }

    @Override
    public ReviewVO getReviewDetail(Long reviewId) {
        ProductReviewEntity entity = productReviewMapper.selectById(reviewId);
        if (entity == null) {
            throw new IllegalArgumentException("评价不存在");
        }
        return toReviewVO(entity);
    }

    @Override
    public Page<ReviewVO> getUserReviews(Long userId, int page, int size) {
        Page<ProductReviewEntity> entityPage = productReviewMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<ProductReviewEntity>()
                .eq(ProductReviewEntity::getUserId, userId)
                .orderByDesc(ProductReviewEntity::getCreateTime)
        );
        return toReviewVOPage(entityPage);
    }

    @Override
    @Transactional
    public void deleteReview(Long reviewId, Long userId) {
        ProductReviewEntity entity = productReviewMapper.selectById(reviewId);
        if (entity == null) {
            throw new IllegalArgumentException("评价不存在");
        }
        if (!entity.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除此评价");
        }
        productReviewMapper.deleteById(reviewId);
    }

    private Page<ReviewVO> toReviewVOPage(Page<ProductReviewEntity> entityPage) {
        // 避免 NPE
        if (entityPage.getRecords() == null || entityPage.getRecords().isEmpty()) {
            Page<ReviewVO> emptyPage = new Page<>(entityPage.getCurrent(), entityPage.getSize());
            emptyPage.setTotal(entityPage.getTotal());
            return emptyPage;
        }

        // 批量查询用户昵称
        List<Long> userIds = entityPage.getRecords().stream()
            .map(ProductReviewEntity::getUserId).distinct().collect(Collectors.toList());
        Map<Long, String> nicknameMap = userMapper.selectBatchIds(userIds).stream()
            .collect(Collectors.toMap(UserEntity::getId, UserEntity::getNickname, (a, b) -> a));

        // 分页转换 + 富化用户昵称
        return (Page<ReviewVO>) PageUtils.convertPage(entityPage, entity -> {
            ReviewVO vo = toReviewVO(entity);
            vo.setReviewerName(nicknameMap.get(entity.getUserId()));
            return vo;
        });
    }

    private ReviewVO toReviewVO(ProductReviewEntity entity) {
        ReviewVO vo = new ReviewVO();
        vo.setId(entity.getId());
        vo.setProductId(entity.getProductId());
        vo.setUserId(entity.getUserId());
        vo.setRating(entity.getRating());
        vo.setContent(entity.getContent());
        vo.setTags(JsonUtils.parseStringArray(entity.getTags()));
        vo.setImages(JsonUtils.parseStringArray(entity.getImages()));
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

}
