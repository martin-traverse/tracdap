#  Copyright 2022 Accenture Global Solutions Limited
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import unittest

import tracdap.shell.platform as platform
import tracdap.shell._impl.grpc as grpc_impl


class GrpcTest(unittest.TestCase):

    def test_generate_service_class(self):

        service_api = platform.TracMetadataApi
        service_class = grpc_impl.generate_service_class(service_api)
        service_instance = service_class()

        self.assertIsInstance(service_class, platform.TracMetadataApi.__class__)
        self.assertIsInstance(service_instance, platform.TracMetadataApi)
