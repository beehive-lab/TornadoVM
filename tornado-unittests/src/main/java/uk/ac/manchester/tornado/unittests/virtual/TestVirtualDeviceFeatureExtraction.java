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
 * tornado-test -V --jvm="-Dtornado.device.desc=virtual-device-CPU.json -Dtornado.printKernel=True -Dtornado.virtual.device=True
 * -Dtornado.print.kernel.dir=virtualKernelOut.out" uk.ac.manchester.tornado.unittests.virtual.TestVirtualDeviceFeatureExtraction
 * </code>
 */
public class TestVirtualDeviceFeatureExtraction extends TornadoTestBase {

    private static final byte TAB = 0x9;
    private static final byte NEWLINE = 0xA;
    private static final byte WHITESPACE = 0x20;

    private static final String FEATURE_DUMP_DIR = System.getProperty("tornado.features.dump.dir");
    private static final int SIZE = 8192;

    private static void maxReduction(float[] input, @Reduce float[] result) {
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = Math.max(result[0], input[i]);
        }
    }

    public static long getByteSum(byte[] bytes) {
        long sum = 0;
        for (byte entry : bytes) {
            if (entry == TAB || entry == NEWLINE || entry == WHITESPACE) {
                continue;
            }
            sum += entry;
        }
        return sum;
    }

    public static boolean performComparison(byte[] source, byte[] expected) {
        long sourceSum = getByteSum(source);
        long expectedSum = getByteSum(expected);
        return sourceSum == expectedSum;
    }

    @After
    public void after() {
        // make sure the source file generated is deleted
        File fileLog = new File(FEATURE_DUMP_DIR);
        if (fileLog.exists()) {
            fileLog.delete();
        }
    }

    private void testVirtuaLDeviceFeatureExtraction(String expectedFeaturesFile) throws TornadoExecutionPlanException {
        float[] input = new float[SIZE];
        float[] result = new float[1];
        IntStream.range(0, SIZE).forEach(idx -> {
            input[idx] = idx;
        });

        Arrays.fill(result, Float.MIN_VALUE);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestVirtualDeviceFeatureExtraction::maxReduction, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        String tornadoSDK = System.getenv("TORNADO_SDK");
        String filePath = tornadoSDK + "/examples/generated/virtualDevice/" + expectedFeaturesFile;

        File fileLog = new File(FEATURE_DUMP_DIR);
        File expectedKernelFile = new File(filePath);
        byte[] inputBytes = null;
        byte[] expectedBytes = null;
        try {
            inputBytes = Files.readAllBytes(fileLog.toPath());
            expectedBytes = Files.readAllBytes(expectedKernelFile.toPath());
        } catch (IOException e) {
            Assert.fail();
        }

        boolean fileEquivalent = performComparison(inputBytes, expectedBytes);
        Assert.assertTrue(fileEquivalent);
    }

    @Test
    public void testVirtualDeviceFeatures() throws TornadoExecutionPlanException {
        assertNotBackend(TornadoVMBackendType.PTX);
        assertNotBackend(TornadoVMBackendType.SPIRV);

        testVirtuaLDeviceFeatureExtraction("virtualDeviceFeaturesGPU.json");
    }

}
