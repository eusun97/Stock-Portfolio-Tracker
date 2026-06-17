package com.eusun97.stocktracker.dto;

import com.eusun97.stocktracker.entity.Trade;
import com.eusun97.stocktracker.entity.TxType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TradeResponse(
        Long id,
        String ticker,
        TxType txType,
        BigDecimal quantity,
        BigDecimal price,
        LocalDate tradedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TradeResponse from(Trade trade) {
        return new TradeResponse(
                trade.getId(),
                trade.getTicker(),
                trade.getTxType(),
                trade.getQuantity(),
                trade.getPrice(),
                trade.getTradedAt(),
                trade.getCreatedAt(),
                trade.getUpdatedAt()
        );
    }
}
