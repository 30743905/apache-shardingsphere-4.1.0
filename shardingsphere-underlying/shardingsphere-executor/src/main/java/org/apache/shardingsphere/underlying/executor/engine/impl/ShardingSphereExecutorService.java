/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.underlying.executor.engine.impl;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.Getter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * ShardingSphere executor service.
 */
@Getter
public final class ShardingSphereExecutorService {
    
    private static final String DEFAULT_NAME_FORMAT = "%d";
    
    private static final ExecutorService SHUTDOWN_EXECUTOR = Executors.newSingleThreadExecutor(ShardingSphereThreadFactoryBuilder.build("Executor-Engine-Closer"));
    
    private ListeningExecutorService executorService;
    
    public ShardingSphereExecutorService(final int executorSize) {
        this(executorSize, DEFAULT_NAME_FORMAT);
    }
    
    public ShardingSphereExecutorService(final int executorSize, final String nameFormat) {
        /**
         * MoreExecutors是guava提供的工具类，是对jdk自带的Executors工具类的扩展
         * MoreExecutors.listeningDecorator():把一个ExecutorService实例转成一个ListeningExecutorService实例
         * addDelayedShutdownHook():给线程池增加一个关闭钩子，在线程池中的线程是守护线程(daemon thread)，用户线程(user thread)执行完后，jvm等待一段时间再关闭，
         * 而是等待一定时间。正常情况下，只要用户线程一结束，jvm就会立即关闭，而不管守护线程任务是否执行完毕。
         *
         */
        executorService = MoreExecutors.listeningDecorator(getExecutorService(executorSize, nameFormat));
        MoreExecutors.addDelayedShutdownHook(executorService, 60, TimeUnit.SECONDS);
    }
    
    private ExecutorService getExecutorService(final int executorSize, final String nameFormat) {
        ThreadFactory threadFactory = ShardingSphereThreadFactoryBuilder.build(nameFormat);
        return 0 == executorSize ? Executors.newCachedThreadPool(threadFactory) : Executors.newFixedThreadPool(executorSize, threadFactory);
    }
    
    /**
     * Close executor service.
     */
    public void close() {
        SHUTDOWN_EXECUTOR.execute(() -> {
            try {
                executorService.shutdown();
                while (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
