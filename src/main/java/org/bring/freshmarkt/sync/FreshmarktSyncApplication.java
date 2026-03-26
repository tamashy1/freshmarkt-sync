package org.bring.freshmarkt.sync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FreshmarktSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(FreshmarktSyncApplication.class, args);
    }

}
