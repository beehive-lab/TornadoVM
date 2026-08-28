/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package uk.ac.manchester.tornado.curand;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.LibraryTaskDescriptor;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.curand.enums.CuRandRngType;

/**
 * Random number generation on the device through NVIDIA cuRAND, as a TornadoVM library task.
 *
 * <p>
 * The numbers are written straight into a TornadoVM array that is already on the device, so a
 * simulation that needs random input does not generate it on the host and upload it. Generation is
 * issued on the execution plan's own CUDA stream, so it is ordered against the kernels and
 * transfers of the same task graph like any other task.
 * </p>
 *
 * <p>
 * Typical use, filling a slice of a larger device-resident buffer:
 * </p>
 *
 * <pre>{@code
 * TaskGraph graph = new TaskGraph("rng")
 *         .transferToDevice(DataTransferMode.FIRST_EXECUTION, buffer)
 *         .libraryTask("normals", CuRand::generateNormal, buffer, offset, count, 0.0f, 1.0f)
 *         .persistOnDevice(buffer);
 * }</pre>
 *
 * <p>
 * cuRAND generates normals in pairs, so {@code count} must be even for the single-precision
 * {@code generateNormal}; an odd count is rejected with {@code CURAND_STATUS_LENGTH_NOT_MULTIPLE}.
 * </p>
 */
public final class CuRand {

    public static final String LIBRARY_NAME = "nvidia/curand";

    /** Seed used unless one is given; matches cuRAND's own default. */
    public static final long DEFAULT_SEED = 0L;

    private CuRand() {
    }

    /**
     * Access flags for a generator call: every argument is read except the destination, which is
     * READ_WRITE rather than WRITE_ONLY.
     *
     * <p>
     * The distinction matters because a call usually fills a slice of a larger device-resident
     * buffer. Declaring the destination write-only tells TornadoVM the whole buffer is produced
     * here, and everything outside the slice would be treated as no longer valid.
     * </p>
     */
    private static Access[] writesInto(int numArgs, int outputIndex) {
        Access[] accesses = new Access[numArgs];
        Arrays.fill(accesses, Access.READ_ONLY);
        accesses[outputIndex] = Access.READ_WRITE;
        return accesses;
    }


    /**
     * Fills {@code count} elements of {@code output}, starting at {@code elementOffset}, with
     * normally distributed single-precision values.
     *
     * @param output
     *     Device-resident destination.
     * @param elementOffset
     *     First element to write, in elements.
     * @param count
     *     How many values to write. Must be even.
     * @param mean
     *     Mean of the distribution.
     * @param stddev
     *     Standard deviation of the distribution.
     */
    public static LibraryTaskDescriptor generateNormal(FloatArray output, int elementOffset, int count, float mean, float stddev) {
        return generateNormal(output, elementOffset, count, mean, stddev, DEFAULT_SEED);
    }

    /** As {@link #generateNormal(FloatArray, int, int, float, float)}, with an explicit seed. */
    public static LibraryTaskDescriptor generateNormal(FloatArray output, int elementOffset, int count, float mean, float stddev, long seed) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("curandGenerateNormal") //
                .withParameters(new Object[] { output, elementOffset, count, mean, stddev, seed }) //
                .withAccess(writesInto(6, 0));
    }

    /** Double-precision {@link #generateNormal(FloatArray, int, int, float, float)}. */
    public static LibraryTaskDescriptor generateNormal(DoubleArray output, int elementOffset, int count, double mean, double stddev) {
        return generateNormal(output, elementOffset, count, mean, stddev, DEFAULT_SEED);
    }

    /** As {@link #generateNormal(DoubleArray, int, int, double, double)}, with an explicit seed. */
    public static LibraryTaskDescriptor generateNormal(DoubleArray output, int elementOffset, int count, double mean, double stddev, long seed) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("curandGenerateNormalDouble") //
                .withParameters(new Object[] { output, elementOffset, count, mean, stddev, seed }) //
                .withAccess(writesInto(6, 0));
    }

    /** Uniform values on (0, 1]. */
    public static LibraryTaskDescriptor generateUniform(FloatArray output, int elementOffset, int count) {
        return generateUniform(output, elementOffset, count, DEFAULT_SEED);
    }

    /** As {@link #generateUniform(FloatArray, int, int)}, with an explicit seed. */
    public static LibraryTaskDescriptor generateUniform(FloatArray output, int elementOffset, int count, long seed) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("curandGenerateUniform") //
                .withParameters(new Object[] { output, elementOffset, count, seed }) //
                .withAccess(writesInto(4, 0));
    }

    /** Double-precision {@link #generateUniform(FloatArray, int, int)}. */
    public static LibraryTaskDescriptor generateUniform(DoubleArray output, int elementOffset, int count) {
        return generateUniform(output, elementOffset, count, DEFAULT_SEED);
    }

    /** As {@link #generateUniform(DoubleArray, int, int)}, with an explicit seed. */
    public static LibraryTaskDescriptor generateUniform(DoubleArray output, int elementOffset, int count, long seed) {
        return new LibraryTaskDescriptor() //
                .withLibrary(LIBRARY_NAME) //
                .withFunction("curandGenerateUniformDouble") //
                .withParameters(new Object[] { output, elementOffset, count, seed }) //
                .withAccess(writesInto(4, 0));
    }

    /** Options selecting the generator; attach with {@code withTuning}. */
    public static LibraryTaskDescriptor withGenerator(LibraryTaskDescriptor descriptor, CuRandRngType rngType) {
        return descriptor.withTuning(rngType);
    }
}
