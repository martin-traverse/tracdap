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

import enum
import typing as tp

import tracdap.rt.exceptions as ex
import tracdap.rt.metadata as metadata

import tracdap.rt_gen.grpc.tracdap.metadata.type_pb2 as type_pb2
import tracdap.rt_gen.grpc.tracdap.metadata.object_id_pb2 as object_id_pb2
import tracdap.rt_gen.grpc.tracdap.metadata.object_pb2 as object_pb2
import tracdap.rt_gen.grpc.tracdap.metadata.model_pb2 as model_pb2
import tracdap.rt_gen.grpc.tracdap.metadata.data_pb2 as data_pb2
import tracdap.rt_gen.grpc.tracdap.metadata.storage_pb2 as storage_pb2
import tracdap.rt_gen.grpc.tracdap.metadata.job_pb2 as job_pb2

from google.protobuf import message as _message


__METADATA_MAPPING = {
    metadata.TypeDescriptor: type_pb2.TypeDescriptor,
    metadata.Value: type_pb2.Value,
    metadata.DecimalValue: type_pb2.DecimalValue,
    metadata.DateValue: type_pb2.DateValue,
    metadata.DatetimeValue: type_pb2.DatetimeValue,
    metadata.ArrayValue: type_pb2.ArrayValue,
    metadata.MapValue: type_pb2.MapValue,
    metadata.TagHeader: object_id_pb2.TagHeader,
    metadata.TagSelector: object_id_pb2.TagSelector,
    metadata.ObjectDefinition: object_pb2.ObjectDefinition,
    metadata.ModelDefinition: model_pb2.ModelDefinition,
    metadata.ModelParameter: model_pb2.ModelParameter,
    metadata.ModelInputSchema: model_pb2.ModelInputSchema,
    metadata.ModelOutputSchema: model_pb2.ModelOutputSchema,
    metadata.SchemaDefinition: data_pb2.SchemaDefinition,
    metadata.TableSchema: data_pb2.TableSchema,
    metadata.FieldSchema: data_pb2.FieldSchema,
    metadata.PartKey: data_pb2.PartKey,
    metadata.DataDefinition: data_pb2.DataDefinition,
    metadata.DataPartition: data_pb2.DataPartition,
    metadata.DataSnapshot: data_pb2.DataSnapshot,
    metadata.DataDelta: data_pb2.DataDelta,
    metadata.StorageDefinition: storage_pb2.StorageDefinition,
    metadata.StorageIncarnation: storage_pb2.StorageIncarnation,
    metadata.StorageCopy: storage_pb2.StorageCopy,
    metadata.StorageItem: storage_pb2.StorageItem,
    metadata.ResultDefinition: job_pb2.ResultDefinition
}


def encode(obj: tp.Any) -> tp.Any:

    # Translate TRAC domain objects into generic dict / list structures
    # These can be accepted by gRPC message constructors, do not try to build messages directly
    # Use shallow copies and builtins to minimize performance impact

    if obj is None:
        return None

    if isinstance(obj, str) or isinstance(obj, bool) or isinstance(obj, int) or isinstance(obj, float):
        return obj

    if isinstance(obj, enum.Enum):
        return obj.value

    if isinstance(obj, list):
        return list(map(encode, obj))

    if isinstance(obj, dict):
        return dict((k, encode(v)) for k, v in obj.items())

    msg_class = __METADATA_MAPPING.get(type(obj))

    if msg_class is None:
        raise ex.ETracInternal(f"No gRPC metadata mapping is available for type {type(obj).__name__}")

    if hasattr(obj, "__slots__"):
        attrs = dict((k, encode(getattr(obj, k))) for k in obj.__slots__)
    else:
        attrs = dict((k, encode(v)) for k, v in obj.__dict__.items() if v is not None)

    return msg_class(**attrs)
