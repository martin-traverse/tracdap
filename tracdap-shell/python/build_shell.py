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

import pathlib
import argparse
import sys


SCRIPT_DIR = pathlib.Path(__file__) \
    .parent \
    .resolve()

ROOT_PATH = SCRIPT_DIR \
    .parent.parent \
    .resolve()

RUNTIME_DIR = ROOT_PATH \
    .joinpath("tracdap-runtime/python")

BUILD_PATH = SCRIPT_DIR \
    .joinpath("build")

COPY_FILES = [
    "pyproject.toml",
    "setup.cfg",
    "README.md",
    "src"
]

PROTO_PATHS = [
    "tracdap-api/tracdap-metadata/src/main/proto",
    "tracdap-api/tracdap-services/src/main/proto"
]

TEST_SRC_PATHS = [
    str(SCRIPT_DIR.joinpath("generated")),
    str(SCRIPT_DIR.joinpath("src")),
    str(SCRIPT_DIR.joinpath("test")),
    str(RUNTIME_DIR.joinpath("generated")),
    str(RUNTIME_DIR.joinpath("src"))]


sys.path.append(str(ROOT_PATH.joinpath("dev")))
import python_build_utils as build_utils  # noqa


def cli_args():

    parser = argparse.ArgumentParser(description='TRAC/Python Runtime Builder')

    parser.add_argument(
        "--target", type=str, metavar="target",
        choices=["codegen", "test", "dist"], nargs="*", required=True,
        help="The target to build")

    return parser.parse_args()


def main():

    args = cli_args()

    if "codegen" in args.target:
        build_utils.generate_from_proto(ROOT_PATH, PROTO_PATHS, SCRIPT_DIR, "tracdap/sh_gen")

    if "test" in args.target:

        build_utils.run_tests(ROOT_PATH, SCRIPT_DIR, TEST_SRC_PATHS, "test/tracdap_test")

    if "dist" in args.target:

        build_utils.reset_build_dir()
        build_utils.copy_project_files(SCRIPT_DIR, BUILD_PATH, COPY_FILES)
        build_utils.copy_license(ROOT_PATH, BUILD_PATH)
        build_utils.set_trac_version(ROOT_PATH, BUILD_PATH, "src/tracdap/shell/_version.py")

        build_utils.copy_generated_files(
            SCRIPT_DIR, "generated/tracdap/sh_gen/domain/tracdap/config",
            BUILD_PATH, "src/tracdap/shell/platform")

        build_utils.filter_setup_cfg(BUILD_PATH, "sh_gen")

        build_utils.run_pypa_build(BUILD_PATH)


main()
