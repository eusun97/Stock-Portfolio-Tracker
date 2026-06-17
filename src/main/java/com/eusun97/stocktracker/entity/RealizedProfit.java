package com.eusun97.stocktracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "realized_profit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RealizedProfit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private Long transactionId;

    @Column(name = "ticker", nullable = false, length = 10)
    private String ticker;

    @Column(name = "sell_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal sellPrice;

    @Column(name = "avg_buy_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal avgBuyPrice;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "profit", nullable = false, precision = 19, scale = 4)
    private BigDecimal profit;

    @Column(name = "realized_at", nullable = false)
    private LocalDate realizedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private RealizedProfit(Long transactionId, String ticker, BigDecimal sellPrice,
                           BigDecimal avgBuyPrice, BigDecimal quantity, BigDecimal profit,
                           LocalDate realizedAt) {
        this.transactionId = transactionId;
        this.ticker = ticker;
        this.sellPrice = sellPrice;
        this.avgBuyPrice = avgBuyPrice;
        this.quantity = quantity;
        this.profit = profit;
        this.realizedAt = realizedAt;
    }

    public static RealizedProfit of(Long tradeId, String ticker, BigDecimal sellPrice,
                                    BigDecimal avgBuyPrice, BigDecimal quantity, LocalDate realizedAt) {
        BigDecimal profit = sellPrice.subtract(avgBuyPrice).multiply(quantity);
        return RealizedProfit.builder()
                .transactionId(tradeId)
                .ticker(ticker)
                .sellPrice(sellPrice)
                .avgBuyPrice(avgBuyPrice)
                .quantity(quantity)
                .profit(profit)
                .realizedAt(realizedAt)
                .build();
    }
}
