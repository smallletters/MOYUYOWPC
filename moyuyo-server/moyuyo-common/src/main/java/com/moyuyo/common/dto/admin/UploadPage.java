package com.moyuyo.common.dto.admin;

import lombok.Data;

import java.util.List;

/**
 * 图片空间分页结果。
 */
@Data
public class UploadPage {

  /** 当前页图片列表 */
  private List<UploadedFile> records;

  /** 图片总数 */
  private long total;

  /** 当前页码（从 1 开始） */
  private int page;

  /** 每页数量 */
  private int size;

  /** 全部图片总占用字节数 */
  private long totalSize;
}
