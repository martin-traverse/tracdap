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
import urllib.parse as _ulp

import tracdap.rt.config as _cfg
import tracdap.rt.exceptions as _ex

_T = _tp.TypeVar("_T")


def has_plugin_config(config: _cfg.PluginConfig, key: str):

    if key in config.publicProperties:
        return True
    elif key in config.properties:
        return True
    else:
        return False


def read_plugin_config(
        config: _cfg.PluginConfig, key: str, *,
        optional: bool = False,
        default: _tp.Optional[_T] = None,
        convert: _tp.Optional[_tp.Type[_T]] = str) -> _T:

    if key in config.publicProperties:
        value = config.publicProperties[key]
    elif key in config.properties:
        value = config.properties[key]
    elif default is not None:
        value = default
    elif optional:
        return None
    else:
        raise _ex.EConfigParse(f"Missing required property: [{key}]")

    try:
        if convert is bool and isinstance(value, str):
            return True if value.lower() == "true" else False
        else:
            return convert(value)

    except (ValueError, TypeError):
        raise _ex.EConfigParse(f"Wrong property type: [{key}] = [{value}], expected type is [{convert}]")


# Handling for credentials supplied via HTTP(S) URLs

__HTTP_TOKEN_KEY = "token"
__HTTP_USER_KEY = "username"
__HTTP_PASS_KEY = "password"


def get_http_credentials(config: _cfg.PluginConfig, *, url: _ulp.ParseResult | None = None) -> str | None:

    token = read_plugin_config(config, __HTTP_TOKEN_KEY, optional=True)
    username = read_plugin_config(config, __HTTP_USER_KEY, optional=True)
    password = read_plugin_config(config, __HTTP_PASS_KEY, optional=True)

    if token is not None:
        return token

    if username is not None and password is not None:
        return f"{username}:{password}"

    if url and url.username:
        credentials_sep = url.netloc.index("@")
        return url.netloc[:credentials_sep]

    return None


def split_http_credentials(credentials: str) -> (str | None, str | None):

    if credentials is None:
        return None, None

    elif ":" in credentials:
        sep = credentials.index(":")
        username = credentials[:sep]
        password = credentials[sep + 1:]
        return username, password

    else:
        return credentials, None


def apply_http_credentials(url: _ulp.ParseResult, credentials: str) -> _ulp.ParseResult:

    if credentials is None:
        return url

    if url.username is None:
        location = f"{credentials}@{url.netloc}"

    else:
        location_sep = url.netloc.index("@")
        location = f"{credentials}@{url.netloc[location_sep + 1:]}"

    return url._replace(netloc=location)
