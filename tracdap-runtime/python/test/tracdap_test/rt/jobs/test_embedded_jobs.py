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

import unittest
import pathlib
import uuid
import datetime as dt

import tracdap.rt.metadata as meta
import tracdap.rt.config as config
import tracdap.rt.ext.embed as embed

import tracdap.rt._impl.static_api as api_hook  # noqa

_ROOT_DIR = pathlib.Path(__file__).parent \
    .joinpath("../../../../../..") \
    .resolve()


class EmbeddedJobsTest(unittest.TestCase):

    def setUp(self):
        api_hook.StaticApiImpl.register_impl()

    def test_embedded_job(self):

        sys_config = config.RuntimeConfig(
            properties={
                "storage.default.location": "STORAGE_NOT_USED"
            },
            resources={
                "tutorials": meta.ResourceDefinition(
                    resourceType=meta.ResourceType.MODEL_REPOSITORY,
                    protocol="local",
                    properties={
                        "repoUrl": str(_ROOT_DIR.joinpath("examples/models/python"))
                    })})

        job_id = str(uuid.uuid4())
        job_timestamp = meta.DatetimeValue(isoDatetime=dt.datetime.now(dt.timezone.utc).isoformat())
        job_config = config.JobConfig(
            jobId=meta.TagHeader(meta.ObjectType.JOB, job_id, 1, job_timestamp, 1, job_timestamp),
            job=meta.JobDefinition(
                jobType=meta.JobType.IMPORT_MODEL,
                importModel=meta.ImportModelJob(
                    language="python", repository="tutorials", path="src",
                    entryPoint="tutorial.using_data.UsingDataModel", version="N/A")))

        with embed.create_runtime(sys_config) as rt:

            result = embed.run_job(rt, job_config)

            self.assertIsInstance(result, config.JobResult)

    def test_embedded_bad_shutdown(self):

        sys_config = config.RuntimeConfig(
            properties={
                "storage.default.location": "STORAGE_NOT_USED"
            },
            resources={
                "tutorials": meta.ResourceDefinition(
                    resourceType=meta.ResourceType.MODEL_REPOSITORY,
                    protocol="local",
                    properties={
                        "repoUrl": str(_ROOT_DIR.joinpath("examples/models/python"))
                    })})

        try:
            rt = embed.create_runtime(sys_config)  # noqa
            del rt

        except Exception as e:
            self.fail("Bad shutdown caused an error" + str(e))
