package com.eusun97.stocktracker.repository;

import com.eusun97.stocktracker.entity.RealizedProfit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RealizedProfitRepository extends JpaRepository<RealizedProfit, Long> {

    void deleteAllByTicker(String ticker);
}
