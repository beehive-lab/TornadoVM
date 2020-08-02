#!/usr/bin/env bash

# $1 which backend to test native. Can be any combination of { opencl, ptx }.

if [[ $1 == *"ptx"* ]]; then
  echo -e "\nTesting the Native PTX API\n"
  tornado uk.ac.manchester.tornado.drivers.ptx.tests.TestPTXJITCompiler
  tornado uk.ac.manchester.tornado.drivers.ptx.tests.TestPTXTornadoCompiler
fi

if [[ $1 == *"opencl"* ]]; then
  echo -e "\nTesting the Native OpenCL API\n"
  tornado uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLJITCompiler
  tornado uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLTornadoCompiler
fi

echo " " 
