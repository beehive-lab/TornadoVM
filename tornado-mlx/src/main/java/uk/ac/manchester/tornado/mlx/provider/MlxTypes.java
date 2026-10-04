/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.mlx.provider;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;

/**
 * MLX element types, as MLX numbers its {@code Dtype} values, and the type of each TornadoVM array.
 */
final class MlxTypes {

    static final int MLX_BOOL = 0;
    static final int MLX_UINT8 = 1;
    static final int MLX_UINT16 = 2;
    static final int MLX_UINT32 = 3;
    static final int MLX_INT8 = 5;
    static final int MLX_INT16 = 6;
    static final int MLX_INT32 = 7;
    static final int MLX_INT64 = 8;
    static final int MLX_FLOAT16 = 9;
    static final int MLX_FLOAT32 = 10;
    static final int MLX_FLOAT64 = 11;
    static final int MLX_BFLOAT16 = 12;
    static final int MLX_COMPLEX64 = 13;

    private MlxTypes() {
    }

    static int dtypeOf(Object array) {
        return switch (array) {
            case FloatArray ignored -> MLX_FLOAT32;
            case HalfFloatArray ignored -> MLX_FLOAT16;
            case BFloat16Array ignored -> MLX_BFLOAT16;
            case DoubleArray ignored -> MLX_FLOAT64;
            case IntArray ignored -> MLX_INT32;
            case LongArray ignored -> MLX_INT64;
            case ShortArray ignored -> MLX_INT16;
            case Int8Array ignored -> MLX_INT8;
            case ByteArray ignored -> MLX_UINT8;
            default -> throw new TornadoRuntimeException("[ERROR] MLX does not support arguments of type " + array.getClass().getSimpleName());
        };
    }
}
