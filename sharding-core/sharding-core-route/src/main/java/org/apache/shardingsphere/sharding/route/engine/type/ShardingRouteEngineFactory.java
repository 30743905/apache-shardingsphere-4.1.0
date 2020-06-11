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

package org.apache.shardingsphere.sharding.route.engine.type;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.sharding.route.engine.condition.ShardingConditions;
import org.apache.shardingsphere.sharding.route.engine.type.broadcast.ShardingDataSourceGroupBroadcastRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.broadcast.ShardingDatabaseBroadcastRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.broadcast.ShardingMasterInstanceBroadcastRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.broadcast.ShardingTableBroadcastRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.complex.ShardingComplexRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.defaultdb.ShardingDefaultDatabaseRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.ignore.ShardingIgnoreRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.standard.ShardingStandardRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.unicast.ShardingUnicastRoutingEngine;
import org.apache.shardingsphere.sql.parser.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.sql.parser.sql.statement.SQLStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dal.DALStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dal.SetStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dal.dialect.mysql.ShowDatabasesStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dal.dialect.mysql.UseStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dal.dialect.postgresql.ResetParameterStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dcl.DCLStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.ddl.DDLStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dml.DMLStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dml.SelectStatement;
import org.apache.shardingsphere.sql.parser.binder.type.TableAvailable;
import org.apache.shardingsphere.sql.parser.sql.statement.tcl.TCLStatement;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationProperties;
import org.apache.shardingsphere.underlying.common.metadata.ShardingSphereMetaData;

import java.util.Collection;

/**
 * Sharding routing engine factory.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShardingRouteEngineFactory {
    
    /**
     * Create new instance of routing engine.
     * 
     * @param shardingRule sharding rule
     * @param metaData meta data of ShardingSphere
     * @param sqlStatementContext SQL statement context
     * @param shardingConditions shardingConditions
     * @param properties sharding sphere properties
     * @return new instance of routing engine
     */
    public static ShardingRouteEngine newInstance(final ShardingRule shardingRule,
                                                  final ShardingSphereMetaData metaData, final SQLStatementContext sqlStatementContext,
                                                  final ShardingConditions shardingConditions, final ConfigurationProperties properties) {
        SQLStatement sqlStatement = sqlStatementContext.getSqlStatement();
        Collection<String> tableNames = sqlStatementContext.getTablesContext().getTableNames();
        //全库路由
        if (sqlStatement instanceof TCLStatement) {
            return new ShardingDatabaseBroadcastRoutingEngine();
        }
        //全库表路由
        if (sqlStatement instanceof DDLStatement) {
            return new ShardingTableBroadcastRoutingEngine(metaData.getSchema(), sqlStatementContext);
        }
        //阻断路由
        if (sqlStatement instanceof DALStatement) {
            return getDALRoutingEngine(shardingRule, sqlStatement, tableNames);
        }
        //全实例路由
        if (sqlStatement instanceof DCLStatement) {
            return getDCLRoutingEngine(sqlStatementContext, metaData);
        }
        //默认库路由
        if (shardingRule.isAllInDefaultDataSource(tableNames)) {
            return new ShardingDefaultDatabaseRoutingEngine(tableNames);
        }
        //全库路由
        if (shardingRule.isAllBroadcastTables(tableNames)) {
            return sqlStatement instanceof SelectStatement ? new ShardingUnicastRoutingEngine(tableNames) : new ShardingDatabaseBroadcastRoutingEngine();
        }
        //默认库路由
        if (sqlStatementContext.getSqlStatement() instanceof DMLStatement && tableNames.isEmpty() && shardingRule.hasDefaultDataSourceName()) {
            return new ShardingDefaultDatabaseRoutingEngine(tableNames);
        }
        //单播路由
        if (sqlStatementContext.getSqlStatement() instanceof DMLStatement && shardingConditions.isAlwaysFalse() || tableNames.isEmpty() || !shardingRule.tableRuleExists(tableNames)) {
            return new ShardingUnicastRoutingEngine(tableNames);
        }
        //分片路由
        return getShardingRoutingEngine(shardingRule, sqlStatementContext, shardingConditions, tableNames, properties);
    }
    
    private static ShardingRouteEngine getDALRoutingEngine(final ShardingRule shardingRule, final SQLStatement sqlStatement, final Collection<String> tableNames) {
        if (sqlStatement instanceof UseStatement) {
            return new ShardingIgnoreRoutingEngine();
        }
        if (sqlStatement instanceof SetStatement || sqlStatement instanceof ResetParameterStatement || sqlStatement instanceof ShowDatabasesStatement) {
            return new ShardingDatabaseBroadcastRoutingEngine();
        }
        if (!tableNames.isEmpty() && !shardingRule.tableRuleExists(tableNames) && shardingRule.hasDefaultDataSourceName()) {
            return new ShardingDefaultDatabaseRoutingEngine(tableNames);
        }
        if (!tableNames.isEmpty()) {
            return new ShardingUnicastRoutingEngine(tableNames);
        }
        return new ShardingDataSourceGroupBroadcastRoutingEngine();
    }
    
    private static ShardingRouteEngine getDCLRoutingEngine(final SQLStatementContext sqlStatementContext, final ShardingSphereMetaData metaData) {
        return isDCLForSingleTable(sqlStatementContext) 
                ? new ShardingTableBroadcastRoutingEngine(metaData.getSchema(), sqlStatementContext) : new ShardingMasterInstanceBroadcastRoutingEngine(metaData.getDataSources());
    }
    
    private static boolean isDCLForSingleTable(final SQLStatementContext sqlStatementContext) {
        if (sqlStatementContext instanceof TableAvailable) {
            TableAvailable tableSegmentsAvailable = (TableAvailable) sqlStatementContext;
            return 1 == tableSegmentsAvailable.getAllTables().size() && !"*".equals(tableSegmentsAvailable.getAllTables().iterator().next().getTableName().getIdentifier().getValue());
        }
        return false;
    }
    
    private static ShardingRouteEngine getShardingRoutingEngine(final ShardingRule shardingRule, final SQLStatementContext sqlStatementContext,
                                                                final ShardingConditions shardingConditions, final Collection<String> tableNames, final ConfigurationProperties properties) {
        /**
         * 根据解析出来逻辑表获取分片表，如:SELECT i.* FROM t_order o, t_order_item i WHERE o.order_id = i.order_id and o.order_id = ?
         * 则shardingTableNames为t_order，t_order_item
         */
        Collection<String> shardingTableNames = shardingRule.getShardingLogicTableNames(tableNames);
        /**
         * 满足以下两个条件走标准路由，否则走复合路由
         *      1、是否只有一张分片表
         *      2、绑定的逻辑表（配置绑定表）是否包含所有分片表
         */
        if (1 == shardingTableNames.size() || shardingRule.isAllBindingTables(shardingTableNames)) {
            //标准路由，获取第一张表路由即可，参考官网案例说明
            return new ShardingStandardRoutingEngine(shardingTableNames.iterator().next(), sqlStatementContext, shardingConditions, properties);
        }
        // TODO config for cartesian set
        //复合路由
        return new ShardingComplexRoutingEngine(tableNames, sqlStatementContext, shardingConditions, properties);
    }
}
