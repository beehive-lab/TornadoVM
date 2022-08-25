#!/usr/bin/env bash

read -ra selected_backends < "${TORNADO_SDK}/etc/tornado.backend"
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
