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

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.collections.types.Int2;
import uk.ac.manchester.tornado.api.collections.types.Int3;
import uk.ac.manchester.tornado.api.collections.types.Int4;
import uk.ac.manchester.tornado.api.collections.types.VectorInt;
import uk.ac.manchester.tornado.api.collections.types.VectorInt2;
import uk.ac.manchester.tornado.api.collections.types.VectorInt3;
import uk.ac.manchester.tornado.api.collections.types.VectorInt4;
import uk.ac.manchester.tornado.unittests.common.TornadoNotSupported;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestInts extends TornadoTestBase {

    private static void addInts2(Int2 a, Int2 b, VectorInt results) {
        Int2 i2 = Int2.add(a, b);
        int r = i2.getX() + i2.getY();
        results.set(0, r);
    }

    @Test
    public void addInt2() {
        int size = 1;
        Int2 a = new Int2(1, 2);
        Int2 b = new Int2(3, 2);
        VectorInt output = new VectorInt(size);

        //@formatter:off
        new TaskSchedule("s0")
            .task("t0", TestInts::addInts2, a, b, output)
            .streamOut(output)
            .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            assertEquals(8, output.get(i));
        }
    }

    private static void addInts3(Int3 a, Int3 b, VectorInt results) {
        Int3 i3 = Int3.add(a, b);
        int r = i3.getX() + i3.getY() + i3.getZ();
        results.set(0, r);
    }

    @Test
    public void addInt3() {
        int size = 1;
        Int3 a = new Int3(1, 2, 3);
        Int3 b = new Int3(3, 2, 1);
        VectorInt output = new VectorInt(size);

        //@formatter:off
        new TaskSchedule("s0")
            .task("t0", TestInts::addInts3, a, b, output)
            .streamOut(output)
            .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            assertEquals(12, output.get(i));
        }
    }

    private static void addInts4(Int4 a, Int4 b, VectorInt results) {
        Int4 i4 = Int4.add(a, b);
        int r = i4.getX() + i4.getY() + i4.getZ() + i4.getW();
        results.set(0, r);
    }

    @Test
    public void addInt4() {
        int size = 1;
        Int4 a = new Int4(1, 2, 3, 4);
        Int4 b = new Int4(4, 3, 2, 1);
        VectorInt output = new VectorInt(size);

        //@formatter:off
        new TaskSchedule("s0")
            .task("t0", TestInts::addInts4, a, b, output)
            .streamOut(output)
            .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            assertEquals(20, output.get(i));
        }
    }

    private static void addIntVectors(int[] a, int[] b, int[] result) {
        for (@Parallel int i = 0; i < a.length; i++) {
            result[i] = a[i] + b[i];
        }
    }

    @Test
    public void testAddInts01() {

        int size = 8;

        int[] a = new int[size];
        int[] b = new int[size];
        int[] output = new int[size];

        for (int i = 0; i < size; i++) {
            a[i] = i;
            b[i] = i;
        }

        //@formatter:off
        new TaskSchedule("s0")
            .task("t0", TestInts::addIntVectors, a, b, output)
            .streamOut(output)
            .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            assertEquals(i + i, output[i]);
        }
    }

    public static void dotProductFunctionMap(int[] a, int[] b, int[] results) {
        for (@Parallel int i = 0; i < a.length; i++) {
            results[i] = a[i] * b[i];
        }
    }

    public static void dotProductFunctionReduce(int[] input, int[] results) {
        int sum = 0;
        for (int i = 0; i < input.length; i++) {
            sum += input[i];
        }
        results[0] = sum;
    }

    @Test
    public void testDotProductDouble() {

        int size = 8;

        int[] a = new int[size];
        int[] b = new int[size];
        int[] outputMap = new int[size];
        int[] outputReduce = new int[1];

        int[] seqMap = new int[size];
        int[] seqReduce = new int[1];

        Random r = new Random();
        for (int i = 0; i < size; i++) {
            a[i] = r.nextInt(1000);
            b[i] = r.nextInt(1000);
        }

        // Sequential computation
        dotProductFunctionMap(a, b, seqMap);
        dotProductFunctionReduce(seqMap, seqReduce);

        //@formatter:off
        new TaskSchedule("s0")
            .task("t0-MAP", TestInts::dotProductFunctionMap, a, b, outputMap)
            .task("t1-REDUCE", TestInts::dotProductFunctionReduce, outputMap, outputReduce)
            .streamOut(outputReduce)
            .execute();
        //@formatter:on

        assertEquals(seqReduce[0], outputReduce[0]);
    }

    public static void addVectorInt2(VectorInt2 a, VectorInt2 b, VectorInt2 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Int2.add(a.get(i), b.get(i)));
        }
    }

    @Test
    public void testVectorFloat2() {
        int size = 16;

        VectorInt2 a = new VectorInt2(size);
        VectorInt2 b = new VectorInt2(size);
        VectorInt2 output = new VectorInt2(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Int2(i, i));
            b.set(i, new Int2(size - i, size - i));
        }

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestInts::addVectorInt2, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            Int2 sequential = new Int2(i + (size - i), i + (size - i));
            assertEquals(sequential.getX(), output.get(i).getX(), 0.001);
            assertEquals(sequential.getY(), output.get(i).getY(), 0.001);
        }
    }

    public static void addVectorInt3(VectorInt3 a, VectorInt3 b, VectorInt3 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Int3.add(a.get(i), b.get(i)));
        }
    }

    @TornadoNotSupported
    public void testVectorInt3() {
        int size = 8;

        VectorInt3 a = new VectorInt3(size);
        VectorInt3 b = new VectorInt3(size);
        VectorInt3 output = new VectorInt3(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Int3(i, i, i));
            b.set(i, new Int3(size - i, size - i, size - i));
        }

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestInts::addVectorInt3, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            Int3 sequential = new Int3(i + (size - i), i + (size - i), i + (size - i));
            assertEquals(sequential.getX(), output.get(i).getX(), 0.001);
            assertEquals(sequential.getY(), output.get(i).getY(), 0.001);
            assertEquals(sequential.getZ(), output.get(i).getZ(), 0.001);
        }
    }

    public static void addVectorInt4(VectorInt4 a, VectorInt4 b, VectorInt4 results) {
        for (@Parallel int i = 0; i < a.getLength(); i++) {
            results.set(i, Int4.add(a.get(i), b.get(i)));
        }
    }

    @TornadoNotSupported
    public void testVectorInt4() {
        int size = 8;

        VectorInt4 a = new VectorInt4(size);
        VectorInt4 b = new VectorInt4(size);
        VectorInt4 output = new VectorInt4(size);

        for (int i = 0; i < size; i++) {
            a.set(i, new Int4(i, i, i, i));
            b.set(i, new Int4(size - i, size - i, size - i, size));
        }

        //@formatter:off
        new TaskSchedule("s0")
                .task("t0", TestInts::addVectorInt4, a, b, output)
                .streamOut(output)
                .execute();
        //@formatter:on

        for (int i = 0; i < size; i++) {
            Int4 sequential = new Int4(i + (size - i), i + (size - i), i + (size - i), i + size);
            assertEquals(sequential.getX(), output.get(i).getX(), 0.001);
            assertEquals(sequential.getY(), output.get(i).getY(), 0.001);
            assertEquals(sequential.getZ(), output.get(i).getZ(), 0.001);
            assertEquals(sequential.getW(), output.get(i).getW(), 0.001);
        }
    }
}