package uk.ac.manchester.tornado.unittests.tensors;

import org.junit.Assert;
import org.junit.Test;
import uk.ac.manchester.tornado.api.types.tensors.GGMLType;
import uk.ac.manchester.tornado.api.types.tensors.Shape;
import uk.ac.manchester.tornado.api.types.tensors.TensorQ4;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import static java.lang.Boolean.FALSE;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.tensors.TestTensorQ4
 * </code>
 */
public class TestTensorQ4 extends TornadoTestBase {
    private static final boolean VERBOSE = FALSE;

    private void printVerbose(String message) {
        if (VERBOSE) System.out.println(message);
    }

    private void printVerboseF(String format, Object... args) {
        if (VERBOSE) System.out.printf(format, args);
    }

    @Test
    public void testBasicQuantization() {
        // Unchanged - passing
        Shape shape = new Shape(1);
        TensorQ4 tensor = new TensorQ4(shape);

        float testValue = 1.0f;
        tensor.setFloat(0, testValue);
        float retrieved = tensor.getFloat(0);
        printVerboseF("Segment size for storing single value %d%n", tensor.getSegment().byteSize());
        Assert.assertEquals(testValue, retrieved, 0.2f);
    }

    @Test
    public void testFourBitRange() {
        Shape shape = new Shape(GGMLType.Q4_0.getBlockSize());
        TensorQ4 tensorQ4 = new TensorQ4(shape);

        // Test a single block to maintain consistent scale
        float[] boundaryValues = {
                -8.0f, -6.0f, -4.0f, -2.0f, 0.0f, 2.0f, 4.0f, 6.0f
        };

        printVerbose("\nTesting 4-bit range quantization:");
        for (int i = 0; i < boundaryValues.length; i++) {
            tensorQ4.setFloat(i, boundaryValues[i]);
            float retrieved = tensorQ4.getFloat(i);
            printVerboseF("4-bit value test: Setting %.1f, got %.1f%n",
                    boundaryValues[i], retrieved);
            // Increased tolerance to account for quantization steps
            Assert.assertEquals("Value mismatch at 4-bit value " + boundaryValues[i],
                    boundaryValues[i], retrieved, 0.6f);
        }
    }

    @Test
    public void testPackedValues() {
        Shape shape = new Shape(GGMLType.Q4_0.getBlockSize());
        TensorQ4 tensorQ4 = new TensorQ4(shape);

        // Test both nibbles of each byte with values well within quantization range
        float[] values = {-4.0f, -2.0f, 0.0f, 2.0f, 4.0f, -4.0f, -2.0f, 2.0f};

        printVerbose("\nTesting packed 4-bit storage:");
        for (int i = 0; i < values.length; i++) {
            tensorQ4.setFloat(i, values[i]);
            float retrieved = tensorQ4.getFloat(i);
            printVerboseF("Packed index %d: Set=%.1f Got=%.1f%n",
                    i, values[i], retrieved);
            Assert.assertEquals("Value mismatch for packed storage",
                    values[i], retrieved, 0.5f);
        }
    }

    @Test
    public void testBlockScaleInterference() {
        int blockSize = GGMLType.Q4_0.getBlockSize();
        Shape shape = new Shape(blockSize * 2);
        TensorQ4 tensorQ4 = new TensorQ4(shape);

        printVerbose("\nTesting block scale interference:");

        // Use values well within the 4-bit quantization range
        for (int i = 0; i < blockSize; i++) {
            float value = -4.0f + (8.0f * i / blockSize); // Range from -4 to 4
            tensorQ4.setFloat(i, value);
            printVerboseF("Block 1 index %d: Set=%.6f%n", i, value);
        }

        for (int i = 0; i < blockSize; i++) {
            float value = -2.0f + (4.0f * i / blockSize); // Range from -2 to 2
            tensorQ4.setFloat(blockSize + i, value);
            printVerboseF("Block 2 index %d: Set=%.6f%n", i, value);
        }

        // Verify first block maintained reasonable accuracy
        for (int i = 0; i < blockSize; i++) {
            float expected = -4.0f + (8.0f * i / blockSize);
            float retrieved = tensorQ4.getFloat(i);
            float absError = Math.abs(retrieved - expected);

            printVerboseF("Block 1 verification index %d: Expected=%.6f Got=%.6f AbsError=%.6f%n",
                    i, expected, retrieved, absError);

            Assert.assertTrue("Block 1 accuracy lost after block 2 update",
                    absError < 0.6f);
        }
    }

    @Test
    public void testFullRangeQuantization() {
        Shape shape = new Shape(GGMLType.Q4_0.getBlockSize());
        TensorQ4 tensorQ4 = new TensorQ4(shape);

        // Test evenly spaced values within quantization range
        float[] testValues = new float[16];
        for (int i = 0; i < 16; i++) {
            testValues[i] = -7.0f + (i * 14.0f / 15.0f); // Range from -7 to 7
        }

        printVerbose("\nTesting quantization range:");
        for (int i = 0; i < testValues.length; i++) {
            tensorQ4.setFloat(i, testValues[i]);
            float retrieved = tensorQ4.getFloat(i);

            printVerboseF("Step %2d: Set=%.3f Got=%.3f%n",
                    i, testValues[i], retrieved);

            float absError = Math.abs(retrieved - testValues[i]);
            Assert.assertTrue(
                    String.format("Excessive quantization error: expected=%.3f, got=%.3f, error=%.3f",
                            testValues[i], retrieved, absError),
                    absError < 0.6f);
        }
    }
    @Test
    public void testTensorQ4SetAndGetFloatVerify() {
        int blockSize = GGMLType.Q4_0.getBlockSize();
        Shape shape = new Shape(blockSize);
        TensorQ4 tensorQ4 = new TensorQ4(shape);

        // Use values within Q4 range (-8 to 7)
        float[] pattern = {0.5f, -1.0f, 4.0f, -6.0f, 0.0f};
        float[] valuesToSet = new float[blockSize];
        for (int i = 0; i < blockSize; i++) {
            valuesToSet[i] = pattern[i % pattern.length];
        }

        printVerboseF("Total elements: %d%n", shape.getSize());
        printVerboseF("Block size: %d%n", blockSize);
        printVerboseF("Total allocated bytes: %d%n", tensorQ4.getSegment().byteSize());

        for (int i = 0; i < valuesToSet.length; i++) {
            tensorQ4.setFloat(i, valuesToSet[i]);
            float retrieved = tensorQ4.getFloat(i);
            printVerboseF("Index %d: Set=%.2f Retrieved=%.2f%n",
                    i, valuesToSet[i], retrieved);
            Assert.assertEquals("Value mismatch at index " + i,
                    valuesToSet[i], retrieved, 0.5f);
        }
    }

    @Test
    public void testSingleBlockPrecision() {
        Shape shape = new Shape(GGMLType.Q4_0.getBlockSize());
        TensorQ4 tensorQ4 = new TensorQ4(shape);

        float baseValue = 4.0f;  // Smaller base value for Q4 range

        printVerbose("\nTesting single block precision:");
        for (int i = 0; i < shape.getSize(); i++) {
            float value = baseValue * (i + 1) / shape.getSize();
            tensorQ4.setFloat(i, value);
            float retrieved = tensorQ4.getFloat(i);
            float relativeError = Math.abs((retrieved - value) / value);

            printVerboseF("Index %d: Set=%.6f Got=%.6f RelError=%.6f%n",
                    i, value, retrieved, relativeError);

            Assert.assertTrue(
                    String.format("Relative error too large at index %d: expected=%.6f, got=%.6f, relative error=%.6f",
                            i, value, retrieved, relativeError),
                    relativeError < 0.3f);  // Higher tolerance for Q4
        }
    }

    @Test
    public void testMaximumPrecisionValues() {
        Shape shape = new Shape(GGMLType.Q4_0.getBlockSize());
        TensorQ4 tensorQ4 = new TensorQ4(shape);

        printVerbose("\nTesting maximum precision values:");

        float[] preciseValues = {
                1.234f,
                -1.234f,
                3.456f,
                -3.456f,
                6.789f,
                -6.789f
        };

        for (int i = 0; i < preciseValues.length; i++) {
            tensorQ4.setFloat(i, preciseValues[i]);
            float retrieved = tensorQ4.getFloat(i);
            float relativeError = Math.abs((retrieved - preciseValues[i]) / preciseValues[i]);

            printVerboseF("Precise value test %d: Set=%.6f Got=%.6f RelError=%.6f%n",
                    i, preciseValues[i], retrieved, relativeError);

            Assert.assertTrue(
                    String.format("Precision lost: expected=%.6f, got=%.6f, error=%.6f",
                            preciseValues[i], retrieved, relativeError),
                    relativeError < 0.2f);
        }
    }

    @Test
    public void testSequentialBlockUpdates() {
        int blockSize = GGMLType.Q4_0.getBlockSize();
        Shape shape = new Shape(blockSize * 3);
        TensorQ4 tensorQ4 = new TensorQ4(shape);

        printVerbose("\nTesting sequential block updates:");

        // Sequential updates with Q4-appropriate values
        for (int block = 0; block < 3; block++) {
            float blockValue = (block + 1) * 2.0f;  // Values: 2, 4, 6
            printVerboseF("\nSetting block %d to %.2f:%n", block, blockValue);

            for (int i = 0; i < blockSize; i++) {
                int index = block * blockSize + i;
                tensorQ4.setFloat(index, blockValue);
                float retrieved = tensorQ4.getFloat(index);
                printVerboseF("Index %d: Set=%.6f Got=%.6f%n",
                        index, blockValue, retrieved);
                Assert.assertEquals("Sequential block update failed",
                        blockValue, retrieved, 0.5f);
            }
        }
    }

    @Test
    public void testNibbleBoundaryUpdates() {
        // Test updating values at nibble boundaries
        int blockSize = GGMLType.Q4_0.getBlockSize();
        Shape shape = new Shape(blockSize);
        TensorQ4 tensorQ4 = new TensorQ4(shape);

        // Set values around nibble boundaries
        float[] values = {1.0f, -1.0f, 2.0f, -2.0f};

        // Test boundaries between nibbles
        for (int i = 0; i < values.length; i++) {
            int index = (i * blockSize/4);  // Space out across block
            tensorQ4.setFloat(index, values[i]);
            float retrieved = tensorQ4.getFloat(index);
            printVerboseF("Nibble boundary %d: Set=%.6f Got=%.6f%n",
                    index, values[i], retrieved);
            Assert.assertEquals("Value mismatch at nibble boundary",
                    values[i], retrieved, 0.5f);
        }
    }

    @Test
    public void testAlternatingNibblePatterns() {
        Shape shape = new Shape(GGMLType.Q4_0.getBlockSize());
        TensorQ4 tensorQ4 = new TensorQ4(shape);

        printVerbose("\nTesting alternating nibble pattern:");

        // Set alternating values across nibble boundaries
        for (int i = 0; i < shape.getSize(); i++) {
            float value = (i % 2 == 0) ? 1.0f : -1.0f;
            tensorQ4.setFloat(i, value);
            float retrieved = tensorQ4.getFloat(i);
            printVerboseF("Index %d: Set=%.6f Got=%.6f%n",
                    i, value, retrieved);
            Assert.assertEquals("Alternating pattern not preserved",
                    value, retrieved, 0.5f);
        }
    }

    @Test
    public void testNibblePackingConsistency() {
        Shape shape = new Shape(GGMLType.Q4_0.getBlockSize());
        TensorQ4 tensorQ4 = new TensorQ4(shape);

        // Create an array of expected quantized values
        float[] expectedValues = {
                -4.0f, -3.5f, -3.0f, -2.5f,
                -2.0f, -1.5f, -1.0f, -0.5f,
                0.0f, 0.5f, 1.0f, 1.5f,
                2.0f, 2.5f, 3.0f, 3.5f
        };

        printVerbose("\nTesting nibble packing consistency:");

        // Set values
        for (int i = 0; i < expectedValues.length; i++) {
            tensorQ4.setFloat(i, expectedValues[i]);
        }

        // Verify quantization
        for (int i = 0; i < expectedValues.length; i++) {
            float retrieved = tensorQ4.getFloat(i);
            printVerboseF("Pattern %2d: Set=%.4f Got=%.4f Diff=%.4f%n",
                    i, expectedValues[i], retrieved,
                    Math.abs(expectedValues[i] - retrieved));

            // Check if the retrieved value is within one quantization step
            float quantStep = 0.5f;  // Quantization step size for Q4
            Assert.assertTrue(
                    String.format("Quantization error too large at index %d: expected=%.4f, got=%.4f",
                            i, expectedValues[i], retrieved),
                    Math.abs(retrieved - expectedValues[i]) <= quantStep
            );
        }

        // Additional verification for nibble boundaries
        printVerbose("\nVerifying nibble boundaries:");
        for (int i = 0; i < expectedValues.length; i += 2) {
            float val1 = tensorQ4.getFloat(i);
            float val2 = tensorQ4.getFloat(i + 1);
            printVerboseF("Nibble pair %d: %.4f %.4f%n", i/2, val1, val2);

            // Verify the difference between adjacent values is consistent
            if (i < expectedValues.length - 2) {
                float diff1 = val2 - val1;
                float diff2 = tensorQ4.getFloat(i + 2) - val2;
                Assert.assertTrue(
                        String.format("Inconsistent quantization steps: %.4f vs %.4f", diff1, diff2),
                        Math.abs(diff1 - diff2) <= 0.1f
                );
            }
        }
    }

    @Test
    public void testGradualValueTransitions() {
        Shape shape = new Shape(GGMLType.Q4_0.getBlockSize());
        TensorQ4 tensorQ4 = new TensorQ4(shape);

        // Test gradual transitions to check quantization steps
        float step = 14.0f / shape.getSize();  // Range from -7 to 7
        for (int i = 0; i < shape.getSize(); i++) {
            float value = -7.0f + (step * i);
            tensorQ4.setFloat(i, value);
            float retrieved = tensorQ4.getFloat(i);
            printVerboseF("Step %d: Set=%.3f Got=%.3f%n",
                    i, value, retrieved);
            Assert.assertEquals("Gradual transition not preserved",
                    value, retrieved, 0.5f);
        }
    }

    @Test
    public void testQ4Symmetry() {
        // Test symmetry of positive and negative values
        Shape shape = new Shape(GGMLType.Q4_0.getBlockSize());
        TensorQ4 tensorQ4 = new TensorQ4(shape);

        for (int i = 0; i <= 7; i++) {
            float positive = i * 1.0f;
            float negative = -positive;

            tensorQ4.setFloat(i * 2, positive);
            tensorQ4.setFloat(i * 2 + 1, negative);

            float retrievedPos = tensorQ4.getFloat(i * 2);
            float retrievedNeg = tensorQ4.getFloat(i * 2 + 1);

            printVerboseF("Symmetry test %d: +%.1f->%.1f, %.1f->%.1f%n",
                    i, positive, retrievedPos, negative, retrievedNeg);

            Assert.assertEquals("Positive value not preserved", positive, retrievedPos, 0.5f);
            Assert.assertEquals("Negative value not preserved", negative, retrievedNeg, 0.5f);
            Assert.assertEquals("Asymmetric quantization",
                    Math.abs(retrievedPos), Math.abs(retrievedNeg), 0.1f);
        }
    }
}
