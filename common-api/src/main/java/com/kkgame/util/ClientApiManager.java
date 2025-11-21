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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 客户端API管理器
 * 用于创建、管理和清理客户端与Dubbo服务实例的映射关系
 */
@Slf4j
public class ClientApiManager {

    // 缓存用户与服务实例的映射关系 (userId_serverName -> ipAndPort)
    // 使用Hutool的TimedCache，5分钟过期，自动清理
    private static final TimedCache<String, String> userServerCache = CacheUtil.newTimedCache(5 * 60 * 1000, 5 * 60 * 1000);

    // 缓存ReferenceConfig实例，按serverName和ipAndPort缓存，所有用户共享
    // key: serverName_ipAndPort, value: ReferenceConfig实例
    private static final Map<String, ReferenceConfig<DubboApi>> referenceCache = new ConcurrentHashMap<>();

    // 用于保护对referenceCache的并发访问
    private static final Map<String, Lock> locks = new ConcurrentHashMap<>();

    static {
        // 启动定时任务，每5分钟清理一次过期的用户服务映射缓存
        userServerCache.schedulePrune(5 * 60 * 1000);
    }

    /**
     * 获取客户端对应的服务实例
     *
     * @param userId     用户ID
     * @param serverName 服务名称
     * @return Dubbo服务实例
     */
    public static DubboApi fetchDubboApi(String userId, String serverName) {
        // 所有用户共享同一个ReferenceConfig实例
        // 先从本地缓存获取ipAndPort
        String cacheKey = userId + "_" + serverName;
        String ipAndPort = userServerCache.get(cacheKey);

        // 如果本地缓存没有，则从Redis获取
        if (ipAndPort == null) {
            StringRedisTemplate redisTemplate = SpringUtil.getBean(StringRedisTemplate.class);
            String redisKey = RedisKeyUtil.fetchUserServerKey(serverName);

            // 从Redis中获取指定serverName对应的ipAndPort
            ipAndPort = (String) redisTemplate.opsForHash().get(redisKey, userId);

            // 放入本地缓存
            if (ipAndPort != null) {
                userServerCache.put(cacheKey, ipAndPort);
            }
        }

        // 生成Reference缓存key（基于serverName和ipAndPort）
        String referenceCacheKey = generateCacheKey(serverName, ipAndPort);

        // 先尝试无锁获取ReferenceConfig实例
        ReferenceConfig<DubboApi> reference = referenceCache.get(referenceCacheKey);
        if (reference == null) {
            // 获取针对该referenceCacheKey的锁
            Lock lock = locks.computeIfAbsent(referenceCacheKey, k -> new ReentrantLock());

            lock.lock();
            try {
                reference = referenceCache.get(referenceCacheKey);
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
                    } else {
                        log.info("随机选择一个实例 {} {}", userId, serverName);
                    }

                    // 将新创建的ReferenceConfig放入缓存
                    referenceCache.put(referenceCacheKey, reference);
                    log.info("创建共享ReferenceConfig实例 {} {} {} {}", userId, serverName, ipAndPort, reference);
                }
            } finally {
                lock.unlock();
            }
        }

        // 每次都通过ReferenceConfig获取新的DubboApi代理实例
        return reference.get();
    }

    /**
     * 移除指定用户的所有服务实例映射
     *
     * @param userId 用户ID
     */
    public static void remove(String userId) {
        // 由于使用共享ReferenceConfig实例，不需要为单个用户移除
        log.info("用户 {} 的API缓存移除请求被忽略（使用共享ReferenceConfig实例）", userId);
        // 清除该用户的所有本地缓存
        String prefix = userId + "_";
        userServerCache.iterator().forEachRemaining(entry -> {
            if (entry.startsWith(prefix)) {
                userServerCache.remove(entry);
            }
        });
    }

    /**
     * 移除指定用户的指定服务实例
     *
     * @param userId     用户ID
     * @param serverName 服务名称
     */
    public static void remove(String userId, String serverName) {
        log.info("用户 {} 的服务 {} 缓存移除请求被忽略（使用共享ReferenceConfig实例）", userId, serverName);
        // 但需要清理用户服务映射缓存
        String cacheKey = userId + "_" + serverName;
        userServerCache.remove(cacheKey);
    }

    /**
     * 清空整个缓存
     */
    public static void clear() {
        // 销毁所有ReferenceConfig
        for (ReferenceConfig<DubboApi> reference : referenceCache.values()) {
            try {
                reference.destroy();
            } catch (Exception e) {
                log.warn("Failed to destroy ReferenceConfig", e);
            }
        }

        referenceCache.clear();
        userServerCache.clear();
        locks.clear();
        log.info("Cleared all shared ReferenceConfigs and user server mapping cache");
    }

    /**
     * 处理消息并转发给对应服务
     * @param userId 玩家id
     * @param serverName 服务名称
     */
    public static DubboApi fetchClientApi(String userId, String serverName) {
        try {
            // 直接获取共享的API实例
            DubboApi api = fetchDubboApi(userId, serverName);

            // 检查获取到的API是否为空
            if (api == null) {
                log.warn("Failed to fetch shared DubboApi for userId: {}, serverName: {}", userId, serverName);
            }
            return api;
        } catch (Exception e) {
            log.error("Error processing message for client: {}", userId, e);
        }
        return null;
    }

    public static void clientAndServiceToRedis(String userId, String serverName, String ipAndPort) {
        String redisKey = RedisKeyUtil.fetchUserServerKey(serverName);
        SpringUtil.getBean(StringRedisTemplate.class).opsForHash().put(redisKey, userId, ipAndPort);

        // 同时更新本地缓存
        String cacheKey = userId + "_" + serverName;
        userServerCache.put(cacheKey, ipAndPort);

        log.info("玩家与服务的映射关系写入redis {} {}: {}", userId, serverName, ipAndPort);
    }

    /**
     * 生成Reference缓存key
     * @param serverName 服务名称
     * @param ipAndPort IP和端口
     * @return 缓存key
     */
    private static String generateCacheKey(String serverName, String ipAndPort) {
        return serverName + "_" + (ipAndPort != null ? ipAndPort : "random");
    }

    /**
     * 当服务实例下线时，清理对应的缓存
     * @param serverName 服务名称
     * @param ipAndPort IP和端口
     */
    public static void clearReferenceCacheByInstance(String serverName, String ipAndPort) {
        String referenceCacheKey = generateCacheKey(serverName, ipAndPort);
        Lock lock = locks.get(referenceCacheKey);

        if (lock != null) {
            lock.lock();
            try {
                ReferenceConfig<DubboApi> reference = referenceCache.get(referenceCacheKey);
                if (reference != null) {
                    try {
                        reference.destroy(); // 销毁ReferenceConfig
                        log.info("Destroyed ReferenceConfig for key: " + referenceCacheKey);
                    } catch (Exception e) {
                        log.warn("Failed to destroy ReferenceConfig for key: " + referenceCacheKey, e);
                    }
                }
                referenceCache.remove(referenceCacheKey);
                locks.remove(referenceCacheKey);
                log.info("Cleaned cache by instance: serverName={}, ipAndPort={}", serverName, ipAndPort);
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 释放所有dubbo api实例
     * 目的是当服务shutdown的时候,释放当前服务Reference的实例,并且清除redis中玩家对应的ip和port
     */
    public static void clearAllReferenceCache() {
        String serverName = CommonUtil.fetchLocalServerName();
        String ipAndPort = CommonUtil.fetchLocalServerId();
        log.info("开始清理服务 {} {} 的Dubbo引用和Redis中的玩家映射", serverName, ipAndPort);

        // 1. 销毁指定服务实例的ReferenceConfig实例
        String referenceCacheKey = generateCacheKey(serverName, ipAndPort);
        Lock lock = locks.get(referenceCacheKey);

        int destroyedCount = 0;
        if (lock != null) {
            lock.lock();
            try {
                ReferenceConfig<DubboApi> reference = referenceCache.get(referenceCacheKey);
                if (reference != null) {
                    try {
                        reference.destroy();
                        destroyedCount++;
                        log.info("已销毁服务 {} 的Dubbo引用", referenceCacheKey);
                    } catch (Exception e) {
                        log.warn("Failed to destroy ReferenceConfig for key: " + referenceCacheKey, e);
                    }
                }
                referenceCache.remove(referenceCacheKey);
                locks.remove(referenceCacheKey);
            } finally {
                lock.unlock();
            }
        }

        // 2. 清理Redis中当前服务实例相关的玩家映射
        StringRedisTemplate redisTemplate = SpringUtil.getBean(StringRedisTemplate.class);
        String redisKey = RedisKeyUtil.fetchUserServerKey(serverName);

        try {
            // 获取所有映射到当前服务实例的用户
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(redisKey);
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                if (ipAndPort.equals(entry.getValue())) {
                    // 删除映射关系
                    redisTemplate.opsForHash().delete(redisKey, entry.getKey());
                    log.info("已从Redis中移除用户 {} 到服务 {} 的映射", entry.getKey(), ipAndPort);
                }
            }
        } catch (Exception e) {
            log.warn("清理Redis中服务 {} 的映射时出错", serverName, e);
        }

        log.info("服务 {} {} 清理完成，共销毁 {} 个Dubbo引用", serverName, ipAndPort, destroyedCount);
    }
}
