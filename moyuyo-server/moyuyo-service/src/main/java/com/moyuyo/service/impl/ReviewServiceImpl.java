package com.moyuyo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyuyo.common.dto.review.CreateReviewRequest;
import com.moyuyo.common.dto.review.ReviewVO;
import com.moyuyo.common.utils.JsonUtils;
import com.moyuyo.common.utils.PageUtils;
import com.moyuyo.dao.entity.ProductReviewEntity;
import com.moyuyo.dao.entity.UserEntity;
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

    @Override
    public Page<ReviewVO> getProductReviews(Long productId, int page, int size) {
        Page<ProductReviewEntity> entityPage = productReviewMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<ProductReviewEntity>()
                .eq(ProductReviewEntity::getProductId, productId)
                .eq(ProductReviewEntity::getStatus, "APPROVED")
                .orderByDesc(ProductReviewEntity::getCreateTime)
        );
        return toReviewVOPage(entityPage);
    }

    @Override
    @Transactional
    public ReviewVO createReview(Long userId, CreateReviewRequest request) {
        ProductReviewEntity entity = new ProductReviewEntity();
        entity.setProductId(request.getProductId());
        entity.setUserId(userId);
        entity.setOrderId(request.getOrderId());
        entity.setOrderItemId(request.getOrderItemId());
        entity.setRating(request.getRating());
        entity.setContent(request.getContent());
        entity.setTags(JsonUtils.toJsonArray(request.getTags()));
        entity.setImages(JsonUtils.toJsonArray(request.getImages()));
        entity.setStatus("PENDING");

        productReviewMapper.insert(entity);

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
