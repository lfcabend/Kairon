package com.kairon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KaironApplication {

    public static void main(String[] args) {
        SpringApplication.run(KaironApplication.class, args);
    }
}
