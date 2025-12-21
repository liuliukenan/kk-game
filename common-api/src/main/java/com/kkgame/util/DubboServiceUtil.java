package com.kkgame.util;

import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 根据 Dubbo 的接口名、版本、group 生成正确的 Nacos serviceName。
 * Dubbo 注册到 Nacos 的 serviceName 规则是：
 * providers:{interfaceName}:{version}:{group}
 * 其中 version 或 group 为空时仍然保留分隔符“:”
 * 例如：
 * interface = com.kkgame.api.DubboApi
 * version = ""
 * group = a-service
 * 结果：providers:com.kkgame.api.DubboApi::a-service
 */
public class DubboServiceUtil {

    private static final Logger log = LoggerFactory.getLogger(DubboServiceUtil.class);

    private static final String PREFIX = "providers:";
    public static final String INTERFACE_NAME = "com.kkgame.api.DubboApi";


    public static String getServiceInstanceInfo(String serverName) {
        try {
            NamingService bean = SpringUtil.getBean("dubboNamingService");
            List<Instance> instances = bean.getAllInstances(build(serverName));

            if (!instances.isEmpty()) {
                Instance instance = instances.get(RandomGenerator.getDefault().nextInt(0, instances.size()));
                return instance.getIp() + ":" + instance.getPort();
            }
        } catch (NacosException ex) {
            log.error("Failed to fetchDubboApi a-service instance info", ex);
        }
        throw new RuntimeException("Failed to fetchDubboApi a-service instance info");
    }

    public static String getLocalServiceInstanceInfo() {
        try {
            NamingService bean = SpringUtil.getBean("dubboNamingService");
            List<Instance> instances = bean.getAllInstances(build(CommonUtil.fetchLocalServerName()));
            NacosDiscoveryProperties nacosDiscoveryProperties = SpringUtil.getBean(NacosDiscoveryProperties.class);
            String localIp = nacosDiscoveryProperties.getIp();
            log.info("localIp :{}", localIp);
            if (!instances.isEmpty()) {
                for (Instance instance : instances) {
                    if (instance.getIp().equals(localIp)) {
                        return instance.getIp() + ":" + instance.getPort();
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("Failed to fetchDubboApi service instance info");
    }

    /**
     * 生成标准 Dubbo serviceName
     *
     * @param interfaceName 接口全限定名
     * @param version       版本（可为空）
     * @param group         分组（可为空）
     * @return 完整的 serviceName
     */
    public static String build(String interfaceName, String version, String group) {
        if (interfaceName == null || interfaceName.isBlank()) {
            throw new IllegalArgumentException("interfaceName 不能为空");
        }

        StringBuilder sb = new StringBuilder(PREFIX);
        sb.append(interfaceName);

        // version
        sb.append(":");
        if (version != null && !version.isBlank()) {
            sb.append(version);
        }

        // group
        sb.append(":");
        if (group != null && !group.isBlank()) {
            sb.append(group);
        }
        return sb.toString();
    }

    public static String build(String version, String group) {
        return build(INTERFACE_NAME, version, group);
    }

    public static String build(String group) {
        return build(INTERFACE_NAME, "", group);
    }

}
