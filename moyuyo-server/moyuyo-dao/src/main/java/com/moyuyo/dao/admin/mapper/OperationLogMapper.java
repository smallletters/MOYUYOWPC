package com.moyuyo.dao.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyuyo.dao.admin.entity.OperationLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作审计日志 Mapper
 * <p>
 * 由 OperationLogPersister 异步批量写入 mo_operation_log 表。
 * 写入路径独立于业务事务，避免审计写入阻塞主业务或被业务回滚误删。
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogEntity> {
}