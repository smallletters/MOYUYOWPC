package com.moyuyo.service.es.repository;

import com.moyuyo.service.es.entity.ProductIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductSearchRepository extends ElasticsearchRepository<ProductIndex, Long> {

    List<ProductIndex> findByNameContaining(String name);

    List<ProductIndex> findByCategoryId(Long categoryId);

    List<ProductIndex> findByBrandId(Long brandId);

    List<ProductIndex> findByOnSaleTrue();
}
