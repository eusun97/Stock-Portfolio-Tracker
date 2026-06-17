package com.eusun97.stocktracker.controller;

import com.eusun97.stocktracker.common.ApiResponse;
import com.eusun97.stocktracker.common.PageResponse;
import com.eusun97.stocktracker.dto.TradeRegisterRequest;
import com.eusun97.stocktracker.dto.TradeResponse;
import com.eusun97.stocktracker.dto.TradeUpdateRequest;
import com.eusun97.stocktracker.service.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

import static java.util.function.Function.identity;

@Tag(name = "Trade", description = "거래내역 관리 API")
@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    @Operation(summary = "거래내역 등록",
            description = "매수(BUY) 또는 매도(SELL) 거래 한 건을 등록한다. " +
                    "단일 트랜잭션 안에서 trade INSERT, holding upsert, (매도 시) realized_profit INSERT 처리.")
    @PostMapping
    public ResponseEntity<ApiResponse<TradeResponse>> register(
            @Valid @RequestBody TradeRegisterRequest request) {

        TradeResponse response = tradeService.register(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(ApiResponse.ok(response));
    }

    @Operation(summary = "거래내역 목록 조회",
            description = "거래내역을 페이징하여 조회한다. 기본 정렬은 traded_at, id 내림차순.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TradeResponse>>> findAll(
            @PageableDefault(size = 10, sort = {"tradedAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {

        PageResponse<TradeResponse> page = PageResponse.of(tradeService.findAll(pageable), identity());
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @Operation(summary = "거래내역 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TradeResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(tradeService.findById(id)));
    }

    @Operation(summary = "거래내역 수정",
            description = "수량/가격/거래일을 수정한다. 종목과 거래종류는 수정할 수 없다. " +
                    "수정 시 해당 종목의 holding/realized_profit 을 처음부터 재계산한다.")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<TradeResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody TradeUpdateRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(tradeService.update(id, request)));
    }

    @Operation(summary = "거래내역 삭제 (soft delete)",
            description = "거래내역을 soft delete 처리하고 해당 종목의 holding/realized_profit 을 재계산한다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tradeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
