#!/bin/env bash

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

echo " ============================================= "
echo " LevelZero tests "
echo " ============================================= "

tornado uk.ac.manchester.tornado.drivers.spirv.levelzero.samples.TestLevelZero

tornado uk.ac.manchester.tornado.drivers.spirv.levelzero.samples.TestLevelZeroDedicatedMemory
tornado uk.ac.manchester.tornado.drivers.spirv.levelzero.samples.TestLevelZeroDedicatedMemoryLong
tornado uk.ac.manchester.tornado.drivers.spirv.levelzero.samples.TestLookUpBufferAddress

uk.ac.manchester.tornado.drivers.spirv.levelzero.samples.TestCopies

tornado uk.ac.manchester.tornado.drivers.spirv.levelzero.samples.SimulationLKBuffer


echo " ============================================= "
echo " TornadoVM, LevelZero, SPIRV tests "
echo " ============================================= "

tornado --printBytecodes --debug -Dtornado.spirv.levelzero.memoryAlloc.shared=True uk.ac.manchester.tornado.examples.spirv.TestPrecompiledSPIRV

tornado --printBytecodes --debug -Dtornado.spirv.levelzero.memoryAlloc.shared=True uk.ac.manchester.tornado.examples.spirv.TestPrecompiledSPIRV2

tornado uk.ac.manchester.tornado.drivers.spirv.tests.TestBackend
tornado uk.ac.manchester.tornado.drivers.spirv.tests.TestSPIRVJITCompiler
tornado uk.ac.manchester.tornado.drivers.spirv.tests.TestSPIRVTornadoCompiler
tornado uk.ac.manchester.tornado.drivers.spirv.tests.TestVM
