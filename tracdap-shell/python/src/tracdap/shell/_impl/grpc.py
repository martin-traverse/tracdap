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

import typing as _tp
import inspect as _inspect

import google.protobuf.descriptor


class GrpcService:

    def _svc_test(self):
        print("In service impl")

    def _unary_call(self, call: google.protobuf.descriptor.MethodDescriptor):
        pass


def generate_service_api_call(request_type: type, response_type: type):

    def service_api_call(self, request: request_type) -> response_type:

        print(f"Got request of type {type(request)}")
        self._svc_test()
        return None

    return service_api_call


def generate_service_class(service_api: type):

    type_name = f"{service_api.__name__}Impl"
    bases = (service_api, GrpcService)
    members = dict()

    for method_name in dir(service_api):

        if method_name.startswith("_"):
            continue

        method_stub = getattr(service_api, method_name)

        if not isinstance(method_stub, _tp.Callable):
            continue

        signature = _inspect.signature(method_stub)
        annotations = _inspect.get_annotations(method_stub)

        if len(signature.parameters) != 2:
            continue

        params = iter(signature.parameters)
        next(params)  # skip self

        request_param = next(params)
        request_type = annotations.get(request_param)
        response_type = signature.return_annotation

        method_impl = generate_service_api_call(request_type, response_type)
        method_impl.__name__ = method_stub.__name__
        method_impl.__doc__ = method_stub.__doc__

        members[method_name] = method_impl

    service_class = type(type_name, bases, members)
    service_class.__module__ = GrpcService.__module__
    service_class.__doc__ = service_api.__doc__

    return service_class
