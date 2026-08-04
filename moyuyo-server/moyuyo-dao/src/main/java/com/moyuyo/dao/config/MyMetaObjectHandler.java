package com.moyuyo.dao.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 元数据自动填充处理器
 * 用于自动填充 @TableField(fill = FieldFill.INSERT) 和 INSERT_UPDATE 标注的字段
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

  @Override
  public void insertFill(MetaObject metaObject) {
    LocalDateTime now = LocalDateTime.now();
    // 填充创建时间（兼容 createTime/create_time/createdAt/created_at 四种命名）
    this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
    this.strictInsertFill(metaObject, "create_time", LocalDateTime.class, now);
    this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
    this.strictInsertFill(metaObject, "created_at", LocalDateTime.class, now);
    // 填充更新时间
    this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    this.strictInsertFill(metaObject, "update_time", LocalDateTime.class, now);
    this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    this.strictInsertFill(metaObject, "updated_at", LocalDateTime.class, now);
  }

  @Override
  public void updateFill(MetaObject metaObject) {
    LocalDateTime now = LocalDateTime.now();
    this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
    this.strictUpdateFill(metaObject, "update_time", LocalDateTime.class, now);
    this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, now);
    this.strictUpdateFill(metaObject, "updated_at", LocalDateTime.class, now);
  }
}
