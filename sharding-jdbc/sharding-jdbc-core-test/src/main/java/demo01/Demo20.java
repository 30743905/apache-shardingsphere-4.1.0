package demo01;

import com.zaxxer.hikari.HikariDataSource;

import org.apache.shardingsphere.api.config.sharding.KeyGeneratorConfiguration;
import org.apache.shardingsphere.api.config.sharding.ShardingRuleConfiguration;
import org.apache.shardingsphere.api.config.sharding.TableRuleConfiguration;
import org.apache.shardingsphere.api.config.sharding.strategy.InlineShardingStrategyConfiguration;
import org.apache.shardingsphere.encrypt.api.EncryptColumnRuleConfiguration;
import org.apache.shardingsphere.encrypt.api.EncryptRuleConfiguration;
import org.apache.shardingsphere.encrypt.api.EncryptTableRuleConfiguration;
import org.apache.shardingsphere.encrypt.api.EncryptorRuleConfiguration;
import org.apache.shardingsphere.shardingjdbc.api.EncryptDataSourceFactory;
import org.apache.shardingsphere.shardingjdbc.api.ShardingDataSourceFactory;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationPropertyKey;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * 你搞忘写注释了
 *
 * @author zhang_zhang
 * @Copyright © 2020 tiger Inc. All rights reserved.
 * @create 2020-06-01 14:46
 */
@Slf4j
public class Demo20 {

    /**
     * 创建数据源
     * @return
     */
    private Map<String, DataSource> dataSourceMap() throws SQLException {

        Properties props = new Properties();
        props.put(ConfigurationPropertyKey.SQL_SHOW.getKey(), true);
        EncryptRuleConfiguration encryptRuleConfig = encryptRuleConfiguration();

        // 配置真实数据源
        Map<String, DataSource> dataSourceMap = new HashMap<>();


        // 配置第一个数据源
        HikariDataSource dataSource1 = new HikariDataSource();
        dataSource1.setJdbcUrl("jdbc:mysql://localhost:3306/ds0?serverTimezone=UTC");
        dataSource1.setUsername("root");
        dataSource1.setPassword("123456");


        DataSource ds00 = EncryptDataSourceFactory.createDataSource(dataSource1, encryptRuleConfig, props);
        dataSourceMap.put("ds0", ds00);

        // 配置第二个数据源
        HikariDataSource dataSource2 = new HikariDataSource();
        dataSource2.setJdbcUrl("jdbc:mysql://localhost:3306/ds1?serverTimezone=UTC");
        dataSource2.setUsername("root");
        dataSource2.setPassword("123456");

        DataSource ds11 = EncryptDataSourceFactory.createDataSource(dataSource2, encryptRuleConfig, props);
        dataSourceMap.put("ds1", ds11);

        return dataSourceMap;
    }

    private EncryptRuleConfiguration encryptRuleConfiguration(){
        // 配置脱敏规则
        Properties props = new Properties();
        props.setProperty("aes.key.value", "123456");
        EncryptorRuleConfiguration encryptorConfig = new EncryptorRuleConfiguration("AES", props);
        EncryptColumnRuleConfiguration columnConfig = new EncryptColumnRuleConfiguration("", "cipher_pwd", "", "aes");
        EncryptTableRuleConfiguration tableConfig = new EncryptTableRuleConfiguration(Collections.singletonMap("pwd", columnConfig));
        EncryptRuleConfiguration encryptRuleConfig = new EncryptRuleConfiguration();
        encryptRuleConfig.getEncryptors().put("aes", encryptorConfig);
        encryptRuleConfig.getTables().put("t_order", tableConfig);
        return encryptRuleConfig;
    }


    @Test
    public void createOrderTable2() throws SQLException {

        DataSource dataSource = dataSourceMap().get("ds0");

        String sql = "CREATE TABLE t_order_e1(\n" +
                "\t\tid bigint NOT NULL,\n" +
                "\t\torder_id INT NOT NULL,\n" +
                "\t\tuser_id INT NOT NULL,\n" +
                "\t\tstatus VARCHAR(45) NULL,\n" +
                "\t\tcipher_pwd VARCHAR(100) NULL,\n" +
                "\t\tmark VARCHAR(100) NULL,\n" +
                "\t\tother01 int null,\n" +
                "\t\tPRIMARY KEY (id)\n" +
                ") ";

        Connection conn = dataSource.getConnection();
        //execute(conn, "drop table t_order", null);
        execute(conn, sql, null);
        conn.close();
    }

    @Test
    public void insert() throws SQLException {
        Properties props = new Properties();
        props.put(ConfigurationPropertyKey.SQL_SHOW.getKey(), true);
        DataSource dataSource = ShardingDataSourceFactory.createDataSource(dataSourceMap(), ruleConfiguration(), props);

        String orderSql = "insert into t_order(id, order_id, user_id, status, mark, other01, pwd) values(?, ?, ?, ?, ?, ?, ?)";
        Connection conn = dataSource.getConnection();
        for(int i=0;i<50;i++){
            List<Object> orderParams = Arrays.asList(i, i%7, i%3, "init", "mark:"+i, i%8, "1122abc");
            execute(conn, orderSql, orderParams);
        }
        conn.close();
    }


    @Test
    public void insert2() throws SQLException {

        DataSource dataSource = dataSourceMap().get("ds0");

        String orderSql = "insert into t_order_e1(id, order_id, user_id, status, mark, other01, pwd) values(?, ?, ?, ?, ?, ?, ?)";
        Connection conn = dataSource.getConnection();
        for(int i=100;i<110;i++){
            List<Object> orderParams = Arrays.asList(i, i%7, i%3, "init", "mark:"+i, i%8, "123abc123333");
            execute(conn, orderSql, orderParams);
        }
        conn.close();
    }

    @Test
    public void selectTest2() throws SQLException {

        DataSource dataSource = dataSourceMap().get("ds0");

        /**
         * 采用InlineShardingStrategy策略分片键，不能在分片键上执行Range范围查询
         */
        String sql = "SELECT * FROM t_order_e1 o  ";
        Connection conn = dataSource.getConnection();
        PreparedStatement preparedStatement = conn.prepareStatement(sql);

        ResultSet rs = preparedStatement.executeQuery();
        printResult(rs);

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
                new TableRuleConfiguration("t_order","ds${0..1}.t_order_${0..3}");

        // 配置分库 + 分表策略
        orderTableRuleConfig.setDatabaseShardingStrategyConfig(
                new InlineShardingStrategyConfiguration("user_id", "ds${user_id % 2}"));
        orderTableRuleConfig.setTableShardingStrategyConfig(
                new InlineShardingStrategyConfiguration("order_id", "t_order_${order_id % 4}"));
        orderTableRuleConfig.setKeyGeneratorConfig(new KeyGeneratorConfiguration("SNOWFLAKE", "id"));
        return orderTableRuleConfig;
    }


    private void execute(Connection conn, String sql, List<Object> parameters){
        PreparedStatement statement = null;
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
                "\t\tid bigint NOT NULL,\n" +
                "\t\torder_id INT NOT NULL,\n" +
                "\t\tuser_id INT NOT NULL,\n" +
                "\t\tstatus VARCHAR(45) NULL,\n" +
                "\t\tpwd VARCHAR(100) NULL,\n" +
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
        String sql = "SELECT user_id, order_id, status, other01 FROM t_order o  WHERE (o.user_id = ? or o.user_id = ?) and (o.order_id = ? or o.order_id = ?)";
        Connection conn = dataSource.getConnection();
        PreparedStatement preparedStatement = conn.prepareStatement(sql);
        preparedStatement.setInt(1, 1);
        preparedStatement.setInt(2, 2);
        preparedStatement.setInt(3, 2);
        preparedStatement.setInt(4, 5);

        ResultSet rs = preparedStatement.executeQuery();
        printResult(rs);

    }

    private void printResult(ResultSet rs) throws SQLException {
        StringBuilder buf = new StringBuilder();
        int columnCount = rs.getMetaData().getColumnCount();
        for(int i=0;i<columnCount;i++){
            buf.append(rs.getMetaData().getColumnName(i+1)).append("\t");
        }
        buf.append("\r\n");
        while(rs.next()){
            for(int i=0;i<columnCount;i++){
                buf.append(rs.getObject(i+1)).append("\t\t");
            }
            buf.append("\r\n");
        }
        log.info("=====table result=====\r\n{}", buf);
    }

    @Test
    public void selectTest001() throws SQLException {

        Properties props = new Properties();
        props.put(ConfigurationPropertyKey.SQL_SHOW.getKey(), true);
        DataSource dataSource = ShardingDataSourceFactory.createDataSource(dataSourceMap(), ruleConfiguration(), props);

        /**
         * 采用InlineShardingStrategy策略分片键，不能在分片键上执行Range范围查询
         */
        //String sql = "SELECT * FROM t_order o  WHERE (o.user_id=? or o.user_id=?) and other01 > ?";
        String sql = "SELECT * FROM t_order o  WHERE o.order_id != ?";
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setInt(1, 3);
            //preparedStatement.setInt(2, 4);
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