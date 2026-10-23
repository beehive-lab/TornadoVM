/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.tc.ir;

/**
 * The Java classes TC passes recognise by identity, as {@link Type.ObjectType}s named by their fully
 * qualified binary names (so a user class that shares a simple name never matches), plus the
 * frontend's own reference types.
 */
public final class ApiTypes {

    private ApiTypes() {
    }

    private static final String API = "uk.ac.manchester.tornado.api.";

    public static final Type.ObjectType KERNEL_CONTEXT = new Type.ObjectType(API + "KernelContext");
    public static final Type.ObjectType TILE_CONTEXT = new Type.ObjectType(API + "tile.TileContext");
    public static final Type.ObjectType TILE = new Type.ObjectType(API + "tile.Tile");
    public static final Type.ObjectType TENSOR_VIEW = new Type.ObjectType(API + "tile.TensorView");
    public static final Type.ObjectType PARTITION_VIEW = new Type.ObjectType(API + "tile.PartitionView");
    /** An Apple simdgroup matrix fragment: a value of the dialect's own type (MSL simdgroup_float8x8). */
    public static final Type.ObjectType MATRIX_8X8_FLOAT = new Type.ObjectType(API + "types.matrix.Matrix8x8Float");
    /** The half-precision fragment (MSL simdgroup_half8x8). */
    public static final Type.ObjectType MATRIX_8X8_HALF = new Type.ObjectType(API + "types.matrix.Matrix8x8Half");
    /** Object types that stay values on the device (the dialect names their type), not Java objects. */
    public static boolean isDeviceValue(Type type) {
        return type.equals(MATRIX_8X8_FLOAT) || type.equals(MATRIX_8X8_HALF);
    }

    public static final Type.ObjectType DEVICE_KERNEL = new Type.ObjectType(API + "DeviceKernel");
    public static final Type.ObjectType ATOMIC_INTEGER = new Type.ObjectType("java.util.concurrent.atomic.AtomicInteger");
    public static final Type.ObjectType STRING = new Type.ObjectType("java.lang.String");
    /** The Object[] of a varargs call. */
    public static final Type.ObjectType OBJECT_ARRAY = new Type.ObjectType("java.lang.Object[]");
    /** A reference whose class the frontend has not resolved yet (a local read before its definition is translated). */
    public static final Type.ObjectType UNRESOLVED = new Type.ObjectType("?");

    /** The type of a class given its JVM internal name ({@code pkg/Outer$Inner}). */
    public static Type.ObjectType of(String internalName) {
        return new Type.ObjectType(internalName.replace('/', '.'));
    }

    /** Whether {@code type} is one of the opaque CUDA Tile types (Tile, TensorView, PartitionView). */
    public static boolean isTileObject(Type type) {
        return TILE.equals(type) || TENSOR_VIEW.equals(type) || PARTITION_VIEW.equals(type);
    }
}
