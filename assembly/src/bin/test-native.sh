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

echo " " 
