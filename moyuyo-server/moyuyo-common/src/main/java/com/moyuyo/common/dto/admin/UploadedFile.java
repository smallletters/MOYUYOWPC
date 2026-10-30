package com.moyuyo.common.dto.admin;

import lombok.Data;

/**
 * 已上传图片（图片空间列表项）。
 */
@Data
public class UploadedFile {

  /** 相对访问路径（如 /uploads/2026/08/18/abc.png） */
  private String url;

  /** 存储文件名（UUID + 扩展名） */
  private String filename;

  /** 文件大小（字节） */
  private long size;

  /** 最后修改时间（毫秒时间戳） */
  private long lastModified;

  /** 所属分组ID，null 表示未分组 */
  private Long groupId;
}
