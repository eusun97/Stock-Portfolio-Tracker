package com.eusun97.stocktracker.service;

import com.eusun97.stocktracker.dto.PortfolioResponse;
import com.eusun97.stocktracker.dto.PortfolioResponse.HoldingItem;
import com.eusun97.stocktracker.dto.PortfolioResponse.Summary;
import com.eusun97.stocktracker.entity.Holding;
import com.eusun97.stocktracker.entity.Stock;
import com.eusun97.stocktracker.infrastructure.client.StockPriceClient;
import com.eusun97.stocktracker.repository.HoldingRepository;
import com.eusun97.stocktracker.repository.RealizedProfitRepository;
import com.eusun97.stocktracker.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final HoldingRepository holdingRepository;
    private final StockRepository stockRepository;
    private final RealizedProfitRepository realizedProfitRepository;
    private final StockPriceClient stockPriceClient;

    @Transactional(readOnly = true)
    public PortfolioResponse get() {
        List<Holding> holdings = holdingRepository.findAll();
        BigDecimal totalRealizedProfit = realizedProfitRepository.sumAllProfit();

        if (holdings.isEmpty()) {
            return new PortfolioResponse(
                    new Summary(BigDecimal.ZERO, BigDecimal.ZERO, totalRealizedProfit, BigDecimal.ZERO),
                    List.of());
        }

        Map<String, String> tickerToName = stockRepository
                .findAllById(holdings.stream().map(Holding::getTicker).toList())
                .stream()
                .collect(Collectors.toMap(Stock::getTicker, Stock::getName));

        List<EvaluatedHolding> evaluated = holdings.stream()
                .map(h -> evaluate(h, tickerToName.getOrDefault(h.getTicker(), h.getTicker())))
                .toList();

        BigDecimal totalAsset = evaluated.stream()
                .map(EvaluatedHolding::evalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCost = evaluated.stream()
                .map(EvaluatedHolding::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEvalProfit = totalAsset.subtract(totalCost);

        BigDecimal totalReturnRate = totalCost.signum() == 0
                ? BigDecimal.ZERO
                : totalEvalProfit.divide(totalCost, SCALE, ROUNDING);

        List<HoldingItem> items = evaluated.stream()
                .sorted(Comparator.comparing(EvaluatedHolding::evalAmount).reversed())
                .map(e -> e.toItem(totalAsset))
                .toList();

        return new PortfolioResponse(
                new Summary(totalAsset, totalEvalProfit, totalRealizedProfit, totalReturnRate),
                items);
    }

    private EvaluatedHolding evaluate(Holding h, String stockName) {
        BigDecimal currentPrice = stockPriceClient.fetchCurrentPrice(h.getTicker());
        BigDecimal evalAmount = currentPrice.multiply(h.getQuantity());
        BigDecimal cost = h.getAvgBuyPrice().multiply(h.getQuantity());
        BigDecimal evalProfit = evalAmount.subtract(cost);
        BigDecimal returnRate = cost.signum() == 0
                ? BigDecimal.ZERO
                : evalProfit.divide(cost, SCALE, ROUNDING);

        return new EvaluatedHolding(h, stockName, currentPrice, evalAmount, cost, evalProfit, returnRate);
    }

    private record EvaluatedHolding(
            Holding holding,
            String stockName,
            BigDecimal currentPrice,
            BigDecimal evalAmount,
            BigDecimal cost,
            BigDecimal evalProfit,
            BigDecimal returnRate
    ) {
        HoldingItem toItem(BigDecimal totalAsset) {
            BigDecimal weight = totalAsset.signum() == 0
                    ? BigDecimal.ZERO
                    : evalAmount.divide(totalAsset, SCALE, ROUNDING);

            return new HoldingItem(
                    holding.getTicker(),
                    stockName,
                    holding.getQuantity(),
                    holding.getAvgBuyPrice(),
                    currentPrice,
                    evalAmount,
                    evalProfit,
                    returnRate,
                    weight
            );
        }
    }
}
