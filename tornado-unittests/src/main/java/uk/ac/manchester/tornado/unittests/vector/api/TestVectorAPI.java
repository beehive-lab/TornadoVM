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

    private final int SIZE = 128;

    public static float randFloat(float min, float max, Random rand) {
        return rand.nextFloat() * (max - min) + min;
    }

    private float[] vectorAddition(FloatArray vector1, FloatArray vector2, int length) {
        float[] result = new float[SIZE];
        VectorSpecies<Float> species = FloatVector.SPECIES_128;

        int width = length / species.length();
        IntStream.range(0, width).parallel().forEach(i -> {
            FloatVector vec1 = FloatVector.fromMemorySegment(species, vector1.getSegment(), (long) i * species.length(), ByteOrder.nativeOrder());
            FloatVector vec2 = FloatVector.fromMemorySegment(species, vector2.getSegment(), (long) i * species.length(), ByteOrder.nativeOrder());
            FloatVector resultVec = vec1.add(vec2);
            resultVec.intoArray(result, i * species.length());
        });

        return result;
    }

    private FloatArray vectorAdditionTornado(FloatArray a, FloatArray b) {
        FloatArray res = new FloatArray(SIZE);
        for (int i = 0; i < a.getSize(); i++) {
            res.set(i, a.get(i) + b.get(i));
        }
        return res;
    }

    @Test
    public void testVectorAPIwithTornadoNativeTypes() {
        Random rand = new Random();
        FloatArray arrayA = new FloatArray(SIZE);
        FloatArray arrayB = new FloatArray(SIZE);
        FloatArray arrayC = new FloatArray(SIZE);

        float[] res = new float[SIZE];

        for (int i = 0; i < arrayA.getSize(); i++) {
            arrayA.set(i, randFloat(0, 2f, rand));
            arrayB.set(i, randFloat(0, 3f, rand));
        }

        arrayC.init(0f);

        res = vectorAddition(arrayA, arrayB, SIZE);

        arrayC = vectorAdditionTornado(arrayA, arrayB);
    }
}
