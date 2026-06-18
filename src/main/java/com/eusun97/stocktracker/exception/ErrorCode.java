package com.eusun97.stocktracker.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "C001", "요청 값이 올바르지 않습니다."),
    INVALID_TRADE_REQUEST(HttpStatus.BAD_REQUEST, "T001", "거래 요청이 올바르지 않습니다."),
    TRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "T002", "거래내역을 찾을 수 없습니다."),
    INSUFFICIENT_HOLDING(HttpStatus.BAD_REQUEST, "T003", "보유 수량이 부족합니다."),
    HOLDING_NOT_FOUND(HttpStatus.BAD_REQUEST, "T004", "보유하지 않은 종목입니다."),
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "종목 정보를 찾을 수 없습니다."),
    EXTERNAL_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "E001", "외부 시세 API 가 일시적으로 응답하지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G001", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
