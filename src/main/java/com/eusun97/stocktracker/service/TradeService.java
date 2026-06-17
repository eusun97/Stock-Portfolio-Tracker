package com.eusun97.stocktracker.service;

import com.eusun97.stocktracker.dto.TradeRegisterRequest;
import com.eusun97.stocktracker.dto.TradeResponse;
import com.eusun97.stocktracker.dto.TradeUpdateRequest;
import com.eusun97.stocktracker.entity.Holding;
import com.eusun97.stocktracker.entity.RealizedProfit;
import com.eusun97.stocktracker.entity.Trade;
import com.eusun97.stocktracker.entity.TxType;
import com.eusun97.stocktracker.repository.HoldingRepository;
import com.eusun97.stocktracker.repository.RealizedProfitRepository;
import com.eusun97.stocktracker.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeService {

    private final TradeRepository tradeRepository;
    private final HoldingRepository holdingRepository;
    private final RealizedProfitRepository realizedProfitRepository;

    @Transactional
    public TradeResponse register(TradeRegisterRequest request) {
        Trade trade = tradeRepository.save(Trade.builder()
                .ticker(request.ticker())
                .txType(request.txType())
                .quantity(request.quantity())
                .price(request.price())
                .tradedAt(request.tradedAt())
                .build());

        if (request.txType() == TxType.BUY) {
            applyBuy(request);
        } else {
            applySell(trade);
        }

        log.info("trade registered id={} ticker={} type={} qty={} price={}",
                trade.getId(), trade.getTicker(), trade.getTxType(),
                trade.getQuantity(), trade.getPrice());

        return TradeResponse.from(trade);
    }

    @Transactional(readOnly = true)
    public Page<TradeResponse> findAll(Pageable pageable) {
        return tradeRepository.findAllByDeletedAtIsNull(pageable)
                .map(TradeResponse::from);
    }

    @Transactional(readOnly = true)
    public TradeResponse findById(Long id) {
        Trade trade = tradeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "거래내역을 찾을 수 없습니다. id=" + id));
        return TradeResponse.from(trade);
    }

    @Transactional
    public TradeResponse update(Long id, TradeUpdateRequest request) {
        Trade trade = tradeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "거래내역을 찾을 수 없습니다. id=" + id));

        trade.update(request.quantity(), request.price(), request.tradedAt());
        recalculateHolding(trade.getTicker());

        log.info("trade updated id={} ticker={} qty={} price={}",
                trade.getId(), trade.getTicker(), trade.getQuantity(), trade.getPrice());

        return TradeResponse.from(trade);
    }

    @Transactional
    public void delete(Long id) {
        Trade trade = tradeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "거래내역을 찾을 수 없습니다. id=" + id));

        trade.softDelete();
        recalculateHolding(trade.getTicker());

        log.info("trade deleted id={} ticker={}", trade.getId(), trade.getTicker());
    }

    private void applyBuy(TradeRegisterRequest request) {
        Holding holding = holdingRepository.findByTickerForUpdate(request.ticker())
                .orElse(null);

        if (holding == null) {
            holdingRepository.save(Holding.builder()
                    .ticker(request.ticker())
                    .quantity(request.quantity())
                    .avgBuyPrice(request.price())
                    .build());
            return;
        }

        holding.applyBuy(request.quantity(), request.price());
    }

    private void applySell(Trade trade) {
        Holding holding = holdingRepository.findByTickerForUpdate(trade.getTicker())
                .orElseThrow(() -> new IllegalStateException(
                        "보유하지 않은 종목은 매도할 수 없습니다. ticker=" + trade.getTicker()));

        if (holding.getQuantity().compareTo(trade.getQuantity()) < 0) {
            throw new IllegalStateException(String.format(
                    "보유 수량이 부족합니다. ticker=%s, 보유=%s, 매도요청=%s",
                    trade.getTicker(), holding.getQuantity(), trade.getQuantity()));
        }

        BigDecimal avgBuyPrice = holding.getAvgBuyPrice();
        realizedProfitRepository.save(RealizedProfit.of(
                trade.getId(),
                trade.getTicker(),
                trade.getPrice(),
                avgBuyPrice,
                trade.getQuantity(),
                trade.getTradedAt()
        ));

        holding.applySell(trade.getQuantity());
        if (holding.isEmpty()) {
            holdingRepository.delete(holding);
        }
    }

    /**
     * 종목 단위 holding + realized_profit 재계산.
     * 거래 수정/삭제 시 정합성 보장을 위해 살아있는 거래내역을 traded_at 순으로 다시 리플레이.
     */
    private void recalculateHolding(String ticker) {
        holdingRepository.findByTickerForUpdate(ticker).ifPresent(holdingRepository::delete);
        realizedProfitRepository.deleteAllByTicker(ticker);
        holdingRepository.flush();
        realizedProfitRepository.flush();

        List<Trade> trades = tradeRepository
                .findAllByTickerAndDeletedAtIsNullOrderByTradedAtAscIdAsc(ticker);

        for (Trade t : trades) {
            if (t.getTxType() == TxType.BUY) {
                replayBuy(t);
            } else {
                replaySell(t);
            }
        }
    }

    private void replayBuy(Trade trade) {
        Holding holding = holdingRepository.findByTickerForUpdate(trade.getTicker())
                .orElse(null);
        if (holding == null) {
            holdingRepository.save(Holding.builder()
                    .ticker(trade.getTicker())
                    .quantity(trade.getQuantity())
                    .avgBuyPrice(trade.getPrice())
                    .build());
            return;
        }
        holding.applyBuy(trade.getQuantity(), trade.getPrice());
    }

    private void replaySell(Trade trade) {
        Holding holding = holdingRepository.findByTickerForUpdate(trade.getTicker())
                .orElseThrow(() -> new IllegalStateException(
                        "재계산 중 보유 없음: 거래 데이터 정합성 오류. ticker=" + trade.getTicker()));

        if (holding.getQuantity().compareTo(trade.getQuantity()) < 0) {
            throw new IllegalStateException(String.format(
                    "재계산 중 보유 부족: 거래 데이터 정합성 오류. ticker=%s, 보유=%s, 매도=%s",
                    trade.getTicker(), holding.getQuantity(), trade.getQuantity()));
        }

        realizedProfitRepository.save(RealizedProfit.of(
                trade.getId(),
                trade.getTicker(),
                trade.getPrice(),
                holding.getAvgBuyPrice(),
                trade.getQuantity(),
                trade.getTradedAt()
        ));

        holding.applySell(trade.getQuantity());
        if (holding.isEmpty()) {
            holdingRepository.delete(holding);
            holdingRepository.flush();
        }
    }
}
