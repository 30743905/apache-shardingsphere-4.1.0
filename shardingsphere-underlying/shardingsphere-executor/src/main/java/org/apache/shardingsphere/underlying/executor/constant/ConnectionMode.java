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

package org.apache.shardingsphere.underlying.executor.constant;

/**
 * Connection Mode.
 */
public enum ConnectionMode {
    /**
     * MEMORY_STRICTLY：代理会保持一个数据库中所有被路由到的表的连接，这种方式的好处是利用流式ResultSet来节省内存
     * CONNECTION_STRICTLY：代理在取出ResultSet中的所有数据后会释放连接，同时，内存的消耗将会增加
     *
     * ConnectionMode有两种模式：内存限制(MEMORY_STRICTLY)和连接限制(CONNECTION_STRICTLY)，本质是一种资源隔离，保护服务器资源不被消耗殆尽。
     *      如果一个连接执行多个 StatementExecuteUnit 则为内存限制(MEMORY_STRICTLY)，采用流式处理，即 StreamQueryResult ，
     *      反之则为连接限制(CONNECTION_STRICTLY)，此时会将所有从 MySQL 服务器返回的数据都加载到内存中。特别是在 Sharding-Proxy 中特别有用，避免将代理服务器撑爆
     *
     */
    MEMORY_STRICTLY, CONNECTION_STRICTLY
}
