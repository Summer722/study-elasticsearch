package cn.study.elasticsearch.test;

import ch.qos.logback.core.net.SyslogOutputStream;
import cn.study.elasticsearch.ItemRespository;
import cn.study.elasticsearch.StudyElasticsearchApplication;
import cn.study.elasticsearch.pojo.Item;
import org.elasticsearch.cluster.metadata.IndexTemplateMetaData;
import org.elasticsearch.index.mapper.SourceToParse;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.aggregations.metrics.avg.InternalAvg;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SourceFilter;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = StudyElasticsearchApplication.class)
public class ElasticsearchTest {

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    @Autowired
    private ItemRespository itemRespository;

    @Test
    public void testIndex(){
        this.elasticsearchTemplate.createIndex(Item.class);
        this.elasticsearchTemplate.putMapping(Item.class);
    }

    @Test
    public void testCreate(){
        Item item = new Item(1L, "小米手机7", " 手机",
                "小米", 3499.00, "http://image.leyou.com/13123.jpg");
        this.itemRespository.save(item);

        List<Item> list = new ArrayList<>();
        list.add(new Item(2L, "坚果手机R1", " 手机", "锤子", 3699.00, "http://image.leyou.com/123.jpg"));
        list.add(new Item(3L, "华为META10", " 手机", "华为", 4499.00, "http://image.leyou.com/3.jpg"));
        // 接收对象集合，实现批量新增
        this.itemRespository.saveAll(list);

    }

    @Test
    public void testFind(){
        Iterable<Item> items = this.itemRespository.findAll(Sort.by("price").descending());
        items.forEach(System.out::println);
    }

    @Test
    public void indexList() {
        List<Item> list = new ArrayList<>();
        list.add(new Item(1L, "小米手机7", "手机", "小米", 3299.00, "http://image.leyou.com/13123.jpg"));
        list.add(new Item(2L, "坚果手机R1", "手机", "锤子", 3699.00, "http://image.leyou.com/13123.jpg"));
        list.add(new Item(3L, "华为META10", "手机", "华为", 4499.00, "http://image.leyou.com/13123.jpg"));
        list.add(new Item(4L, "小米Mix2S", "手机", "小米", 4299.00, "http://image.leyou.com/13123.jpg"));
        list.add(new Item(5L, "荣耀V10", "手机", "华为", 2799.00, "http://image.leyou.com/13123.jpg"));
        // 接收对象集合，实现批量新增
        itemRespository.saveAll(list);
    }

    @Test
    public void testSearch(){
        //通过查询构建器工具查询条件
        MatchQueryBuilder queryBuilder = QueryBuilders.matchQuery("title", "手机");
        //执行查询，获取结果集
        Iterable<Item> items = this.itemRespository.search(queryBuilder);
        items.forEach(System.out::println);

    }

    @Test
    public void testNative(){
        //构建自定义查询构建器
        NativeSearchQueryBuilder queryBuilder=new NativeSearchQueryBuilder();
        //添加基本的查询条件
        queryBuilder.withQuery(QueryBuilders.matchQuery("category", "手机"));
        ///添加分页条件，页码从0开始
        //queryBuilder.withPageable(PageRequest.of(1,2));
        queryBuilder.withSort(SortBuilders.fieldSort("price").order(SortOrder.DESC));
        //执行查询获取分页结果集
        Page<Item> itemPage = this.itemRespository.search(queryBuilder.build());
        System.out.println(itemPage.getTotalPages());
        System.out.println(itemPage.getTotalElements());
        itemPage.forEach(System.out::println);
    }

    @Test
    public void testAggs(){
        //初始化自定义查询构建器
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
        //添加聚合
        queryBuilder.addAggregation(AggregationBuilders.terms("brandAgg").field("brand"));
        //添加结果集过滤，不包括任何字段
        queryBuilder.withSourceFilter(new FetchSourceFilter(new String[]{},null));
        //执行聚合查询
        AggregatedPage<Item> itemPage = (AggregatedPage<Item>)(this.itemRespository.search(queryBuilder.build()));
        //解析聚合结果集,根据聚合类型以及字段类型要进行强转，brand-是字符川类型的，聚合类型是词条类型，brandAgg-通过聚合名称获取聚合对象
        StringTerms brandAgg=(StringTerms)itemPage.getAggregation("brandAgg");
        //获取桶的集合
        List<StringTerms.Bucket> buckets = brandAgg.getBuckets();
        buckets.forEach(bucket -> {
            System.out.println(bucket.getKeyAsString());
            System.out.println(bucket.getDocCount());
        });
    }

    @Test
    public void testSubAgg(){
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
        // 不查询任何结果
        queryBuilder.withSourceFilter(new FetchSourceFilter(new String[]{""}, null));
        // 1、添加一个新的聚合，聚合类型为terms，聚合名称为brands，聚合字段为brand
        queryBuilder.addAggregation(
                AggregationBuilders.terms("brands").field("brand")
                        .subAggregation(AggregationBuilders.avg("priceAvg").field("price")) // 在品牌聚合桶内进行嵌套聚合，求平均值
        );
        // 2、查询,需要把结果强转为AggregatedPage类型
        AggregatedPage<Item> aggPage = (AggregatedPage<Item>) this.itemRespository.search(queryBuilder.build());
        // 3、解析
        // 3.1、从结果中取出名为brands的那个聚合，
        // 因为是利用String类型字段来进行的term聚合，所以结果要强转为StringTerm类型
        StringTerms agg = (StringTerms) aggPage.getAggregation("brands");
        // 3.2、获取桶
        List<StringTerms.Bucket> buckets = agg.getBuckets();
        // 3.3、遍历
        for (StringTerms.Bucket bucket : buckets) {
            // 3.4、获取桶中的key，即品牌名称  3.5、获取桶中的文档数量
            System.out.println(bucket.getKeyAsString() + "，共" + bucket.getDocCount() + "台");

            // 3.6.获取子聚合结果：
            InternalAvg avg = (InternalAvg) bucket.getAggregations().asMap().get("priceAvg");
            System.out.println("平均售价：" + avg.getValue());
        }

    }
}
