package demo01;

import org.apache.shardingsphere.api.config.sharding.ShardingRuleConfiguration;
import org.apache.shardingsphere.api.config.sharding.TableRuleConfiguration;
import org.apache.shardingsphere.api.config.sharding.strategy.InlineShardingStrategyConfiguration;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.core.yaml.config.sharding.YamlRootShardingConfiguration;
import org.apache.shardingsphere.core.yaml.swapper.ShardingRuleConfigurationYamlSwapper;
import org.apache.shardingsphere.sharding.rewrite.context.ShardingSQLRewriteContextDecorator;
import org.apache.shardingsphere.sharding.route.engine.ShardingRouteDecorator;
import org.apache.shardingsphere.sql.parser.SQLParserEngine;
import org.apache.shardingsphere.sql.parser.SQLParserEngineFactory;
import org.apache.shardingsphere.sql.parser.binder.metadata.column.ColumnMetaData;
import org.apache.shardingsphere.sql.parser.binder.metadata.index.IndexMetaData;
import org.apache.shardingsphere.sql.parser.binder.metadata.schema.SchemaMetaData;
import org.apache.shardingsphere.sql.parser.binder.metadata.table.TableMetaData;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationProperties;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationPropertyKey;
import org.apache.shardingsphere.underlying.common.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.underlying.common.metadata.datasource.DataSourceMetas;
import org.apache.shardingsphere.underlying.rewrite.context.SQLRewriteContext;
import org.apache.shardingsphere.underlying.rewrite.engine.SQLRewriteResult;
import org.apache.shardingsphere.underlying.rewrite.engine.SQLRouteRewriteEngine;
import org.apache.shardingsphere.underlying.route.DataNodeRouter;
import org.apache.shardingsphere.underlying.route.context.RouteContext;
import org.junit.Test;

import java.io.IOException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 你搞忘写注释了
 *
 * @author zhang_zhang
 * @Copyright © 2020 tiger Inc. All rights reserved.
 * @create 2020-06-04 10:27
 */
public class SQLRouteRewriteEngineTest {

    @Test
    public void createSQLRewriteResults() throws IOException {

        //1.创建分库分表规则
        ShardingRule shardingRule = shardingRule();

        //2.创建sql解析引擎
        SQLParserEngine sqlParserEngine = SQLParserEngineFactory.getSQLParserEngine("MySQL");

        //3.创建metaData
        ShardingSphereMetaData metaData = createShardingSphereMetaData();

        Properties props = new Properties();
        props.put(ConfigurationPropertyKey.SQL_SHOW, true);
        ConfigurationProperties properties = new ConfigurationProperties(props);

        //String sql = "SELECT * FROM t_order o  WHERE (o.user_id=? or o.user_id=?) AND o.order_id=? and o.status=? ";
        String sql = "SELECT * FROM t_order o  WHERE o.user_id=? AND (o.order_id=? or o.order_id=?) and o.status=? ";
        List<Object> parameters = Arrays.asList(11L, 22L, 33);

        RouteContext routeContext = new DataNodeRouter(metaData, properties, sqlParserEngine).route(sql, parameters, false);
        //4.创建路由引擎进行路由解析
        ShardingRouteDecorator shardingRouteDecorator = new ShardingRouteDecorator();
        routeContext = shardingRouteDecorator.decorate(routeContext, metaData, shardingRule, properties);

        //5.SQL重写引擎
        SQLRewriteContext sqlRewriteContext = new SQLRewriteContext(
                mock(SchemaMetaData.class), routeContext.getSqlStatementContext(), sql, parameters);

        ShardingSQLRewriteContextDecorator shardingSQLRewriteContextDecorator = new ShardingSQLRewriteContextDecorator();
        shardingSQLRewriteContextDecorator.setRouteContext(routeContext);
        shardingSQLRewriteContextDecorator.decorate(shardingRule, properties, sqlRewriteContext);
        sqlRewriteContext.generateSQLTokens();
        Collection<SQLRewriteResult> ret = new SQLRouteRewriteEngine().rewrite(sqlRewriteContext, routeContext.getRouteResult()).values();
        ret.stream().forEach(x -> {
            System.out.println(x.getSql());
        });

    }

    private ShardingRule shardingRule(){
        //1.创建ShardingRuleConfiguration
        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();
        shardingRuleConfig.getTableRuleConfigs().add(orderRule());
        shardingRuleConfig.getTableRuleConfigs().add(orderItemRule());

        List<String> dbs = Arrays.asList("ds0", "ds1");
        return new ShardingRule(shardingRuleConfig, dbs);
    }


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


    private ShardingSphereMetaData createShardingSphereMetaData() {
        SchemaMetaData schemaMetaData = mock(SchemaMetaData.class);
        when(schemaMetaData.getAllTableNames()).thenReturn(Arrays.asList("t_order", "t_order_item"));
        TableMetaData orderTableMetaData = mock(TableMetaData.class);
        when(orderTableMetaData.getColumns()).thenReturn(createColumnMetaDataMap());
        Map<String, IndexMetaData> indexMetaDataMap = new HashMap<>(1, 1);
        indexMetaDataMap.put("index_name", new IndexMetaData("index_name"));
        when(orderTableMetaData.getIndexes()).thenReturn(indexMetaDataMap);
        when(schemaMetaData.containsTable("t_order")).thenReturn(true);
        when(schemaMetaData.get("t_order")).thenReturn(orderTableMetaData);
        when(schemaMetaData.get("t_order_item")).thenReturn(mock(TableMetaData.class));
        when(schemaMetaData.getAllColumnNames("t_order")).thenReturn(Arrays.asList("user_id", "order_id", "index_name", "status"));
        return new ShardingSphereMetaData(mock(DataSourceMetas.class), schemaMetaData);
    }

    private Map<String, ColumnMetaData> createColumnMetaDataMap() {
        Map<String, ColumnMetaData> result = new LinkedHashMap<>();
        result.put("user_id", new ColumnMetaData("user_id", Types.INTEGER, "INT", true, true, false));
        result.put("order_id", mock(ColumnMetaData.class));
        result.put("index_name", mock(ColumnMetaData.class));
        result.put("status", mock(ColumnMetaData.class));
        return result;
    }

}