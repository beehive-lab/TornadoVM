/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package uk.ac.manchester.tornado.unittests.vectortypes;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.Float2;
import uk.ac.manchester.tornado.api.collections.types.Float3;
import uk.ac.manchester.tornado.api.collections.types.Float4;
import uk.ac.manchester.tornado.api.collections.types.Float6;
import uk.ac.manchester.tornado.api.collections.types.Float8;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat2;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat3;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat4;
import uk.ac.manchester.tornado.api.collections.types.VectorFloat8;
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestFloats extends TornadoTestBase {

    private static void dotMethodFloat2(Float2 a, Float2 b, VectorFloat result) {
        float dot = Float2.dot(a, b);
        result.set(0, dot);
    }

    @Test
    public void testSimpleDotProductFloat2() {
        Float2 a = new Float2(1f, 2f);
        Float2 b = new Float2(3f, 2f);
        VectorFloat output = new VectorFloat(1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::dotMethodFloat2, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        assertEquals(7, output.get(0), 0.001);
    }

    private static void dotMethodFloat3(Float3 a, Float3 b, VectorFloat result) {
        float dot = Float3.dot(a, b);
        result.set(0, dot);
    }

    @Test
    public void testSimpleDotProductFloat3() {
        Float3 a = new Float3(1f, 2f, 3f);
        Float3 b = new Float3(3f, 2f, 1f);
        VectorFloat output = new VectorFloat(1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::dotMethodFloat3, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        assertEquals(10, output.get(0), 0.001f);
    }

    private static void dotMethodFloat4(Float4 a, Float4 b, VectorFloat result) {
        float dot = Float4.dot(a, b);
        result.set(0, dot);
    }

    @Test
    public void testSimpleDotProductFloat4() {
        Float4 a = new Float4(1f, 2f, 3f, 4f);
        Float4 b = new Float4(4f, 3f, 2f, 1f);
        VectorFloat output = new VectorFloat(1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::dotMethodFloat4, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        assertEquals(20, output.get(0), 0.001f);
    }

    private static void dotMethodFloat6(Float6 a, Float6 b, VectorFloat result) {
        float dot = Float6.dot(a, b);
        result.set(0, dot);
    }

    @Test
    public void testSimpleDotProductFloat6() {
        Float6 a = new Float6(1f, 2f, 3f, 4f, 5f, 6f);
        Float6 b = new Float6(6f, 5f, 4f, 3f, 2f, 1f);
        VectorFloat output = new VectorFloat(1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::dotMethodFloat6, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        assertEquals(56, output.get(0), 0.001f);
    }

    private static void dotMethodFloat8(Float8 a, Float8 b, VectorFloat result) {
        float dot = Float8.dot(a, b);
        result.set(0, dot);
    }

    @Test
    public void testSimpleDotProductFloat8() {
        Float8 a = new Float8(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f);
        Float8 b = new Float8(8f, 7f, 6f, 5f, 4f, 3f, 2f, 1f);
        VectorFloat output = new VectorFloat(1);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::dotMethodFloat8, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        assertEquals(120, output.get(0), 0.001f);
    }

    // This is currently not supported
    @Ignore
    public void simpleDotProduct() {
        Float3 a = new Float3(1f, 2f, 3f);
        Float3 b = new Float3(3f, 2f, 1f);

        // Another way to test simple dot product
        TaskSchedule s1 = new TaskSchedule("s1").task("t1", Float3::dot, a, b);
        s1.warmup();
        s1.schedule();
        assertEquals(10, s1.getReturnValue("t1"), 0.001);
    }

    private static void testFloat3Add(Float3 a, Float3 b, VectorFloat3 results) {
        results.set(0, Float3.add(a, b));
    }

    @Test
    public void testSimpleVectorAddition() {
        int size = 1;
        Float3 a = new Float3(1f, 2f, 3f);
        Float3 b = new Float3(3f, 2f, 1f);
        VectorFloat3 output = new VectorFloat3(size);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::testFloat3Add, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            assertEquals(4, output.get(i).getX(), 0.001);
            assertEquals(4, output.get(i).getY(), 0.001);
            assertEquals(4, output.get(i).getZ(), 0.001);
        }
    }

    /**
     * Test using the {@link Float} Java Wrapper class
     *
     * @param a
     * @param b
     * @param result
     */
    private static void addFloat(Float[] a, Float[] b, Float[] result) {
        for (@Parallel int i = 0; i < a.length; i++) {
            result[i] = a[i] + b[i];
        }
    }

    @TornadoNotSupported
    public void testFloat1() {
        int size = 8;

        Float[] a = new Float[size];
        Float[] b = new Float[size];
        Float[] output = new Float[size];

        for (int i = 0; i < size; i++) {
            a[i] = (float) i;
            b[i] = (float) i;
        }

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::addFloat, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            assertEquals(i + i, output[i], 0.001);
        }
    }

    /**
     * Test using the {@link Float2} Tornado wrapper class
     *
     * @param a
     * @param b
     * @param result
     */
    private static void addFloat2(Float2[] a, Float2[] b, Float2[] result) {
        for (@Parallel int i = 0; i < a.length; i++) {
            result[i] = Float2.add(a[i], b[i]);
        }
    }

    @TornadoNotSupported
    public void testFloat2() {
        int size = 8;

        Float2[] a = new Float2[size];
        Float2[] b = new Float2[size];
        Float2[] output = new Float2[size];

        for (int i = 0; i < size; i++) {
            a[i] = new Float2(i, i);
            b[i] = new Float2(i, i);
        }

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::addFloat2, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            Float2 sequential = new Float2(i + i, i + i);
            assertEquals(sequential.getX(), output[i].getX(), 0.001);
            assertEquals(sequential.getY(), output[i].getY(), 0.001);
        }
    }

    /**
     * Test using Tornado {@link VectorFloat3} data type
     *
     * @param a
     * @param b
     * @param results
     */
    public static void addVectorFloat2(VectorFloat2 a, VectorFloat2 b, VectorFloat2 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float2.add(a.get(i), b.get(i)));
        }
    }

    @Test
    public void testVectorFloat2() {
        int size = 16;

        VectorFloat2 a = new VectorFloat2(size);
        VectorFloat2 b = new VectorFloat2(size);
        VectorFloat2 output = new VectorFloat2(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Float2(i, i));
            b.set(i, new Float2(size - i, size - i));
        }

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::addVectorFloat2, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            Float2 sequential = new Float2(i + (size - i), i + (size - i));
            assertEquals(sequential.getX(), output.get(i).getX(), 0.001);
            assertEquals(sequential.getY(), output.get(i).getY(), 0.001);
        }
    }

    /**
     * Test using Tornado {@link VectorFloat3} data type
     *
     * @param a
     * @param b
     * @param results
     */
    public static void addVectorFloat3(VectorFloat3 a, VectorFloat3 b, VectorFloat3 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float3.add(a.get(i), b.get(i)));
        }
    }

    @Test
    public void testVectorFloat3() {
        int size = 8;

        VectorFloat3 a = new VectorFloat3(size);
        VectorFloat3 b = new VectorFloat3(size);
        VectorFloat3 output = new VectorFloat3(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Float3(i, i, i));
            b.set(i, new Float3(size - i, size - i, size - i));
        }

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::addVectorFloat3, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            Float3 sequential = new Float3(i + (size - i), i + (size - i), i + (size - i));
            assertEquals(sequential.getX(), output.get(i).getX(), 0.001);
            assertEquals(sequential.getY(), output.get(i).getY(), 0.001);
            assertEquals(sequential.getZ(), output.get(i).getZ(), 0.001);
        }
    }

    @Test
    public void testVectorFloat3toString() {
        int size = 2;

        VectorFloat3 a = new VectorFloat3(size);
        VectorFloat3 b = new VectorFloat3(size);
        VectorFloat3 output = new VectorFloat3(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Float3(i, i, i));
            b.set(i, new Float3((float) size - i, (float) size - i, (float) size - i));
        }

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::addVectorFloat3, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        System.out.println(" Test Output " + output.toString());
    }

    /**
     * Test using Tornado {@link VectorFloat4} data type
     *
     * @param a
     * @param b
     * @param results
     */
    public static void addVectorFloat4(VectorFloat4 a, VectorFloat4 b, VectorFloat4 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float4.add(a.get(i), b.get(i)));
        }
    }

    @Test
    public void testVectorFloat4() {

        int size = 8;

        VectorFloat4 a = new VectorFloat4(size);
        VectorFloat4 b = new VectorFloat4(size);
        VectorFloat4 output = new VectorFloat4(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Float4(i, i, i, i));
            b.set(i, new Float4(size - i, size - i, size - i, size));
        }

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::addVectorFloat4, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            Float4 sequential = new Float4(i + (size - i), i + (size - i), i + (size - i), i + size);
            assertEquals(sequential.getX(), output.get(i).getX(), 0.001);
            assertEquals(sequential.getY(), output.get(i).getY(), 0.001);
            assertEquals(sequential.getZ(), output.get(i).getZ(), 0.001);
            assertEquals(sequential.getW(), output.get(i).getW(), 0.001);
        }
    }

    public static void addVectorFloat8(VectorFloat8 a, VectorFloat8 b, VectorFloat8 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Float8.add(a.get(i), b.get(i)));
        }
    }

    @Test
    public void testVectorFloat8() {
        int size = 8;

        VectorFloat8 a = new VectorFloat8(size);
        VectorFloat8 b = new VectorFloat8(size);
        VectorFloat8 output = new VectorFloat8(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Float8(i, i, i, i, i, i, i, i));
            b.set(i, new Float8(size - i, size - i, size - i, size, size - i, size - i, size - i, size));
        }

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::addVectorFloat8, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            Float8 sequential = new Float8(i + (size - i), i + (size - i), i + (size - i), i + size, i + (size - i), i + (size - i), i + (size - i), i + size);
            assertEquals(sequential.getS0(), output.get(i).getS0(), 0.001);
            assertEquals(sequential.getS1(), output.get(i).getS1(), 0.001);
            assertEquals(sequential.getS2(), output.get(i).getS2(), 0.001);
            assertEquals(sequential.getS3(), output.get(i).getS3(), 0.001);
            assertEquals(sequential.getS4(), output.get(i).getS4(), 0.001);
            assertEquals(sequential.getS5(), output.get(i).getS5(), 0.001);
            assertEquals(sequential.getS6(), output.get(i).getS6(), 0.001);
            assertEquals(sequential.getS7(), output.get(i).getS7(), 0.001);
        }
    }

    public static void addVectorFloat8Storage(VectorFloat8 a, VectorFloat8 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            Float8 float8 = a.get(i);
            results.set(i, new Float8(float8.getArray()));
        }
    }

    @Test
    public void testVectorFloat8_Storage() {
        int size = 8;

        VectorFloat8 a = new VectorFloat8(size);
        VectorFloat8 output = new VectorFloat8(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Float8(i, i, i, i, i, i, i, i));
        }

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::addVectorFloat8Storage, a, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            Float8 sequential = new Float8(i, i, i, i, i, i, i, i);
            assertEquals(sequential.getS0(), output.get(i).getS0(), 0.001);
            assertEquals(sequential.getS1(), output.get(i).getS1(), 0.001);
            assertEquals(sequential.getS2(), output.get(i).getS2(), 0.001);
            assertEquals(sequential.getS3(), output.get(i).getS3(), 0.001);
            assertEquals(sequential.getS4(), output.get(i).getS4(), 0.001);
            assertEquals(sequential.getS5(), output.get(i).getS5(), 0.001);
            assertEquals(sequential.getS6(), output.get(i).getS6(), 0.001);
            assertEquals(sequential.getS7(), output.get(i).getS7(), 0.001);
        }
    }

    public static void dotProductFunctionMap(float[] a, float[] b, float[] results) {
        for (@Parallel int i = 0; i < a.length; i++) {
            results[i] = a[i] * b[i];
        }
    }

    public static void dotProductFunctionReduce(float[] input, float[] results) {
        float sum = 0.0f;
        for (int i = 0; i < input.length; i++) {
            sum += input[i];
        }
        results[0] = sum;
    }

    @Test
    public void testDotProduct() {

        int size = 8;

        float[] a = new float[size];
        float[] b = new float[size];
        float[] outputMap = new float[size];
        float[] outputReduce = new float[1];

        float[] seqMap = new float[size];
        float[] seqReduce = new float[1];

        Random r = new Random();
        for (int i = 0; i < size; i++) {
            a[i] = r.nextFloat();
            b[i] = r.nextFloat();
        }

        // Sequential computation
        dotProductFunctionMap(a, b, seqMap);
        dotProductFunctionReduce(seqMap, seqReduce);

        // Parallel computation with Tornado
        //@formatter:off
        new TaskSchedule("s0")
                .task("t0-MAP", TestFloats::dotProductFunctionMap, a, b, outputMap)
                .task("t1-REDUCE", TestFloats::dotProductFunctionReduce, outputMap, outputReduce)
                .streamOut(outputReduce)
                .execute();
        //@formatter:on

        assertEquals(seqReduce[0], outputReduce[0], 0.001);
    }

    private static void vectorPhiTest(VectorFloat3 input, VectorFloat3 output) {
        Float3 sum = new Float3();
        for (int i = 0; i < input.getLength(); i++) {
            sum = Float3.add(sum, input.get(i));
        }
        output.set(0, sum);
    }

    @Test
    public void vectorPhiTest() {

        final VectorFloat3 input = new VectorFloat3(8);
        final VectorFloat3 output = new VectorFloat3(1);

        input.fill(1f);

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestFloats::vectorPhiTest, input, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        assertEquals(8.0f, output.get(0).getS0(), 0.001);
        assertEquals(8.0f, output.get(0).getS1(), 0.001);
        assertEquals(8.0f, output.get(0).getS2(), 0.001);

    }

}