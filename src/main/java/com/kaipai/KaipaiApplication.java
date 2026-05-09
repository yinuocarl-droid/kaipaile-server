package com.kaipai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.kaipai.module.server.**.mapper")
@EnableScheduling
public class KaipaiApplication {

    public static void main(String[] args) {
        SpringApplication.run(KaipaiApplication.class, args);
    }

}
