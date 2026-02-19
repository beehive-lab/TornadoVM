#!/usr/bin/env bash

#
# Copyright (c) 2013-2023, APT Group, Department of Computer Science,
# The University of Manchester.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

read -ra selected_backends < "${TORNADOVM_HOME}/etc/tornado.backend"
if [[ $selected_backends == *"ptx"* ]]; then
  echo -e "\nTesting the Native PTX API\n"
  tornado uk.ac.manchester.tornado.drivers.ptx.tests.TestPTXJITCompiler
  tornado uk.ac.manchester.tornado.drivers.ptx.tests.TestPTXTornadoCompiler
fi

if [[ $selected_backends == *"opencl"* ]]; then
  echo -e "\nTesting the Native OpenCL API\n"
  tornado uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLJITCompiler
  tornado uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLTornadoCompiler
fi

if [[ $selected_backends == *"spirv"* ]]; then

  ## The SPIR-V Backend import Level Zero and OpenCL

  echo -e "\nTesting the Native SPIR-V API\n"
  tornado uk.ac.manchester.tornado.drivers.spirv.tests.TestSPIRVJITCompiler
  tornado uk.ac.manchester.tornado.drivers.spirv.tests.TestSPIRVTornadoCompiler


  echo -e "\nTesting the Native OpenCL API\n"
  tornado uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLJITCompiler
  tornado uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLTornadoCompiler
fi

echo " "
