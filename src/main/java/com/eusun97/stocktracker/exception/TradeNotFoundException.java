package com.eusun97.stocktracker.exception;

public class TradeNotFoundException extends BusinessException {

    public TradeNotFoundException(Long id) {
        super(ErrorCode.TRADE_NOT_FOUND, "거래내역을 찾을 수 없습니다. id=" + id);
    }
}
