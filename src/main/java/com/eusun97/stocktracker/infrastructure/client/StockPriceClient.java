package com.eusun97.stocktracker.infrastructure.client;

import java.math.BigDecimal;

public interface StockPriceClient {

    BigDecimal fetchCurrentPrice(String ticker);

    StockInfo fetchStockInfo(String ticker);

    record StockInfo(String ticker, String name, String market) {
    }
}
