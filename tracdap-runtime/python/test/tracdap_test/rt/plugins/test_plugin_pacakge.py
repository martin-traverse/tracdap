#  Licensed to the Fintech Open Source Foundation (FINOS) under one or
#  more contributor license agreements. See the NOTICE file distributed
#  with this work for additional information regarding copyright ownership.
#  FINOS licenses this file to you under the Apache License, Version 2.0
#  (the "License"); you may not use this file except in compliance with the
#  License. You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import pathlib
import unittest
import subprocess as sp

import tracdap.rt.config as cfg
import tracdap.rt.metadata as meta
import tracdap.rt.exceptions as ex
import tracdap.rt.launch as launch
import tracdap.rt._impl.runtime as runtime
import tracdap.rt._impl.core.logging as log

import tracdap_test.rt.plugins.test_ext.test_ext_config_loader as ext_loader


class ConfigParserTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        log.configure_logging()

    def setUp(self) -> None:

        commit_hash_proc = sp.run(["git", "rev-parse", "HEAD"], stdout=sp.PIPE)
        self.commit_hash = commit_hash_proc.stdout.decode('utf-8').strip()

        current_repo_url = pathlib.Path(__file__) \
            .joinpath("../../../../../../..") \
            .resolve()

        self.sys_config = cfg.RuntimeConfig()
        self.sys_config.properties["storage.default.location"] = "storage_1"
        self.sys_config.properties["storage.default.format"] = "CSV"

        self.sys_config.resources["unit_test_repo"] = meta.ResourceDefinition(
            resourceType=meta.ResourceType.MODEL_REPOSITORY,
            protocol="local",
            properties={"repoUrl": str(current_repo_url)})

        self.sys_config.resources["storage_1"] = meta.ResourceDefinition(
            resourceType=meta.ResourceType.INTERNAL_STORAGE,
            protocol="LOCAL",
            properties={"rootPath": str(current_repo_url.joinpath("examples/models/python/data"))})

    def test_ext_config_loader_sys_ok(self):

        plugin_package = "tracdap_test.rt.plugins.test_ext"

        trac_runtime = runtime.TracRuntime("test-ext:sys_config_HuX-7", plugin_packages=[plugin_package], dev_mode=True)
        trac_runtime.pre_start()

        # Check that the sys config contains what came from the TEST EXT loader plugin
        self.assertTrue("TEST_EXT_REPO" in trac_runtime._sys_config.resources)

    def test_ext_config_loader_sys_not_found(self):

        plugin_package = "tracdap_test.rt.plugins.test_ext"

        trac_runtime = runtime.TracRuntime("test-ext:sys_config_unknown", plugin_packages=[plugin_package], dev_mode=True)

        # Config error gets wrapped in startup error
        self.assertRaises(ex.EStartup, lambda: trac_runtime.pre_start())

    def test_ext_config_loader_job_ok(self):

        plugin_package = "tracdap_test.rt.plugins.test_ext"

        trac_runtime = runtime.TracRuntime(self.sys_config, plugin_packages=[plugin_package], dev_mode=True)

        with trac_runtime as rt:

            # Load a config object that exists
            job_config = rt.load_job_config("test-ext:job_config_A1-6")
            self.assertIsInstance(job_config, cfg.JobConfig)

    def test_ext_config_loader_job_not_found(self):

        plugin_package = "tracdap_test.rt.plugins.test_ext"

        trac_runtime = runtime.TracRuntime(self.sys_config, plugin_packages=[plugin_package], dev_mode=True)

        with trac_runtime as rt:

            # Load a config object that does not exist
            self.assertRaises(ex.EConfigLoad, lambda: rt.load_job_config("test-ext:job_config_B1-9"))

    def test_ext_config_loader_wrong_protocol(self):

        plugin_package = "tracdap_test.rt.plugins.test_ext"

        trac_runtime = runtime.TracRuntime(self.sys_config, plugin_packages=[plugin_package], dev_mode=True)

        with trac_runtime as rt:

            # Load a config object with the wrong protocol
            self.assertRaises(ex.EConfigLoad, lambda: rt.load_job_config("test-ext-2:job_config_B1-9"))

    def test_launch_model(self):

        launch.launch_model(
            ext_loader.TestExtModel, "test-ext:job_config_A1-6", "test-ext:sys_config_HuX-7",
            plugin_package="tracdap_test.rt.plugins.test_ext")

    def test_launch_model_wrong_protocol(self):

        self.assertRaises(ex.EStartup, lambda: launch.launch_model(
            ext_loader.TestExtModel, "test-ext:job_config_A1-6", "test-ext-2:sys_config_HuX-7",
            plugin_package="tracdap_test.rt.plugins.test_ext"))

        self.assertRaises(ex.EConfigLoad, lambda: launch.launch_model(
            ext_loader.TestExtModel, "test-ext-2:job_config_A1-6", "test-ext:sys_config_HuX-7",
            plugin_package="tracdap_test.rt.plugins.test_ext"))

    def test_launch_model_config_not_found(self):

        self.assertRaises(ex.EStartup, lambda: launch.launch_model(
            ext_loader.TestExtModel, "test-ext:job_config_A1-6", "test-ext:sys_config_unknown",
            plugin_package="tracdap_test.rt.plugins.test_ext"))

        self.assertRaises(ex.EConfigLoad, lambda: launch.launch_model(
            ext_loader.TestExtModel, "test-ext:job_config_unknown", "test-ext:sys_config_HuX-7",
            plugin_package="tracdap_test.rt.plugins.test_ext"))

    def test_launch_job(self):

        launch.launch_job(
            "test-ext:job_config_A1-6", "test-ext:sys_config_HuX-7",
            dev_mode=True, plugin_package="tracdap_test.rt.plugins.test_ext")

    def test_launch_job_wrong_protocol(self):

        self.assertRaises(ex.EStartup, lambda: launch.launch_job(
            "test-ext:job_config_A1-6", "test-ext-2:sys_config_HuX-7",
            dev_mode=True, plugin_package="tracdap_test.rt.plugins.test_ext"))

        self.assertRaises(ex.EConfigLoad, lambda: launch.launch_job(
            "test-ext-2:job_config_A1-6", "test-ext:sys_config_HuX-7",
            dev_mode=True, plugin_package="tracdap_test.rt.plugins.test_ext"))

    def test_launch_job_config_not_found(self):

        self.assertRaises(ex.EStartup, lambda: launch.launch_job(
            "test-ext:job_config_A1-6", "test-ext:sys_config_unknown",
            dev_mode=True, plugin_package="tracdap_test.rt.plugins.test_ext"))

        self.assertRaises(ex.EConfigLoad, lambda: launch.launch_job(
            "test-ext:job_config_unknown", "test-ext:sys_config_HuX-7",
            dev_mode=True, plugin_package="tracdap_test.rt.plugins.test_ext"))

    def test_launch_cli(self):

        launch.launch.launch_cli([
            "--sys-config", "test-ext:sys_config_HuX-7",
            "--job-config", "test-ext:job_config_A1-6",
            "--plugin-package", "tracdap_test.rt.plugins.test_ext",
            "--dev-mode"])

    def test_launch_cli_wrong_protocol(self):

        self.assertRaises(ex.EStartup, lambda: launch.launch.launch_cli([
            "--sys-config", "test-ext-2:sys_config_HuX-7",
            "--job-config", "test-ext:job_config_A1-6",
            "--plugin-package", "tracdap_test.rt.plugins.test_ext",
            "--dev-mode"]))

        self.assertRaises(ex.EConfigLoad, lambda: launch.launch.launch_cli([
            "--sys-config", "test-ext:sys_config_HuX-7",
            "--job-config", "test-ext-2:job_config_A1-6",
            "--plugin-package", "tracdap_test.rt.plugins.test_ext",
            "--dev-mode"]))

    def test_launch_cli_config_not_found(self):

        self.assertRaises(ex.EStartup, lambda: launch.launch.launch_cli([
            "--sys-config", "test-ext:sys_config_unknown",
            "--job-config", "test-ext:job_config_A1-6",
            "--plugin-package", "tracdap_test.rt.plugins.test_ext",
            "--dev-mode"]))

        self.assertRaises(ex.EConfigLoad, lambda: launch.launch.launch_cli([
            "--sys-config", "test-ext:sys_config_HuX-7",
            "--job-config", "test-ext:job_config_unknown",
            "--plugin-package", "tracdap_test.rt.plugins.test_ext",
            "--dev-mode"]))
