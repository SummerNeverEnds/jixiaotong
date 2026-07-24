package com.jixiaotong.performance.es.repository;

import com.jixiaotong.performance.es.entity.PerfReviewEsDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface PerfReviewEsRepository extends ElasticsearchRepository<PerfReviewEsDoc, String> {
}
