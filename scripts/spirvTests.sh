#!/bin/bash 


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
