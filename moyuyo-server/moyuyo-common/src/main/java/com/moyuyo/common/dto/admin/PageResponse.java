package com.moyuyo.common.dto.admin;

import lombok.Data;

import java.util.List;

@Data
public class PageResponse<T> {

  private List<T> records;
  private long total;
  private int page;
  private int size;
}
