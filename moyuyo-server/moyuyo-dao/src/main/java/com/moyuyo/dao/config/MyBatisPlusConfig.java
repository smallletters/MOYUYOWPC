package com.moyuyo.dao.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan({"com.moyuyo.dao.mapper", "com.moyuyo.dao.admin.mapper"})
public class MyBatisPlusConfig {

  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
    // 单页硬上限：Controller 若漏做 PageParamGuard，攻击者传 size=100000 仍会被截断到 200，
    // 避免一次性查 10W 行导致的数据库 IO 风暴与 JSON 序列化 OOM
    pagination.setMaxLimit(200L);
    interceptor.addInnerInterceptor(pagination);
    return interceptor;
  }
}
