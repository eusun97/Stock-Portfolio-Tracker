package com.eusun97.stocktracker.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeUpdateRequest(
        @NotNull
        @DecimalMin(value = "0.0001", message = "수량은 0보다 커야 합니다")
        BigDecimal quantity,

        @NotNull
        @DecimalMin(value = "0", message = "가격은 0 이상이어야 합니다")
        BigDecimal price,

        @NotNull
        LocalDate tradedAt
) {
}
