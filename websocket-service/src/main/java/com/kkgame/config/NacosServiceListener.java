package com.kkgame.config;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class NacosServiceListener extends Subscriber<InstancesChangeEvent> {

    private static final Logger log = LoggerFactory.getLogger(NacosServiceListener.class);

    // Spring Cloud的NamingService
    @Autowired
    @Qualifier("namingService")
    private NamingService namingService;

    // Dubbo的NamingService
    @Autowired
    @Qualifier("dubboNamingService")
    private NamingService dubboNamingService;

    // 定时任务执行器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @PostConstruct
    public void init() {
        // 注册监听器
        NotifyCenter.registerSubscriber(this);
        log.info("注册Nacos服务实例变更监听器完成");

        // 初始化时获取当前所有服务并订阅
        initAndSubscribeServices();

        // 启动定时检查任务，发现新服务
//        startPeriodicServiceDiscovery();
    }

    @Override
    public void onEvent(InstancesChangeEvent event) {
        String serviceName = event.getServiceName();
        List<Instance> instances = event.getHosts();

        log.info("服务变更事件触发(InstancesChangeEvent) - 服务名称: {}, 当前实例数: {}", serviceName, instances.size());

        // 直接处理变更的实例，无需维护自己的缓存
        processInstanceChanges(serviceName, instances);
    }

    /**
     * 初始化时获取当前所有服务并订阅
     */
    private void initAndSubscribeServices() {
        log.info("开始初始化并订阅Nacos服务列表");

        // 获取并订阅Spring Cloud服务
        subscribeSpringCloudServices();

        // 获取并订阅Dubbo服务
        subscribeDubboServices();
    }

    /**
     * 订阅Spring Cloud服务
     */
    private void subscribeSpringCloudServices() {
        try {
            ListView<String> services = namingService.getServicesOfServer(1, Integer.MAX_VALUE);
            log.info("当前Spring Cloud服务数量: {}", services.getData().size());

            for (String serviceName : services.getData()) {
                subscribeService(namingService, serviceName);
            }
        } catch (Exception e) {
            log.error("订阅Spring Cloud服务列表失败", e);
        }
    }

    /**
     * 订阅Dubbo服务
     */
    private void subscribeDubboServices() {
        try {
            ListView<String> services = dubboNamingService.getServicesOfServer(1, Integer.MAX_VALUE);
            log.info("当前Dubbo服务数量: {}", services.getData().size());

            for (String serviceName : services.getData()) {
                subscribeService(dubboNamingService, serviceName);
            }
        } catch (Exception e) {
            log.error("订阅Dubbo服务列表失败", e);
        }
    }

    /**
     * 订阅特定服务的实例变化
     */
    private void subscribeService(NamingService namingService, String serviceName) {
        try {
            namingService.subscribe(serviceName, event -> {
                if (event instanceof NamingEvent) {
                    List<Instance> instances = ((NamingEvent) event).getInstances();
                    log.info("服务 {} 实例变更通知(NamingEvent) - 当前实例数: {}", serviceName, instances.size());
                    processInstanceChanges(serviceName, instances);
                }
            });
            log.info("成功订阅服务: {}", serviceName);

            // 记录服务当前实例状态
            List<Instance> instances = namingService.getAllInstances(serviceName);
            log.info("服务 {} 初始实例数: {}", serviceName, instances.size());
            logServiceInstances(serviceName, instances);
        } catch (Exception e) {
            log.error("订阅服务 {} 失败", serviceName, e);
        }
    }

    /**
     * 记录服务实例信息
     */
    private void logServiceInstances(String serviceName, List<Instance> instances) {
        for (Instance instance : instances) {
            String ipPort = instance.getIp() + ":" + instance.getPort();
            log.info("服务实例信息 - 服务名称: {}, IP端口: {}, 实例ID: {}, 是否健康: {}, 是否启用: {}",
                serviceName, ipPort, instance.getInstanceId(), instance.isHealthy(), instance.isEnabled());
        }
    }

    /**
     * 处理实例变化
     * @param serviceName 服务名称
     * @param instances 当前实例列表
     */
    private void processInstanceChanges(String serviceName, List<Instance> instances) {
        // 遍历所有实例，根据健康状态和启用状态判断实例是否可用
        for (Instance instance : instances) {
            String ipPort = instance.getIp() + ":" + instance.getPort();

            // 记录所有变更的实例信息
            log.info("服务实例状态 - 服务名称: {}, IP端口: {}, 实例ID: {}, 是否健康: {}, 是否启用: {}",
                serviceName, ipPort, instance.getInstanceId(), instance.isHealthy(), instance.isEnabled());

            // 如果实例不健康或未启用，则清理其缓存
            if (!instance.isHealthy() || !instance.isEnabled()) {
                log.info("===服务实例不可用=== 服务名称: {}, IP端口: {}, 实例ID: {}",
                    serviceName, ipPort, instance.getInstanceId());
                clearInstanceCache(serviceName, ipPort);
            }
        }
    }

    /**
     * 清理不可用实例的缓存
     * @param serviceName 服务名称
     * @param ipPort 不可用实例的IP和端口
     */
    private void clearInstanceCache(String serviceName, String ipPort) {
        // 调用ClientApiUtil清理对应实例的缓存
        com.kkgame.util.ClientApiManager.clearReferenceCacheByInstance(serviceName, ipPort);
    }

    @Override
    public Class<? extends Event> subscribeType() {
        return InstancesChangeEvent.class;
    }

    @PreDestroy
    public void destroy() {
        // 取消注册监听器
        NotifyCenter.deregisterSubscriber(this);
        // 关闭定时任务
        scheduler.shutdown();
        log.info("注销Nacos服务实例变更监听器");
    }

    /**
     * 启动定时服务发现任务
     */
    private void startPeriodicServiceDiscovery() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                discoverAndSubscribeNewServices(namingService, "Spring Cloud");
                discoverAndSubscribeNewServices(dubboNamingService, "Dubbo");
            } catch (Exception e) {
                log.error("定时发现新服务时发生错误", e);
            }
        }, 30, 30, TimeUnit.SECONDS); // 每30秒检查一次新服务
    }

    /**
     * 发现并订阅新服务
     */
    private void discoverAndSubscribeNewServices(NamingService namingService, String serviceType) {
        try {
            ListView<String> services = namingService.getServicesOfServer(1, Integer.MAX_VALUE);
            log.debug("检查{}新服务，当前服务数量: {}", serviceType, services.getData().size());

            // 由于我们每次都会重新订阅所有服务，Nacos客户端会处理重复订阅的问题
            for (String serviceName : services.getData()) {
                subscribeService(namingService, serviceName);
            }
        } catch (Exception e) {
            log.error("发现{}新服务时发生错误", serviceType, e);
        }
    }
}
