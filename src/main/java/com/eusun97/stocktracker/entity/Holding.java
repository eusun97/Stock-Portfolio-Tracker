package com.eusun97.stocktracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "holding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holding {

    @Id
    @Column(name = "ticker", length = 10)
    private String ticker;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "avg_buy_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal avgBuyPrice;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Holding(String ticker, BigDecimal quantity, BigDecimal avgBuyPrice) {
        this.ticker = ticker;
        this.quantity = quantity;
        this.avgBuyPrice = avgBuyPrice;
    }

    public void applyBuy(BigDecimal addQuantity, BigDecimal buyPrice) {
        BigDecimal totalCost = this.avgBuyPrice.multiply(this.quantity)
                .add(buyPrice.multiply(addQuantity));
        BigDecimal newQuantity = this.quantity.add(addQuantity);
        this.avgBuyPrice = totalCost.divide(newQuantity, 4, java.math.RoundingMode.HALF_UP);
        this.quantity = newQuantity;
    }

    public void applySell(BigDecimal sellQuantity) {
        this.quantity = this.quantity.subtract(sellQuantity);
    }

    public boolean isEmpty() {
        return this.quantity.compareTo(BigDecimal.ZERO) <= 0;
    }
}
