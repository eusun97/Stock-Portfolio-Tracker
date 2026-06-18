package com.eusun97.stocktracker.exception;

public class StockNotFoundException extends BusinessException {

    public StockNotFoundException(String ticker) {
        super(ErrorCode.STOCK_NOT_FOUND, "종목 정보를 찾을 수 없습니다. ticker=" + ticker);
    }
}
