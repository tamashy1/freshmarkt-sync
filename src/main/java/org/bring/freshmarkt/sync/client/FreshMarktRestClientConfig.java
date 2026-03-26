package org.bring.freshmarkt.sync.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class FreshMarktRestClientConfig {

    @Bean
    public RestClient freshMarktHttpClient(@Value("${freshmarkt.api.base-url}") String baseUrl,
                                           @Value("${freshmarkt.api.key}") String apiKey) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-API-Key",apiKey)
                .build();
    }
}
