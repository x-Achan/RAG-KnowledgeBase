package com.ccy.ragkb.service.loader;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ParseResult {

    /** 解析是否成功 */
    private boolean success;

    /** 错误信息（success=false 时有值） */
    private String errorMsg;

    /** 解析出的页面列表（PDF 按页，其他格式整体算一页） */
    private List<PageContent> pages;

    /**
     * PDF：文档总页数
     * DOCX/MD：总节数（按照标题分割）
     * txt:一页
     * */
    private int totalPages;

    /** 文档标题（如果能识别） */
    private String title;

    @Data
    @Builder
    public static class PageContent {
        /**
         * PDF：物理页码（1-based）
         * DOCX：节号（以标题分割，每一章算一节）
         * txt：一页
         * */
        private int pageNum;
        /** 该页的纯文本内容 */
        private String text;
        /** 该页识别到的章节标题（可能为空） */
        private String sectionTitle;
    }

    public static ParseResult failure(String errorMsg) {
        return ParseResult.builder()
                .success(false)
                .errorMsg(errorMsg)
                .pages(List.of())
                .build();
    }

    /** 获取所有页的合并文本 */
    public String getFullText() {
        if (pages == null) return "";
        return pages.stream()
                .map(PageContent::getText)
                .filter(t -> t != null && !t.isBlank())
                .reduce("", (a, b) -> a + "\n\n" + b)
                .strip();
    }
}