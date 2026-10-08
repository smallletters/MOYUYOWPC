package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 发货策略与配送方式的多对多绑定（mo_shipping_strategy_method）。
 * 表达"某个 strategy 允许使用哪几种配送方式"。
 * 复合主键：(strategy_id, method_id)，不引入额外 surrogate id。
 */
@Data
@TableName("mo_shipping_strategy_method")
public class ShippingStrategyMethodEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联 mo_shipping_strategy.id */
    private Long strategyId;

    /** 关联 mo_shipping_method.id */
    private Long methodId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShippingStrategyMethodEntity that)) return false;
        return Objects.equals(strategyId, that.strategyId)
                && Objects.equals(methodId, that.methodId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(strategyId, methodId);
    }
}
