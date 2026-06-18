package com.eusun97.stocktracker.repository;

import com.eusun97.stocktracker.entity.RealizedProfit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;

public interface RealizedProfitRepository extends JpaRepository<RealizedProfit, Long> {

    void deleteAllByTicker(String ticker);

    @Query("SELECT COALESCE(SUM(r.profit), 0) FROM RealizedProfit r")
    BigDecimal sumAllProfit();
}
