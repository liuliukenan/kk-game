package com.kkgame.controller;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.kkgame.util.DubboServiceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.random.RandomGenerator;

@RestController
public class TestController {

    @Autowired
    @Qualifier("dubboNamingService")
    private NamingService dubboNamingService;


    @GetMapping("/test/1")
    public String test() {
        try {
            String serviceName = DubboServiceUtil.build("a-service");
            List<Instance> instances =
                    dubboNamingService.getAllInstances(serviceName);

            dubboNamingService.getServicesOfServer(1, 100).getData().forEach(System.out::println);

            if (!instances.isEmpty()) {
                for (com.alibaba.nacos.api.naming.pojo.Instance instance : instances) {
                    System.out.println("Dubbo IP: " + instance.getIp());
                    System.out.println("Dubbo Port: " + instance.getPort());
                }
                Instance instance = instances.get(RandomGenerator.getDefault().nextInt(0, instances.size()));
                return instance.toInetAddr();
            }
        } catch (NacosException ex) {
            throw new RuntimeException(ex);
        }
        return "";
    }
}
