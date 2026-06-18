package com.eusun97.stocktracker.controller;

import com.eusun97.stocktracker.common.ApiResponse;
import com.eusun97.stocktracker.dto.PortfolioResponse;
import com.eusun97.stocktracker.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Portfolio", description = "포트폴리오 조회 API")
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @Operation(summary = "포트폴리오 조회",
            description = "총자산/총평가손익/총실현손익/총수익률 + 보유 종목별 평가 정보")
    @GetMapping
    public ApiResponse<PortfolioResponse> get() {
        return ApiResponse.ok(portfolioService.get());
    }
}
