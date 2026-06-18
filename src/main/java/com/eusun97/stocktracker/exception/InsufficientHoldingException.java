package com.eusun97.stocktracker.exception;

public class InsufficientHoldingException extends BusinessException {

    public InsufficientHoldingException(String message) {
        super(ErrorCode.INSUFFICIENT_HOLDING, message);
    }
}
