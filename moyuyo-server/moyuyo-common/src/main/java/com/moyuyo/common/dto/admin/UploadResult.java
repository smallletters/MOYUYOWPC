package com.moyuyo.common.dto.admin;

import lombok.Data;

/**
 * 文件上传结果 DTO。
 * <p>
 * url 为相对路径（如 /uploads/2026/08/18/abc.png），前端按需拼接 baseURL。
 */
@Data
public class UploadResult {

  /** 相对访问路径（如 /uploads/2026/08/18/abc.png） */
  private String url;

  /** 存储文件名（UUID + 扩展名） */
  private String filename;

  /** 用户上传的原始文件名 */
  private String originalName;

  /** 文件大小（字节） */
  private Long size;

  /** Content-Type（如 image/png） */
  private String contentType;
}