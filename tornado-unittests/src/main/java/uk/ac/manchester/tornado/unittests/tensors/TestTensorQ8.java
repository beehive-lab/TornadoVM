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

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tensors.TestTensorQ8
 * </code>
 */
public class TestTensorQ8 extends TornadoTestBase {

    @Test
    public void testBasicQuantization() {
        // Test with a simple 1D tensor
        Shape shape = new Shape(1);
        TensorQ8 tensor = new TensorQ8(shape);

        // Test setting and getting a single value
        float testValue = 1.5f;
        tensor.setFloat(0, testValue);
        float retrieved = tensor.getFloat(0);
        System.out.println("Segment size for storing single value " + tensor.getSegment().byteSize());
        Assert.assertEquals(testValue, retrieved, 0.1f);
    }

    @Test
    public void testTensorQ8SetAndGetFloat() {
        // Define the shape and create a tensor
        Shape shape = new Shape(5); // 1D tensor with 128 elements
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        // Set some values in the tensor using setFloat and then retrieve them with getFloat
        float[] valuesToSet = {0.5f, -1.0f, 25.0f, -30.5f, 0.0f};
        for (int i = 0; i < valuesToSet.length; i++) {
            tensorQ8.setFloat(i, valuesToSet[i]);
        }

        // Check that each retrieved value matches the set value within tolerance
        for (int i = 0; i < valuesToSet.length; i++) {
            Assert.assertEquals(valuesToSet[i], tensorQ8.getFloat(i), 0.1f);
        }
    }

    @Test
    public void testTensorQ8SetAndGetFloatVerify() {
        // Use a size that's aligned with Q8_0 block size (typically 32 elements)
        int blockSize = GGMLType.Q8_0.getBlockSize();  // Should be 32
        Shape shape = new Shape(blockSize);  // Use full block size
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        // Create test values array matching the block size
        float[] valuesToSet = new float[blockSize];
        // Fill with repeating pattern
        float[] pattern = {0.5f, -1.0f, 25.0f, -30.5f, 0.0f};
        for (int i = 0; i < blockSize; i++) {
            valuesToSet[i] = pattern[i % pattern.length];
        }

        // Print expected layout information
        System.out.println("Total elements: " + shape.getSize());
        System.out.println("Block size: " + blockSize);
        System.out.println("Total allocated bytes: " + tensorQ8.getSegment().byteSize());

        // Set values
        for (int i = 0; i < valuesToSet.length; i++) {
            tensorQ8.setFloat(i, valuesToSet[i]);
            // Immediately verify each value after setting
            float retrieved = tensorQ8.getFloat(i);
            System.out.printf("Index %d: Set=%.2f Retrieved=%.2f%n",
                    i, valuesToSet[i], retrieved);
            Assert.assertEquals("Value mismatch at index " + i,
                    valuesToSet[i], retrieved, 0.1f);
        }

        // Verify all values again
        for (int i = 0; i < valuesToSet.length; i++) {
            float retrieved = tensorQ8.getFloat(i);
            Assert.assertEquals("Final verification failed at index " + i,
                    valuesToSet[i], retrieved, 0.1f);
        }
    }

    @Test
    public void testMixedScaleValues() {
        // Test handling of mixed scales within a block
        Shape shape = new Shape(GGMLType.Q8_0.getBlockSize());
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        // Set values with very different scales
        tensorQ8.setFloat(0, 100.0f);
        tensorQ8.setFloat(1, 0.001f);
        tensorQ8.setFloat(2, -100.0f);
        tensorQ8.setFloat(3, -0.001f);

        // Verify large values maintain reasonable accuracy
        Assert.assertEquals(100.0f, tensorQ8.getFloat(0), 1.0f);
        Assert.assertEquals(-100.0f, tensorQ8.getFloat(2), 1.0f);

        // Small values might have less precision but should maintain sign
        float small1 = tensorQ8.getFloat(1);
        float small2 = tensorQ8.getFloat(3);
        Assert.assertTrue("Small positive value lost sign", small1 >= 0);
        Assert.assertTrue("Small negative value lost sign", small2 <= 0);
    }

    @Test
    public void testQuantizationRange() {
        // Test extreme values and quantization handling
        Shape shape = new Shape(GGMLType.Q8_0.getBlockSize());
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        // Test values in separate blocks to maintain scale independence
        float[] testValues = {
                0.0f,              // Zero
                1e-6f,            // Very small positive
                -1e-6f,           // Very small negative
                100.0f,           // Large positive
                -100.0f,          // Large negative
        };

        for (int i = 0; i < testValues.length; i++) {
            tensorQ8.setFloat(i, testValues[i]);
            float retrieved = tensorQ8.getFloat(i);

            // For very small values, check if they're close to zero
            if (Math.abs(testValues[i]) < 1e-5f) {
                Assert.assertTrue("Small value not close to zero",
                        Math.abs(retrieved) < 1e-4f);
            } else {
                // For larger values, check relative error
                float relativeError = Math.abs((retrieved - testValues[i]) / testValues[i]);
                Assert.assertTrue("Large relative error at index " + i +
                                ": expected=" + testValues[i] + ", got=" + retrieved,
                        relativeError < 0.01f);
            }
        }
    }

    @Test
    public void testInt8Range() {
        // Test the full INT8 range in a dedicated test
        Shape shape = new Shape(GGMLType.Q8_0.getBlockSize());
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        // Set a few values at INT8 boundaries
        float[] boundaryValues = {
                -128.0f,   // Min INT8
                -127.0f,
                -64.0f,
                0.0f,
                63.0f,
                126.0f,
                127.0f     // Max INT8
        };

        // Set values one at a time to ensure same scale
        for (int i = 0; i < boundaryValues.length; i++) {
            tensorQ8.setFloat(i, boundaryValues[i]);
            float retrieved = tensorQ8.getFloat(i);
            System.out.printf("INT8 boundary test: Setting %.1f, got %.1f%n",
                    boundaryValues[i], retrieved);
            Assert.assertEquals("Value mismatch at INT8 boundary " + boundaryValues[i],
                    boundaryValues[i], retrieved, 1.0f);  // Allow 1.0 tolerance for boundary values
        }
    }

    @Test
    public void testIndependentBlocks() {
        // Test that blocks can handle different scales independently
        int blockSize = GGMLType.Q8_0.getBlockSize();
        Shape shape = new Shape(blockSize * 3);  // 3 blocks
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        System.out.println("\nTesting independent blocks with different scales:");

        // Block 1: Small values (0.1 to 1.0)
        System.out.println("\nBlock 1 - Small values:");
        for (int i = 0; i < blockSize; i++) {
            float value = 0.1f + (0.9f * i / blockSize);
            tensorQ8.setFloat(i, value);
            float retrieved = tensorQ8.getFloat(i);
            System.out.printf("Index %d: Set=%.6f Got=%.6f Diff=%.6f%n",
                    i, value, retrieved, Math.abs(value - retrieved));
        }

        // Block 2: Medium values (10 to 20)
        System.out.println("\nBlock 2 - Medium values:");
        for (int i = 0; i < blockSize; i++) {
            float value = 10.0f + (10.0f * i / blockSize);
            tensorQ8.setFloat(blockSize + i, value);
            float retrieved = tensorQ8.getFloat(blockSize + i);
            System.out.printf("Index %d: Set=%.6f Got=%.6f Diff=%.6f%n",
                    i, value, retrieved, Math.abs(value - retrieved));
        }

        // Block 3: Large values (100 to 200)
        System.out.println("\nBlock 3 - Large values:");
        for (int i = 0; i < blockSize; i++) {
            float value = 100.0f + (100.0f * i / blockSize);
            tensorQ8.setFloat(2 * blockSize + i, value);
            float retrieved = tensorQ8.getFloat(2 * blockSize + i);
            System.out.printf("Index %d: Set=%.6f Got=%.6f Diff=%.6f%n",
                    i, value, retrieved, Math.abs(value - retrieved));
        }

        // Verify blocks maintain reasonable accuracy
        System.out.println("\nVerifying accuracy for each block:");

        // Helper function to check max absolute difference in a block
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

            System.out.printf("Block %d stats:%n", block);
            System.out.printf("  Value range: %.6f to %.6f%n", minVal, maxVal);
            System.out.printf("  Max absolute difference: %.6f%n", maxDiff);
            System.out.printf("  Max relative error: %.6f%%%n", maxRelErr * 100);

            // Verify block maintains reasonable range and accuracy
            float expectedMaxErr;
            if (block == 0) {  // Small values
                expectedMaxErr = 0.5f;  // Larger relative error acceptable for small values
            } else if (block == 1) {  // Medium values
                expectedMaxErr = 0.2f;  // 20% error acceptable for medium values
            } else {  // Large values
                expectedMaxErr = 0.1f;  // 10% error acceptable for large values
            }

            Assert.assertTrue(
                    String.format("Block %d error too large: %.2f%% > %.2f%%",
                            block, maxRelErr * 100, expectedMaxErr * 100),
                    maxRelErr < expectedMaxErr);
        }
    }

    @Test
    public void testConstantBlock() {
        // Test how well we can represent a constant value
        int blockSize = GGMLType.Q8_0.getBlockSize();
        Shape shape = new Shape(blockSize);
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        float testValue = 10.0f;
        System.out.println("\nTesting constant value block:");

        // Set all values in block to same value
        for (int i = 0; i < blockSize; i++) {
            tensorQ8.setFloat(i, testValue);
        }

        // Verify values
        float maxDiff = 0.0f;
        for (int i = 0; i < blockSize; i++) {
            float retrieved = tensorQ8.getFloat(i);
            float diff = Math.abs(retrieved - testValue);
            maxDiff = Math.max(maxDiff, diff);
            System.out.printf("Index %d: Expected=%.6f Got=%.6f Diff=%.6f%n",
                    i, testValue, retrieved, diff);
        }

        float relativeError = maxDiff / Math.abs(testValue);
        System.out.printf("Maximum relative error: %.6f%%%n", relativeError * 100);

        Assert.assertTrue(
                String.format("Relative error too large for constant block: %.2f%%",
                        relativeError * 100),
                relativeError < 0.1f);  // Expect very good accuracy for constant values
    }

    @Test
    public void testSingleBlockPrecision() {
        // Test precision within a single block using relative error metrics
        Shape shape = new Shape(GGMLType.Q8_0.getBlockSize());
        TensorQ8 tensorQ8 = new TensorQ8(shape);

        float baseValue = 10.0f;  // Use a reasonable base value

        System.out.println("\nTesting single block precision:");
        for (int i = 0; i < shape.getSize(); i++) {
            float value = baseValue * (i + 1) / shape.getSize();  // Spread values evenly
            tensorQ8.setFloat(i, value);
            float retrieved = tensorQ8.getFloat(i);
            float relativeError = Math.abs((retrieved - value) / value);

            System.out.printf("Index %d: Set=%.6f Got=%.6f RelError=%.6f%n",
                    i, value, retrieved, relativeError);

            Assert.assertTrue(String.format(
                            "Relative error too large at index %d: expected=%.6f, got=%.6f, relative error=%.6f",
                            i, value, retrieved, relativeError),
                    relativeError < 0.1f);  // Allow 10% relative error
        }
    }
}
