package com.shiliuai;

import com.shiliuai.config.ShiliuProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ShiliuProperties.class)
public class ShiliuAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShiliuAiApplication.class, args);
    }
}
