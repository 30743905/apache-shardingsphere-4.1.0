package demo01;


import com.google.common.base.Preconditions;

import com.zaxxer.hikari.HikariDataSource;

import org.apache.shardingsphere.api.config.sharding.ShardingRuleConfiguration;
import org.apache.shardingsphere.api.config.sharding.TableRuleConfiguration;
import org.apache.shardingsphere.api.config.sharding.strategy.InlineShardingStrategyConfiguration;
import org.apache.shardingsphere.core.metadata.ShardingMetaDataLoader;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.encrypt.api.EncryptRuleConfiguration;
import org.apache.shardingsphere.sharding.execute.sql.StatementExecuteUnit;
import org.apache.shardingsphere.sharding.execute.sql.prepare.SQLExecutePrepareCallback;
import org.apache.shardingsphere.sharding.execute.sql.prepare.SQLExecutePrepareTemplate;
import org.apache.shardingsphere.sharding.rewrite.context.ShardingSQLRewriteContextDecorator;
import org.apache.shardingsphere.sharding.route.engine.ShardingRouteDecorator;
import org.apache.shardingsphere.shardingjdbc.api.EncryptDataSourceFactory;
import org.apache.shardingsphere.shardingjdbc.executor.PreparedStatementExecutor;
import org.apache.shardingsphere.shardingjdbc.jdbc.adapter.invocation.SetParameterMethodInvocation;
import org.apache.shardingsphere.sql.parser.SQLParserEngine;
import org.apache.shardingsphere.sql.parser.SQLParserEngineFactory;
import org.apache.shardingsphere.sql.parser.binder.metadata.schema.SchemaMetaData;
import org.apache.shardingsphere.underlying.common.config.DatabaseAccessConfiguration;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationProperties;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationPropertyKey;
import org.apache.shardingsphere.underlying.common.database.type.dialect.MySQLDatabaseType;
import org.apache.shardingsphere.underlying.common.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.underlying.common.metadata.datasource.DataSourceMetas;
import org.apache.shardingsphere.underlying.common.rule.DataNode;
import org.apache.shardingsphere.underlying.executor.constant.ConnectionMode;
import org.apache.shardingsphere.underlying.executor.context.ExecutionContext;
import org.apache.shardingsphere.underlying.executor.context.ExecutionUnit;
import org.apache.shardingsphere.underlying.executor.context.SQLUnit;
import org.apache.shardingsphere.underlying.executor.engine.InputGroup;
import org.apache.shardingsphere.underlying.executor.log.SQLLogger;
import org.apache.shardingsphere.underlying.rewrite.context.SQLRewriteContext;
import org.apache.shardingsphere.underlying.rewrite.engine.SQLRewriteResult;
import org.apache.shardingsphere.underlying.rewrite.engine.SQLRouteRewriteEngine;
import org.apache.shardingsphere.underlying.route.DataNodeRouter;
import org.apache.shardingsphere.underlying.route.context.RouteContext;
import org.apache.shardingsphere.underlying.route.context.RouteResult;
import org.apache.shardingsphere.underlying.route.context.RouteUnit;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;


/**
 * @author Administrator
 * @Copyright © 2020 tiger Inc. All rights reserved.
 * @create 2020-06-07 21:30
 */
@Slf4j
public class SQLRouteRewriteEngine02Test {

    private Map<String, DataSource> dataSourceMap = new HashMap<>();
    private ShardingRule shardingRule;
    private MySQLDatabaseType databaseType = new MySQLDatabaseType();

    /**
     * 创建数据源
     * @return
     */
    private void dataSourceMap(){
        // 配置第一个数据源
        HikariDataSource dataSource1 = new HikariDataSource();
        dataSource1.setJdbcUrl("jdbc:mysql://localhost:3306/ds0?serverTimezone=UTC");
        dataSource1.setUsername("root");
        dataSource1.setPassword("123456");
        dataSourceMap.put("ds0", dataSource1);

        // 配置第二个数据源
        HikariDataSource dataSource2 = new HikariDataSource();
        dataSource2.setJdbcUrl("jdbc:mysql://localhost:3306/ds1?serverTimezone=UTC");
        dataSource2.setUsername("root");
        dataSource2.setPassword("123456");
        dataSourceMap.put("ds1", dataSource2);
    }

    private void shardingRule(){
        //1.创建ShardingRuleConfiguration
        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();
        shardingRuleConfig.getTableRuleConfigs().add(orderRule());
        shardingRuleConfig.getTableRuleConfigs().add(orderItemRule());

        List<String> dbs = Arrays.asList("ds0", "ds1");
        this.shardingRule = new ShardingRule(shardingRuleConfig, dbs);
    }


    private TableRuleConfiguration orderRule(){
        // 配置Order表规则
        TableRuleConfiguration orderTableRuleConfig =
                new TableRuleConfiguration("t_order","ds${0..1}.t_order_${0..3}");

        // 配置分库 + 分表策略
        orderTableRuleConfig.setDatabaseShardingStrategyConfig(
                new InlineShardingStrategyConfiguration("user_id", "ds${user_id % 2}"));
        orderTableRuleConfig.setTableShardingStrategyConfig(
                new InlineShardingStrategyConfiguration("order_id", "t_order_${order_id % 4}"));
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


    /**
     * 解析出数据库元信息DataSourceMetas和表元信息SchemaMetaData
     * @return
     * @throws SQLException
     */
    private ShardingSphereMetaData createShardingSphereMetaData() throws SQLException {


        Map<String, DatabaseAccessConfiguration> result = new LinkedHashMap<>(dataSourceMap.size(), 1);
        for (Map.Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
            DataSource dataSource = entry.getValue();
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                result.put(entry.getKey(), new DatabaseAccessConfiguration(metaData.getURL(), metaData.getUserName(), null));
            }
        }

        DataSourceMetas dataSourceMetas = new DataSourceMetas(databaseType, result);



        SchemaMetaData schemaMetaData =
                new ShardingMetaDataLoader(dataSourceMap, shardingRule, 1, false)
                        .load(databaseType);

        return new ShardingSphereMetaData(dataSourceMetas, schemaMetaData);
    }


    @Before
    public void init(){
        this.dataSourceMap();
        this.shardingRule();
    }


    @Test
    public void createSQLRewriteResults() throws Exception {

        //2.创建sql解析引擎
        SQLParserEngine sqlParserEngine = SQLParserEngineFactory.getSQLParserEngine(databaseType.getName());

        //3.创建metaData
        ShardingSphereMetaData metaData = createShardingSphereMetaData();

        Properties props = new Properties();
        props.put(ConfigurationPropertyKey.SQL_SHOW, true);
        ConfigurationProperties properties = new ConfigurationProperties(props);

        //String sql = "SELECT * FROM t_order o  WHERE (o.user_id=? or o.user_id=?) AND o.order_id=? and o.status=? ";
        String sql = "SELECT user_id, order_id, status, other01 FROM t_order o  WHERE (o.user_id = ? or o.user_id = ?) and (o.order_id = ? or o.order_id = ?)";
        List<Object> parameters = Arrays.asList(1L, 2L, 2L, 5L);

        //String sql = "show tables from ds1";
        //List<Object> parameters = Arrays.asList();

        //String sql = "SELECT * FROM t_order o  WHERE (o.user_id = ? or o.user_id = ?)";
        //List<Object> parameters = Arrays.asList(1L, 2L);

        /**
         * RouteContext主要包含三个元素：sqlStatementContext、parameters和RouteResult
         * 1、DataNodeRouter#route()主要完成前面两个填充工作，利用parserEngine.parse(sql, useCache)将sql解析SQLStatement，然后包装成SQLStatementContext；
         *      SQLStatementContextFactory.newInstance(metaData.getSchema(), sql, parameters, sqlStatement)
         * 2、创建RouteContext对象：new RouteContext(sqlStatementContext, parameters, new RouteResult())
         *      注意：RouteContext对象中sqlStatementContext、parameters被填充完成，routeResult虽然也被填充，但是填充的是一个空的，
         *      RouteResult中的routeUnits和originalDataNodes还都没有被填充，这才是接下来SQL路由主要需要完成的工作
         *
         *  这里创建的RouteContext没有填充真正的分库分表数据，这里主要封装sqlStatementContext、parameters参数进行传递
         */
        RouteContext routeContext = new DataNodeRouter(metaData, properties, sqlParserEngine).route(sql, parameters, false);
        //4.创建路由引擎进行路由解析
        ShardingRouteDecorator shardingRouteDecorator = new ShardingRouteDecorator();
        //这里会真正进行分库分表，并返回包装这些信息的RouteContext对象，注意：这里是新创建的，和DataNodeRouter#route()方法返回的RouteContext不是同一个对象
        routeContext = shardingRouteDecorator.decorate(routeContext, metaData, shardingRule, properties);

        //5.SQL重写引擎

        SQLRewriteContext sqlRewriteContext = new SQLRewriteContext(metaData.getSchema(), routeContext.getSqlStatementContext(), sql, parameters);

        ShardingSQLRewriteContextDecorator shardingSQLRewriteContextDecorator = new ShardingSQLRewriteContextDecorator();
        shardingSQLRewriteContextDecorator.setRouteContext(routeContext);
        /**
         * decorate()方法中通过调用addSQLTokenGenerators()给sqlRewriteContext添加SQLTokenGenerator列表
         */
        shardingSQLRewriteContextDecorator.decorate(shardingRule, properties, sqlRewriteContext);
        sqlRewriteContext.generateSQLTokens();

        Set<Map.Entry<RouteUnit, SQLRewriteResult>> entries = new SQLRouteRewriteEngine().rewrite(sqlRewriteContext, routeContext.getRouteResult()).entrySet();
        for(Map.Entry<RouteUnit, SQLRewriteResult> entry : entries){
            log.info("db:{}, sql:{}", entry.getKey().getDataSourceMapper().getActualName(), entry.getValue().getSql());
        }


    }


    @Test
    public void createSQLRewriteResultsV2() throws Exception {

        //2.创建sql解析引擎
        SQLParserEngine sqlParserEngine = SQLParserEngineFactory.getSQLParserEngine(databaseType.getName());

        //3.创建metaData
        ShardingSphereMetaData metaData = createShardingSphereMetaData();

        Properties props = new Properties();
        props.put(ConfigurationPropertyKey.SQL_SHOW.getKey(), true);
        ConfigurationProperties properties = new ConfigurationProperties(props);

        //String sql = "SELECT * FROM t_order o  WHERE (o.user_id=? or o.user_id=?) AND o.order_id=? and o.status=? ";
        String sql = "SELECT user_id, order_id, status, other01 FROM t_order o  WHERE (o.user_id = ? or o.user_id = ?) and (o.order_id = ? or o.order_id = ?)";
        List<Object> parameters = Arrays.asList(1L, 2L, 2L, 5L);

        //String sql = "show tables from ds1";
        //List<Object> parameters = Arrays.asList();

        //String sql = "SELECT * FROM t_order o  WHERE (o.user_id = ? or o.user_id = ?)";
        //List<Object> parameters = Arrays.asList(1L, 2L);

        /**
         * RouteContext主要包含三个元素：sqlStatementContext、parameters和RouteResult
         * 1、DataNodeRouter#route()主要完成前面两个填充工作，利用parserEngine.parse(sql, useCache)将sql解析SQLStatement，然后包装成SQLStatementContext；
         *      SQLStatementContextFactory.newInstance(metaData.getSchema(), sql, parameters, sqlStatement)
         * 2、创建RouteContext对象：new RouteContext(sqlStatementContext, parameters, new RouteResult())
         *      注意：RouteContext对象中sqlStatementContext、parameters被填充完成，routeResult虽然也被填充，但是填充的是一个空的，
         *      RouteResult中的routeUnits和originalDataNodes还都没有被填充，这才是接下来SQL路由主要需要完成的工作
         *
         *  这里创建的RouteContext没有填充真正的分库分表数据，这里主要封装sqlStatementContext、parameters参数进行传递
         */
        RouteContext routeContext = new DataNodeRouter(metaData, properties, sqlParserEngine).route(sql, parameters, false);
        //4.创建路由引擎进行路由解析
        ShardingRouteDecorator shardingRouteDecorator = new ShardingRouteDecorator();
        //这里会真正进行分库分表，并返回包装这些信息的RouteContext对象，注意：这里是新创建的，和DataNodeRouter#route()方法返回的RouteContext不是同一个对象
        routeContext = shardingRouteDecorator.decorate(routeContext, metaData, shardingRule, properties);




        //5.SQL重写引擎

        SQLRewriteContext sqlRewriteContext = new SQLRewriteContext(metaData.getSchema(), routeContext.getSqlStatementContext(), sql, parameters);

        ShardingSQLRewriteContextDecorator shardingSQLRewriteContextDecorator = new ShardingSQLRewriteContextDecorator();
        shardingSQLRewriteContextDecorator.setRouteContext(routeContext);
        /**
         * decorate()方法中通过调用addSQLTokenGenerators()给sqlRewriteContext添加SQLTokenGenerator列表
         */
        shardingSQLRewriteContextDecorator.decorate(shardingRule, properties, sqlRewriteContext);
        sqlRewriteContext.generateSQLTokens();

        Collection<ExecutionUnit> executionUnits = new LinkedHashSet<>();
        Set<Map.Entry<RouteUnit, SQLRewriteResult>> entries = new SQLRouteRewriteEngine().rewrite(sqlRewriteContext, routeContext.getRouteResult()).entrySet();
        for (Map.Entry<RouteUnit, SQLRewriteResult> entry : new SQLRouteRewriteEngine().rewrite(sqlRewriteContext, routeContext.getRouteResult()).entrySet()) {
            executionUnits.add(new ExecutionUnit(entry.getKey().getDataSourceMapper().getActualName(), new SQLUnit(entry.getValue().getSql(), entry.getValue().getParameters())));
        }

        //SQL经过路由和重写后，下一步就开始真正执行
        ExecutionContext executionContext = new ExecutionContext(routeContext.getSqlStatementContext());
        executionContext.getExecutionUnits().addAll(executionUnits);

        //打印经过路由和重写后需要执行的sql
        if (properties.<Boolean>getValue(ConfigurationPropertyKey.SQL_SHOW)) {
            SQLLogger.logSQL(sql, properties.<Boolean>getValue(ConfigurationPropertyKey.SQL_SIMPLE), executionContext.getSqlStatementContext(), executionContext.getExecutionUnits());
        }





        //对sql执行进行分组，数据库维度，并将sql绑定到真正的Statement上
        SQLExecutePrepareTemplate sqlExecutePrepareTemplate = new SQLExecutePrepareTemplate(1);
        Collection<InputGroup<StatementExecuteUnit>> inputGroups = sqlExecutePrepareTemplate.getExecuteUnitGroups(executionContext.getExecutionUnits(), callback());



        List<SetParameterMethodInvocation> setParameterMethodInvocations = new LinkedList<>();
        int i = 0;
        for (Object each : parameters) {
            Object[] args = new Object[]{++i, each};
            setParameterMethodInvocations.add(new SetParameterMethodInvocation(PreparedStatement.class.getMethod("setObject", new Class[]{int.class, Object.class}), args, each));
        }



        List<Statement> statements = new ArrayList<>();
        for (InputGroup<StatementExecuteUnit> each : inputGroups) {
            statements.addAll(each.getInputs().stream().map(StatementExecuteUnit::getStatement).collect(Collectors.toList()));
        }
        for(Statement statement:statements){
            for (SetParameterMethodInvocation each : setParameterMethodInvocations) {
                each.invoke(statement);
            }
        }







        System.out.println("===========");
    }

    private SQLExecutePrepareCallback callback(){
        return new SQLExecutePrepareCallback() {

            @Override
            public List<Connection> getConnections(final ConnectionMode connectionMode, final String dataSourceName, final int connectionSize) throws SQLException {
                return createConnections(dataSourceName, connectionMode, connectionSize);
            }

            @Override
            public StatementExecuteUnit createStatementExecuteUnit(final Connection connection, final ExecutionUnit executionUnit, final ConnectionMode connectionMode) throws SQLException {
                return new StatementExecuteUnit(executionUnit, connection.prepareStatement(executionUnit.getSqlUnit().getSql()), connectionMode);
            }
        };
    }



    private List<Connection> createConnections(final String dataSourceName, final ConnectionMode connectionMode, final int connectionSize) throws SQLException {
        DataSource dataSource = dataSourceMap.get(dataSourceName);

        if (1 == connectionSize) {
            Connection connection = dataSource.getConnection();
            return Collections.singletonList(connection);
        }
        if (ConnectionMode.CONNECTION_STRICTLY == connectionMode) {
            return createConnections(dataSourceName, dataSource, connectionSize);
        }
        synchronized (dataSource) {
            return createConnections(dataSourceName, dataSource, connectionSize);
        }
    }


    private List<Connection> createConnections(final String dataSourceName, final DataSource dataSource, final int connectionSize) throws SQLException {
        List<Connection> result = new ArrayList<>(connectionSize);
        for (int i = 0; i < connectionSize; i++) {
            try {
                Connection connection = dataSource.getConnection();
                result.add(connection);
            } catch (final SQLException ex) {
                for (Connection each : result) {
                    each.close();
                }
                throw new SQLException(String.format("Could't get %d connections one time, partition succeed connection(%d) have released!", connectionSize, result.size()), ex);
            }
        }
        return result;
    }




}