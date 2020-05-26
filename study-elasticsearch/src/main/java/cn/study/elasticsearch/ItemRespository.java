package cn.study.elasticsearch;

import cn.study.elasticsearch.pojo.Item;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ItemRespository extends ElasticsearchRepository<Item,Long> {
    List<Item> findByTitle(String title);
}
