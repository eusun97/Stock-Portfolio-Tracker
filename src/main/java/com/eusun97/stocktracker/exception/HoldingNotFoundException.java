package com.eusun97.stocktracker.exception;

public class HoldingNotFoundException extends BusinessException {

    public HoldingNotFoundException(String message) {
        super(ErrorCode.HOLDING_NOT_FOUND, message);
    }
}
