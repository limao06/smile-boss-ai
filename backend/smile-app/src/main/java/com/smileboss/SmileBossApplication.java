package com.smileboss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"com.smileboss"})
@EnableScheduling
public class SmileBossApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmileBossApplication.class, args);
    }
}
