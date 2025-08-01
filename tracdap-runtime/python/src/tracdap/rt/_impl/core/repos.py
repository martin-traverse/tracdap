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

import typing as _tp

import tracdap.rt.ext.plugins as plugins
import tracdap.rt.config as cfg
import tracdap.rt.metadata as meta
import tracdap.rt.exceptions as ex
import tracdap.rt._impl.core.logging as _logging

# Import repo interfaces
from tracdap.rt.ext.repos import *


class RepositoryManager:

    def __init__(self, sys_config: cfg.RuntimeConfig):

        self._log = _logging.logger_for_object(self)
        self._repos: _tp.Dict[str, IModelRepository] = dict()

        # Initialize all repos in the system config
        # Any errors for missing repo types (plugins) will be raised during startup

        for resource_key, resource in sys_config.resources.items():
            if resource.resourceType == meta.ResourceType.MODEL_REPOSITORY:

                try:

                    # Add global properties related to the repo protocol
                    related_props = {
                        k: v for (k, v) in sys_config.properties.items()
                        if k.startswith(f"{resource.protocol}.")}

                    resource.properties.update(related_props)

                    self._repos[resource_key] = plugins.PluginManager.load_plugin(IModelRepository, resource)

                except ex.EPluginNotAvailable as e:

                    msg = f"Model repository type [{resource.protocol}] is not recognised" \
                          + " (this could indicate a missing model repository plugin)"

                    self._log.error(msg)
                    raise ex.EStartup(msg) from e

    def get_repository(self, repo_name: str) -> IModelRepository:

        repo = self._repos.get(repo_name)

        if repo is None:

            msg = f"Model repository [{repo_name}] is unknown or not configured" \
                  + " (this could indicate a missing repository entry in the system config)"

            self._log.error(msg)
            raise ex.EModelRepoConfig(msg)

        return repo
