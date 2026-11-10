package com.framebucket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FrameBucketApplication {
    public static void main(String[] args) {
        SpringApplication.run(FrameBucketApplication.class, args);
    }
}
