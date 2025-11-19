package com.kkgame;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDubbo
@EnableDiscoveryClient
public class MatchServiceApplication {

    public static void main(String[] args) {
        System.setProperty("com.alibaba.com.caucho.hessian.useUnsafeSerializer", "false");
        System.setProperty("dubbo.security.serialize.check.level", "WARN");
        SpringApplication.run(MatchServiceApplication.class, args);
        System.out.println("MatchServiceApplication success");
    }
}
