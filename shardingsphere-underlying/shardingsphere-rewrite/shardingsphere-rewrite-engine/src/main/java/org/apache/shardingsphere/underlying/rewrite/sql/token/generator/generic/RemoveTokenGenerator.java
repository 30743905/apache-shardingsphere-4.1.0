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

package org.apache.shardingsphere.underlying.rewrite.sql.token.generator.generic;

import com.google.common.base.Preconditions;
import org.apache.shardingsphere.sql.parser.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.sql.parser.sql.segment.generic.RemoveAvailable;
import org.apache.shardingsphere.sql.parser.sql.statement.dal.dialect.mysql.ShowColumnsStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dal.dialect.mysql.ShowTableStatusStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dal.dialect.mysql.ShowTablesStatement;
import org.apache.shardingsphere.underlying.rewrite.sql.token.generator.CollectionSQLTokenGenerator;
import org.apache.shardingsphere.underlying.rewrite.sql.token.pojo.generic.RemoveToken;

import java.util.Collection;
import java.util.Collections;

/**
 * Remove token generator.
 */
public final class RemoveTokenGenerator implements CollectionSQLTokenGenerator {
    
    @Override
    public boolean isGenerateSQLToken(final SQLStatementContext sqlStatementContext) {
        /**
         * show tables会被解析成ShowTablesStatement，如果show tables带有schema信息，会被移除掉，
         * 比如：show tables from ds1会被改写成show tables
         */
        if (sqlStatementContext.getSqlStatement() instanceof ShowTablesStatement) {
            return ((ShowTablesStatement) sqlStatementContext.getSqlStatement()).getFromSchema().isPresent();
        }
        /**
         * 语法:SHOW TABLE STATUS [FROM db_name] [LIKE 'pattern']
         * 参数：[FROM db_name]  可选，表示查询哪个数据库下面的表信息。
         * 　　　[LIKE 'pattern'] 可选，表示查询哪些具体的表名。
         *
         * 比如：show table status from ds1 like 't_order_0';
         */
        if (sqlStatementContext.getSqlStatement() instanceof ShowTableStatusStatement) {
            return ((ShowTableStatusStatement) sqlStatementContext.getSqlStatement()).getFromSchema().isPresent();
        }

        /**
         * SHOW COLUMNS FROM tbl_name [FROM db_name]︰列出表的列信息
         * 或SHOW COLUMNS FROM [db_name.]tbl_name
         */
        if (sqlStatementContext.getSqlStatement() instanceof ShowColumnsStatement) {
            return ((ShowColumnsStatement) sqlStatementContext.getSqlStatement()).getFromSchema().isPresent();
        }
        return false;
    }

    /**
     * 将需要移除的部分生成RemoveToken，后续sql改写的时候会将RemoveToken起始和终止中间部分内容替换成RemoveToken.toString()，toString()返回空字符串，所以就实现移除效果
     * @param sqlStatementContext SQL statement context
     * @return
     */
    @Override
    public Collection<RemoveToken> generateSQLTokens(final SQLStatementContext sqlStatementContext) {
        if (sqlStatementContext.getSqlStatement() instanceof ShowTablesStatement) {
            Preconditions.checkState(((ShowTablesStatement) sqlStatementContext.getSqlStatement()).getFromSchema().isPresent());
            RemoveAvailable removeAvailable = ((ShowTablesStatement) sqlStatementContext.getSqlStatement()).getFromSchema().get();
            return Collections.singletonList(new RemoveToken(removeAvailable.getStartIndex(), removeAvailable.getStopIndex()));
        }
        if (sqlStatementContext.getSqlStatement() instanceof ShowTableStatusStatement) {
            Preconditions.checkState(((ShowTableStatusStatement) sqlStatementContext.getSqlStatement()).getFromSchema().isPresent());
            RemoveAvailable removeAvailable = ((ShowTableStatusStatement) sqlStatementContext.getSqlStatement()).getFromSchema().get();
            return Collections.singletonList(new RemoveToken(removeAvailable.getStartIndex(), removeAvailable.getStopIndex()));
        }
        if (sqlStatementContext.getSqlStatement() instanceof ShowColumnsStatement) {
            Preconditions.checkState(((ShowColumnsStatement) sqlStatementContext.getSqlStatement()).getFromSchema().isPresent());
            RemoveAvailable removeAvailable = ((ShowColumnsStatement) sqlStatementContext.getSqlStatement()).getFromSchema().get();
            return Collections.singletonList(new RemoveToken(removeAvailable.getStartIndex(), removeAvailable.getStopIndex()));
        }
        return Collections.emptyList();
    }
}
