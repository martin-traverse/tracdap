/*
 * Licensed to the Fintech Open Source Foundation (FINOS) under one or
 * more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * FINOS licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finos.tracdap.common.db.dialects;

import org.finos.tracdap.common.db.JdbcDialect;
import org.finos.tracdap.common.db.JdbcErrorCode;
import org.finos.tracdap.common.db.JdbcException;
import org.finos.tracdap.common.exception.EStartup;

import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public abstract class Dialect implements IDialect {

    public static IDialect dialectFor(JdbcDialect dialect) {

        switch (dialect) {

            case H2:
                return new H2SqlDialect();
            case MYSQL:
                return new MySqlDialect();
            case MARIADB:
                return new MariaDbDialect();
            case POSTGRESQL:
                return new PostgreSqlDialect();
            case SQLSERVER:
                return new SqlServerDialect();
            case ORACLE:
                return new OracleDialect();

            default:
                throw new EStartup("Unsupported JDBC dialect: " + dialect.name());
        }
    }

    // Generate error mappings for all synthetic error codes
    private static final Map<Integer, JdbcErrorCode> syntheticErrorCodes = Stream
            .of(JdbcErrorCode.values())
            .collect(Collectors.toMap(Enum::ordinal, error -> error));

    @Override
    public final JdbcErrorCode mapErrorCode(SQLException error) {

        if (JdbcException.SYNTHETIC_ERROR.equals(error.getSQLState()))
            return syntheticErrorCodes.getOrDefault(error.getErrorCode(), JdbcErrorCode.UNKNOWN_ERROR_CODE);
        else
            return mapDialectErrorCode(error);
    }

    protected abstract JdbcErrorCode mapDialectErrorCode(SQLException error);
}