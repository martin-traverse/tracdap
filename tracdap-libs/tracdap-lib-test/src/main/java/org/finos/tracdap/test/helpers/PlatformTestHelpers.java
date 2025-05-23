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

package org.finos.tracdap.test.helpers;

import org.finos.tracdap.common.config.ConfigManager;
import org.finos.tracdap.common.exception.ETracInternal;
import org.finos.tracdap.common.plugin.PluginManager;
import org.finos.tracdap.common.service.TracServiceBase;
import org.finos.tracdap.common.startup.StandardArgs;
import org.finos.tracdap.common.startup.Startup;
import org.finos.tracdap.common.startup.StartupSequence;
import org.finos.tracdap.tools.secrets.SecretTool;
import org.finos.tracdap.tools.deploy.DeployTool;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;


public class PlatformTestHelpers {

    @FunctionalInterface
    public interface LauncherFunction<T> {

        T launch(StartupSequence startup);
    }

    public interface Launcher<T> {

        T launch(LauncherFunction<T> launcher);
    }

    public static <T> Launcher<T> prepare(Class<T> toolClass, Path workingDir, URL configPath, String keystoreKey) {

        return prepare(toolClass, workingDir, configPath, keystoreKey, true);
    }

    public static <T> Launcher<T> prepare(Class<T> toolClass, Path workingDir, URL configPath, String keystoreKey, boolean useSecrets) {

        var startup = Startup.useConfigFile(toolClass, workingDir, configPath.toString(), keystoreKey);
        startup.runStartupSequence(useSecrets);

        return launcher -> launcher.launch(startup);
    }

    public static void runSecretTool(Path workingDir, URL configPath, String keystoreKey, List<StandardArgs.Task> tasks) {

        // Do not use secrets during start up for the secret tool
        // The secret tool is often used to create the secrets file

        var launcher = prepare(SecretTool.class, workingDir, configPath, keystoreKey, /* useSecrets = */ false);
        var secretTool = launcher.launch(startup -> new SecretTool(startup.getPlugins(), startup.getConfig(), keystoreKey));

        secretTool.runTasks(tasks);
    }

    public static void runDeployTool(Path workingDir, URL configPath, String keystoreKey, List<StandardArgs.Task> tasks) {

        var launcher = prepare(DeployTool.class, workingDir, configPath, keystoreKey);
        var deployDb = launcher.launch(startup -> new DeployTool(startup.getConfig()));

        deployDb.runDeployment(tasks);
    }

    public static <TSvc extends TracServiceBase> TSvc startService(
            Class<TSvc> serviceClass, Path workingDir,
            URL configPath, String keystoreKey) {

        try {

            var startup = Startup.useConfigFile(
                    serviceClass, workingDir,
                    configPath.toString(), keystoreKey);

            startup.runStartupSequence();

            var plugins = startup.getPlugins();
            var config = startup.getConfig();

            var constructor = serviceClass.getConstructor(PluginManager.class, ConfigManager.class);
            var service = constructor.newInstance(plugins, config);
            service.start();

            return service;
        }
        catch (NoSuchMethodException e) {

            var err = String.format(
                    "Service class [%s] does not provide the standard service constructor",
                    serviceClass.getSimpleName());

            throw new ETracInternal(err);
        }
        catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {

            var err = String.format(
                    "Service class [%s] cannot be constructed: %s",
                    serviceClass.getSimpleName(), e.getMessage());

            throw new ETracInternal(err, e);
        }
    }
}
