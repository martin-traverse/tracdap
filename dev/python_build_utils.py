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
import shutil
import os
import sys
import unittest
import subprocess
import packaging.version
import fileinput
import platform


def generate_from_proto(
        root_path: pathlib.Path, proto_src: [str],
        project_path: pathlib.Path, gen_output_dir: str):

    generated_dir = project_path.joinpath("generated")

    if generated_dir.exists():
        shutil.rmtree(generated_dir)

    generated_dir.mkdir(parents=False, exist_ok=False)

    protoc_ctrl = root_path.joinpath("dev/codegen/protoc-ctrl.py")

    domain_cmd = [
        str(sys.executable), str(protoc_ctrl), "python_runtime",
        "--out", f"{generated_dir.joinpath(gen_output_dir).joinpath('domain')}"]

    proto_cmd = [
        str(sys.executable), str(protoc_ctrl), "python_proto",
        "--out", f"{generated_dir.joinpath(gen_output_dir).joinpath('proto')}"]

    for proto_path in proto_src:
        domain_cmd += ["--proto_path", proto_path]
        proto_cmd += ["--proto_path", proto_path]

    domain_proc = subprocess.Popen(domain_cmd, stdout=subprocess.PIPE, cwd=root_path, env=os.environ)
    domain_out, domain_err = domain_proc.communicate()
    domain_result = domain_proc.wait()

    print(domain_out.decode("utf-8"))

    if domain_result != 0:
        raise subprocess.SubprocessError("Failed to generate domain classes from definitions")

    proto_proc = subprocess.Popen(proto_cmd, stdout=subprocess.PIPE, cwd=root_path, env=os.environ)
    proto_out, proto_err = proto_proc.communicate()
    proto_result = proto_proc.wait()

    print(proto_out.decode("utf-8"))

    if proto_result != 0:
        raise subprocess.SubprocessError("Failed to generate proto classes from definitions")


def reset_build_dir(build_path: pathlib.Path):

    if build_path.exists():
        shutil.rmtree(build_path)

    build_path.mkdir(parents=False, exist_ok=False)


def copy_project_files(project_dir: pathlib.Path, build_dir: pathlib.Path, file_list: [str]):

    for file in file_list:

        source_path = project_dir.joinpath(file)
        target_path = build_dir.joinpath(file)

        if source_path.is_dir():
            shutil.copytree(source_path, target_path)
        else:
            shutil.copy(source_path, target_path)


def copy_license(license_dir: pathlib.Path, build_dir: pathlib.Path):

    # Copy the license file out of the project root

    shutil.copy(
        license_dir.joinpath("../../LICENSE"),
        build_dir.joinpath("LICENSE"))


def copy_generated_files(
        project_dir, generate_rel_path,
        build_dir, target_relative_path):

    # For generated packages, the main source tree contains placeholders that import everything
    # from the generated tree. We want to remove the placeholders and put the generated code into
    # the main source tree

    generated_metadata_path = project_dir.joinpath(generate_rel_path)
    target_metadata_path = build_dir.joinpath(target_relative_path)

    shutil.rmtree(target_metadata_path)
    shutil.copytree(generated_metadata_path, target_metadata_path)


def filter_setup_cfg(build_dir: pathlib.Path, filter_text: str):

    # Remove references to rt_gen package in setup.cfg, since everything is now in place under src/

    for line in fileinput.input(build_dir.joinpath("setup.cfg"), inplace=True):
        if filter_text not in line:
            print(line, end='')


def set_trac_version(root_dir: pathlib.Path, build_dir: pathlib.Path, version_file: str):

    if platform.system().lower().startswith("win"):
        command = ['powershell', '-ExecutionPolicy', 'Bypass', '-File', f'{root_dir}\\dev\\version.ps1']
    else:
        command = [f'{root_dir}/dev/version.sh']

    process = subprocess.Popen(command, stdout=subprocess.PIPE, cwd=root_dir)
    output, err = process.communicate()
    exit_code = process.wait()

    if exit_code != 0:
        raise subprocess.SubprocessError('Failed to get TRAC d.a.p. version')

    raw_version = output.decode('utf-8').strip()

    # Using Python's Version class normalises the version according to PEP440
    trac_version = packaging.version.Version(raw_version)

    # Set the version number used in the package metadata

    # setup.cfg uses file: and attr: for reading the version in from external sources
    # attr: doesn't work with namespace packages, __version__ has to be in the root package
    # file: works for the sdist build but is throwing an error for bdist_wheel, this could be a bug
    # Writing the version directly into setup.cfg avoids both of these issues

    for line in fileinput.input(build_dir.joinpath("setup.cfg"), inplace=True):
        if line.startswith("version ="):
            print(f"version = {str(trac_version)}")
        else:
            print(line, end="")

    # Set the version number embedded into the package

    embedded_version_file = build_dir.joinpath(version_file)
    embedded_header_copied = False

    for line in fileinput.input(embedded_version_file, inplace=True):

        if line.isspace() and not embedded_header_copied:
            embedded_header_copied = True
            print("")
            print(f'__version__ = "{str(trac_version)}"')

        if not embedded_header_copied:
            print(line, end="")


def run_tests(root_dir: pathlib.Path, project_dir: pathlib.Path, src_paths, test_path):

    cwd = os.getcwd()
    python_path = [*sys.path]

    try:

        os.chdir(root_dir)

        for src_path in src_paths:
            sys.path.append(src_path)

        runner = unittest.TextTestRunner()
        loader = unittest.TestLoader()
        suite = loader.discover(
            start_dir=str(project_dir.joinpath(test_path)),
            top_level_dir=str(project_dir.joinpath("test")))

        result = runner.run(suite)

        if not result.wasSuccessful():
            exit(-1)

    finally:

        os.chdir(cwd)
        sys.path = python_path


def run_pypa_build(build_dir: pathlib.Path):

    build_exe = sys.executable
    build_args = ["python", "-m", "build"]

    build_result = subprocess.run(executable=build_exe, args=build_args, cwd=build_dir)

    if build_result.returncode != 0:
        raise subprocess.SubprocessError(f"PyPA Build failed with exit code {build_result.returncode}")
