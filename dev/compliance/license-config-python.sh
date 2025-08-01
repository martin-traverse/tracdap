# Licensed to the Fintech Open Source Foundation (FINOS) under one or
# more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# FINOS licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with the
# License. You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

ALLOWED_LICENSES="Apache Software License"
ALLOWED_LICENSES="${ALLOWED_LICENSES};Apache 2.0"
ALLOWED_LICENSES="${ALLOWED_LICENSES};Apache-2.0"
ALLOWED_LICENSES="${ALLOWED_LICENSES};Apache License 2.0"
ALLOWED_LICENSES="${ALLOWED_LICENSES};MIT"
ALLOWED_LICENSES="${ALLOWED_LICENSES};MIT License"
ALLOWED_LICENSES="${ALLOWED_LICENSES};MIT No Attribution License (MIT-0)"
ALLOWED_LICENSES="${ALLOWED_LICENSES};BSD License"
ALLOWED_LICENSES="${ALLOWED_LICENSES};BSD-3-Clause"
ALLOWED_LICENSES="${ALLOWED_LICENSES};3-Clause BSD License;"
ALLOWED_LICENSES="${ALLOWED_LICENSES};Python Software Foundation License"
ALLOWED_LICENSES="${ALLOWED_LICENSES};PSF-2.0"
ALLOWED_LICENSES="${ALLOWED_LICENSES};ISC License (ISCL)"
ALLOWED_LICENSES="${ALLOWED_LICENSES};The Unlicense (Unlicense)"

# Some packages specify dual licensing as a string with both licenses
# The license checker can't separate them out, so they are listed here
ALLOWED_LICENSES="${ALLOWED_LICENSES};MIT AND Python-2.0"
ALLOWED_LICENSES="${ALLOWED_LICENSES};Apache-2.0 OR BSD-3-Clause"
ALLOWED_LICENSES="${ALLOWED_LICENSES};Apache-2.0 AND MIT"
ALLOWED_LICENSES="${ALLOWED_LICENSES};Apache-2.0 AND CNRI-Python"

# Dual licensing for Dulwich is Apache or GPL (we are choosing Apache)
ALLOWED_LICENSES="${ALLOWED_LICENSES};Apache-2.0 OR GPL-2.0-or-later"


# The "certifi" package is a dependency of Python Safety, licensed under MPL 2.0
# It is OK to use since the compliance tools are not distributed
# So, we can exclude the package from our license report

IGNORE_LICENSE=certifi

# The aiohappyeyeballs library is now a dependency of aiohttp, which is widely used
# It is licensed under the Python (PSF-2.0) license
# However, licensing info is not correctly declared in their Python packaging
# https://pypi.org/project/aiohappyeyeballs/

IGNORE_LICENSE="${IGNORE_LICENSE} aiohappyeyeballs"

# The click library is licensed under the BSD 3-clause license
# License info is no longer correctly detected
# This is happening for more packages due to a recent standards change for Python license metadata
# A tool is needed that understands the new format but not yet available

# Here is the license info for click:
# https://github.com/pallets/click/?tab=BSD-3-Clause-1-ov-file#readme

IGNORE_LICENSE="${IGNORE_LICENSE} click"
