package com.kkgame.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.naming.NamingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class NacosConfig {

    @Value("${spring.cloud.nacos.server-addr}")
    private String serverAddr;

    @Value("${spring.cloud.nacos.discovery.namespace:}")
    private String namespace;

    @Value("${dubbo.registry.parameters.namespace:}")
    private String dubboNameSpace;

    @Bean
    public NamingService namingService() throws Exception {
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        if (namespace != null && !namespace.isEmpty()) {
            properties.put("namespace", namespace);
        }
        return NacosFactory.createNamingService(properties);
    }

    // 这里因为把dubbo服务与其他服务分开，所以把dubbo服务单独放在一个命名空间下
    @Bean("dubboNamingService")
    public NamingService dubboNamingService() throws Exception {
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        if (dubboNameSpace != null && !dubboNameSpace.isEmpty()) {
            properties.put("namespace", dubboNameSpace);
        }
        return NacosFactory.createNamingService(properties);
    }
}
