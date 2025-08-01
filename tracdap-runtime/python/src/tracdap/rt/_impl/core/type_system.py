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

import typing as tp
import decimal
import datetime as dt

import tracdap.rt.metadata as _meta
import tracdap.rt.exceptions as _ex


class TypeMapping:

    """
    Map types between the TRAC metadata type system and native Python types.

    To map individual values, use :py:class:`MetadataCodec`.
    To map primary data, use :py:class:`DataMapping <tracdap.rt.impl.data.DataMapping>`.
    """

    __PRIMITIVE_TYPES: tp.List[_meta.BasicType] = [
        _meta.BasicType.BOOLEAN,
        _meta.BasicType.INTEGER,
        _meta.BasicType.FLOAT,
        _meta.BasicType.DECIMAL,
        _meta.BasicType.STRING,
        _meta.BasicType.DATE,
        _meta.BasicType.DATETIME
    ]

    __TRAC_TO_PYTHON_BASIC_TYPE: tp.Dict[_meta.BasicType, type] = {
        _meta.BasicType.BOOLEAN: bool,
        _meta.BasicType.INTEGER: int,
        _meta.BasicType.FLOAT: float,
        _meta.BasicType.DECIMAL: decimal.Decimal,
        _meta.BasicType.STRING: str,
        _meta.BasicType.DATE: dt.date,
        _meta.BasicType.DATETIME: dt.datetime
    }

    __PYTHON_TO_TRAC_BASIC_TYPE: tp.Dict[type, _meta.BasicType] = {
        bool: _meta.BasicType.BOOLEAN,
        int: _meta.BasicType.INTEGER,
        float: _meta.BasicType.FLOAT,
        decimal.Decimal: _meta.BasicType.DECIMAL,
        str: _meta.BasicType.STRING,
        dt.date: _meta.BasicType.DATE,
        dt.datetime: _meta.BasicType.DATETIME
    }

    @classmethod
    def is_primitive(cls, trac_type: tp.Union[_meta.BasicType, _meta.TypeDescriptor]) -> bool:

        if isinstance(trac_type, _meta.BasicType):
            return trac_type in cls.__PRIMITIVE_TYPES
        else:
            return trac_type.basicType in cls.__PRIMITIVE_TYPES

    @classmethod
    def trac_to_python(cls, trac_type: _meta.TypeDescriptor) -> type:

        return cls.trac_to_python_basic_type(trac_type.basicType)

    @classmethod
    def trac_to_python_basic_type(cls, trac_basic_type: _meta.BasicType) -> type:

        python_type = cls.__TRAC_TO_PYTHON_BASIC_TYPE.get(trac_basic_type)

        if python_type is None:
            raise _ex.ETracInternal(f"No Python type mapping available for TRAC type [{trac_basic_type}]")

        return python_type

    @classmethod
    def python_to_trac(cls, python_type: type) -> _meta.TypeDescriptor:

        basic_type = cls.python_to_trac_basic_type(python_type)
        return _meta.TypeDescriptor(basic_type)

    @classmethod
    def python_to_trac_basic_type(cls, python_type: type) -> _meta.BasicType:

        basic_type = cls.__PYTHON_TO_TRAC_BASIC_TYPE.get(python_type)

        if basic_type is None:
            raise _ex.ETracInternal(f"No TRAC type mapping available for Python type [{python_type}]")

        return basic_type


class MetadataCodec:

    """
    Map values between TRAC metadata :py:class:`Value <tracdap.rt.metadata.Value>` objects and native Python values.

    To map types, use :py:class:`TypeMapping`.
    To map primary data, use :py:class:`DataMapping <tracdap.rt.impl.data.DataMapping>`.
    """

    @staticmethod
    def decode_value(value: _meta.Value) -> tp.Any:

        if value is None or not isinstance(value, _meta.Value):
            raise _ex.ETracInternal()

        if value.type is None or \
           value.type.basicType is None or \
           value.type.basicType == _meta.BasicType.BASIC_TYPE_NOT_SET:

            raise _ex.ETracInternal("Missing type information")

        return MetadataCodec._decode_value_for_type(value, value.type)

    @staticmethod
    def _decode_value_for_type(value: _meta.Value, type_desc: _meta.TypeDescriptor):

        basic_type = type_desc.basicType

        if basic_type == _meta.BasicType.BOOLEAN:
            return value.booleanValue

        if basic_type == _meta.BasicType.INTEGER:
            return value.integerValue

        if basic_type == _meta.BasicType.FLOAT:
            return value.floatValue

        if basic_type == _meta.BasicType.DECIMAL:
            return decimal.Decimal(value.decimalValue.decimal)

        if basic_type == _meta.BasicType.STRING:
            return value.stringValue

        if basic_type == _meta.BasicType.DATE:
            return dt.date.fromisoformat(value.dateValue.isoDate)

        if basic_type == _meta.BasicType.DATETIME:
            return dt.datetime.fromisoformat(value.datetimeValue.isoDatetime)

        if basic_type == _meta.BasicType.ARRAY:
            items = value.arrayValue.items
            return list(MetadataCodec._decode_value_for_type(x, type_desc.arrayType) for x in items)

        if basic_type == _meta.BasicType.MAP:
            items = value.mapValue.entries.items()
            return dict((k, MetadataCodec._decode_value_for_type(v, type_desc.mapType)) for k, v in items)

        raise _ex.ETracInternal(f"Cannot decode value of type [{basic_type}]")

    @classmethod
    def encode_value(cls, value: tp.Any) -> _meta.Value:

        if value is None:
            raise _ex.ETracInternal("Cannot encode a null value")

        if isinstance(value, bool):
            type_desc = _meta.TypeDescriptor(_meta.BasicType.BOOLEAN)
            return _meta.Value(type_desc, booleanValue=value)

        if isinstance(value, int):
            type_desc = _meta.TypeDescriptor(_meta.BasicType.INTEGER)
            return _meta.Value(type_desc, integerValue=value)

        if isinstance(value, float):
            type_desc = _meta.TypeDescriptor(_meta.BasicType.FLOAT)
            return _meta.Value(type_desc, floatValue=value)

        if isinstance(value, decimal.Decimal):
            type_desc = _meta.TypeDescriptor(_meta.BasicType.DECIMAL)
            return _meta.Value(type_desc, decimalValue=_meta.DecimalValue(str(value)))

        if isinstance(value, str):
            type_desc = _meta.TypeDescriptor(_meta.BasicType.STRING)
            return _meta.Value(type_desc, stringValue=value)

        # dt.datetime inherits dt.date, so check datetime first to avoid encoding datetime as a date
        if isinstance(value, dt.datetime):
            type_desc = _meta.TypeDescriptor(_meta.BasicType.DATETIME)
            return _meta.Value(type_desc, datetimeValue=_meta.DatetimeValue(value.isoformat()))

        if isinstance(value, dt.date):
            type_desc = _meta.TypeDescriptor(_meta.BasicType.DATE)
            return _meta.Value(type_desc, dateValue=_meta.DateValue(value.isoformat()))

        if isinstance(value, list):

            if len(value) == 0:
                raise _ex.ETracInternal("Cannot encode an empty list")

            array_raw_type = type(value[0])
            array_trac_type = TypeMapping.python_to_trac(array_raw_type)

            if any(map(lambda x: type(x) != array_raw_type, value)):
                raise _ex.ETracInternal("Cannot encode a list with values of different types")

            encoded_items = list(map(lambda x: cls.convert_value(x, array_trac_type, True), value))

            return _meta.Value(
                _meta.TypeDescriptor(_meta.BasicType.ARRAY, arrayType=array_trac_type),
                arrayValue=_meta.ArrayValue(encoded_items))

        if isinstance(value, dict):

            if len(value) == 0:
                raise _ex.ETracInternal("Cannot encode an empty dict")

            map_raw_type = type(next(iter(value.values())))
            map_trac_type = TypeMapping.python_to_trac(map_raw_type)

            if any(map(lambda x: type(x) != array_raw_type, value.values())):
                raise _ex.ETracInternal("Cannot encode a dict with values of different types")

            encoded_entries = dict(map(lambda kv: (kv[0], cls.convert_value(kv[1], map_trac_type, True)), value.items()))

            return _meta.Value(
                _meta.TypeDescriptor(_meta.BasicType.ARRAY, mapType=map_trac_type),
                mapValue=_meta.MapValue(encoded_entries))

        raise _ex.ETracInternal(f"Cannot encode value of type [{type(value).__name__}]")

    @classmethod
    def convert_value(cls, raw_value: tp.Any, type_desc: _meta.TypeDescriptor, nested: bool = False):

        if type_desc.basicType == _meta.BasicType.BOOLEAN:
            return cls.convert_boolean_value(raw_value, nested)

        if type_desc.basicType == _meta.BasicType.INTEGER:
            return cls.convert_integer_value(raw_value)

        if type_desc.basicType == _meta.BasicType.FLOAT:
            return cls.convert_float_value(raw_value)

        if type_desc.basicType == _meta.BasicType.DECIMAL:
            return cls.convert_decimal_value(raw_value)

        if type_desc.basicType == _meta.BasicType.STRING:
            return cls.convert_string_value(raw_value)

        if type_desc.basicType == _meta.BasicType.DATE:
            return cls.convert_date_value(raw_value)

        if type_desc.basicType == _meta.BasicType.DATETIME:
            return cls.convert_datetime_value(raw_value)

        if type_desc.basicType == _meta.BasicType.ARRAY:
            return cls.convert_array_value(raw_value, type_desc.arrayType)

        if type_desc.basicType == _meta.BasicType.MAP:
            return cls.convert_map_value(raw_value, type_desc.mapType)

        raise _ex.ETracInternal(f"Conversion to value type [{type_desc.basicType.name}] is not supported yet")

    @staticmethod
    def convert_array_value(raw_value: tp.List[tp.Any], array_type: _meta.TypeDescriptor) -> _meta.Value:

        type_desc = _meta.TypeDescriptor(basicType=_meta.BasicType.ARRAY, arrayType=array_type)

        if not isinstance(raw_value, list):
            msg = f"Value of type [{type(raw_value).__name__}] cannot be converted to {_meta.BasicType.ARRAY.name}"
            raise _ex.ETracInternal(msg)

        items = list(map(lambda x: MetadataCodec.convert_value(x, array_type, True), raw_value))

        return _meta.Value(type_desc, arrayValue=_meta.ArrayValue(items))

    @staticmethod
    def convert_map_value(raw_value: tp.Dict[str, tp.Any], map_type: _meta.TypeDescriptor) -> _meta.Value:

        type_desc = _meta.TypeDescriptor(basicType=_meta.BasicType.MAP, mapType=map_type)

        if not isinstance(raw_value, dict):
            msg = f"Value of type [{type(raw_value).__name__}] cannot be converted to {_meta.BasicType.MAP.name}"
            raise _ex.ETracInternal(msg)

        entries = dict(map(lambda kv: (kv[0], MetadataCodec.convert_value(kv[1], map_type, True)), raw_value.items()))

        return _meta.Value(type_desc, mapValue=_meta.MapValue(entries))

    @staticmethod
    def convert_boolean_value(raw_value: tp.Any, nested: bool = False) -> _meta.Value:

        type_desc = _meta.TypeDescriptor(_meta.BasicType.BOOLEAN) if not nested else None

        if isinstance(raw_value, bool):
            return _meta.Value(type_desc, booleanValue=raw_value)

        msg = f"Value of type [{type(raw_value).__name__}] cannot be converted to {_meta.BasicType.BOOLEAN.name}"
        raise _ex.ETracInternal(msg)

    @staticmethod
    def convert_integer_value(raw_value: tp.Any, nested: bool = False) -> _meta.Value:

        type_desc = _meta.TypeDescriptor(_meta.BasicType.INTEGER) if not nested else None

        # isinstance(bool_value, int) returns True! An explicit check is needed
        if isinstance(raw_value, int) and not isinstance(raw_value, bool):
            return _meta.Value(type_desc, integerValue=raw_value)

        if isinstance(raw_value, float) and raw_value.is_integer():
            return _meta.Value(type_desc, integerValue=int(raw_value))

        msg = f"Value of type [{type(raw_value).__name__}] cannot be converted to {_meta.BasicType.INTEGER.name}"
        raise _ex.ETracInternal(msg)

    @staticmethod
    def convert_float_value(raw_value: tp.Any, nested: bool = False) -> _meta.Value:

        type_desc = _meta.TypeDescriptor(_meta.BasicType.FLOAT) if not nested else None

        if isinstance(raw_value, float):
            return _meta.Value(type_desc, floatValue=raw_value)

        # isinstance(bool_value, int) returns True! An explicit check is needed
        if isinstance(raw_value, int) and not isinstance(raw_value, bool):
            return _meta.Value(type_desc, floatValue=float(raw_value))

        msg = f"Value of type [{type(raw_value).__name__}] cannot be converted to {_meta.BasicType.FLOAT.name}"
        raise _ex.ETracInternal(msg)

    @staticmethod
    def convert_decimal_value(raw_value: tp.Any, nested: bool = False) -> _meta.Value:

        type_desc = _meta.TypeDescriptor(_meta.BasicType.DECIMAL) if not nested else None

        if isinstance(raw_value, decimal.Decimal):
            return _meta.Value(type_desc, decimalValue=_meta.DecimalValue(str(raw_value)))

        # isinstance(bool_value, int) returns True! An explicit check is needed
        if isinstance(raw_value, int) or isinstance(raw_value, float) and not isinstance(raw_value, bool):
            return _meta.Value(type_desc, decimalValue=_meta.DecimalValue(str(raw_value)))

        msg = f"Value of type [{type(raw_value).__name__}] cannot be converted to {_meta.BasicType.DECIMAL.name}"
        raise _ex.ETracInternal(msg)

    @staticmethod
    def convert_string_value(raw_value: tp.Any, nested: bool = False) -> _meta.Value:

        type_desc = _meta.TypeDescriptor(_meta.BasicType.STRING) if not nested else None

        if isinstance(raw_value, str):
            return _meta.Value(type_desc, stringValue=raw_value)

        if isinstance(raw_value, bool) or \
           isinstance(raw_value, int) or \
           isinstance(raw_value, float) or \
           isinstance(raw_value, decimal.Decimal):

            return _meta.Value(type_desc, stringValue=str(raw_value))

        msg = f"Value of type [{type(raw_value).__name__}] cannot be converted to {_meta.BasicType.STRING.name}"
        raise _ex.ETracInternal(msg)

    @staticmethod
    def convert_date_value(raw_value: tp.Any, nested: bool = False) -> _meta.Value:

        type_desc = _meta.TypeDescriptor(_meta.BasicType.DATE) if not nested else None

        if isinstance(raw_value, dt.date):
            return _meta.Value(type_desc, dateValue=_meta.DateValue(isoDate=raw_value.isoformat()))

        if isinstance(raw_value, str):
            date_value = dt.date.fromisoformat(raw_value)
            return _meta.Value(type_desc, dateValue=_meta.DateValue(isoDate=date_value.isoformat()))

        msg = f"Value of type [{type(raw_value).__name__}] cannot be converted to {_meta.BasicType.DATE.name}"
        raise _ex.ETracInternal(msg)

    @staticmethod
    def convert_datetime_value(raw_value: tp.Any, nested: bool = False) -> _meta.Value:

        type_desc = _meta.TypeDescriptor(_meta.BasicType.DATETIME) if not nested else None

        if isinstance(raw_value, dt.datetime):
            return _meta.Value(type_desc, datetimeValue=_meta.DatetimeValue(isoDatetime=raw_value.isoformat()))

        if isinstance(raw_value, str):
            datetime_value = dt.datetime.fromisoformat(raw_value)
            return _meta.Value(type_desc, datetimeValue=_meta.DatetimeValue(isoDatetime=datetime_value.isoformat()))

        msg = f"Value of type [{type(raw_value).__name__}] cannot be converted to {_meta.BasicType.DATETIME.name}"
        raise _ex.ETracInternal(msg)
