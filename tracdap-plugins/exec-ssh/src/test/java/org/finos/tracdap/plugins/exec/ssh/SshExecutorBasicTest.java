/*
 * Copyright 2023 Accenture Global Solutions Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finos.tracdap.plugins.exec.ssh;

import org.finos.tracdap.common.config.ConfigManager;
import org.finos.tracdap.common.exec.ExecutorBasicTestSuite;
import org.finos.tracdap.common.exec.IBatchExecutor;
import org.finos.tracdap.common.plugin.PluginManager;
import org.finos.tracdap.config.PlatformConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

import java.nio.file.Path;


@Tag("integration")
@Tag("int-exec")
public class SshExecutorBasicTest extends ExecutorBasicTestSuite {

    String configPath = "/Volumes/Data/Dev/Code/trac/dev/config/trac-devlocal.yaml";
    Path workingDir = Path.of(configPath).getParent().getParent().getParent();

    @BeforeEach
    public void setupExecutor() {

        var pluginManager = new PluginManager();
        pluginManager.initConfigPlugins();
        pluginManager.initRegularPlugins();

        var configManager = new ConfigManager(configPath, workingDir, pluginManager);
        var platformConfig = configManager.loadRootConfigObject(PlatformConfig.class);

        executor = pluginManager.createService(IBatchExecutor.class, platformConfig.getExecutor(), configManager);
        executor.start();
    }
}
