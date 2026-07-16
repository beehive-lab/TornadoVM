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
package uk.ac.manchester.tornado.fuzz;

import java.util.Random;

/**
 * Thin seeded wrapper over {@link java.util.Random}. Every fuzzing decision is
 * drawn from here so that a whole test case is reconstructable from its seed.
 */
public final class RandomGen {

    private final Random random;

    public RandomGen(long seed) {
        // Scramble the seed with a SplitMix64 finalizer. java.util.Random seeded with
        // sequential values yields correlated first draws, which would make nearby
        // fuzz seeds pick the same template/op. Mixing decorrelates them.
        this.random = new Random(mix(seed));
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    public int nextIntBetween(int lowInclusive, int highInclusive) {
        return lowInclusive + random.nextInt(highInclusive - lowInclusive + 1);
    }

    public long nextLong() {
        return random.nextLong();
    }

    public float nextFloat() {
        return random.nextFloat();
    }

    public double nextDouble() {
        return random.nextDouble();
    }

    public boolean nextBoolean() {
        return random.nextBoolean();
    }

    public <T> T pick(T[] options) {
        return options[random.nextInt(options.length)];
    }

    public int pick(int[] options) {
        return options[random.nextInt(options.length)];
    }
}
