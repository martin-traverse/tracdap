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
import dataclasses as _dc
import datetime as _dt
import typing as _tp

import tracdap.rt.api.experimental as trac
import pandas as pd

import tutorial.schemas as schemas


class EvolutionModel(enum.Enum):
    PERTURB = 1
    SCATTER = 2
    STOCHASTIC = 3

@_dc.dataclass
class ScenarioConfig:

    scenario_name: str
    default_weight: float
    evolution_model: EvolutionModel
    apply_smoothing: bool

@_dc.dataclass
class RunConfig:

    include_front_book: bool
    base_date: _dt.date
    base_scenario: ScenarioConfig

    stress_scenarios: dict[str, ScenarioConfig]


class PrepareConfig(trac.TracModel):

    def define_parameters(self) -> _tp.Dict[str, trac.ModelParameter]:

        return trac.define_parameters()

    def define_inputs(self) -> _tp.Dict[str, trac.ModelInputSchema]:

        run_config_struct = trac.define_struct(RunConfig)
        run_config = trac.define_input(run_config_struct, label="Run configuration")

        scenarios = trac.define_input_table(
            trac.F("scenario_name", trac.STRING, "Scenario name", business_key=True),
            trac.F("default_weight", trac.FLOAT, "Default weight"),
            trac.F("evolution_model", trac.STRING, "Evolution model", categorical=True),
            trac.F("apply_smoothing", trac.BOOLEAN, "Smoothing flag"))

        return {
            "run_config": run_config,
            "scenarios": scenarios
        }

    def define_outputs(self) -> _tp.Dict[str, trac.ModelOutputSchema]:

        modified_config = trac.define_output_struct(RunConfig, label="Modified config for next model stage")
        return {"augmented_config": modified_config}

    def run_model(self, ctx: trac.TracContext):

        run_config = ctx.get_struct("run_config", RunConfig)
        scenarios = ctx.get_pandas_table("scenarios")

        # Add supplied scenarios to the run config
        # Iterating rows is slow, but ok for processing small config data sets
        for _, row in scenarios.iterrows():

            scenario_name=row["scenario_name"]

            run_config.stress_scenarios[scenario_name] = ScenarioConfig(
                scenario_name=scenario_name,
                default_weight=row["default_weight"],
                evolution_model=row["evolution_model"],
                apply_smoothing=row["apply_smoothing"])

        ctx.put_struct("augmented_config", run_config)


class RunSimulation(trac.TracModel):

    def define_parameters(self) -> _tp.Dict[str, trac.ModelParameter]:

        return trac.define_parameters(
            trac.P("t0_date", trac.DATE, "T0 date for projection"),
            trac.P("projection_period", trac.INTEGER, "Projection period (in days)"))

    def define_inputs(self) -> _tp.Dict[str, trac.ModelInputSchema]:

        run_config_struct = trac.define_struct(RunConfig)
        run_config = trac.define_input(run_config_struct, label="Run configuration")

        customer_loans_schema = trac.load_schema(schemas, "customer_loans.csv")
        customer_loans = trac.define_input(customer_loans_schema, label="Customer loans")

        return {
            "augmented_config": run_config,
            "customer_loans": customer_loans
        }

    def define_outputs(self) -> _tp.Dict[str, trac.ModelOutputSchema]:

        customer_loans_schema = trac.load_schema(schemas, "customer_loans.csv")

        simulated_loans = trac.define_output_table(
            [
                trac.F("scenario_name", trac.STRING, "Scenario name"),
                trac.F("projection_period", trac.INTEGER, "Simulation period in days"),
                *customer_loans_schema.fields,
            ],
            label="Simulated customer loans")

        return {"simulated_loans": simulated_loans}

    def run_model(self, ctx: trac.TracContext):

        projection_period = ctx.get_parameter("projection_period")
        run_config = ctx.get_struct("augmented_config", RunConfig)
        customer_loans = ctx.get_pandas_table("customer_loans")

        t0_data = customer_loans.assign(
            scenario_name = "t0_data",
            projection_period = 0)

        ctx.log().info("Running base scenario...")
        base_result = self.run_scenario(t0_data, run_config.base_scenario, projection_period)
        results = [base_result]

        for scenario_name, scenario in run_config.stress_scenarios.items():
            ctx.log().info(f"Running scenario [{scenario_name}]....")
            scenario_result = self.run_scenario(t0_data, scenario, projection_period)
            results.append(scenario_result)

        simulated_loans = pd.concat(results)

        ctx.put_pandas_table("simulated_loans", simulated_loans)

    def run_scenario(self, t0_data: pd.DataFrame, scenario: ScenarioConfig, projection_period: int):

        return t0_data.assign(
            scenario_name = scenario.scenario_name,
            projection_period = projection_period)


if __name__ == "__main__":
    import tracdap.rt.launch as launch
    launch.launch_model(RunSimulation, "config/structured_objects.yaml", "config/sys_config.yaml")
