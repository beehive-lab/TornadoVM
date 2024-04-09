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
package uk.ac.manchester.tornado.unittests.vector.api;

import java.nio.ByteOrder;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.vector.api.TestVectorAPI
 * </code>
 */
public class TestVectorAPI extends TornadoTestBase {

    private static final int SIZE = 2048;

    private static final float DELTA = 0.001f;
    private static final Random rand = new Random();
    private static FloatArray arrayA;
    private static FloatArray arrayB;
    private static FloatArray referenceResult;

    public static float randFloat(float min, float max, Random rand) {
        return rand.nextFloat() * (max - min) + min;
    }

    @BeforeClass
    public static void setUpBeforeClass() {
        arrayA = new FloatArray(SIZE);
        arrayB = new FloatArray(SIZE);
        referenceResult = new FloatArray(SIZE);
        referenceResult.init(0f);

        for (int i = 0; i < arrayA.getSize(); i++) {
            arrayA.set(i, randFloat(0, 2f, rand));
            arrayB.set(i, randFloat(0, 3f, rand));
        }

        referenceResult = vectorAdditionFloatArray(arrayA, arrayB);
    }

    private static FloatArray vectorAdditionFloatArray(FloatArray a, FloatArray b) {
        FloatArray res = new FloatArray(SIZE);
        for (int i = 0; i < a.getSize(); i++) {
            res.set(i, a.get(i) + b.get(i));
        }
        return res;
    }

    /**
     * Executes parallel vector addition using the Vector API and a specified VectorSpecies.
     *
     * @param vector1
     *     The first float array.
     * @param vector2
     *     The second float array.
     * @param species
     *     The VectorSpecies defining the SIMD instructions width.
     * @return An array containing the result of the parallel addition.
     */
    private float[] parallelVectorAdd(FloatArray vector1, FloatArray vector2, VectorSpecies<Float> species) {
        float[] result = new float[SIZE];
        System.out.println(species.toString());
        int width = vector1.getSize() / species.length();
        IntStream.range(0, width).parallel().forEach(i -> {
            long offsetIndex = (long) i * species.length() * Float.BYTES;
            FloatVector vec1 = FloatVector.fromMemorySegment(species, vector1.getSegment(), offsetIndex, ByteOrder.nativeOrder());
            FloatVector vec2 = FloatVector.fromMemorySegment(species, vector2.getSegment(), offsetIndex, ByteOrder.nativeOrder());
            FloatVector resultVec = vec1.add(vec2);
            resultVec.intoArray(result, i * species.length());
        });

        return result;
    }

    /**
     * Test method for vector addition with 64-bit vector species.
     */
    @Test
    public void test64BitVectors() {
        VectorSpecies<Float> species = FloatVector.SPECIES_64;
        float[] result = parallelVectorAdd(arrayA, arrayB, species);
        Assert.assertArrayEquals(result, referenceResult.toHeapArray(), DELTA);
    }

    /**
     * Test method for vector addition with 128-bit vector species.
     */
    @Test
    public void test128BitVectors() {
        VectorSpecies<Float> species = FloatVector.SPECIES_128;
        float[] result = parallelVectorAdd(arrayA, arrayB, species);
        Assert.assertArrayEquals(result, referenceResult.toHeapArray(), DELTA);
    }

    /**
     * Test method for vector addition with 256-bit vector species.
     */
    @Test
    public void test256BitVectors() {
        VectorSpecies<Float> species = FloatVector.SPECIES_256;
        float[] result = parallelVectorAdd(arrayA, arrayB, species);
        Assert.assertArrayEquals(result, referenceResult.toHeapArray(), DELTA);
    }

    /**
     * Test method for vector addition with 512-bit vector species.
     */
    @Test
    public void test512BitVectors() {
        VectorSpecies<Float> species = FloatVector.SPECIES_512;
        float[] result = parallelVectorAdd(arrayA, arrayB, species);
        Assert.assertArrayEquals(result, referenceResult.toHeapArray(), DELTA);
    }
}
