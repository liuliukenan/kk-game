package com.kkgame.manager;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import cn.hutool.extra.spring.SpringUtil;
import com.kkgame.api.DubboApi;
import com.kkgame.constans.RedisKeyUtil;
import com.kkgame.enums.ServerNameEnum;
import com.kkgame.util.CommonUtil;
import com.kkgame.util.DubboServiceUtil;
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
     * 同一个服务的用户共享同一个ReferenceConfig实例
     *
     * @param userId     用户ID
     * @param serverName 服务名称
     * @return Dubbo服务实例
     */
    public static DubboApi fetchDubboApi(String userId, String serverName) {

        // 获取目标服务的ip和端口
        // 如果是无状态服务，则随机找一个dubb服务实例
        // 如果是有状态服务，则从本地缓存获取ipAndPort
        String ipAndPort = fetchIpAndPort(userId, serverName);

        // 生成Reference缓存key（基于serverName和ipAndPort）
        String referenceCacheKey = generateCacheKey(serverName, ipAndPort);

        // 先尝试无锁获取ReferenceConfig实例
        ReferenceConfig<DubboApi> reference = referenceCache.get(referenceCacheKey);
        log.info("referenceCache size {}", referenceCache.size());
        if (reference == null) {
            // 获取针对该referenceCacheKey的锁
            Lock lock = locks.computeIfAbsent(referenceCacheKey, k -> new ReentrantLock());
            log.info("locks size {}", locks.size());

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
                    // 轮询负载均衡
                    reference.setLoadbalance("roundrobin");
                    reference.setGroup(serverName);
                    if (StringUtils.hasLength(ipAndPort)) {
                        // 直接连接到指定的IP和端口
                        reference.setSticky(true);
                        reference.setUrl("dubbo://" + ipAndPort + "/" + DubboApi.class.getName());
                        log.info("绑定到服务对应的实例 {} {} {}", userId, serverName, ipAndPort);
                    } else {
                        // 无状态服务关闭粘性连接
                        reference.setSticky(false);
                        log.info("随机选择一个实例 {} {}", userId, serverName);
                    }

                    // 将新创建的ReferenceConfig放入缓存
                    referenceCache.put(referenceCacheKey, reference);
                    log.info("创建共享ReferenceConfig实例 {} {} {} {}", userId, serverName, ipAndPort, reference);
                }
            } finally {
                lock.unlock();
                locks.remove(referenceCacheKey);
            }
        }

        // 每次都通过ReferenceConfig获取新的DubboApi代理实例
        DubboApi dubboApi = reference.get();
        log.info("dubboApi {}", dubboApi.hashCode());
        return dubboApi;
    }

    private static String fetchIpAndPort(String userId, String serverName) {
        String cacheKey = userId + "_" + serverName;
        String ipAndPort = null;
        if (ServerNameEnum.isStateful(serverName)) {
            ipAndPort = userServerCache.get(cacheKey);
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
        }
        return ipAndPort;
    }

    /**
     * 移除指定用户的所有服务实例映射
     *
     * @param userId 用户ID
     */
    public static void removeUserCache(String userId) {
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
    public static void removeUserCache(String userId, String serverName) {
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
     *
     * @param userId     玩家id
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
     *
     * @param serverName 服务名称
     * @param ipAndPort  IP和端口
     * @return 缓存key
     */
    private static String generateCacheKey(String serverName, String ipAndPort) {
        return serverName + "_" + (ipAndPort != null ? ipAndPort : "random");
    }

    /**
     * 释放所有dubbo api实例
     * 目的是当服务shutdown的时候,释放当前服务Reference的实例,并且清除redis中玩家对应的ip和port
     */
    public static void clearLocalReferenceCache(String serverName, String ipAndPort) {
        log.info("开始清理服务 {} {} 的Dubbo引用和Redis中的玩家映射", serverName, ipAndPort);

        // 1. 释放所有dubbo api实例
        clearReferenceCacheByInstance(serverName, ipAndPort);

        // 2. 清理Redis中当前服务实例相关的玩家映射
        clearRedisCache(serverName, ipAndPort);

        log.info("服务 {} {} 清理完成", serverName, ipAndPort);
    }

    public static void clearLocalReferenceCache() {
        // 1. 清理Redis中当前服务实例相关的玩家映射
        String ipAndPort = DubboServiceUtil.getLocalServiceInstanceInfo();
        String serverName = CommonUtil.fetchLocalServerName();
        log.info("开始清理Redis中当前服务实例 serverName {} {} 的玩家映射", serverName, ipAndPort);
        clearRedisCache(serverName, ipAndPort);
        // 2. 释放所有dubbo api实例
        referenceCache.forEach((key, value) -> clearReferenceCache(key));
    }

    /**
     * 当服务实例下线时，清理对应的缓存
     *
     * @param serverName 服务名称
     * @param ipAndPort  IP和端口
     */
    public static void clearReferenceCacheByInstance(String serverName, String ipAndPort) {
        String referenceCacheKey = generateCacheKey(serverName, ipAndPort);
        clearReferenceCache(referenceCacheKey);
    }

    private static void clearReferenceCache(String referenceCacheKey) {
        Lock lock = locks.get(referenceCacheKey);
        if (lock != null) {
            lock.lock();
            try {
                ReferenceConfig<DubboApi> reference = referenceCache.get(referenceCacheKey);
                if (reference != null) {
                    try {
                        reference.destroy(); // 销毁ReferenceConfig
                        log.info("Destroyed ReferenceConfig for key: {}", referenceCacheKey);
                    } catch (Exception e) {
                        log.warn("Failed to destroy ReferenceConfig for key: {}", referenceCacheKey, e);
                    }
                }
                referenceCache.remove(referenceCacheKey);
                locks.remove(referenceCacheKey);
                log.info("Cleaned cache by instance: {}", referenceCacheKey);
            } catch (Exception e) {
                log.error("Error cleaning cache by instance: {}", referenceCacheKey, e);
            } finally {
                lock.unlock();
            }
        }
    }

    private static void clearRedisCache(String serverName, String ipAndPort) {
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
    }
}
