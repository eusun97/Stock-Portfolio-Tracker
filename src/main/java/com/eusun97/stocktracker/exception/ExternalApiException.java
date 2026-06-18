package com.eusun97.stocktracker.exception;

public class ExternalApiException extends BusinessException {

    public ExternalApiException(String message) {
        super(ErrorCode.EXTERNAL_API_UNAVAILABLE, message);
    }

    public ExternalApiException(String message, Throwable cause) {
        super(ErrorCode.EXTERNAL_API_UNAVAILABLE, message, cause);
    }
}
