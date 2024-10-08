#  Copyright 2024 Accenture Global Solutions Limited
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

import typing as tp
import tracdap.rt.api as trac

import pandas as pd


class BulkDataImport(trac.TracImportModel):

    IMPORT_LOG_SCHEMA = trac.define_schema(
        trac.F("storage_path", trac.STRING, "Storage path", business_key=True),
        trac.F("file_name", trac.STRING, "File name", business_key=True),
        trac.F("size", trac.INTEGER, "File size", business_key=True),
        trac.F("mtime", trac.DATETIME, "Last modified time", business_key=True),
    )

    def define_parameters(self) -> tp.Dict[str, trac.ModelParameter]:
        return dict()

    def define_inputs(self) -> tp.Dict[str, trac.ModelInputSchema]:
        return dict()

    def define_outputs(self) -> tp.Dict[str, trac.ModelOutputSchema]:
        return { "import_log": trac.ModelOutputSchema(schema=self.IMPORT_LOG_SCHEMA) }

    def run_model(self, ctx: trac.TracDataContext):

        storage = ctx.get_file_storage("staging_data")
        root_dir = storage.stat(".")

        import_log = self.import_dir(storage, root_dir)

        ctx.put_pandas_table("import_log", pd.DataFrame(import_log))

    def import_dir(self, storage: trac.TracFileStorage, dir_info: trac.FileStat):

        import_log = []

        for entry in storage.ls(dir_info.storage_path):

            if entry.file_type == trac.FileType.FILE:
                log_entry = self.import_file(storage, entry)
                import_log.append(log_entry)
            else:
                log_entries = self.import_dir(storage, entry)
                import_log.extend(log_entries)

        return import_log

    def import_file(self, storage: trac.TracFileStorage, file: trac.FileStat):

        with storage.read_byte_stream(file.storage_path) as file_stream:
            pass

        # Log entry for this file
        return {
            "storage_path": file.storage_path,
            "file_name": file.file_name,
            "size": file.size,
            "mtime": file.mtime
        }

class SelectiveDataImport(trac.TracImportModel):

    def define_parameters(self) -> tp.Dict[str, trac.ModelParameter]:
        pass

    def define_outputs(self) -> tp.Dict[str, trac.ModelOutputSchema]:
        pass

    def run_model(self, ctx: trac.TracDataContext):

        data_selection = ctx.get_parameter("data_selection")





if __name__ == "__main__":
    import tracdap.rt.launch as launch
    launch.launch_model(BulkDataImport, "config/data_import.yaml", "config/sys_config.yaml")
