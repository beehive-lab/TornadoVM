/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.tensors;

import org.junit.Assert;
import org.junit.Test;
import uk.ac.manchester.tornado.api.types.tensors.GGMLType;
import uk.ac.manchester.tornado.api.types.tensors.Shape;
import uk.ac.manchester.tornado.api.types.tensors.TensorQ8;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import static java.lang.Boolean.FALSE;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tensors.TestTensorQ8
 * </code>
 */
public class TestTensorQ8 extends TornadoTestBase {

    private static final boolean VERBOSE = FALSE;  // Control verbose output

    private void printVerbose(String message) {
        if (VERBOSE) {
            System.out.println(message);
        }
    }

    private void printVerboseF(String format, Object... args) {
        if (VERBOSE) {
            System.out.printf(format, args);
        }
    }

    @Test
    public void testBasicQuantization() {
        Shape shape = new Shape(1);
        TensorQ8 tensor = new TensorQ8(shape);

        float testValue = 1.5f;
        tensor.setFloat(0, testValue);
        float retrieved = tensor.getFloat(0);
        printVerboseF("Segment size for storing single value %d%n", tensor.getSegment().byteSize());
        Assert.assertEquals(testValue, retrieved, 0.1f);
    }

    @Test
    public void testTensorQ8SetAndGetFloat() {
        Shape shape = new Shape(5);
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        float[] valuesToSet = {0.5f, -1.0f, 25.0f, -30.5f, 0.0f};
        for (int i = 0; i < valuesToSet.length; i++) {
            tensorQ8.setFloat(i, valuesToSet[i]);
        }

        for (int i = 0; i < valuesToSet.length; i++) {
            Assert.assertEquals(valuesToSet[i], tensorQ8.getFloat(i), 0.1f);
        }
    }

    @Test
    public void testTensorQ8SetAndGetFloatVerify() {
        int blockSize = GGMLType.Q8_0.getBlockSize();
        Shape shape = new Shape(blockSize);
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        float[] valuesToSet = new float[blockSize];
        float[] pattern = {0.5f, -1.0f, 25.0f, -30.5f, 0.0f};
        for (int i = 0; i < blockSize; i++) {
            valuesToSet[i] = pattern[i % pattern.length];
        }

        printVerboseF("Total elements: %d%n", shape.getSize());
        printVerboseF("Block size: %d%n", blockSize);
        printVerboseF("Total allocated bytes: %d%n", tensorQ8.getSegment().byteSize());

        for (int i = 0; i < valuesToSet.length; i++) {
            tensorQ8.setFloat(i, valuesToSet[i]);
            float retrieved = tensorQ8.getFloat(i);
            printVerboseF("Index %d: Set=%.2f Retrieved=%.2f%n",
                    i, valuesToSet[i], retrieved);
            Assert.assertEquals("Value mismatch at index " + i,
                    valuesToSet[i], retrieved, 0.1f);
        }

        for (int i = 0; i < valuesToSet.length; i++) {
            float retrieved = tensorQ8.getFloat(i);
            Assert.assertEquals("Final verification failed at index " + i,
                    valuesToSet[i], retrieved, 0.1f);
        }
    }

    @Test
    public void testMixedScaleValues() {
        Shape shape = new Shape(GGMLType.Q8_0.getBlockSize());
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        tensorQ8.setFloat(0, 100.0f);
        tensorQ8.setFloat(1, 0.001f);
        tensorQ8.setFloat(2, -100.0f);
        tensorQ8.setFloat(3, -0.001f);

        Assert.assertEquals(100.0f, tensorQ8.getFloat(0), 1.0f);
        Assert.assertEquals(-100.0f, tensorQ8.getFloat(2), 1.0f);

        float small1 = tensorQ8.getFloat(1);
        float small2 = tensorQ8.getFloat(3);
        Assert.assertTrue("Small positive value lost sign", small1 >= 0);
        Assert.assertTrue("Small negative value lost sign", small2 <= 0);
    }

    @Test
    public void testQuantizationRange() {
        Shape shape = new Shape(GGMLType.Q8_0.getBlockSize());
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        float[] testValues = {
                0.0f, 1e-6f, -1e-6f, 100.0f, -100.0f,
        };

        for (int i = 0; i < testValues.length; i++) {
            tensorQ8.setFloat(i, testValues[i]);
            float retrieved = tensorQ8.getFloat(i);

            if (Math.abs(testValues[i]) < 1e-5f) {
                Assert.assertTrue("Small value not close to zero",
                        Math.abs(retrieved) < 1e-4f);
            } else {
                float relativeError = Math.abs((retrieved - testValues[i]) / testValues[i]);
                Assert.assertTrue("Large relative error at index " + i +
                                ": expected=" + testValues[i] + ", got=" + retrieved,
                        relativeError < 0.01f);
            }
        }
    }

    @Test
    public void testInt8Range() {
        Shape shape = new Shape(GGMLType.Q8_0.getBlockSize());
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        float[] boundaryValues = {
                -128.0f, -127.0f, -64.0f, 0.0f, 63.0f, 126.0f, 127.0f
        };

        for (int i = 0; i < boundaryValues.length; i++) {
            tensorQ8.setFloat(i, boundaryValues[i]);
            float retrieved = tensorQ8.getFloat(i);
            printVerboseF("INT8 boundary test: Setting %.1f, got %.1f%n",
                    boundaryValues[i], retrieved);
            Assert.assertEquals("Value mismatch at INT8 boundary " + boundaryValues[i],
                    boundaryValues[i], retrieved, 1.0f);
        }
    }

    @Test
    public void testIndependentBlocks() {
        int blockSize = GGMLType.Q8_0.getBlockSize();
        Shape shape = new Shape(blockSize * 3);
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        printVerbose("\nTesting independent blocks with different scales:");

        printVerbose("\nBlock 1 - Small values:");
        for (int i = 0; i < blockSize; i++) {
            float value = 0.1f + (0.9f * i / blockSize);
            tensorQ8.setFloat(i, value);
            float retrieved = tensorQ8.getFloat(i);
            printVerboseF("Index %d: Set=%.6f Got=%.6f Diff=%.6f%n",
                    i, value, retrieved, Math.abs(value - retrieved));
        }

        printVerbose("\nBlock 2 - Medium values:");
        for (int i = 0; i < blockSize; i++) {
            float value = 10.0f + (10.0f * i / blockSize);
            tensorQ8.setFloat(blockSize + i, value);
            float retrieved = tensorQ8.getFloat(blockSize + i);
            printVerboseF("Index %d: Set=%.6f Got=%.6f Diff=%.6f%n",
                    i, value, retrieved, Math.abs(value - retrieved));
        }

        printVerbose("\nBlock 3 - Large values:");
        for (int i = 0; i < blockSize; i++) {
            float value = 100.0f + (100.0f * i / blockSize);
            tensorQ8.setFloat(2 * blockSize + i, value);
            float retrieved = tensorQ8.getFloat(2 * blockSize + i);
            printVerboseF("Index %d: Set=%.6f Got=%.6f Diff=%.6f%n",
                    i, value, retrieved, Math.abs(value - retrieved));
        }

        printVerbose("\nVerifying accuracy for each block:");

        for (int block = 0; block < 3; block++) {
            float maxDiff = 0.0f;
            float maxRelErr = 0.0f;
            float minVal = Float.MAX_VALUE;
            float maxVal = Float.MIN_VALUE;

            for (int i = 0; i < blockSize; i++) {
                int idx = block * blockSize + i;
                float original = (block == 0) ? (0.1f + (0.9f * i / blockSize)) :
                        (block == 1) ? (10.0f + (10.0f * i / blockSize)) :
                                (100.0f + (100.0f * i / blockSize));
                float retrieved = tensorQ8.getFloat(idx);
                float diff = Math.abs(original - retrieved);
                float relErr = diff / Math.abs(original);

                maxDiff = Math.max(maxDiff, diff);
                maxRelErr = Math.max(maxRelErr, relErr);
                minVal = Math.min(minVal, retrieved);
                maxVal = Math.max(maxVal, retrieved);
            }

            printVerboseF("Block %d stats:%n", block);
            printVerboseF("  Value range: %.6f to %.6f%n", minVal, maxVal);
            printVerboseF("  Max absolute difference: %.6f%n", maxDiff);
            printVerboseF("  Max relative error: %.6f%%%n", maxRelErr * 100);

            float expectedMaxErr = (block == 0) ? 0.5f : (block == 1) ? 0.2f : 0.1f;

            Assert.assertTrue(
                    String.format("Block %d error too large: %.2f%% > %.2f%%",
                            block, maxRelErr * 100, expectedMaxErr * 100),
                    maxRelErr < expectedMaxErr);
        }
    }


    @Test
    public void testRepeatedUpdates() {
        Shape shape = new Shape(GGMLType.Q8_0.getBlockSize());
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        float testValue = 1.0f;
        int testIndex = 0;

        printVerbose("\nTesting repeated updates stability:");
        for (int i = 0; i < 100; i++) {
            tensorQ8.setFloat(testIndex, testValue);
            float retrieved = tensorQ8.getFloat(testIndex);
            printVerboseF("Update %d: Expected=%.6f Got=%.6f%n",
                    i, testValue, retrieved);
            Assert.assertEquals("Value unstable after repeated updates",
                    testValue, retrieved, 0.1f);
        }
    }

    @Test
    public void testAlternatingPatterns() {
        Shape shape = new Shape(GGMLType.Q8_0.getBlockSize());
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        printVerbose("\nTesting alternating pattern preservation:");

        // Set alternating values
        printVerbose("Setting alternating values:");
        for (int i = 0; i < shape.getSize(); i++) {
            float value = (i % 2 == 0) ? 1.0f : -1.0f;
            tensorQ8.setFloat(i, value);
            float retrieved = tensorQ8.getFloat(i);
            printVerboseF("Index %d: Set=%.6f Got=%.6f%n",
                    i, value, retrieved);
        }

        // Verify alternating values
        printVerbose("\nVerifying alternating pattern:");
        for (int i = 0; i < shape.getSize(); i++) {
            float expected = (i % 2 == 0) ? 1.0f : -1.0f;
            float retrieved = tensorQ8.getFloat(i);
            printVerboseF("Index %d: Expected=%.6f Got=%.6f%n",
                    i, expected, retrieved);
            Assert.assertEquals("Alternating pattern not preserved",
                    expected, retrieved, 0.1f);
        }
    }

    @Test
    public void testSingleBlockPrecision() {
        Shape shape = new Shape(GGMLType.Q8_0.getBlockSize());
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        float baseValue = 10.0f;

        printVerbose("\nTesting single block precision:");
        for (int i = 0; i < shape.getSize(); i++) {
            float value = baseValue * (i + 1) / shape.getSize();
            tensorQ8.setFloat(i, value);
            float retrieved = tensorQ8.getFloat(i);
            float relativeError = Math.abs((retrieved - value) / value);

            printVerboseF("Index %d: Set=%.6f Got=%.6f RelError=%.6f%n",
                    i, value, retrieved, relativeError);

            Assert.assertTrue(
                    String.format("Relative error too large at index %d: expected=%.6f, got=%.6f, relative error=%.6f",
                            i, value, retrieved, relativeError),
                    relativeError < 0.1f);
        }
    }

    @Test
    public void testConstantBlock() {
        int blockSize = GGMLType.Q8_0.getBlockSize();
        Shape shape = new Shape(blockSize);
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        float testValue = 10.0f;
        printVerbose("\nTesting constant value block:");

        printVerbose("Setting constant values:");
        for (int i = 0; i < blockSize; i++) {
            tensorQ8.setFloat(i, testValue);
        }

        float maxDiff = 0.0f;
        printVerbose("\nVerifying constant values:");
        for (int i = 0; i < blockSize; i++) {
            float retrieved = tensorQ8.getFloat(i);
            float diff = Math.abs(retrieved - testValue);
            maxDiff = Math.max(maxDiff, diff);
            printVerboseF("Index %d: Expected=%.6f Got=%.6f Diff=%.6f%n",
                    i, testValue, retrieved, diff);
        }

        float relativeError = maxDiff / Math.abs(testValue);
        printVerboseF("Maximum relative error: %.6f%%%n", relativeError * 100);

        Assert.assertTrue(
                String.format("Relative error too large for constant block: %.2f%%",
                        relativeError * 100),
                relativeError < 0.1f);
    }

    @Test
    public void testNonAlignedBlockSize() {
        int blockSize = GGMLType.Q8_0.getBlockSize();
        Shape shape = new Shape(blockSize + 5);
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        printVerbose("\nTesting non-aligned block size:");
        for (int i = 0; i < shape.getSize(); i++) {
            float value = i * 1.5f;
            tensorQ8.setFloat(i, value);
            float retrieved = tensorQ8.getFloat(i);
            printVerboseF("Index %d: Set=%.6f Got=%.6f%n",
                    i, value, retrieved);
            Assert.assertEquals("Value mismatch in non-aligned blocks",
                    value, retrieved, 0.1f);
        }
    }

    @Test
    public void testZeroCrossing() {
        Shape shape = new Shape(GGMLType.Q8_0.getBlockSize());
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        float[][] testRanges = {
                {-0.001f, -0.0001f, 0.0f, 0.0001f, 0.001f},
                {-0.1f, -0.05f, 0.0f, 0.05f, 0.1f},
                {-1.0f, -0.5f, 0.0f, 0.5f, 1.0f}
        };

        printVerbose("\nTesting zero crossing behavior:");
        for (int range = 0; range < testRanges.length; range++) {
            printVerboseF("\nRange %d:%n", range);

            for (int i = 0; i < testRanges[range].length; i++) {
                float value = testRanges[range][i];
                tensorQ8.setFloat(i, value);
                float retrieved = tensorQ8.getFloat(i);

                printVerboseF("Value: %10.6f -> Retrieved: %10.6f%n",
                        value, retrieved);

                if (Math.abs(value) >= 0.01f) {
                    Assert.assertEquals(
                            String.format("Sign mismatch for value %.6f", value),
                            Math.signum(value), Math.signum(retrieved), 0.0f);
                } else {
                    Assert.assertTrue(
                            String.format("Small value %.6f not close enough to zero (got %.6f)",
                                    value, retrieved),
                            Math.abs(retrieved) < 0.01f);
                }
            }
        }
    }
}
