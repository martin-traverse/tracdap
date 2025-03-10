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

package org.finos.tracdap.tools.deploy.metadb;

import org.finos.tracdap.common.config.ConfigManager;
import org.finos.tracdap.common.exception.ETracPublic;
import org.finos.tracdap.common.startup.StandardArgs;
import org.finos.tracdap.common.db.JdbcSetup;
import org.finos.tracdap.common.exception.EStartup;
import org.finos.tracdap.common.startup.Startup;
import org.finos.tracdap.config.PlatformConfig;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;


/**
 * Deployment tool to manage the TRAC metadata database
 */
public class DeployMetaDB {

    /** Task name for deploying the schema **/
    public final static String DEPLOY_SCHEMA_TASK = "deploy_schema";

    /** Task name for adding a tenant **/
    public final static String ADD_TENANT_TASK = "add_tenant";

    /** Task name for setting a tenant's description **/
    public final static String ALTER_TENANT_TASK = "alter_tenant";

    private final static String SCHEMA_LOCATION = "classpath:%s/rollout";

    private final static List<StandardArgs.Task> METADB_TASKS = List.of(
            StandardArgs.task(DEPLOY_SCHEMA_TASK, "Deploy/update metadata database with the latest physical schema"),
            StandardArgs.task(ADD_TENANT_TASK, List.of("CODE", "DESCRIPTION"), "Add a new tenant to the metadata database"),
            StandardArgs.task(ALTER_TENANT_TASK, List.of("CODE", "DESCRIPTION"), "Alter the description for an existing tenant"));

    private final Logger log;
    private final ConfigManager configManager;

    /**
     * Construct a new instance of the deployment tool
     *
     * @param configManager A prepared instance of ConfigManager
     */
    public DeployMetaDB(ConfigManager configManager) {

        this.log = LoggerFactory.getLogger(getClass());
        this.configManager = configManager;
    }

    /**
     * Run deployment tasks, as specified by standard args tasks on the commane line
     *
     * @param tasks The list of deployment tasks to execute
     */
    public void runDeployment(List<StandardArgs.Task> tasks) {

        var platformConfig = configManager.loadRootConfigObject(PlatformConfig.class);

        var metaDbConfig = platformConfig.getMetadata().getDatabase();

        var dialect = JdbcSetup.getSqlDialect(metaDbConfig);
        var dataSource = JdbcSetup.createDatasource(configManager, metaDbConfig);

        // Pick up DB deploy scripts depending on the SQL dialect
        var scriptsLocation = String.format(SCHEMA_LOCATION, dialect.name().toLowerCase());

        log.info("SQL Dialect: " + dialect);
        log.info("Scripts location: " + scriptsLocation);

        try {

            for (var task : tasks) {

                if (DEPLOY_SCHEMA_TASK.equals(task.getTaskName()))
                    deploySchema(dataSource, scriptsLocation);

                else if (ADD_TENANT_TASK.equals(task.getTaskName()))
                    addTenant(dataSource, task.getTaskArg(0), task.getTaskArg(1));

                else if (ALTER_TENANT_TASK.equals(task.getTaskName()))
                    alterTenant(dataSource, task.getTaskArg(0), task.getTaskArg(1));

                else
                    throw new EStartup(String.format("Unknown task: [%s]", task.getTaskName()));
            }

            log.info("All tasks complete");
        }
        finally {

            JdbcSetup.destroyDatasource(dataSource);
        }
    }

    private void deploySchema(DataSource dataSource, String scriptsLocation) {

        log.info("Running task: Deploy schema...");

        var flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(scriptsLocation)
                .sqlMigrationPrefix("")
                .sqlMigrationSuffixes(".sql", ".ddl", ".dml")
                .load();

        flyway.migrate();
    }

    private void addTenant(DataSource dataSource, String tenantCode, String description) {

        log.info("Running task: Add tenant...");
        log.info("New tenant code: [{}]", tenantCode);

        var maxSelect = "select max(tenant_id) from tenant";
        var insertTenant = "insert into tenant (tenant_id, tenant_code, description) values (?, ?, ?)";

        short nextId;

        try (var conn = dataSource.getConnection()) {

            try (var stmt = conn.prepareStatement(maxSelect); var rs = stmt.executeQuery()) {

                if (rs.next()) {

                    nextId = rs.getShort(1);

                    if (rs.wasNull())
                        nextId = 1;
                    else
                        nextId++;
                }
                else
                    nextId = 1;
            }

            try (var stmt = conn.prepareStatement(insertTenant)) {

                stmt.setShort(1, nextId);
                stmt.setString(2, tenantCode);
                stmt.setString(3, description);
                stmt.execute();
            }
        }
        catch (SQLException e) {

            throw new ETracPublic("Failed to add tenant: " + e.getMessage(), e);
        }
    }

    private void alterTenant(DataSource dataSource, String tenantCode, String description) {

        log.info("Running task: Alter tenant...");
        log.info("Tenant code: [{}]", tenantCode);

        var selectTenant = "select tenant_id from tenant where tenant_code = ?";
        var updateTenant = "update tenant set description = ? where tenant_id = ?";

        short tenantId;

        try (var conn = dataSource.getConnection()) {

            try (var stmt = conn.prepareStatement(selectTenant)) {

                stmt.setString(1, tenantCode);

                try (var rs = stmt.executeQuery()) {
                    rs.next();
                    tenantId = rs.getShort(1);
                }
            }

            try (var stmt = conn.prepareStatement(updateTenant)) {

                stmt.setString(1, description);
                stmt.setShort(2, tenantId);
                stmt.execute();
            }
        }
        catch (SQLException e) {

            throw new ETracPublic("Failed to alter tenant: " + e.getMessage(), e);
        }

    }

    /**
     * Entry point for the DeployMetaDB tool.
     *
     * @param args Command line args
     */
    public static void main(String[] args) {

        try {

            var startup = Startup.useCommandLine(DeployMetaDB.class, args, METADB_TASKS);
            startup.runStartupSequence();

            var config = startup.getConfig();
            var tasks = startup.getArgs().getTasks();

            var deploy = new DeployMetaDB(config);
            deploy.runDeployment(tasks);

            System.exit(0);
        }
        catch (EStartup e) {

            if (e.isQuiet())
                System.exit(e.getExitCode());

            System.err.println("The service failed to start: " + e.getMessage());
            e.printStackTrace(System.err);

            System.exit(e.getExitCode());
        }
        catch (Exception e) {

            System.err.println("There was an unexpected error on the main thread: " + e.getMessage());
            e.printStackTrace(System.err);

            System.exit(-1);
        }
    }
}
