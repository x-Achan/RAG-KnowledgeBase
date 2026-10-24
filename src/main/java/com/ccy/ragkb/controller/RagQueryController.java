package com.ccy.ragkb.controller;

import com.ccy.ragkb.dto.ApiResponse;
import com.ccy.ragkb.dto.RagQueryRequest;
import com.ccy.ragkb.service.RagQueryService;
import com.ccy.ragkb.service.RagQueryServiceV2;
import com.ccy.ragkb.service.RagQueryServiceV3;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagQueryController {

    private final RagQueryService ragQueryService;
    private final RagQueryServiceV2 ragQueryService2;
    private final RagQueryServiceV3 ragQueryService3;

    @PostMapping("/query")
    public ApiResponse<String> query(@RequestBody RagQueryRequest req) {
        return ApiResponse.ok(ragQueryService.query(req.getQuestion(), req.getKbIds()));
    }

    @PostMapping("/query2")
    public ApiResponse<String> query2(@RequestBody RagQueryRequest req) {
        return ApiResponse.ok(ragQueryService2.query(req.getQuestion(), req.getKbIds()));
    }

    @PostMapping("/query3")
    public ApiResponse<String> query3(@RequestBody RagQueryRequest req) {
        return ApiResponse.ok(ragQueryService3.query(req.getQuestion(), req.getKbIds()));
    }
}