package com.nintails.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NintailsEcommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NintailsEcommerceApplication.class, args);
    }
}
