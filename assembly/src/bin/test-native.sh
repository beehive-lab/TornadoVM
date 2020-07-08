#!/usr/bin/env bash

echo -e "\nTesting the Native PTX API\n"
tornado uk.ac.manchester.tornado.drivers.cuda.tests.TestPTXJITCompiler
tornado uk.ac.manchester.tornado.drivers.cuda.tests.TestPTXTornadoCompiler

echo -e "\nTesting the Native OpenCL API\n"
tornado uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLJITCompiler
tornado uk.ac.manchester.tornado.drivers.opencl.tests.TestOpenCLTornadoCompiler

echo " " 
