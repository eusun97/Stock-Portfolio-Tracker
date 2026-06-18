package com.eusun97.stocktracker.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioResponse(
        Summary summary,
        List<HoldingItem> holdings
) {
    public record Summary(
            BigDecimal totalAsset,
            BigDecimal totalEvalProfit,
            BigDecimal totalRealizedProfit,
            BigDecimal totalReturnRate
    ) {
    }

    public record HoldingItem(
            String ticker,
            String stockName,
            BigDecimal quantity,
            BigDecimal avgPrice,
            BigDecimal currentPrice,
            BigDecimal evalAmount,
            BigDecimal evalProfit,
            BigDecimal returnRate,
            BigDecimal weight
    ) {
    }
}
