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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;


public class PostgreSqlDialect extends Dialect {

    private static final Map<Integer, JdbcErrorCode> dialectErrorCodes = Map.ofEntries(
            Map.entry(23505, JdbcErrorCode.INSERT_DUPLICATE),
            Map.entry(23503, JdbcErrorCode.INSERT_MISSING_FK));

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public JdbcDialect dialectCode() {
        return JdbcDialect.POSTGRESQL;
    }

    @Override
    public JdbcErrorCode mapDialectErrorCode(SQLException error) {

        var errorCodeString = error.getSQLState();

        try {
            var errorCode = Integer.parseInt(errorCodeString);
            return dialectErrorCodes.getOrDefault(errorCode, JdbcErrorCode.UNKNOWN_ERROR_CODE);
        }
        catch (NumberFormatException e) {
            log.error("Postgresql error state is not an integer error code: [{}]", errorCodeString);
            return JdbcErrorCode.UNKNOWN_ERROR_CODE;
        }
    }

    @Override
    public boolean supportsGeneratedKeys() {
        return true;
    }

    @Override
    public int booleanType() {
        return Types.BOOLEAN;
    }

}
