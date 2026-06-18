package com.eusun97.stocktracker.infrastructure.client.publicdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicDataResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, Integer numOfRows, Integer pageNo, Integer totalCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String basDt,
            String srtnCd,
            String isinCd,
            String itmsNm,
            String mrktCtg,
            String clpr,
            String vs,
            String fltRt,
            String mkp,
            String hipr,
            String lopr,
            String trqu,
            String trPrc,
            String lstgStCnt,
            String mrktTotAmt
    ) {
    }
}
