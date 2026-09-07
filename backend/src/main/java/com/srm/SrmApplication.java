package com.srm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.srm.**.mapper")
@SpringBootApplication
public class SrmApplication {
    public static void main(String[] args) {
        SpringApplication.run(SrmApplication.class, args);
    }
}
