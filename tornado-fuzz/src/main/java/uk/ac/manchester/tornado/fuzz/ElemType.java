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

/**
 * The scalar element types the fuzzer currently generates kernels over. Each
 * maps to a TornadoVM off-heap array type in
 * {@code uk.ac.manchester.tornado.api.types.arrays}.
 */
public enum ElemType {
    INT("IntArray", "int", true),
    LONG("LongArray", "long", true),
    FLOAT("FloatArray", "float", false),
    DOUBLE("DoubleArray", "double", false);

    /** Simple name of the backing {@code TornadoNativeArray} type. */
    public final String arrayType;
    /** Java primitive name used when emitting source. */
    public final String prim;
    /** Whether an exact {@code ==} oracle applies (integers) vs a float tolerance. */
    public final boolean exact;

    ElemType(String arrayType, String prim, boolean exact) {
        this.arrayType = arrayType;
        this.prim = prim;
        this.exact = exact;
    }
}
