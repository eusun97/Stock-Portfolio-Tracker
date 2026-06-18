package com.eusun97.stocktracker.repository;

import com.eusun97.stocktracker.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, String> {

    Optional<Stock> findByTicker(String ticker);
}
