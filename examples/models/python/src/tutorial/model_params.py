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
import dataclasses as _dc
import datetime as _dt
import tracdap.rt.api.experimental as trac


@_dc.dataclass
class MyStruct:

    var1: str
    var2: float
    var3: _dt.datetime


class ModelParamsExmaple(trac.TracModel):

    def define_parameters(self) -> _tp.Dict[str, trac.ModelParameter]:

        my_map = trac.map_type(trac.STRING)
        my_struct_1 = trac.struct_type(var1=trac.FLOAT, var2=trac.DATETIME)
        my_struct_2 = trac.struct_type(python_type=MyStruct)

        return trac.define_parameters(
            trac.P("my_map", my_map, label="Map type"),
            trac.P("my_struct_1", my_struct_1, label="Struct type defined in code"),
            trac.P("my_struct_2", my_struct_2, label="Struct type defined as a dataclass"))


    def define_inputs(self) -> _tp.Dict[str, trac.ModelInputSchema]:
        pass

    def define_outputs(self) -> _tp.Dict[str, trac.ModelOutputSchema]:
        pass

    def run_model(self, ctx: trac.TracContext):

        struct_param = ctx.get_parameter("my_struct_1", python_type=MyStruct)

        pass
