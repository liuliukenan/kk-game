package com.kkgame.util;

import cn.hutool.core.thread.NamedThreadFactory;
import com.google.protobuf.util.JsonFormat;
import com.kkgame.api.DubboApi;
import com.kkgame.manager.ClientApiManager;
import com.kkgame.protobuf.MessageData;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 消息处理器，确保同一用户的消息按顺序处理
 */
@Slf4j
public class MessageProcessor {

    // 单例实例
    private static volatile MessageProcessor instance;

    // 线程池，用于处理用户消息
    private final ExecutorService[] threadPool;

    // 线程数
    private final int threadCount = 10;

    // 私有构造函数，防止外部实例化
    private MessageProcessor() {
        threadPool = new ExecutorService[threadCount];
        for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
            threadPool[threadIndex] = createSingleThreadExecutor(threadIndex);
        }
    }

    /**
     * 获取单例实例
     * @return MessageProcessor实例
     */
    private static MessageProcessor getInstance() {
        if (instance == null) {
            synchronized (MessageProcessor.class) {
                if (instance == null) {
                    instance = new MessageProcessor();
                }
            }
        }
        return instance;
    }

    /**
     * 静态方法，处理protobuf消息，确保同一用户的消息按顺序处理
     * @param userId 用户ID
     * @param serverName 服务名称
     * @param messageData 消息数据
     */
    public static void sendMessage(String userId, String serverName, MessageData messageData) {
        getInstance().processProtoMessage(userId, serverName, messageData);
    }

    /**
     * 创建单线程执行器，并添加监控和重启机制
     * @param threadIndex 线程索引
     * @return ExecutorService执行器
     */
    private ExecutorService createSingleThreadExecutor(int threadIndex) {
        ThreadFactory threadFactory = new NamedThreadFactory("user-message-processor-" + threadIndex, true);
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()) {

            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);
                if (t != null) {
                    log.error("线程{}执行任务时发生异常", threadIndex, t);
                    // 如果线程异常退出，重新创建一个新的线程执行器
                    recreateThreadPool(threadIndex);
                }
            }
        };
    }

    /**
     * 重新创建线程池
     * @param threadIndex 线程索引
     */
    private synchronized void recreateThreadPool(int threadIndex) {
        if (threadPool[threadIndex] != null && !threadPool[threadIndex].isShutdown()) {
            threadPool[threadIndex].shutdown();
        }
        threadPool[threadIndex] = createSingleThreadExecutor(threadIndex);
        log.info("已重新创建线程池，索引：{}", threadIndex);
    }

    /**
     * 处理protobuf消息，确保同一用户的消息按顺序处理
     * @param userId 用户ID
     * @param serverName 服务名称
     * @param messageData 消息数据
     */
    public void processProtoMessage(String userId, String serverName, MessageData messageData) {
        processMessageInOrder(userId, serverName, api -> {
            try {
                log.info("转发给{}服务, userId: {}, message: {}", serverName, userId, JsonFormat.printer().print(messageData));
                api.processMessageProto(messageData.toByteArray());
            } catch (Exception e) {
                log.error("处理用户 {} 的消息时发生异常", userId, e);
            }
        });
    }

    /**
     * 按顺序处理用户消息
     * @param userId 用户ID
     * @param serverName 服务名称
     * @param messageHandler 消息处理函数
     */
    public void processMessageInOrder(String userId, String serverName, Consumer<DubboApi> messageHandler) {
        // 创建一个可运行的任务
        Runnable task = () -> {
            try {
                DubboApi api = ClientApiManager.fetchClientApi(userId, serverName);
                if (api != null) {
                    messageHandler.accept(api);
                }
            } catch (Exception e) {
                log.error("处理用户 {} 的消息时发生异常", userId, e);
            }
        };

        // 根据userId选择固定的线程来保证同一用户的消息按顺序处理
        int threadIndex = userId.hashCode() % threadCount;
        if (threadIndex < 0) {
            threadIndex = -threadIndex;
        }
        // 提交任务到指定的线程执行
        try {
            threadPool[threadIndex].execute(task);
        } catch (RejectedExecutionException e) {
            log.error("线程池{}已关闭，无法提交任务", threadIndex);
            // 如果执行被拒绝，尝试重新创建线程池并重新提交任务
            recreateThreadPool(threadIndex);
            threadPool[threadIndex].execute(task);
        }
    }


}
