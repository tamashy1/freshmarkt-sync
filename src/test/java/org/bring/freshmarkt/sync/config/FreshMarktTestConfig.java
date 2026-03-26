package org.bring.freshmarkt.sync.config;

import org.bring.freshmarkt.sync.client.FreshMarktClient;
import org.bring.freshmarkt.sync.client.FreshMarktClientStub;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class FreshMarktTestConfig {

    @Bean
    @Primary
    public FreshMarktClient freshMarktClient() {
        return new FreshMarktClientStub();
    }

}
