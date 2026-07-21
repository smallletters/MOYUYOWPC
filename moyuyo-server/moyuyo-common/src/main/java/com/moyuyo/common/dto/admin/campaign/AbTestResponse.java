package com.moyuyo.common.dto.admin.campaign;

import lombok.Data;

@Data
public class AbTestResponse {

  private Long id;
  private String name;
  private String variantA;
  private String variantB;
  private String status;
  private String winner;
}
