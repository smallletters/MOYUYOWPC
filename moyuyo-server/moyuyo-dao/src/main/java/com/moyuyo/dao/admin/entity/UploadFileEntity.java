package com.moyuyo.dao.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图片空间图片与分组映射实体（对应 mo_upload_file）。
 * <p>
 * 图片文件本身存储在磁盘/挂载卷，本表只记录 URL 与分组的归属关系。
 */
@Data
@TableName("mo_upload_file")
public class UploadFileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 图片相对访问地址（如 /uploads/2026/10/30/xxx.png） */
    private String url;

    /** 上传场景：ADMIN 管理端素材 / AVATAR 头像 / COMMUNITY 社区 / DM 私信聊天 / USER 其他用户上传 */
    private String scene;

    /** 文件大小（字节） */
    private Long size;

    /** 所属分组ID，NULL 表示未分组 */
    private Long groupId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
