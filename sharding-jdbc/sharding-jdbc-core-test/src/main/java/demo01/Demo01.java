package demo01;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.shardingsphere.api.config.sharding.ShardingRuleConfiguration;
import org.apache.shardingsphere.api.config.sharding.TableRuleConfiguration;
import org.apache.shardingsphere.api.config.sharding.strategy.InlineShardingStrategyConfiguration;
import org.apache.shardingsphere.shardingjdbc.api.ShardingDataSourceFactory;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationPropertyKey;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

/**
 * 你搞忘写注释了
 *
 * @author zhang_zhang
 * @Copyright © 2020 tiger Inc. All rights reserved.
 * @create 2020-06-01 14:46
 */
public class Demo01 {


    @Test
    public void test01() throws SQLException {
        // 配置真实数据源
        Map<String, DataSource> dataSourceMap = new HashMap<>();

        // 配置第一个数据源
        BasicDataSource dataSource1 = new BasicDataSource();
        //dataSource1.setDriverClassName("com.mysql.jdbc.Driver");
        dataSource1.setUrl("jdbc:mysql://localhost:3306/ds0?serverTimezone=UTC");
        dataSource1.setUsername("root");
        dataSource1.setPassword("123456");
        dataSourceMap.put("ds0", dataSource1);

        // 配置第二个数据源
        BasicDataSource dataSource2 = new BasicDataSource();
        //dataSource2.setDriverClassName("com.mysql.jdbc.Driver");
        dataSource2.setUrl("jdbc:mysql://localhost:3306/ds1?serverTimezone=UTC");
        dataSource2.setUsername("root");
        dataSource2.setPassword("123456");
        dataSourceMap.put("ds1", dataSource2);



        // 配置分片规则
        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();
        shardingRuleConfig.getTableRuleConfigs().add(orderRule());
        shardingRuleConfig.getTableRuleConfigs().add(orderItemRule());

        // 获取数据源对象
        Properties props = new Properties();
        props.put(ConfigurationPropertyKey.SQL_SHOW.getKey(), true);


        DataSource dataSource = ShardingDataSourceFactory.createDataSource(dataSourceMap, shardingRuleConfig, props);
        //DataSource dataSource = YamlShardingDataSourceFactory.createDataSource(yamlFile);

        String sql = "SELECT * FROM t_order o  WHERE (o.user_id=? or o.user_id=?) AND o.order_id=? and o.status=? ";
        //String sql = "SELECT * FROM t_order o  WHERE o.user_id=? AND o.order_id=? and o.status=? ";
        //String sql = "SELECT i.* FROM t_order o JOIN t_order_item i ON o.order_id=i.order_id WHERE o.user_id=? AND o.order_id=?";
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setInt(1, 10);
            preparedStatement.setInt(2, 11);
            preparedStatement.setInt(3, 1100);
            preparedStatement.setString(4, "init");
            try (ResultSet rs = preparedStatement.executeQuery()) {
                while(rs.next()) {
                    System.out.println(rs.getInt(1));
                    System.out.println(rs.getInt(2));
                }
            }
        }



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


}