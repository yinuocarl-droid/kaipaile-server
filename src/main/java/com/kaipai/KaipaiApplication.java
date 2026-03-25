package com.kaipai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.kaipai.module.server.**.mapper")
public class KaipaiApplication {

    public static void main(String[] args) {
        SpringApplication.run(KaipaiApplication.class, args);
    }

}
