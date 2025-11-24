package com.kkgame.listener;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.kkgame.enums.ServerNameEnum;
import com.kkgame.manager.ClientApiManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class NacosServiceListener extends Subscriber<InstancesChangeEvent> {

    private static final Logger log = LoggerFactory.getLogger(NacosServiceListener.class);

    // Dubbo的NamingService
    @Resource
    @Qualifier("dubboNamingService")
    private NamingService dubboNamingService;

    // 用于存储每个服务的实例列表缓存
    private final Map<String, Set<String>> serviceInstancesCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 注册监听器
        NotifyCenter.registerSubscriber(this);
        log.info("注册Nacos服务实例变更监听器完成");

        // 只获取并订阅Dubbo服务中有状态的服务
        subscribeStatefulDubboServices();

        // 启动定时检查任务，发现新服务
//        startPeriodicServiceDiscovery();
    }

    @Override
    public void onEvent(InstancesChangeEvent event) {
        String serviceName = event.getServiceName();
        List<Instance> instances = event.getHosts();

        // 只处理有状态的服务
        if (!isStatefulService(serviceName)) {
            return;
        }

        log.info("服务变更事件触发(InstancesChangeEvent) - 服务名称: {}, 当前实例数: {}", serviceName, instances.size());

        // 处理实例变化，包括检测下线的实例
        processInstanceChangesWithOfflineDetection(serviceName, instances);
    }

    /**
     * 判断是否为有状态服务
     *
     * @param serviceName 服务名称
     * @return 是否为有状态服务
     */
    private boolean isStatefulService(String serviceName) {
        try {
            return ServerNameEnum.isStateful(serviceName);
        } catch (Exception e) {
            log.warn("判断服务是否有状态时出错: {}", serviceName, e);
            return false;
        }
    }

    /**
     * 订阅有状态的Dubbo服务
     */
    private void subscribeStatefulDubboServices() {
        log.info("开始订阅有状态的Dubbo服务");
        // 直接遍历ServerNameEnum枚举中的有状态服务
        for (ServerNameEnum serverNameEnum : ServerNameEnum.values()) {
            // 只订阅有状态的服务
            if (serverNameEnum.getStateful()) {
                String serviceName = serverNameEnum.getServerName();
                subscribeService(dubboNamingService, serviceName);
            }
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
                    processInstanceChangesWithOfflineDetection(serviceName, instances);
                }
            });
            log.info("成功订阅服务: {}", serviceName);

            // 记录服务当前实例状态
            List<Instance> instances = namingService.getAllInstances(serviceName);
            log.info("服务 {} 初始实例数: {}", serviceName, instances.size());
            logServiceInstances(serviceName, instances);

            // 初始化缓存
            Set<String> instanceKeys = instances.stream()
                    .map(instance -> instance.getIp() + ":" + instance.getPort())
                    .collect(Collectors.toSet());
            serviceInstancesCache.put(serviceName, instanceKeys);
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
     * 处理实例变化，包括检测下线的实例
     *
     * @param serviceName      服务名称
     * @param currentInstances 当前实例列表
     */
    private void processInstanceChangesWithOfflineDetection(String serviceName, List<Instance> currentInstances) {
        // 获取当前实例的标识集合
        Set<String> currentInstanceKeys = currentInstances.stream()
                .map(instance -> instance.getIp() + ":" + instance.getPort())
                .collect(Collectors.toSet());

        // 从缓存中获取上一次的实例列表
        Set<String> previousInstanceKeys = serviceInstancesCache.getOrDefault(serviceName, new HashSet<>());

        // 更新缓存
        serviceInstancesCache.put(serviceName, currentInstanceKeys);

        // 找出新增的实例
        Set<String> newInstanceKeys = new HashSet<>(currentInstanceKeys);
        newInstanceKeys.removeAll(previousInstanceKeys);

        // 找出下线的实例
        Set<String> offlineInstanceKeys = new HashSet<>(previousInstanceKeys);
        offlineInstanceKeys.removeAll(currentInstanceKeys);

        log.info("服务 {} 实例变化情况 - 新增实例数: {}, 下线实例数: {}",
                serviceName, newInstanceKeys.size(), offlineInstanceKeys.size());

        // 处理当前实例状态（包括健康和不健康的）
        for (Instance instance : currentInstances) {
            String ipPort = instance.getIp() + ":" + instance.getPort();

            // 记录所有变更的实例信息
            log.info("服务实例状态 - 服务名称: {}, IP端口: {}, 实例ID: {}, 是否健康: {}, 是否启用: {}",
                    serviceName, ipPort, instance.getInstanceId(), instance.isHealthy(), instance.isEnabled());

            // 如果实例不健康或未启用，则清理其缓存
            if (!instance.isHealthy() || !instance.isEnabled()) {
                log.info("===服务实例不可用=== 服务名称: {}, IP端口: {}, 实例ID: {}",
                        serviceName, ipPort, instance.getInstanceId());
                ClientApiManager.clearLocalReferenceCache(serviceName, ipPort);
            }
        }

        // 处理下线的实例
        for (String offlineInstanceKey : offlineInstanceKeys) {
            log.info("===检测到服务实例下线=== 服务名称: {}, IP端口: {}", serviceName, offlineInstanceKey);
            ClientApiManager.clearLocalReferenceCache(serviceName, offlineInstanceKey);
        }
    }

    @Override
    public Class<? extends Event> subscribeType() {
        return InstancesChangeEvent.class;
    }

    @PreDestroy
    public void destroy() {
        // 取消注册监听器
        NotifyCenter.deregisterSubscriber(this);
        log.info("注销Nacos服务实例变更监听器");
    }
}
