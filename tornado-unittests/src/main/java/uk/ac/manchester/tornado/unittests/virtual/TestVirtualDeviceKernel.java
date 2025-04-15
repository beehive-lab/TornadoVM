/*
 * Copyright (c) 2020, 2022, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.unittests.virtual;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.stream.IntStream;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V --jvm="-Dtornado.device.desc=virtual-device-GPU.json -Dtornado.printKernel=True -Dtornado.virtual.device=True
 * -Dtornado.print.kernel.dir=virtualKernelOut.out" uk.ac.manchester.tornado.unittests.virtual.TestVirtualDeviceKernel
 * </code>
 */
public class TestVirtualDeviceKernel extends TornadoTestBase {

    private static final String SOURCE_DIR = System.getProperty("tornado.print.kernel.dir");
    private static final int SIZE = 8192;

    private static void maxReduction(float[] input, @Reduce float[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = Math.max(result[0], input[i]);
        }
    }

    @After
    public void after() {
        // make sure the source file generated is deleted
        File fileLog = new File(SOURCE_DIR);
        if (fileLog.exists()) {
            fileLog.delete();
        }
    }

    private void testVirtualDeviceKernel(String expectedCodeFile) throws TornadoExecutionPlanException {
        float[] input = new float[SIZE];
        float[] result = new float[1];
        IntStream.range(0, SIZE).forEach(idx -> input[idx] = idx);

        Arrays.fill(result, Float.MIN_VALUE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestVirtualDeviceKernel::maxReduction, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        String tornadoSDK = System.getenv("TORNADO_SDK");
        String filePath = tornadoSDK + "/examples/generated/virtualDevice/" + expectedCodeFile;

        File fileLog = new File(SOURCE_DIR);
        File expectedKernelFile = new File(filePath);
        byte[] generatedKernel = null;
        byte[] expectedKernel = null;
        try {
            generatedKernel = Files.readAllBytes(fileLog.toPath());
            expectedKernel = Files.readAllBytes(expectedKernelFile.toPath());
        } catch (IOException e) {
            Assert.fail();
        }

        boolean fileEquivalent = TestVirtualDeviceFeatureExtraction.performComparison(generatedKernel, expectedKernel);
        Assert.assertTrue("There is a mismatch between pre-compiled and JIT compiled kernels.", fileEquivalent);
    }

    @Test
    public void testVirtualDeviceKernel() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);
        testVirtualDeviceKernel("virtualDeviceKernelGPU.cl");
    }
}
