package com.moyuyo.common.dto.review;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReviewVO {

    private Long id;

    private Long productId;

    private Long orderId;

    private Long orderItemId;

    private Long userId;

    private Integer rating;

    private String content;

    private List<String> tags;

    private List<String> images;

    private String status;

    private String reviewerName;

    private LocalDateTime createTime;
}
