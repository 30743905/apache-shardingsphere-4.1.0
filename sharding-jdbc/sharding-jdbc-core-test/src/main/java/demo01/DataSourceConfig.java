package demo01;

/**
 * 你搞忘写注释了
 *
 * @author zhang_zhang
 * @Copyright © 2020 tiger Inc. All rights reserved.
 * @create 2020-06-08 17:02
 */

import com.zaxxer.hikari.HikariDataSource;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.shardingsphere.api.config.masterslave.MasterSlaveRuleConfiguration;
import org.apache.shardingsphere.encrypt.api.EncryptColumnRuleConfiguration;
import org.apache.shardingsphere.encrypt.api.EncryptRuleConfiguration;
import org.apache.shardingsphere.encrypt.api.EncryptTableRuleConfiguration;
import org.apache.shardingsphere.encrypt.api.EncryptorRuleConfiguration;
import org.apache.shardingsphere.shardingjdbc.api.EncryptDataSourceFactory;
import org.apache.shardingsphere.shardingjdbc.api.MasterSlaveDataSourceFactory;

import javax.sql.DataSource;

import java.sql.SQLException;
import java.util.*;

public class DataSourceConfig {


    private Map<String, DataSource> dataSourceMap() {
        // 配置真实数据源
        Map<String, DataSource> dataSourceMap = new HashMap<>();


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

        return dataSourceMap;
    }


    public void getDataSource() throws SQLException {
        //主数据源名称
        String masterName = "master";
        String slaveName0 = "slave0";
        List<String> slaveNames = new ArrayList<>();
        slaveNames.add(slaveName0);


        // 配置脱敏规则
        Properties props = new Properties();
        props.setProperty("aes.key.value", "123456");
        EncryptorRuleConfiguration encryptorConfig = new EncryptorRuleConfiguration("AES", props);
        EncryptColumnRuleConfiguration columnConfig = new EncryptColumnRuleConfiguration("plain_pwd", "cipher_pwd", "", "aes");
        EncryptTableRuleConfiguration tableConfig = new EncryptTableRuleConfiguration(Collections.singletonMap("pwd", columnConfig));
        EncryptRuleConfiguration encryptRuleConfig = new EncryptRuleConfiguration();
        encryptRuleConfig.getEncryptors().put("aes", encryptorConfig);
        encryptRuleConfig.getTables().put("t_encrypt", tableConfig);


        // 配置真实数据源
        Map<String, DataSource> dataSourceMap = new HashMap<>();
        // 配置主库
        HikariDataSource dataSource1 = new HikariDataSource();
        dataSource1.setJdbcUrl("jdbc:mysql://localhost:3306/ds0?serverTimezone=UTC");
        dataSource1.setUsername("root");
        dataSource1.setPassword("123456");

        // 获取数据源对象,加入脱敏规则
        DataSource dataSource11 = EncryptDataSourceFactory.createDataSource(dataSource1, encryptRuleConfig, props);
        dataSourceMap.put(masterName, dataSource11);


        // 配置第一个从库
        HikariDataSource dataSource2 = new HikariDataSource();
        dataSource2.setJdbcUrl("jdbc:mysql://localhost:3306/ds2?serverTimezone=UTC");
        dataSource2.setUsername("root");
        dataSource2.setPassword("123456");

        // 获取数据源对象,加入脱敏规则
        DataSource dataSource22 = EncryptDataSourceFactory.createDataSource(dataSource2, encryptRuleConfig, props);
        dataSourceMap.put(slaveName0, dataSource22);

        // 配置读写分离规则
        MasterSlaveRuleConfiguration masterSlaveRuleConfig = new MasterSlaveRuleConfiguration("ds_master_slave", masterName, slaveNames);
        // 获取数据源对象
        DataSource dataSource = MasterSlaveDataSourceFactory.createDataSource(dataSourceMap, masterSlaveRuleConfig, new Properties());




    }
}