package com.eusun97.stocktracker.repository;

import com.eusun97.stocktracker.entity.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    Page<Trade> findAllByDeletedAtIsNull(Pageable pageable);

    Optional<Trade> findByIdAndDeletedAtIsNull(Long id);

    List<Trade> findAllByTickerAndDeletedAtIsNullOrderByTradedAtAscIdAsc(String ticker);
}
