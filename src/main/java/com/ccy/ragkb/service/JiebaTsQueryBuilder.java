package com.ccy.ragkb.service;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 将用户查询转换为 PostgreSQL tsquery 格式（基于 jieba 中文分词）。
 *
 * 使用 jieba 做中文分词，再按停用词/长度过滤，最后用 & 连接成 AND 查询。
 * 例如："API 限流策略是什么" → "API & 限流 & 策略"
 */
@Component
@Slf4j
public class JiebaTsQueryBuilder {

    /**
     * JiebaSegmenter 内部会加载词典（约几十 MB），初始化较慢，
     * 因此做成单例复用，避免每次 build 都 new 一个。
     * 它是线程安全的，可以放心作为成员变量。
     */
    private static final JiebaSegmenter SEGMENTER = new JiebaSegmenter();

    // 停用词（这些词在全文检索中无意义）
    private static final List<String> STOP_WORDS = List.of(
            "的", "了", "是", "在", "有", "和", "与", "或", "这", "那",
            "什么", "怎么", "如何", "为什么", "哪些", "怎样", "请问",
            "一个", "一种", "可以", "我们", "你们", "他们", "以及",
            "a", "an", "the", "is", "are", "what", "how"
    );

    /**
     * 将问题转为 tsquery 格式。
     * 例如："API 限流策略是什么" → "API & 限流 & 策略"
     */
    public String build(String query) {
        if (query == null || query.isBlank()) return null;

        // jieba 分词：SEARCH 模式会把长词再切出短词，召回更好
        // 例："限流策略" → ["限流", "策略", "限流策略"]
        List<SegToken> tokens = SEGMENTER.process(query, JiebaSegmenter.SegMode.SEARCH);

        List<String> keywords = tokens.stream()
                .map(t -> t.word.strip())
                .filter(t -> !t.isBlank())
                .filter(t -> t.length() >= 2)              // 过滤单字符
                .filter(t -> !t.matches(".*[&|!():'<>].*")) // 过滤 tsquery 特殊字符
                .filter(t -> !STOP_WORDS.contains(t.toLowerCase()))
                .distinct()                                // 去重，避免 "限流 & 限流"
                .collect(Collectors.toList());

        if (keywords.isEmpty()) {
            // 降级：取整个查询的前20字符
            keywords = List.of(query.substring(0, Math.min(20, query.length())));
        }

        // 用 & 连接（AND 查询），至少含所有关键词
        String tsQuery = String.join(" & ", keywords);
        log.debug("[TsQuery] query='{}' → tsQuery='{}'", query, tsQuery);
        return tsQuery;
    }

    public static void main(String[] args) {
        JiebaTsQueryBuilder builder = new JiebaTsQueryBuilder();

        // 注意：第一次 new JiebaSegmenter 会加载词典，启动稍慢（1~2秒），之后很快
        String[] tests = {
                "API 限流策略是什么?",
                "如何配置 Redis 连接池",
                "用户登录失败怎么排查"
        };
        for (String q : tests) {
            System.out.println(q + "  →  " + builder.build(q));
        }
    }
}