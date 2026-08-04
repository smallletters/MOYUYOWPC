package com.moyuyo.common.dto.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddressRequest {

  @NotBlank(message = "收件人不能为空")
  @Size(max = 50, message = "收件人姓名最长50字符")
  private String receiver;

  @NotBlank(message = "手机号不能为空")
  @Pattern(regexp = "^\\+?[0-9\\-\\s]{6,20}$", message = "手机号格式不正确")
  private String phone;

  @NotBlank(message = "国家不能为空")
  private String country;

  @NotBlank(message = "省份不能为空")
  private String province;

  @NotBlank(message = "城市不能为空")
  private String city;

  private String district;

  @NotBlank(message = "详细地址不能为空")
  @Size(max = 200, message = "详细地址最长200字符")
  private String detail;

  private String zipCode;

  private String tag;

  private Boolean isDefault;
}
