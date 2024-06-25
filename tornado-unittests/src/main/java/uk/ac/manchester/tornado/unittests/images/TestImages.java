/*
 * Copyright (c) 2013-2020, 2022, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.unittests.images;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.images.ImageByte3;
import uk.ac.manchester.tornado.api.types.images.ImageByte4;
import uk.ac.manchester.tornado.api.types.images.ImageFloat;
import uk.ac.manchester.tornado.api.types.images.ImageFloat3;
import uk.ac.manchester.tornado.api.types.images.ImageFloat4;
import uk.ac.manchester.tornado.api.types.images.ImageFloat8;
import uk.ac.manchester.tornado.api.types.vectors.Byte3;
import uk.ac.manchester.tornado.api.types.vectors.Byte4;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.api.types.vectors.Float8;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Test for {@link ImageFloat} and {@link ImageByte3} data structures in
 * Tornado.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.images.TestImages
 * </code>
 *
 */
public class TestImages extends TornadoTestBase {
    // CHECKSTYLE:OFF

    public static void taskWithImages(final ImageFloat a, final ImageFloat b) {
        for (@Parallel int i = 0; i < a.X(); i++) {
            for (@Parallel int j = 0; j < a.Y(); j++) {
                float value = a.get(i, j);
                b.set(i, j, value);
            }
        }
    }

    public static void taskWithImagesFloat3(final ImageFloat3 a, final ImageFloat3 b) {
        for (@Parallel int i = 0; i < a.X(); i++) {
            for (@Parallel int j = 0; j < a.Y(); j++) {
                Float3 value = a.get(i, j);
                b.set(i, j, value);
            }
        }
    }

    public static void taskWithImagesFloat4(final ImageFloat4 a, final ImageFloat4 b) {
        for (@Parallel int i = 0; i < a.X(); i++) {
            for (@Parallel int j = 0; j < a.Y(); j++) {
                Float4 value = a.get(i, j);
                b.set(i, j, value);
            }
        }
    }

    public static void taskWithImagesFloat8(final ImageFloat8 a, final ImageFloat8 b) {
        for (@Parallel int i = 0; i < a.X(); i++) {
            for (@Parallel int j = 0; j < a.Y(); j++) {
                Float8 value = a.get(i, j);
                b.set(i, j, value);
            }
        }
    }

    public static void taskWithImagesByte3(final ImageByte3 a, final ImageByte3 b) {
        for (@Parallel int i = 0; i < a.X(); i++) {
            for (@Parallel int j = 0; j < a.Y(); j++) {
                Byte3 value = a.get(i, j);
                b.set(i, j, value);
            }
        }
    }

    public static void taskWithImagesByte4(final ImageByte4 a, final ImageByte4 b) {
        for (@Parallel int i = 0; i < a.X(); i++) {
            for (@Parallel int j = 0; j < a.Y(); j++) {
                Byte4 value = a.get(i, j);
                b.set(i, j, value);
            }
        }
    }

    public static void testCopyImagesParallel(final ImageFloat a, final ImageFloat b) {
        for (@Parallel int i = 0; i < a.X(); i++) {
            for (@Parallel int j = 0; j < a.Y(); j++) {
                float value = a.get(i, j) + 1;
                b.set(i, j, value);
            }
        }
    }

    public static void testCopyImagesParallelRandom(final ImageFloat a, final ImageFloat b) {
        for (@Parallel int i = 0; i < a.X(); i++) {
            for (@Parallel int j = 0; j < a.Y(); j++) {
                float value = a.get(i, j) + 0.01f;
                b.set(i, j, value);
            }
        }
    }

    public static void testCopyImagesSequential(final ImageFloat a, final ImageFloat b) {
        for (int i = 0; i < a.X(); i++) {
            for (int j = 0; j < a.Y(); j++) {
                float value = a.get(i, j) + 1;
                b.set(i, j, value);
            }
        }
    }

    /**
     * Test for image::fill kernel with square image.
     */
    @Test
    public void testImageFloat01() throws TornadoExecutionPlanException {

        final int N = 128;
        final int M = 128;

        final ImageFloat image = new ImageFloat(M, N);
        image.fill(100f);

        final TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, image) //
                .task("t0", image::fill, 1f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, image);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(1f, image.get(i, j), 0.001);
            }
        }
    }

    /**
     * Test for image::fill kernel with non-square image.
     */
    @Test
    public void testImageFloat02() throws TornadoExecutionPlanException {

        final int M = 128;
        final int N = 32;

        final ImageFloat image = new ImageFloat(M, N);
        image.fill(100f);

        final TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", image::fill, 1f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, image);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(1f, image.get(i, j), 0.001);
            }
        }
    }

    /**
     * Test for image::fill kernel with non-square image.
     */
    @Test
    public void testImageFloat03() throws TornadoExecutionPlanException {

        final int M = 32;
        final int N = 512;

        final ImageFloat image = new ImageFloat(M, N);
        image.fill(100f);

        final TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", image::fill, 1f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, image);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(1f, image.get(i, j), 0.001);
            }
        }
    }

    /**
     * Test for computing a referenced method using {@link ImageFloat} on the OpenCL
     * device using square-matrices.
     */
    @Test
    public void testImageFloat04() throws TornadoExecutionPlanException {

        final int M = 32;
        final int N = 32;

        final ImageFloat imageA = new ImageFloat(M, N);
        final ImageFloat imageB = new ImageFloat(M, N);
        imageA.fill(100f);
        imageB.fill(0f);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, imageA) //
                .task("t1", TestImages::taskWithImages, imageA, imageB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(100f, imageB.get(i, j), 0.001);
            }
        }
    }

    /**
     * Test for computing a referenced method using {@link ImageFloat} on the OpenCL
     * device using non-square matrices and small size.
     */
    @Test
    public void testImageFloat05() throws TornadoExecutionPlanException {

        final int M = 16;
        final int N = 4;

        final ImageFloat imageA = new ImageFloat(M, N);
        final ImageFloat imageB = new ImageFloat(M, N);
        imageA.fill(100f);
        imageB.fill(-1f);

        final TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, imageA) //
                .task("t1", TestImages::taskWithImages, imageA, imageB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(100f, imageB.get(i, j), 0.001);
            }
        }
    }

    /**
     * Test for computing a referenced method using {@link ImageFloat} on the OpenCL
     * device using non-square matrices with big size.
     */
    @Test
    public void testImageFloat06() throws TornadoExecutionPlanException {

        final int M = 256;
        final int N = 512;

        final ImageFloat imageA = new ImageFloat(M, N);
        final ImageFloat imageB = new ImageFloat(M, N);
        imageA.fill(100f);
        imageB.fill(-1f);

        final TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, imageA) //
                .task("t1", TestImages::taskWithImages, imageA, imageB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                assertEquals(100f, imageB.get(i, j), 0.001);
            }
        }
    }

    @Test
    public void testImageFloat07() throws TornadoExecutionPlanException {

        final int M = 512;
        final int N = 512;

        final ImageFloat3 imageA = new ImageFloat3(M, N);
        final ImageFloat3 imageB = new ImageFloat3(M, N);
        imageA.fill(100f);

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Float3 value = new Float3(10f, 20f, 30f);
                imageA.set(i, j, value);
            }
        }

        final TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, imageA) //
                .task("t0", TestImages::taskWithImagesFloat3, imageA, imageB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Float3 result = imageB.get(i, j);
                assertEquals(10, result.getX(), 0.001);
                assertEquals(20, result.getY(), 0.001);
                assertEquals(30, result.getZ(), 0.001);
            }
        }
    }

    @Test
    public void testImageFloat08() throws TornadoExecutionPlanException {

        final int M = 512;
        final int N = 32;

        final ImageFloat3 imageA = new ImageFloat3(M, N);
        final ImageFloat3 imageB = new ImageFloat3(M, N);
        imageA.fill(100f);

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Float3 value = new Float3(10f, 20f, 30f);
                imageA.set(i, j, value);
            }
        }

        final TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, imageA) //
                .task("t0", TestImages::taskWithImagesFloat3, imageA, imageB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Float3 result = imageB.get(i, j);
                assertEquals(10, result.getX(), 0.001);
                assertEquals(20, result.getY(), 0.001);
                assertEquals(30, result.getZ(), 0.001);
            }
        }
    }

    @Test
    public void testImageFloat09() throws TornadoExecutionPlanException {

        final int M = 512;
        final int N = 512;

        final ImageFloat4 imageA = new ImageFloat4(M, N);
        final ImageFloat4 imageB = new ImageFloat4(M, N);
        imageA.fill(100f);

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Float4 value = new Float4(10f, 20f, 30f, 40f);
                imageA.set(i, j, value);
            }
        }

        final TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, imageA) //
                .task("t0", TestImages::taskWithImagesFloat4, imageA, imageB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Float4 result = imageB.get(i, j);
                assertEquals(10, result.getX(), 0.001);
                assertEquals(20, result.getY(), 0.001);
                assertEquals(30, result.getZ(), 0.001);
                assertEquals(40, result.getW(), 0.001);
            }
        }
    }

    @Test
    public void testImageFloat10() throws TornadoExecutionPlanException {

        final int M = 32;
        final int N = 512;

        final ImageFloat4 imageA = new ImageFloat4(M, N);
        final ImageFloat4 imageB = new ImageFloat4(M, N);
        imageA.fill(100f);

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Float4 value = new Float4(10f, 20f, 30f, 40f);
                imageA.set(i, j, value);
            }
        }

        final TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, imageA) //
                .task("t0", TestImages::taskWithImagesFloat4, imageA, imageB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Float4 result = imageB.get(i, j);
                assertEquals(10, result.getX(), 0.001);
                assertEquals(20, result.getY(), 0.001);
                assertEquals(30, result.getZ(), 0.001);
                assertEquals(40, result.getW(), 0.001);
            }
        }
    }

    @Test
    public void testImageFloat11() throws TornadoExecutionPlanException {

        final int M = 512;
        final int N = 32;

        final ImageFloat8 imageA = new ImageFloat8(M, N);
        final ImageFloat8 imageB = new ImageFloat8(M, N);
        imageA.fill(100f);

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Float8 value = new Float8(10f, 20f, 30f, 40f, 50f, 60f, 70f, 80f);
                imageA.set(i, j, value);
            }
        }

        final TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, imageA) //
                .task("t0", TestImages::taskWithImagesFloat8, imageA, imageB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Float8 result = imageB.get(i, j);
                assertEquals(10f, result.getS0(), 0.001);
                assertEquals(20f, result.getS1(), 0.001);
                assertEquals(30f, result.getS2(), 0.001);
                assertEquals(40f, result.getS3(), 0.001);
                assertEquals(50f, result.getS4(), 0.001);
                assertEquals(60f, result.getS5(), 0.001);
                assertEquals(70f, result.getS6(), 0.001);
                assertEquals(80f, result.getS7(), 0.001);
            }
        }
    }

    @Test
    public void testImageFloat12() throws TornadoExecutionPlanException {

        final int M = 512;
        final int N = 512;

        final ImageByte3 imageA = new ImageByte3(M, N);
        final ImageByte3 imageB = new ImageByte3(M, N);
        imageA.fill((byte) 10);

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Byte3 value = new Byte3((byte) 10, (byte) 11, (byte) 12);
                imageA.set(i, j, value);
            }
        }

        final TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, imageA) //
                .task("t0", TestImages::taskWithImagesByte3, imageA, imageB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Byte3 result = imageB.get(i, j);
                assertEquals(10, result.getX(), 0.001);
                assertEquals(11, result.getY(), 0.001);
                assertEquals(12, result.getZ(), 0.001);
            }
        }
    }

    @Test
    public void testImageFloat13() throws TornadoExecutionPlanException {

        final int M = 16;
        final int N = 2048;

        final ImageByte3 imageA = new ImageByte3(M, N);
        final ImageByte3 imageB = new ImageByte3(M, N);
        imageA.fill((byte) 10);

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Byte3 value = new Byte3((byte) 10, (byte) 11, (byte) 12);
                imageA.set(i, j, value);
            }
        }

        final TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, imageA) //
                .task("t0", TestImages::taskWithImagesByte3, imageA, imageB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Byte3 result = imageB.get(i, j);
                assertEquals(10, result.getX(), 0.001);
                assertEquals(11, result.getY(), 0.001);
                assertEquals(12, result.getZ(), 0.001);
            }
        }
    }

    @Test
    public void testImageFloat14() throws TornadoExecutionPlanException {

        final int M = 64;
        final int N = 64;

        final ImageByte4 imageA = new ImageByte4(M, N);
        final ImageByte4 imageB = new ImageByte4(M, N);
        imageA.fill((byte) 10);

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Byte4 value = new Byte4((byte) 10, (byte) 11, (byte) 12, (byte) 13);
                imageA.set(i, j, value);
            }
        }

        final TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, imageA) //
                .task("t0", TestImages::taskWithImagesByte4, imageA, imageB) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Byte4 result = imageB.get(i, j);
                assertEquals(10, result.getX(), 0.001);
                assertEquals(11, result.getY(), 0.001);
                assertEquals(12, result.getZ(), 0.001);
                assertEquals(13, result.getW(), 0.001);
            }
        }
    }

    @Test
    public void testImageFloat15() throws TornadoExecutionPlanException {

        final int M = 32;
        final int N = 1024;

        final ImageByte4 imageA = new ImageByte4(M, N);
        final ImageByte4 imageB = new ImageByte4(M, N);
        imageA.fill((byte) 10);

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Byte4 value = new Byte4((byte) 10, (byte) 11, (byte) 12, (byte) 13);
                imageA.set(i, j, value);
            }
        }

        final TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, imageA) //
                .task("t0", TestImages::taskWithImagesByte4, imageA, imageB)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                Byte4 result = imageB.get(i, j);
                assertEquals(10, result.getX(), 0.001);
                assertEquals(11, result.getY(), 0.001);
                assertEquals(12, result.getZ(), 0.001);
                assertEquals(13, result.getW(), 0.001);
            }
        }
    }

    @Test
    public void testImageFloat16() throws TornadoExecutionPlanException {

        final int M = 64;
        final int N = 64;

        final int base = 10;

        final ImageFloat imageA = new ImageFloat(M, N);
        final ImageFloat imageB = new ImageFloat(M, N);
        imageA.fill(base);

        final TaskGraph taskGraph = new TaskGraph("testLoop") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, imageA) //
                .task("image", TestImages::testCopyImagesParallel, imageA, imageB)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {

            // Execute 10000 times
            int iteration = 0;
            while (iteration < 10000) {
                executionPlan.execute();

                // Check result
                for (int i = 0; i < M; i++) {
                    for (int j = 0; j < N; j++) {
                        assertEquals((11 + iteration), imageB.get(i, j), 0.1f);
                    }
                }

                // Set the new array
                for (int i = 0; i < M; i++) {
                    for (int j = 0; j < N; j++) {
                        imageA.set(i, j, imageB.get(i, j));
                    }
                }
                iteration++;
            }
        }
    }

    @Test
    public void testImageFloat17() throws TornadoExecutionPlanException {

        final int M = 64;
        final int N = 64;

        final int base = 10;

        final ImageFloat imageA = new ImageFloat(M, N);
        final ImageFloat imageB = new ImageFloat(M, N);
        imageA.fill(base);

        final TaskGraph taskGraph = new TaskGraph("testLoop") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, imageA) //
                .task("image", TestImages::testCopyImagesSequential, imageA, imageB)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {

            // Execute 10000 times
            int iteration = 0;
            while (iteration < 10000) {
                executionPlan.execute();

                // Check result
                for (int i = 0; i < M; i++) {
                    for (int j = 0; j < N; j++) {
                        assertEquals((11 + iteration), imageB.get(i, j), 0.1f);
                    }
                }

                // Set the new array
                for (int i = 0; i < M; i++) {
                    for (int j = 0; j < N; j++) {
                        imageA.set(i, j, imageB.get(i, j));
                    }
                }
                iteration++;
            }
        }
    }

    @Test
    public void testImageFloat18() throws TornadoExecutionPlanException {

        final int M = 64;
        final int N = 64;

        float base = new Random().nextFloat();

        final ImageFloat imageA = new ImageFloat(M, N);
        final ImageFloat imageB = new ImageFloat(M, N);
        imageA.fill(base);

        final TaskGraph taskGraph = new TaskGraph("testLoop") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, imageA) //
                .task("image", TestImages::testCopyImagesParallelRandom, imageA, imageB)//
                .transferToHost(DataTransferMode.EVERY_EXECUTION, imageB);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {

            // Execute 10000 times
            int iteration = 0;
            while (iteration < 10000) {
                executionPlan.execute();

                // Check result
                for (int i = 0; i < M; i++) {
                    for (int j = 0; j < N; j++) {
                        assertEquals((base + 0.01f), imageB.get(i, j), 0.01f);
                    }
                }
                base += 0.01f;

                // Set the new array
                for (int i = 0; i < M; i++) {
                    for (int j = 0; j < N; j++) {
                        imageA.set(i, j, imageB.get(i, j));
                    }
                }
                iteration++;
            }
        }
    }
    // CHECKSTYLE:ON

}
