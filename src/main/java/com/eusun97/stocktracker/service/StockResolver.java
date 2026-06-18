package com.eusun97.stocktracker.service;

import com.eusun97.stocktracker.entity.Stock;
import com.eusun97.stocktracker.infrastructure.client.StockPriceClient;
import com.eusun97.stocktracker.infrastructure.client.StockPriceClient.StockInfo;
import com.eusun97.stocktracker.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockResolver {

    private final StockRepository stockRepository;
    private final StockPriceClient stockPriceClient;

    @Transactional(propagation = Propagation.MANDATORY)
    public Stock resolveOrFetch(String ticker) {
        return stockRepository.findByTicker(ticker)
                .orElseGet(() -> fetchAndSave(ticker));
    }

    private Stock fetchAndSave(String ticker) {
        StockInfo info = stockPriceClient.fetchStockInfo(ticker);
        Stock stock = Stock.builder()
                .ticker(info.ticker())
                .name(info.name())
                .market(info.market())
                .build();

        try {
            return stockRepository.save(stock);
        } catch (DataIntegrityViolationException e) {
            log.info("stock concurrent insert detected, re-fetch ticker={}", ticker);
            return stockRepository.findByTicker(ticker)
                    .orElseThrow(() -> e);
        }
    }
}
