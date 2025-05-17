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

import org.finos.tracdap.common.cache.CacheEntry;
import org.finos.tracdap.common.cache.CacheHelpers;
import org.finos.tracdap.common.cache.CacheTicket;
import org.finos.tracdap.common.cache.IJobCache;
import org.finos.tracdap.common.db.JdbcBaseDal;
import org.finos.tracdap.common.db.JdbcDialect;
import org.finos.tracdap.common.db.JdbcErrorCode;
import org.finos.tracdap.common.db.JdbcException;

import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;


public class JdbcJobCache <TValue extends Serializable> extends JdbcBaseDal implements IJobCache<TValue> {

    private final String cacheName;

    // Only JDBC cache manager can create instances
    JdbcJobCache(DataSource dataSource, JdbcDialect dialect, String cacheName) {
        super(dataSource, dialect);
        this.cacheName = cacheName;
    }

    @Override
    public CacheTicket openNewTicket(String key, Duration duration) {

        CacheHelpers.checkKey(key);
        CacheHelpers.checkDuration(duration);
        CacheHelpers.checkMaxDuration(key, duration);

        return wrapTransaction(conn -> {
            createBlankEntry(key, conn);
            return openTicket(key, CacheTicket.FIRST_REVISION, duration, conn);
        });
    }

    @Override
    public CacheTicket openTicket(String key, int revision, Duration duration) {

        CacheHelpers.checkKey(key);
        CacheHelpers.checkRevision(revision);
        CacheHelpers.checkDuration(duration);
        CacheHelpers.checkMaxDuration(key, duration);

        return wrapTransaction(conn -> {
            return openTicket(key, revision, duration, conn);
        });
    }

    private CacheTicket openTicket(String key, int revision, Duration duration, Connection conn) throws SQLException {

        var entryFk = entryKey(conn, key);

        var grantTime = Instant.now();
        var ticket = CacheTicket.forDuration(this, key, revision, grantTime, duration);

        var query = "insert into cache_ticket (\n" +
                "  entry_fk,\n" +
                "  revision,\n" +
                "  grant_time,\n" +
                "  expiry_time\n" +
                ")\n" +
                "values (?, ?, ?, ?)";

        try (var stmt = prepareStatement(conn, query, "ticket_pk")) {

            stmt.setLong(1, entryFk);
            stmt.setInt(2, ticket.revision());
            stmt.setTimestamp(3, new Timestamp(ticket.grantTime().toEpochMilli()));
            stmt.setTimestamp(4, new Timestamp(ticket.expiry().toEpochMilli()));

            stmt.executeUpdate();
        }
        catch (SQLException error) {

            var code = dialect.mapErrorCode(error);

            if (code == JdbcErrorCode.INSERT_DUPLICATE)
                return CacheTicket.supersededTicket(key, revision, grantTime);

            if (code == JdbcErrorCode.INSERT_MISSING_FK)
                return CacheTicket.missingEntryTicket(key, revision, grantTime);

            throw CacheErrors.catchAll(error, dialect);
        }

        return ticket;
    }

    @Override
    public void closeTicket(CacheTicket ticket) {

        CacheHelpers.checkTicket(ticket);

        wrapTransaction(conn -> {
            closeTicket(ticket, conn);
        });
    }

    private void closeTicket(CacheTicket ticket, Connection conn) throws SQLException {

        var deleteTicket =
            "delete from cache_ticket \n" +
            "where entry_fk = (select entry_pk from cache_entry where cache_name = ? and entry = ?)\n" +
            "and revision = ?";

        try (var stmt = conn.prepareStatement(deleteTicket)) {
            stmt.setString(1, cacheName);
            stmt.setString(2, ticket.key());
            stmt.setInt(3, ticket.revision());
            stmt.executeUpdate();
        }

        var deleteNullEntry =
            "delete from cache_entry\n" +
            "where cache_name = ?\n" +
            "and entry = ?\n" +
            "and encoded_value is null";

        try (var stmt = conn.prepareStatement(deleteNullEntry)) {
            stmt.setString(1, cacheName);
            stmt.setString(2, ticket.key());
            stmt.executeUpdate();
        }
    }

    @Override
    public int createEntry(CacheTicket ticket, String status, TValue value) {

        var commitTime = Instant.now();

        CacheHelpers.checkValidTicket(ticket, "create", commitTime);
        CacheHelpers.checkValidStatus(ticket, status);
        CacheHelpers.checkValidValue(ticket, value);

        return wrapTransaction(conn -> {
            return createEntry();
        });
    }

    private void createBlankEntry(String entry, Connection conn) throws SQLException {

        var query = "insert into cache_entry (\n" +
                "  cache_name,\n" +
                "  entry,\n" +
                "  revision\n" +
                ")\n" +
                "values (?, ?, ?)";

        try (var stmt = prepareStatement(conn, query, "entry_pk")) {

            stmt.setString(1, cacheName);
            stmt.setString(2, entry);
            stmt.setInt(3, CacheTicket.FIRST_REVISION);

            stmt.executeUpdate();
        }
        catch (SQLException error) {

            var code = dialect.mapErrorCode(error);

            if (code == JdbcErrorCode.INSERT_DUPLICATE)
                return;

            throw CacheErrors.catchAll(error, dialect);
        }
    }

    private int createEntry(CacheTicket ticket, String status, TValue value, Instant commitTime, Connection conn) throws SQLException {

        var encodedValue = CacheHelpers.encodeValue(value);

        var query = "update cache_entry (\n" +
                "  revision\n" +
                "  status\n" +
                "  encoded_value\n" +
                ")\n" +
                "values (?, ?, ?) \n" +
                "where cache_name = ?\n" +
                "and entry = ?";

        try (var stmt = prepareStatement(conn, query, "entry_pk")) {

            stmt.setInt(1, ticket.revision());
            stmt.setString(2, status);
            stmt.setBytes(3, encodedValue);

            stmt.setString(4, cacheName);
            stmt.setString(5, ticket.key());

            stmt.executeUpdate();
        }
        catch (SQLException error) {

            var code = dialect.mapErrorCode(error);

            if (code == JdbcErrorCode.INSERT_DUPLICATE)
                return;

            throw CacheErrors.catchAll(error, dialect);
        }
    }

    @Override
    public int updateEntry(CacheTicket ticket, String status, TValue tValue) {
        return 0;
    }

    @Override
    public void deleteEntry(CacheTicket ticket) {

    }

    @Override
    public CacheEntry<TValue> readEntry(CacheTicket ticket) {
        return null;
    }

    @Override
    public Optional<CacheEntry<TValue>> queryKey(String key) {
        return Optional.empty();
    }

    @Override
    public List<CacheEntry<TValue>> queryStatus(List<String> statuses) {
        return List.of();
    }

    @Override
    public List<CacheEntry<TValue>> queryStatus(List<String> statuses, boolean includeOpenTickets) {
        return List.of();
    }


    private PreparedStatement prepareStatement(Connection conn, String query, String keyColumn) throws SQLException {

        if (dialect.supportsGeneratedKeys() && keyColumn != null)
            return conn.prepareStatement(query, new String[] { keyColumn });
        else
            return conn.prepareStatement(query);
    }

    private long entryKey(Connection conn, Statement stmt, String entry) throws SQLException {

        if (dialect.supportsGeneratedKeys())
            return generatedKey(stmt);
        else
            return entryKey(conn, entry);
    }

    private long entryKey(Connection conn, String entry) throws SQLException {

        var query = "select entry_pk from cache_entry where entry = ?";

        try (var stmt = conn.prepareStatement(query)) {

            stmt.setString(1, entry);

            try (var rs = stmt.executeQuery()) {
                return readKey(rs);
            }
        }
    }

    private long ticketKey(Connection conn, Statement stmt, long entry_pk, int revision) throws SQLException {

        if (dialect.supportsGeneratedKeys())
            return generatedKey(stmt);
        else
            return ticketKey(conn, entry_pk, revision);
    }

    private long ticketKey(Connection conn, long entry_pk, int revision) throws SQLException {

        var query = "select ticket_pk from cache_ticket where entry_pk = ? and revision = ?";

        try (var stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, entry_pk);
            stmt.setInt(2, revision);

            try (var rs = stmt.executeQuery()) {
                return readKey(rs);
            }
        }
    }

    private long generatedKey(Statement stmt) throws SQLException {

        try (ResultSet rs = stmt.getGeneratedKeys()) {
            return readKey(rs);
        }
    }

    private long readKey(ResultSet rs) throws SQLException {

        rs.next();
        long key = rs.getLong(1);

        if (rs.next())
            throw new JdbcException(JdbcErrorCode.TOO_MANY_ROWS);

        return key;
    }
}
