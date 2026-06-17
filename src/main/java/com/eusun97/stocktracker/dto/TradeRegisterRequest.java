package com.eusun97.stocktracker.dto;

import com.eusun97.stocktracker.entity.TxType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeRegisterRequest(
        @NotNull
        @Pattern(regexp = "\\d{6}", message = "ticker 는 6자리 숫자여야 합니다")
        String ticker,

        @NotNull
        TxType txType,

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
