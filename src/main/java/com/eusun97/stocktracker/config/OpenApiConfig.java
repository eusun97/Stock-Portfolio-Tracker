package com.eusun97.stocktracker.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI stockTrackerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stock Portfolio Tracker API")
                        .description("개인 주식 포트폴리오 관리 및 수익률 분석 API")
                        .version("v1.0"));
    }
}
