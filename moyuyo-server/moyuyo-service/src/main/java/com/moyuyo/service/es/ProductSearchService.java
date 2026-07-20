package com.moyuyo.service.es;

import com.moyuyo.service.es.entity.ProductIndex;
import com.moyuyo.service.es.repository.ProductSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ProductSearchService {

    @Autowired(required = false)
    private ProductSearchRepository searchRepository;

    @Autowired(required = false)
    private ElasticsearchTemplate elasticsearchTemplate;

    public void indexProduct(ProductIndex product) {
        if (searchRepository == null) {
            log.warn("ES 未配置，跳过索引: id={}", product.getId());
            return;
        }
        searchRepository.save(product);
        log.debug("商品索引已更新: id={}", product.getId());
    }

    public void deleteProductIndex(Long id) {
        if (searchRepository == null) {
            log.warn("ES 未配置，跳过删除索引: id={}", id);
            return;
        }
        searchRepository.deleteById(id);
        log.debug("商品索引已删除: id={}", id);
    }

    public ProductIndex findById(Long id) {
        if (searchRepository == null) {
            return null;
        }
        return searchRepository.findById(id).orElse(null);
    }

    public List<ProductIndex> search(String keyword, int page, int size) {
        if (elasticsearchTemplate == null) {
            log.warn("ES 未配置，搜索返回空结果");
            return Collections.emptyList();
        }
        Criteria criteria = new Criteria();
        if (keyword != null && !keyword.isEmpty()) {
            criteria = criteria.or("name").contains(keyword)
                    .or("detail").contains(keyword)
                    .or("brandName").contains(keyword)
                    .or("categoryName").contains(keyword);
        }
        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(PageRequest.of(page, size));
        return elasticsearchTemplate.search(query, ProductIndex.class)
                .stream()
                .map(org.springframework.data.elasticsearch.core.SearchHit::getContent)
                .toList();
    }

    public List<ProductIndex> findByCategory(Long categoryId) {
        if (searchRepository == null) {
            return Collections.emptyList();
        }
        return searchRepository.findByCategoryId(categoryId);
    }

    public void bulkIndex(List<ProductIndex> products) {
        if (searchRepository == null) {
            log.warn("ES 未配置，跳过批量索引: count={}", products.size());
            return;
        }
        searchRepository.saveAll(products);
        log.info("批量索引完成: count={}", products.size());
    }
}
