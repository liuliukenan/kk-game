package com.kkgame.util;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.extra.spring.SpringUtil;
import com.kkgame.api.DubboApi;
import com.kkgame.constans.RedisKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.ReferenceConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 客户端API管理器
 * 用于创建、管理和清理客户端与Dubbo服务实例的映射关系
 */
@Slf4j
public class ClientApiManager {

    // 缓存用户与DubboApi实例的映射关系
    // 外层key: userId, 里层key: serverName, value: DubboApi实例
    // 使用Hutool的TimedCache，30分钟过期，自动清理
    private static final TimedCache<String, Map<String, DubboApi>> clientApiMap = CacheUtil.newTimedCache(30 * 60 * 1000, 30 * 60 * 1000);

    // 缓存ReferenceConfig实例，按serverName缓存，多个用户共享
    private static final Map<String, ReferenceConfig<DubboApi>> referenceCache = new ConcurrentHashMap<>();

    // 记录ReferenceConfig的最后使用时间，用于清理长时间未使用的引用
    private static final Map<String, Long> referenceLastUsedTime = new ConcurrentHashMap<>();

    // 用于保护对referenceCache和referenceLastUsedTime的并发访问
    private static final Map<String, Lock> locks = new ConcurrentHashMap<>();

    // 定时清理器
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // 静态初始化块，设置系统属性以解决Java 17兼容性问题
    static {
        // 禁用Hessian的字节码优化，避免Java 17模块访问限制问题
        System.setProperty("dubbo.serialize.by.bytecode", "false");
        // 设置默认序列化方式为native/hessian2，避免Hessian兼容性问题
        System.setProperty("dubbo.serialization", "hessian2");

        // 启动定时任务，每10分钟检查一次，清理30分钟内未使用的ReferenceConfig
        scheduler.scheduleWithFixedDelay(ClientApiManager::cleanExpiredReferences, 10, 10, TimeUnit.MINUTES);
    }

    /**
     * 添加客户端和服务实例的映射关系
     *
     * @param userId     用户ID
     * @param serverName 服务名称
     * @param api        Dubbo服务实例
     */
    public static void put(String userId, String serverName, DubboApi api) {
        Map<String, DubboApi> innerMap = clientApiMap.get(userId);
        if (innerMap == null) {
            innerMap = new ConcurrentHashMap<>();
            clientApiMap.put(userId, innerMap);
        }
        innerMap.put(serverName, api);
        log.info("添加用户API缓存 userId:{} servername:{} api:{} clientApiMap size:{}", userId, serverName, api, clientApiMap.size());
    }

    /**
     * 获取客户端对应的服务实例
     *
     * @param userId     用户ID
     * @param serverName 服务名称
     * @return Dubbo服务实例
     */
    public static DubboApi fetchDubboApi(String userId, String serverName) {
        Map<String, DubboApi> innerMap = clientApiMap.get(userId);
        if (innerMap != null) {
            return innerMap.get(serverName);
        }
        return null;
    }

    /**
     * 移除指定用户的所有服务实例映射
     *
     * @param userId 用户ID
     */
    public static void remove(String userId) {
        clientApiMap.remove(userId);
    }

    /**
     * 移除指定用户的指定服务实例
     *
     * @param userId     用户ID
     * @param serverName 服务名称
     */
    public static void remove(String userId, String serverName) {
        Map<String, DubboApi> innerMap = clientApiMap.get(userId);
        if (innerMap != null) {
            log.info("删除用户API缓存 userId:{} servername:{}", userId, serverName);
            innerMap.remove(serverName);
        }
    }

    /**
     * 判断指定用户是否存在指定服务的映射关系
     *
     * @param userId     用户ID
     * @param serverName 服务名称
     * @return 是否存在映射关系
     */
    public static boolean containsKey(String userId, String serverName) {
        Map<String, DubboApi> innerMap = clientApiMap.get(userId);
        if (innerMap != null) {
            return innerMap.containsKey(serverName);
        }
        return false;
    }

    /**
     * 清空整个缓存
     */
    public static void clear() {
        clientApiMap.clear();
    }


    /**
     * 创建客户端API实例
     * @param userId 用户ID
     * @param serverName 服务名称
     */
    public static void createClientApi(String userId, String serverName) {
        try {
            StringRedisTemplate redisTemplate = SpringUtil.getBean(StringRedisTemplate.class);
            String redisKey = RedisKeyUtil.USER_SERVER_KEY + serverName;

            // 从Hash中获取指定serverName对应的ipAndPort
            String ipAndPort = (String) redisTemplate.opsForHash().get(redisKey, userId);

            // 生成缓存key（仅基于serverName和ipAndPort）
            String cacheKey = generateCacheKey(serverName, ipAndPort);

            // 先尝试无锁获取引用
            ReferenceConfig<DubboApi> reference = referenceCache.get(cacheKey);
            if (reference == null) {
                // 获取针对该cacheKey的锁
                Lock lock = locks.computeIfAbsent(cacheKey, k -> new ReentrantLock());

                lock.lock();
                try {
                    reference = referenceCache.get(cacheKey);
                    if (reference == null) {
                        reference = new ReferenceConfig<>();
                        reference.setInterface(DubboApi.class);
                        // 适当增加超时时间
                        reference.setTimeout(3000);
                        // 重试次数
                        reference.setRetries(1);
                        reference.setScope("remote");
                        reference.setSticky(true);
                        // 轮询负载均衡
                        reference.setLoadbalance("roundrobin");
                        reference.setGroup(serverName);
                        if (StringUtils.hasLength(ipAndPort)) {
                            // 直接连接到指定的IP和端口
                            reference.setUrl("dubbo://" + ipAndPort + "/" + DubboApi.class.getName());
                            log.info("绑定到服务对应的实例 {} {} {}", userId, serverName, ipAndPort);

                            // 注册服务实例信息用于健康检查
//                            ServiceInstanceListener.registerServiceInstance(serverName, ipAndPort);
                        } else {
                            log.info("随机选择一个实例 {} {}", userId, serverName);
                        }

                        // 将新创建的ReferenceConfig放入缓存
                        referenceCache.put(cacheKey, reference);
                    }
                } finally {
                    lock.unlock();
                }
            }

            // 更新最后使用时间
            referenceLastUsedTime.put(cacheKey, System.currentTimeMillis());

            // 为每个客户端创建独立的DubboApi实例（基于共享的ReferenceConfig配置）
            DubboApi clientSpecificApi = reference.get();
            log.info("创建 {} {} {} {}", userId, serverName, ipAndPort, clientSpecificApi);
            put(userId, serverName, clientSpecificApi);
        } catch (Exception e) {
            log.error("Failed to create {} reference for client: {}", serverName, userId, e);
        }
    }

    /**
     * 确保客户端API实例已创建
     * @param userId 玩家id
     * @param serverName 服务名称
     */
    public static void ensureClientApi(String userId, String serverName) {
        if (!containsKey(userId, serverName)) {
            createClientApi(userId, serverName);
        }
    }

    /**
     * 处理消息并转发给对应服务
     * @param userId 玩家id
     * @param serverName 服务名称
     */
    public static DubboApi fetchClientApi(String userId, String serverName) {
        try {
            // 确保客户端API已创建
            ensureClientApi(userId, serverName);

            // 使用已分配的实例处理请求
            return fetchDubboApi(userId, serverName);

        } catch (Exception e) {
            log.error("Error processing message for client: {}", userId, e);
        }
        return null;
    }

    public static void clientAndServiceToRedis(String userId, String serverName, String ipAndPort) {
        String redisKey = RedisKeyUtil.USER_SERVER_KEY + serverName;
        SpringUtil.getBean(StringRedisTemplate.class).opsForHash().put(redisKey, userId, ipAndPort);
        log.info("玩家与服务的映射关系写入redis {} {}: {}", userId, serverName, ipAndPort);
    }

    /**
     * 生成ReferenceConfig缓存key
     * @param serverName 服务名称
     * @param ipAndPort IP和端口
     * @return 缓存key
     */
    private static String generateCacheKey(String serverName, String ipAndPort) {
        return serverName + "_" + (ipAndPort != null ? ipAndPort : "random");
    }

    /**
     * 清理ReferenceConfig缓存（通常在应用关闭时调用）
     */
    public static void clearAllReferenceCache() {
        // 先销毁所有ReferenceConfig
        for (ReferenceConfig<DubboApi> reference : referenceCache.values()) {
            try {
                reference.destroy();
            } catch (Exception e) {
                log.warn("Failed to destroy ReferenceConfig", e);
            }
        }

        referenceCache.clear();
        referenceLastUsedTime.clear();
        locks.clear();
        log.info("Cleared all ReferenceConfigs");
    }

    /**
     * 清理过期的ReferenceConfig引用
     */
    private static void cleanExpiredReferences() {
        try {
            long currentTime = System.currentTimeMillis();
            long expireTime = 30 * 60 * 1000; // 30分钟

            referenceLastUsedTime.entrySet().removeIf(entry -> {
                if (currentTime - entry.getValue() > expireTime) {
                    String cacheKey = entry.getKey();
                    Lock lock = locks.get(cacheKey);
                    if (lock != null) {
                        lock.lock();
                        try {
                            ReferenceConfig<DubboApi> reference = referenceCache.get(cacheKey);
                            if (reference != null) {
                                try {
                                    reference.destroy(); // 销毁ReferenceConfig
                                } catch (Exception e) {
                                    log.warn("Failed to destroy ReferenceConfig for key: " + cacheKey, e);
                                }
                            }
                            referenceCache.remove(cacheKey);
                            locks.remove(cacheKey);
                            log.info("Cleaned expired ReferenceConfig: " + cacheKey);
                            return true;
                        } finally {
                            lock.unlock();
                        }
                    }
                }
                return false;
            });
        } catch (Exception e) {
            log.error("Error cleaning expired references", e);
        }
    }

    /**
     * 当服务实例下线时，清理对应的Reference缓存
     * @param serverName 服务名称
     * @param ipAndPort IP和端口
     */
    public static void clearReferenceCacheByInstance(String serverName, String ipAndPort) {
        String cacheKey = generateCacheKey(serverName, ipAndPort);
        Lock lock = locks.get(cacheKey);

        if (lock != null) {
            lock.lock();
            try {
                ReferenceConfig<DubboApi> reference = referenceCache.get(cacheKey);
                if (reference != null) {
                    try {
                        reference.destroy(); // 销毁ReferenceConfig
                        log.info("Destroyed ReferenceConfig for key: " + cacheKey);
                    } catch (Exception e) {
                        log.warn("Failed to destroy ReferenceConfig for key: " + cacheKey, e);
                    }
                }
                referenceCache.remove(cacheKey);
                referenceLastUsedTime.remove(cacheKey);
                locks.remove(cacheKey);
                log.info("Cleaned ReferenceConfig by instance: serverName={}, ipAndPort={}", serverName, ipAndPort);
            } finally {
                lock.unlock();
            }
        }

        // 同时清理客户端缓存
        clearClientCacheByInstance(serverName, ipAndPort);
    }

    /**
     * 当服务实例下线时，清理客户端缓存
     * @param serverName 服务名称
     * @param ipAndPort IP和端口
     */
    private static void clearClientCacheByInstance(String serverName, String ipAndPort) {
        // 清理Redis中存储的用户与服务实例的映射关系
        try {
            StringRedisTemplate redisTemplate = SpringUtil.getBean(StringRedisTemplate.class);
            String redisKey = RedisKeyUtil.USER_SERVER_KEY + serverName;

            // 获取所有映射到该服务实例的用户
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(redisKey);
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                if (ipAndPort.equals(entry.getValue())) {
                    // 删除映射关系
                    redisTemplate.opsForHash().delete(redisKey, entry.getKey());
                    log.info("Removed redis mapping for user: {} to server: {}", entry.getKey(), ipAndPort);
                }
            }
        } catch (Exception e) {
            log.error("Error clearing redis mapping for server: {} ipAndPort: {}", serverName, ipAndPort, e);
        }
    }

}