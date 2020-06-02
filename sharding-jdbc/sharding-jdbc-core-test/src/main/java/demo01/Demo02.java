package demo01;

import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.shardingsphere.sql.parser.core.parser.SQLParserExecutor;
import org.apache.shardingsphere.sql.parser.core.visitor.ParseTreeVisitorFactory;
import org.apache.shardingsphere.sql.parser.core.visitor.VisitorRule;
import org.apache.shardingsphere.sql.parser.sql.statement.SQLStatement;
import org.junit.Test;

/**
 * 你搞忘写注释了
 *
 * @author zhang_zhang
 * @Copyright © 2020 tiger Inc. All rights reserved.
 * @create 2020-06-02 18:20
 */
public class Demo02 {

    @Test
    public void test01(){
        String databaseTypeName = "MySQL";
        String sql = "SELECT * FROM t_order o  WHERE o.user_id=? AND o.order_id=? ";
        String sql2 = "insert into t_order(user_id, order_id) values(?, ?)";
        ParseTree parseTree = new SQLParserExecutor(databaseTypeName, sql2).execute().getRootNode();
        SQLStatement result = (SQLStatement) ParseTreeVisitorFactory.newInstance(databaseTypeName, VisitorRule.valueOf(parseTree.getClass())).visit(parseTree);
        System.out.println(result);
    }
}