package demo01;

import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.shardingsphere.api.config.sharding.ShardingRuleConfiguration;
import org.apache.shardingsphere.api.config.sharding.TableRuleConfiguration;
import org.apache.shardingsphere.api.config.sharding.strategy.InlineShardingStrategyConfiguration;
import org.apache.shardingsphere.api.config.sharding.strategy.ShardingStrategyConfiguration;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.core.strategy.route.value.ListRouteValue;
import org.apache.shardingsphere.core.strategy.route.value.RouteValue;
import org.apache.shardingsphere.sharding.route.engine.condition.ShardingCondition;
import org.apache.shardingsphere.sharding.route.engine.condition.ShardingConditions;
import org.apache.shardingsphere.sharding.route.engine.condition.engine.WhereClauseShardingConditionEngine;
import org.apache.shardingsphere.sharding.route.engine.type.ShardingRouteEngine;
import org.apache.shardingsphere.sharding.route.engine.type.ShardingRouteEngineFactory;
import org.apache.shardingsphere.sharding.route.engine.type.complex.ShardingComplexRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.standard.ShardingStandardRoutingEngine;
import org.apache.shardingsphere.shardingjdbc.api.ShardingDataSourceFactory;
import org.apache.shardingsphere.sql.parser.binder.segment.select.groupby.GroupByContext;
import org.apache.shardingsphere.sql.parser.binder.segment.select.orderby.OrderByContext;
import org.apache.shardingsphere.sql.parser.binder.segment.select.pagination.PaginationContext;
import org.apache.shardingsphere.sql.parser.binder.segment.select.projection.ProjectionsContext;
import org.apache.shardingsphere.sql.parser.binder.statement.dml.SelectStatementContext;
import org.apache.shardingsphere.sql.parser.core.parser.SQLParserExecutor;
import org.apache.shardingsphere.sql.parser.core.visitor.ParseTreeVisitorFactory;
import org.apache.shardingsphere.sql.parser.core.visitor.VisitorRule;
import org.apache.shardingsphere.sql.parser.sql.statement.SQLStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dml.SelectStatement;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationProperties;
import org.apache.shardingsphere.underlying.route.context.RouteResult;
import org.junit.Before;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

/**
 * 路由引擎
 *
 * @author zhang_zhang
 * @Copyright © 2020 tiger Inc. All rights reserved.
 * @create 2020-06-03 16:23
 */
public class ShardingRouteEngineTest {

    private ShardingRule shardingRule;


    private TableRuleConfiguration orderRule(){
        // 配置Order表规则
        TableRuleConfiguration orderTableRuleConfig =
                new TableRuleConfiguration("t_order","ds${0..1}.t_order_${0..1}");

        // 配置分库 + 分表策略
        orderTableRuleConfig.setDatabaseShardingStrategyConfig(
                new InlineShardingStrategyConfiguration("user_id", "ds${user_id % 2}"));
        orderTableRuleConfig.setTableShardingStrategyConfig(
                new InlineShardingStrategyConfiguration("order_id", "t_order_${order_id % 2}"));
        return orderTableRuleConfig;
    }

    private TableRuleConfiguration orderItemRule(){
        // 配置Order表规则
        TableRuleConfiguration orderTableRuleConfig =
                new TableRuleConfiguration("t_order_item","ds${0..1}.t_order_item_${0..1}");

        // 配置分库 + 分表策略
        orderTableRuleConfig.setDatabaseShardingStrategyConfig(
                new InlineShardingStrategyConfiguration("user_id", "ds${user_id % 2}"));
        orderTableRuleConfig.setTableShardingStrategyConfig(
                new InlineShardingStrategyConfiguration("order_id", "t_order_item_${order_id % 2}"));
        return orderTableRuleConfig;

    }



    @Before
    public void init() throws SQLException {
        // 配置分片规则
        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();
        shardingRuleConfig.getTableRuleConfigs().add(orderRule());
        shardingRuleConfig.getTableRuleConfigs().add(orderItemRule());

        shardingRule = new ShardingRule(shardingRuleConfig, Arrays.asList("ds0", "ds1"));
    }


    public void test01(){

        String databaseTypeName = "MySQL";
        String sql = "SELECT * FROM t_order o  WHERE o.user_id=? AND o.order_id=? ";
        ParseTree parseTree = new SQLParserExecutor(databaseTypeName, sql).execute().getRootNode();
        SelectStatement sqlStatement = (SelectStatement) ParseTreeVisitorFactory.newInstance(databaseTypeName, VisitorRule.valueOf(parseTree.getClass())).visit(parseTree);
        System.out.println(sqlStatement);
        SelectStatementContext selectStatementContext = new SelectStatementContext(null, "", Collections.emptyList(), sqlStatement);

        //new ShardingConditions(new WhereClauseShardingConditionEngine(shardingRule, schemaMetaData).createShardingConditions(selectStatementContext, parameters));

        //new ShardingStandardRoutingEngine(tableNames, sqlStatementContext, shardingConditions, new Properties());

        //ShardingRouteEngine shardingRouteEngine = ShardingRouteEngineFactory.newInstance(shardingRule, metaData, sqlStatementContext, shardingConditions, properties);
        //RouteResult routeResult = shardingRouteEngine.route(shardingRule);



        ShardingStandardRoutingEngine standardRoutingEngine =
                createShardingStandardRoutingEngine("t_order", createShardingConditions("t_order"));

        //2.创建分片规则
        ShardingRule shardingRule = createBasedShardingRule();

        //3.使用路由引擎进行路由
        RouteResult routeResult = standardRoutingEngine.route(shardingRule);



    }

    protected final ShardingRule createBasedShardingRule() {
        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();

        shardingRuleConfig.getTableRuleConfigs().add(
                createInlineTableRuleConfig(
                        "t_order",
                        "ds_${0..1}.t_order_${0..1}",
                        "t_order_${order_id % 2}",
                        "ds_${user_id % 2}"));
        return new ShardingRule(shardingRuleConfig, Arrays.asList("ds_0", "ds_1"));
    }

    private TableRuleConfiguration createInlineTableRuleConfig(final String tableName, final String actualDataNodes, final String algorithmExpression, final String dsAlgorithmExpression) {

        TableRuleConfiguration result = new TableRuleConfiguration(tableName, actualDataNodes);
        result.setDatabaseShardingStrategyConfig(createInlineShardingStrategyConfiguration(dsAlgorithmExpression));
        result.setTableShardingStrategyConfig(createInlineShardingStrategyConfiguration(algorithmExpression));

        return result;
    }

    private InlineShardingStrategyConfiguration createInlineShardingStrategyConfiguration(final String algorithmExpression) {
        int startIndex = algorithmExpression.indexOf('{');
        int endIndex = algorithmExpression.indexOf('%');
        String shardingColumn = algorithmExpression.substring(startIndex + 1, endIndex).trim();
        return new InlineShardingStrategyConfiguration(shardingColumn, algorithmExpression);
    }




    private ShardingStandardRoutingEngine createShardingStandardRoutingEngine(final String logicTableName, final ShardingConditions shardingConditions) {
        return new ShardingStandardRoutingEngine(
                logicTableName,
                new SelectStatementContext(
                        new SelectStatement(),
                        new GroupByContext(Collections.emptyList(), 0),
                        new OrderByContext(Collections.emptyList(), false),
                        new ProjectionsContext(0, 0, false, Collections.emptyList()),
                        new PaginationContext(null, null, Collections.emptyList())),
                shardingConditions,
                new ConfigurationProperties(new Properties())
        );
    }

    protected final ShardingConditions createShardingConditions(final String tableName) {
        List<ShardingCondition> result = new ArrayList<>();

        /**
         * RouteValue接口实现类：AlwaysFalseRouteValue、ListRouteValue、RangeRouteValue
         */
        RouteValue shardingValue1 = new ListRouteValue<>("user_id", tableName, Arrays.asList(1L, 2L));
        RouteValue shardingValue2 = new ListRouteValue<>("order_id", tableName, Collections.singleton(22L));

        //将RouteValue包装成ShardingCondition，ShardingCondition对应多个RouteValue
        ShardingCondition shardingCondition = new ShardingCondition();
        shardingCondition.getRouteValues().add(shardingValue1);
        shardingCondition.getRouteValues().add(shardingValue2);
        result.add(shardingCondition);

        return new ShardingConditions(result);
    }

}