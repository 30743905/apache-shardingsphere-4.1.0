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

package org.apache.shardingsphere.sharding.execute.sql.prepare;

import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.underlying.executor.constant.ConnectionMode;
import org.apache.shardingsphere.underlying.executor.engine.InputGroup;
import org.apache.shardingsphere.sharding.execute.sql.StatementExecuteUnit;
import org.apache.shardingsphere.underlying.executor.context.ExecutionUnit;
import org.apache.shardingsphere.underlying.executor.context.SQLUnit;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * SQL execute prepare template.
 */
@RequiredArgsConstructor
public final class SQLExecutePrepareTemplate {
    
    private final int maxConnectionsSizePerQuery;
    
    /**
     * Get execute unit groups.
     *
     * @param executionUnits execution units
     * @param callback SQL execute prepare callback
     * @return statement execute unit groups
     * @throws SQLException SQL exception
     */
    public Collection<InputGroup<StatementExecuteUnit>> getExecuteUnitGroups(final Collection<ExecutionUnit> executionUnits, final SQLExecutePrepareCallback callback) throws SQLException {
        return getSynchronizedExecuteUnitGroups(executionUnits, callback);
    }
    
    private Collection<InputGroup<StatementExecuteUnit>> getSynchronizedExecuteUnitGroups(
            final Collection<ExecutionUnit> executionUnits, final SQLExecutePrepareCallback callback) throws SQLException {
        //getSQLUnitGroups()对执行的sql进行分组，Map.key:数据库名称，value：同一个数据下的所有SQLUnit列表
        Map<String, List<SQLUnit>> sqlUnitGroups = getSQLUnitGroups(executionUnits);
        Collection<InputGroup<StatementExecuteUnit>> result = new LinkedList<>();
        for (Entry<String, List<SQLUnit>> entry : sqlUnitGroups.entrySet()) {
            result.addAll(getSQLExecuteGroups(entry.getKey(), entry.getValue(), callback));
        }
        /**
         * 已数据库维度进行分组，每个库被封装成一个组：InputGroup
         * InputGroup包装的是一个StatementExecuteUnit集合，即一个数据库上可能需要执行多条sql，每条sql被封装成一个StatementExecuteUnit对象
         * StatementExecuteUnit包含三个元素：
         *     executionUnit：执行单元
         *     statement：根据执行单元创建的具体Statement
         *     connectionMode：数据库模式
         */
        return result;
    }
    
    private Map<String, List<SQLUnit>> getSQLUnitGroups(final Collection<ExecutionUnit> executionUnits) {
        Map<String, List<SQLUnit>> result = new LinkedHashMap<>(executionUnits.size(), 1);
        for (ExecutionUnit each : executionUnits) {
            if (!result.containsKey(each.getDataSourceName())) {
                result.put(each.getDataSourceName(), new LinkedList<>());
            }
            result.get(each.getDataSourceName()).add(each.getSqlUnit());
        }
        return result;
    }
    
    private List<InputGroup<StatementExecuteUnit>> getSQLExecuteGroups(final String dataSourceName,
                                                                       final List<SQLUnit> sqlUnits, final SQLExecutePrepareCallback callback) throws SQLException {
        List<InputGroup<StatementExecuteUnit>> result = new LinkedList<>();

        //在maxConnectionSizePerQuery允许的范围内，当一个连接需要执行的请求数量大于1时，意味着当前的数据库连接无法持有相应的数据结果集，则必须采用内存归并；
        //反之，当一个连接需要执行的请求数量等于1时，意味着当前的数据库连接可以持有相应的数据结果集，则可以采用流式归并

        //计算所需要的分区大小
        int desiredPartitionSize = Math.max(0 == sqlUnits.size() % maxConnectionsSizePerQuery ? sqlUnits.size() / maxConnectionsSizePerQuery : sqlUnits.size() / maxConnectionsSizePerQuery + 1, 1);
        List<List<SQLUnit>> sqlUnitPartitions = Lists.partition(sqlUnits, desiredPartitionSize);
        ConnectionMode connectionMode = maxConnectionsSizePerQuery < sqlUnits.size() ? ConnectionMode.CONNECTION_STRICTLY : ConnectionMode.MEMORY_STRICTLY;
        List<Connection> connections = callback.getConnections(connectionMode, dataSourceName, sqlUnitPartitions.size());
        int count = 0;
        for (List<SQLUnit> each : sqlUnitPartitions) {
            result.add(getSQLExecuteGroup(connectionMode, connections.get(count++), dataSourceName, each, callback));
        }
        return result;
    }
    
    private InputGroup<StatementExecuteUnit> getSQLExecuteGroup(final ConnectionMode connectionMode, final Connection connection,
                                                                final String dataSourceName, final List<SQLUnit> sqlUnitGroup, final SQLExecutePrepareCallback callback) throws SQLException {
        List<StatementExecuteUnit> result = new LinkedList<>();
        for (SQLUnit each : sqlUnitGroup) {
            result.add(callback.createStatementExecuteUnit(connection, new ExecutionUnit(dataSourceName, each), connectionMode));
        }
        return new InputGroup<>(result);
    }
}
