package demo01;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.shardingsphere.api.config.sharding.ShardingRuleConfiguration;
import org.apache.shardingsphere.api.config.sharding.TableRuleConfiguration;
import org.apache.shardingsphere.api.config.sharding.strategy.ComplexShardingStrategyConfiguration;
import org.apache.shardingsphere.api.config.sharding.strategy.InlineShardingStrategyConfiguration;
import org.apache.shardingsphere.api.config.sharding.strategy.StandardShardingStrategyConfiguration;
import org.apache.shardingsphere.shardingjdbc.api.ShardingDataSourceFactory;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.resultset.ShardingResultSet;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationPropertyKey;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

/**
 * 复合分片策略
 *
 * @author zhang_zhang
 * @Copyright © 2020 tiger Inc. All rights reserved.
 * @create 2020-06-01 14:46
 */
public class Demo013 {

    /**
     * 创建数据源
     * @return
     */
    private Map<String, DataSource> dataSourceMap(){
        // 配置真实数据源
        Map<String, DataSource> dataSourceMap = new HashMap<>();

        // 配置第一个数据源
        BasicDataSource dataSource1 = new BasicDataSource();
        dataSource1.setUrl("jdbc:mysql://localhost:3306/ds0?serverTimezone=UTC");
        dataSource1.setUsername("root");
        dataSource1.setPassword("123456");
        dataSourceMap.put("ds0", dataSource1);

        // 配置第二个数据源
        BasicDataSource dataSource2 = new BasicDataSource();
        dataSource2.setUrl("jdbc:mysql://localhost:3306/ds1?serverTimezone=UTC");
        dataSource2.setUsername("root");
        dataSource2.setPassword("123456");
        dataSourceMap.put("ds1", dataSource2);

        return dataSourceMap;
    }

    /**
     * 创建分库分表规则配置
     * @return
     */
    private ShardingRuleConfiguration ruleConfiguration(){
        // 配置分片规则
        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();
        shardingRuleConfig.getTableRuleConfigs().add(orderRule());

        return shardingRuleConfig;
    }


    private TableRuleConfiguration orderRule(){
        // 配置Order表规则
        TableRuleConfiguration orderTableRuleConfig =
                new TableRuleConfiguration("t_order","ds${0..1}.t_order_${0..1}");

        // 配置分库 + 分表策略  String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<T> shardingValue)
        orderTableRuleConfig.setDatabaseShardingStrategyConfig(new ComplexShardingStrategyConfiguration("user_id,other01", (names, value) ->{
            return names;
        }));

        orderTableRuleConfig.setTableShardingStrategyConfig(
                new InlineShardingStrategyConfiguration("order_id", "t_order_${order_id % 2}"));
        return orderTableRuleConfig;
    }



    private void execute(Connection conn, String sql, List<Object> parameters){
        PreparedStatement statement;
        try {
            statement = conn.prepareStatement(sql);
            if(parameters != null){
                for(int i=0;i<parameters.size();i++){
                    statement.setObject(i+1, parameters.get(i));
                }
            }
            statement.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    @Test
    public void createOrderTable() throws SQLException {
        Properties props = new Properties();
        props.put(ConfigurationPropertyKey.SQL_SHOW.getKey(), true);
        DataSource dataSource = ShardingDataSourceFactory.createDataSource(dataSourceMap(), ruleConfiguration(), props);

        String sql = "CREATE TABLE t_order(\n" +
                "\t\tid INT NOT NULL,\n" +
                "\t\torder_id INT NOT NULL,\n" +
                "\t\tuser_id INT NOT NULL,\n" +
                "\t\tstatus VARCHAR(45) NULL,\n" +
                "\t\tmark VARCHAR(100) NULL,\n" +
                "\t\tother01 int null,\n" +
                "\t\tPRIMARY KEY (id)\n" +
                ") ";

        Connection conn = dataSource.getConnection();
        execute(conn, "drop table t_order", null);
        execute(conn, sql, null);
        conn.close();
    }



    @Test
    public void insert() throws SQLException {
        Properties props = new Properties();
        props.put(ConfigurationPropertyKey.SQL_SHOW.getKey(), true);
        DataSource dataSource = ShardingDataSourceFactory.createDataSource(dataSourceMap(), ruleConfiguration(), props);

        String orderSql = "insert into t_order(id, order_id, user_id, status, mark, other01) values(?, ?, ?, ?, ?, ?)";

        Connection conn = dataSource.getConnection();

        for(int i=0;i<20;i++){
            List<Object> orderParams = Arrays.asList(i, i%5, i%3, "init", "mark:"+i, i%8);
            execute(conn, orderSql, orderParams);
        }

        conn.close();
    }

    @Test
    public void truncateTest() throws SQLException {
        Properties props = new Properties();
        props.put(ConfigurationPropertyKey.SQL_SHOW.getKey(), true);
        DataSource dataSource = ShardingDataSourceFactory.createDataSource(dataSourceMap(), ruleConfiguration(), props);

        String orderSql = "truncate table t_order";
        String orderItemSql = "truncate table t_order_item";

        Connection conn = dataSource.getConnection();

        for(int i=0;i<10;i++){
            execute(conn, orderSql, null);
            execute(conn, orderItemSql, null);
        }

        conn.close();
    }

    @Test
    public void selectTest() throws SQLException {

        Properties props = new Properties();
        props.put(ConfigurationPropertyKey.SQL_SHOW.getKey(), true);
        DataSource dataSource = ShardingDataSourceFactory.createDataSource(dataSourceMap(), ruleConfiguration(), props);

        /**
         * 采用InlineShardingStrategy策略分片键，不能在分片键上执行Range范围查询
         */
        //String sql = "SELECT * FROM t_order o  WHERE (o.user_id=? or o.user_id=?) and other01 > ?";
        //String sql = "SELECT * FROM t_order o  WHERE (o.user_id=? or o.user_id=?) and other01 > ?";
        //String sql = "SELECT * FROM t_order o  WHERE o.user_id >= ? and other01 > ?";
        String sql = "SELECT * FROM t_order o  WHERE o.user_id = ? and other01 = ?";
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setInt(1, 1);
            preparedStatement.setInt(2, 3);
            //preparedStatement.setString(4, "init");
            try (ResultSet rs = preparedStatement.executeQuery()) {
                StringBuilder buf = new StringBuilder();
                int columnCount = rs.getMetaData().getColumnCount();
                for(int i=0;i<columnCount;i++){
                    buf.append(rs.getMetaData().getColumnName(i+1)).append("\t");
                }
                System.out.println(buf);
                while(rs.next()) {
                    System.out.print(rs.getObject(1)+"\t\t");
                    System.out.print(rs.getObject(2)+"\t\t");
                    System.out.print(rs.getObject(3)+"\t\t");
                    System.out.print(rs.getObject(4)+"\t\t");
                    System.out.print(rs.getObject(5)+"\t\t");
                    System.out.print(rs.getObject(6)+"\r\n");
                }
            }
        }
    }


    @Test
    public void selectTest02() throws SQLException {

        Properties props = new Properties();
        props.put(ConfigurationPropertyKey.SQL_SHOW.getKey(), true);
        DataSource dataSource = ShardingDataSourceFactory.createDataSource(dataSourceMap(), ruleConfiguration(), props);

        /**
         * 采用InlineShardingStrategy策略分片键，不能在分片键上执行Range范围查询
         */
        //String sql = "SELECT * FROM t_order o  WHERE (o.user_id=? or o.user_id=?) and other01 > ?";
        String sql = "SELECT * FROM t_order o  WHERE (o.user_id=? or o.user_id=? or o.order_id=? or o.order_id=?) and other01 > ?";
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setInt(1, 0);
            preparedStatement.setInt(2, 7);
            preparedStatement.setInt(1, 8);
            preparedStatement.setInt(2, 7);
            preparedStatement.setInt(3, 3);
            //preparedStatement.setString(4, "init");
            try (ResultSet rs = preparedStatement.executeQuery()) {
                while(rs.next()) {
                    System.out.print(rs.getObject(1)+"\t");
                    System.out.print(rs.getObject(2)+"\t");
                    System.out.print(rs.getObject(3)+"\t");
                    System.out.print(rs.getObject(4)+"\t");
                    System.out.print(rs.getObject(5)+"\r\n");
                }
            }
        }
    }

    @Test
    public void selectTest03() throws SQLException {

        Properties props = new Properties();
        props.put(ConfigurationPropertyKey.SQL_SHOW.getKey(), true);
        DataSource dataSource = ShardingDataSourceFactory.createDataSource(dataSourceMap(), ruleConfiguration(), props);

        /**
         * 采用InlineShardingStrategy策略分片键，不能在分片键上执行Range范围查询
         */
        //String sql = "SELECT * FROM t_order o  WHERE (o.user_id=? or o.user_id=?) and other01 > ?";
        String sql = "SELECT * FROM t_order o  WHERE  other01 > ?";
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setInt(1, 3);
            //preparedStatement.setString(4, "init");
            try (ResultSet rs = preparedStatement.executeQuery()) {
                while(rs.next()) {
                    System.out.print(rs.getObject(1)+"\t");
                    System.out.print(rs.getObject(2)+"\t");
                    System.out.print(rs.getObject(3)+"\t");
                    System.out.print(rs.getObject(4)+"\t");
                    System.out.print(rs.getObject(5)+"\r\n");
                }
            }
        }
    }


}