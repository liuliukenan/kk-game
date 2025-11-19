package com.kkgame;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDubbo
public class WebSocketServiceApplication {

    public static void main(String[] args) {
        System.setProperty("com.alibaba.com.caucho.hessian.useUnsafeSerializer", "false");
        SpringApplication.run(WebSocketServiceApplication.class, args);
    }

}
