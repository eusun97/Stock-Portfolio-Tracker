package com.eusun97.stocktracker.infrastructure.client.publicdata;

import com.eusun97.stocktracker.exception.ExternalApiException;
import com.eusun97.stocktracker.exception.StockNotFoundException;
import com.eusun97.stocktracker.infrastructure.client.StockPriceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class PublicDataStockPriceClient implements StockPriceClient {

    private static final String CB_NAME = "publicData";
    private static final String SUCCESS_CODE = "00";

    private final RestClient restClient;
    private final PublicDataProperties properties;

    public PublicDataStockPriceClient(
            @Qualifier("publicDataRestClient") RestClient restClient,
            PublicDataProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fetchCurrentPriceFallback")
    @Retry(name = CB_NAME)
    @Override
    public BigDecimal fetchCurrentPrice(String ticker) {
        PublicDataResponse.Item item = callApi(ticker);
        try {
            return new BigDecimal(item.clpr());
        } catch (NumberFormatException e) {
            throw new ExternalApiException(
                    "외부 API 응답 가격 파싱 실패: " + item.clpr(), e);
        }
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fetchStockInfoFallback")
    @Retry(name = CB_NAME)
    @Override
    public StockInfo fetchStockInfo(String ticker) {
        PublicDataResponse.Item item = callApi(ticker);
        return new StockInfo(item.srtnCd(), item.itmsNm(), item.mrktCtg());
    }

    private PublicDataResponse.Item callApi(String ticker) {
        URI uri = buildUri(ticker);
        try {
            PublicDataResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(PublicDataResponse.class);

            return extractItem(response, ticker);

        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            log.warn("publicdata 4xx ticker={} status={}", ticker, status);
            throw new ExternalApiException(
                    "외부 API 호출 실패 (status=" + status + ")", e);
        } catch (RestClientException e) {
            log.warn("publicdata RestClientException ticker={} cause={}",
                    ticker, e.getMessage());
            throw new ExternalApiException("외부 API 통신 오류", e);
        }
    }

    private URI buildUri(String ticker) {
        return UriComponentsBuilder.fromUriString(properties.baseUrl())
                .queryParam("serviceKey", properties.serviceKey())
                .queryParam("resultType", "json")
                .queryParam("likeSrtnCd", ticker)
                .queryParam("numOfRows", 1)
                .queryParam("pageNo", 1)
                .build(true)
                .toUri();
    }

    private PublicDataResponse.Item extractItem(PublicDataResponse response, String ticker) {
        Optional<PublicDataResponse.Item> first = Optional.ofNullable(response)
                .map(PublicDataResponse::response)
                .filter(r -> SUCCESS_CODE.equals(r.header() != null ? r.header().resultCode() : null))
                .map(PublicDataResponse.Response::body)
                .map(PublicDataResponse.Body::items)
                .map(PublicDataResponse.Items::item)
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0));

        if (first.isEmpty()) {
            throw new StockNotFoundException(ticker);
        }
        return first.get();
    }

    @SuppressWarnings("unused")
    private BigDecimal fetchCurrentPriceFallback(String ticker, Throwable t) {
        log.warn("publicdata current price fallback ticker={} cause={}", ticker, t.toString());
        if (t instanceof StockNotFoundException sn) throw sn;
        throw new ExternalApiException(
                "외부 시세 API 일시 장애 (ticker=" + ticker + ")", t);
    }

    @SuppressWarnings("unused")
    private StockInfo fetchStockInfoFallback(String ticker, Throwable t) {
        log.warn("publicdata stock info fallback ticker={} cause={}", ticker, t.toString());
        if (t instanceof StockNotFoundException sn) throw sn;
        throw new ExternalApiException(
                "외부 시세 API 일시 장애 (ticker=" + ticker + ")", t);
    }
}
