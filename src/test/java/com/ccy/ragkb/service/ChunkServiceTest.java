package com.ccy.ragkb.service;

import com.ccy.ragkb.service.loader.ParseResult;
import com.ccy.ragkb.service.splitter.ChunkResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ChunkServiceTest {

    @Autowired
    private ChunkService chunkService;
    @Autowired
    private DocumentLoaderService loaderService;

    @Autowired
    private  EmbeddingService embeddingService;

    @Test
    void chunksNotTooLargeOrTooSmall() {
        String longText = "这是一段测试文本。".repeat(200); // 约 1800 字符
        ParseResult result = ParseResult.builder()
                .success(true)
                .pages(List.of(ParseResult.PageContent.builder()
                        .pageNum(1)
                        .text(longText)
                        .build()))
                .totalPages(1)
                .build();

        List<ChunkResult> chunks = chunkService.chunk(result);
        System.out.println(chunks);

        int count = 0;
        for (ChunkResult chunk : chunks) {
            // 每块不应超过 chunkSize（findGoodBreakPoint 只向前回退断点，不会超出上限）
            assertThat(chunk.getContent().length()).isLessThanOrEqualTo(512);
            // 每块至少 20 字符（ChunkService 已过滤更短的碎片）
            assertThat(chunk.getContent().length()).isGreaterThanOrEqualTo(20);
            System.out.printf("第 {i} 个chunk:" + chunk,count++);
        }

        // 验证相邻块有重叠
        if (chunks.size() >= 2) {
            String end0 = chunks.get(0).getContent();
            String start1 = chunks.get(1).getContent();
            // 第一块末尾的内容应该出现在第二块开头附近（重叠）
            String overlapPart = end0.substring(Math.max(0, end0.length() - 64));
            assertThat(start1).contains(overlapPart.substring(0, Math.min(30, overlapPart.length())));
        }
    }

    @Test
    void testchunk() throws IOException {
        ClassPathResource resource = new ClassPathResource("test-docs/pdf.pdf");
        try (InputStream is = resource.getInputStream()) {
            ParseResult result = loaderService.load(is, "pdf.pdf");
            System.out.println("pdf的页数：" + result.getPages().size());
            List<ChunkResult> chunks = chunkService.chunk(result);
            System.out.println("chunk的数量：" + chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                System.out.printf("第" + i + "个chunk：" + chunks.get(i),i);
            }
        }
    }

    @Test
    void testEmbeding(){
        String str = "这是一个";
        float[] embed = embeddingService.embed(str);
        System.out.println("向量维度：" + embed.length);
        System.out.println(Arrays.toString(Arrays.copyOf(embed, 5)));
        assertThat(embed).hasSize(1024);
    }

}