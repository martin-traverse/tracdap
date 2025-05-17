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

package org.finos.tracdap.common.cache.jdbc;

import org.finos.tracdap.common.db.JdbcErrorCode;
import org.finos.tracdap.common.db.dialects.IDialect;
import org.finos.tracdap.common.exception.ECacheDuplicate;
import org.finos.tracdap.common.exception.ETracInternal;

import java.sql.SQLException;
import java.text.MessageFormat;

public class CacheErrors {

    static final String DUPLICATE_OBJECT_ID = "Duplicate job cache entry for {0} [{1}]";

    static final String UNRECOGNISED_ERROR_CODE = "Unrecognised SQL Error code: {0}, sqlstate = {1}, error code = {2}";
    static final String UNHANDLED_ERROR = "Unhandled SQL Error code: {0}";


    static void duplicateCacheEntry(SQLException error, IDialect dialect, String entry, String cacheType) {

        var code = dialect.mapErrorCode(error);

        if (code != JdbcErrorCode.INSERT_DUPLICATE)
            return;

        var message = MessageFormat.format(DUPLICATE_OBJECT_ID, entry, cacheType);
        throw new ECacheDuplicate(message, error);
    }

    static ETracInternal catchAll(SQLException error, IDialect dialect) {

        var code = dialect.mapErrorCode(error);

        // An unknown error code means the SQL error could not be mapped
        if (code == JdbcErrorCode.UNKNOWN_ERROR_CODE) {

            var message = MessageFormat.format(UNRECOGNISED_ERROR_CODE,
                    dialect.dialectCode(),
                    error.getSQLState(),
                    error.getErrorCode());

            return new ETracInternal(message, error);
        }

        // Last fallback - the error code was mapped but there is a missing handler
        var message = MessageFormat.format(UNHANDLED_ERROR, code.name());
        return new ETracInternal(message, error);
    }
}
