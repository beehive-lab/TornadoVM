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
package uk.ac.manchester.tornado.curand.enums;

/**
 * Pseudo-random generators of {@code curandRngType_t}, with the values cuRAND uses.
 *
 * @see <a href="https://docs.nvidia.com/cuda/curand/group__HOST.html">cuRAND host API</a>
 */
public enum CuRandRngType {

    /** Whatever cuRAND considers a good default; currently XORWOW. */
    CURAND_RNG_PSEUDO_DEFAULT(100),
    CURAND_RNG_PSEUDO_XORWOW(101),
    CURAND_RNG_PSEUDO_MRG32K3A(121),
    CURAND_RNG_PSEUDO_MTGP32(141),
    CURAND_RNG_PSEUDO_MT19937(142),
    /** Counter-based; the cheapest of the pseudo-random generators for bulk normals. */
    CURAND_RNG_PSEUDO_PHILOX4_32_10(161);

    private final int value;

    CuRandRngType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
