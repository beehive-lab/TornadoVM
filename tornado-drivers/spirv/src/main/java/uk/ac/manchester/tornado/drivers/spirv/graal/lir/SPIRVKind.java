/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, 2023, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import java.util.HashMap;
import java.util.Map;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

/**
 * SPIR-V Types:
 * https://www.khronos.org/registry/spir-v/specs/unified1/SPIRV.html#_types
 *
 * Compatible with Level Zero:
 * https://spec.oneapi.com/level-zero/latest/core/SPIRV.html
 *
 * Note: Floating-point types are represented and stored using IEEE-754
 * semantics. All integer formats are represented and stored using
 * 2’s-complement format.
 *
 */
public enum SPIRVKind implements PlatformKind {

    // @formatter:off

    // Scalar Types
    OP_TYPE_BOOL(1, java.lang.Boolean.TYPE),
    OP_TYPE_INT_8(1, java.lang.Byte.TYPE),          // Byte, Char
    OP_TYPE_INT_16(2, java.lang.Short.TYPE),        // Short
    OP_TYPE_INT_32(4, java.lang.Integer.TYPE),      // Integer 32 bits
    OP_TYPE_INT_64(8, java.lang.Long.TYPE),         // Long
    OP_TYPE_FLOAT_16(2, java.lang.Float.TYPE),      // Half float
    OP_TYPE_FLOAT_32(4, java.lang.Float.TYPE),      // Float 32 (FP32)
    OP_TYPE_FLOAT_64(8, java.lang.Double.TYPE),     // Double (FP64)

    // Vector types

    // OP_TYPE_VECTOR2
    OP_TYPE_VECTOR2_INT_16(2, uk.ac.manchester.tornado.api.types.vectors.Short2.TYPE, OP_TYPE_INT_16),
    OP_TYPE_VECTOR2_VOLUME_INT_16(2, uk.ac.manchester.tornado.api.types.vectors.Short2.TYPE, OP_TYPE_INT_16),
    OP_TYPE_VECTOR2_INT_32(2, uk.ac.manchester.tornado.api.types.vectors.Int2.TYPE, OP_TYPE_INT_32),
    OP_TYPE_VECTOR2_INT_64(2, uk.ac.manchester.tornado.api.types.vectors.Int2.TYPE, OP_TYPE_INT_64),
    OP_TYPE_VECTORINT2_INT_32(2, uk.ac.manchester.tornado.api.types.collections.VectorInt2.TYPE, OP_TYPE_INT_32),

    // OP_TYPE_VECTOR 3
    OP_TYPE_VECTOR3_INT_8(3, uk.ac.manchester.tornado.api.types.vectors.Byte3.TYPE, OP_TYPE_INT_8),
    OP_TYPE_VECTOR3_INT_16(3, uk.ac.manchester.tornado.api.types.vectors.Short3.TYPE, OP_TYPE_INT_16),
    OP_TYPE_VECTOR3_INT_32(3, uk.ac.manchester.tornado.api.types.vectors.Int3.TYPE, OP_TYPE_INT_32),
    OP_TYPE_VECTORINT3_INT_32(3, uk.ac.manchester.tornado.api.types.collections.VectorInt3.TYPE, OP_TYPE_INT_32),
    OP_TYPE_VECTOR3_INT_64(3, uk.ac.manchester.tornado.api.types.vectors.Int3.TYPE, OP_TYPE_INT_64),
    OP_TYPE_IMAGEBYTE3_INT_8(3, uk.ac.manchester.tornado.api.types.images.ImageByte3.TYPE, OP_TYPE_INT_8),

    // OP_TYPE_VECTOR 4
    OP_TYPE_VECTOR4_INT_8(4, uk.ac.manchester.tornado.api.types.vectors.Byte4.TYPE, OP_TYPE_INT_8),
    OP_TYPE_IMAGEBYTE4_INT_8(4, uk.ac.manchester.tornado.api.types.images.ImageByte4.TYPE, OP_TYPE_INT_8),
    OP_TYPE_VECTOR4_INT_32(4, uk.ac.manchester.tornado.api.types.vectors.Int4.TYPE, OP_TYPE_INT_32),
    OP_TYPE_VECTOR4_INT_64(4, uk.ac.manchester.tornado.api.types.vectors.Int4.TYPE, OP_TYPE_INT_64),
    OP_TYPE_VECTORINT4_INT_32(4, uk.ac.manchester.tornado.api.types.collections.VectorInt4.TYPE, OP_TYPE_INT_32),

    // OP_TYPE_VECTOR 8
    OP_TYPE_VECTOR8_INT_32(8, uk.ac.manchester.tornado.api.types.vectors.Int8.TYPE, OP_TYPE_INT_32),
    OP_TYPE_VECTOR16_INT_32(16, uk.ac.manchester.tornado.api.types.vectors.Int16.TYPE, OP_TYPE_INT_32),
    OP_TYPE_VECTOR8_INT_64(8, uk.ac.manchester.tornado.api.types.vectors.Int8.TYPE, OP_TYPE_INT_64),
    OP_TYPE_VECTOR16_INT_64(16, uk.ac.manchester.tornado.api.types.vectors.Int16.TYPE, OP_TYPE_INT_64),
    OP_TYPE_VECTORINT8_INT_32(8, uk.ac.manchester.tornado.api.types.collections.VectorInt8.TYPE, OP_TYPE_INT_32),
    OP_TYPE_VECTORINT16_INT_32(16, uk.ac.manchester.tornado.api.types.collections.VectorInt16.TYPE, OP_TYPE_INT_32),

    // OP_TYPE_VECTOR2 Float
    OP_TYPE_VECTOR2_FLOAT_16(2, uk.ac.manchester.tornado.api.types.vectors.Float2.TYPE, OP_TYPE_FLOAT_16),  // Half float
    OP_TYPE_VECTOR2_FLOAT_32(2, uk.ac.manchester.tornado.api.types.vectors.Float2.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_VECTOR2_FLOAT_64(2, uk.ac.manchester.tornado.api.types.vectors.Double2.TYPE, OP_TYPE_FLOAT_64),
    OP_TYPE_VECTOR2_HALF_FLOAT(2, uk.ac.manchester.tornado.api.types.vectors.Half2.TYPE, OP_TYPE_FLOAT_16),
    OP_TYPE_VECTORFLOAT2_FLOAT_32(2, uk.ac.manchester.tornado.api.types.collections.VectorFloat2.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_VECTORDOUBLE2_FLOAT_64(2, uk.ac.manchester.tornado.api.types.collections.VectorDouble2.TYPE, OP_TYPE_FLOAT_64),
    OP_TYPE_VECTORHALF2_FLOAT_16(2, uk.ac.manchester.tornado.api.types.collections.VectorHalf2.TYPE, OP_TYPE_FLOAT_16),

    // OP_TYPE_VECTOR3 Float
    OP_TYPE_VECTOR3_FLOAT_16(3, uk.ac.manchester.tornado.api.types.vectors.Float3.TYPE, OP_TYPE_FLOAT_16),  // Half float
    OP_TYPE_VECTOR3_FLOAT_32(3, uk.ac.manchester.tornado.api.types.vectors.Float3.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_VECTOR3_FLOAT_64(3, uk.ac.manchester.tornado.api.types.vectors.Double3.TYPE, OP_TYPE_FLOAT_64),
    OP_TYPE_VECTOR3_HALF_FLOAT(3, uk.ac.manchester.tornado.api.types.vectors.Half3.TYPE, OP_TYPE_FLOAT_16),
    OP_TYPE_VECTORFLOAT3_FLOAT_32(3, uk.ac.manchester.tornado.api.types.collections.VectorFloat3.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_IMAGEFLOAT3_FLOAT_32(3, uk.ac.manchester.tornado.api.types.images.ImageFloat3.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_VECTORDOUBLE3_FLOAT_64(3, uk.ac.manchester.tornado.api.types.collections.VectorDouble3.TYPE, OP_TYPE_FLOAT_64),
    OP_TYPE_VECTORHALF3_FLOAT_16(3, uk.ac.manchester.tornado.api.types.collections.VectorHalf3.TYPE, OP_TYPE_FLOAT_16),

    // OP_TYPE_VECTOR4 Float
    OP_TYPE_VECTOR4_FLOAT_16(4, uk.ac.manchester.tornado.api.types.vectors.Float4.TYPE, OP_TYPE_FLOAT_16),  // Half float
    OP_TYPE_VECTOR4_FLOAT_32(4, uk.ac.manchester.tornado.api.types.vectors.Float4.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_VECTOR4_FLOAT_64(4, uk.ac.manchester.tornado.api.types.vectors.Double4.TYPE, OP_TYPE_FLOAT_64),
    OP_TYPE_VECTOR4_HALF_FLOAT(4, uk.ac.manchester.tornado.api.types.vectors.Half4.TYPE, OP_TYPE_FLOAT_16),
    OP_TYPE_VECTORDOUBLE4_FLOAT_64(4, uk.ac.manchester.tornado.api.types.collections.VectorDouble4.TYPE, OP_TYPE_FLOAT_64),
    OP_TYPE_VECTORFLOAT4_FLOAT_32(4, uk.ac.manchester.tornado.api.types.collections.VectorFloat4.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_MATRIX2DFLOAT4_FLOAT_32(4, uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat4.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_MATRIX3DFLOAT4_FLOAT_32(4, uk.ac.manchester.tornado.api.types.matrix.Matrix3DFloat4.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_MATRIX4X4FLOAT_FLOAT_32(4, uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_IMAGEFLOAT4_FLOAT_32(4, uk.ac.manchester.tornado.api.types.images.ImageFloat4.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_VECTORHALF4_FLOAT_16(4, uk.ac.manchester.tornado.api.types.collections.VectorHalf4.TYPE, OP_TYPE_FLOAT_16),

    // OP_TYPE_VECTOR8 Float
    OP_TYPE_VECTOR8_FLOAT_16(8, uk.ac.manchester.tornado.api.types.vectors.Float8.TYPE, OP_TYPE_FLOAT_16),  // Half float
    OP_TYPE_VECTOR8_FLOAT_32(8, uk.ac.manchester.tornado.api.types.vectors.Float8.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_VECTOR16_FLOAT_32(16, uk.ac.manchester.tornado.api.types.vectors.Float16.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_VECTOR8_FLOAT_64(8, uk.ac.manchester.tornado.api.types.vectors.Double8.TYPE, OP_TYPE_FLOAT_64),
    OP_TYPE_VECTOR16_FLOAT_64(16, uk.ac.manchester.tornado.api.types.vectors.Double16.TYPE, OP_TYPE_FLOAT_64),
    OP_TYPE_VECTOR8_HALF_FLOAT(8, uk.ac.manchester.tornado.api.types.vectors.Half8.TYPE, OP_TYPE_FLOAT_16),
    OP_TYPE_VECTOR16_HALF_FLOAT(16, uk.ac.manchester.tornado.api.types.vectors.Half16.TYPE, OP_TYPE_FLOAT_16),
    OP_TYPE_VECTORDOUBLE8_FLOAT_64(8, uk.ac.manchester.tornado.api.types.collections.VectorDouble8.TYPE, OP_TYPE_FLOAT_64),
    OP_TYPE_VECTORDOUBLE16_FLOAT_64(16, uk.ac.manchester.tornado.api.types.collections.VectorDouble16.TYPE, OP_TYPE_FLOAT_64),
    OP_TYPE_VECTORFLOAT8_FLOAT_32(8, uk.ac.manchester.tornado.api.types.collections.VectorFloat8.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_VECTORFLOAT16_FLOAT_32(16, uk.ac.manchester.tornado.api.types.collections.VectorFloat16.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_IMAGEFLOAT8_FLOAT_32(8, uk.ac.manchester.tornado.api.types.images.ImageFloat8.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_VECTORHALF8_FLOAT_16(8, uk.ac.manchester.tornado.api.types.collections.VectorHalf8.TYPE, OP_TYPE_FLOAT_16),
    OP_TYPE_VECTORHALF16_FLOAT_16(16, uk.ac.manchester.tornado.api.types.collections.VectorHalf16.TYPE, OP_TYPE_FLOAT_16),


    OP_TYPE_VOID(0, java.lang.Void.TYPE),

    // A pointer is represented as a long value (8 bytes)
    OP_TYPE_POINTER(8, java.lang.Long.TYPE),

    ILLEGAL(0, null),

    // Atomics
    INTEGER_ATOMIC_JAVA(4, java.util.concurrent.atomic.AtomicInteger.class);

    // @formatter:on

    public static final String VECTOR_COLLECTION_PATH = "uk.ac.manchester.tornado.api.types.vectors";
    public static final String COLLECTION_PATH = "uk.ac.manchester.tornado.api.types.collections";
    private static Map<String, SPIRVKind> vectorTable;

    static {
        vectorTable = new HashMap<>();

        // Bytes
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Byte3;", SPIRVKind.OP_TYPE_VECTOR3_INT_8);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Byte4;", SPIRVKind.OP_TYPE_VECTOR4_INT_8);

        // Integers
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Int2;", SPIRVKind.OP_TYPE_VECTOR2_INT_32);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Int3;", SPIRVKind.OP_TYPE_VECTOR3_INT_32);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Int4;", SPIRVKind.OP_TYPE_VECTOR4_INT_32);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Int8;", SPIRVKind.OP_TYPE_VECTOR8_INT_32);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Int16;", SPIRVKind.OP_TYPE_VECTOR16_INT_32);

        // Floats
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Float2;", SPIRVKind.OP_TYPE_VECTOR2_FLOAT_32);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Float3;", SPIRVKind.OP_TYPE_VECTOR3_FLOAT_32);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Float4;", SPIRVKind.OP_TYPE_VECTOR4_FLOAT_32);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Float8;", SPIRVKind.OP_TYPE_VECTOR8_FLOAT_32);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Float16;", SPIRVKind.OP_TYPE_VECTOR16_FLOAT_32);

        // Double
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Double2;", SPIRVKind.OP_TYPE_VECTOR2_FLOAT_64);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Double3;", SPIRVKind.OP_TYPE_VECTOR3_FLOAT_64);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Double4;", SPIRVKind.OP_TYPE_VECTOR4_FLOAT_64);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Double8;", SPIRVKind.OP_TYPE_VECTOR8_FLOAT_64);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Double16;", SPIRVKind.OP_TYPE_VECTOR16_FLOAT_64);

        // Half Floats
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Half2;", SPIRVKind.OP_TYPE_VECTOR2_HALF_FLOAT);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Half3;", SPIRVKind.OP_TYPE_VECTOR3_HALF_FLOAT);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Half4;", SPIRVKind.OP_TYPE_VECTOR4_HALF_FLOAT);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Half8;", SPIRVKind.OP_TYPE_VECTOR8_HALF_FLOAT);
        vectorTable.put("Luk/ac/manchester/tornado/api/types/vectors/Half16;", SPIRVKind.OP_TYPE_VECTOR16_HALF_FLOAT);
    }

    private final int size;
    private final int vectorLength;
    private final SPIRVKind kind;
    private final SPIRVKind elementKind;
    private final Class<?> javaClass;
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private final EnumKey key = new EnumKey(this);

    SPIRVKind(int sizeInBytes, Class<?> javaClass) {
        this(sizeInBytes, javaClass, null);
    }

    SPIRVKind(int numElements, Class<?> javaClass, SPIRVKind kind) {
        this.kind = this;
        this.javaClass = javaClass;
        this.elementKind = kind;
        this.size = (elementKind == null) ? numElements : elementKind.size * numElements;
        this.vectorLength = (elementKind == null) ? 1 : numElements;
    }

    public static SPIRVKind fromJavaKind(JavaKind stackKind) {
        return switch (stackKind) {
            case Void -> SPIRVKind.OP_TYPE_VOID;
            case Boolean -> SPIRVKind.OP_TYPE_BOOL;
            case Byte -> SPIRVKind.OP_TYPE_INT_8;
            case Short -> SPIRVKind.OP_TYPE_INT_16;
            case Int -> SPIRVKind.OP_TYPE_INT_32;
            case Long -> SPIRVKind.OP_TYPE_INT_64;
            case Float -> SPIRVKind.OP_TYPE_FLOAT_32;
            case Double -> SPIRVKind.OP_TYPE_FLOAT_64;
            default -> throw new TornadoRuntimeException("Java type not supported: " + stackKind);
        };
    }

    public static SPIRVKind fromJavaKindForMethodCalls(JavaKind stackKind) {
        return switch (stackKind) {
            case Void -> SPIRVKind.OP_TYPE_VOID;
            case Boolean -> SPIRVKind.OP_TYPE_BOOL;
            case Char -> SPIRVKind.OP_TYPE_INT_8;
            case Byte -> SPIRVKind.OP_TYPE_INT_8;
            case Short -> SPIRVKind.OP_TYPE_INT_16;
            case Int -> SPIRVKind.OP_TYPE_INT_32;
            case Long -> SPIRVKind.OP_TYPE_INT_64;
            case Float -> SPIRVKind.OP_TYPE_FLOAT_32;
            case Double -> SPIRVKind.OP_TYPE_FLOAT_64;
            case Object ->
                // we return a 64-bit long value
                SPIRVKind.OP_TYPE_INT_64;
            default -> throw new TornadoRuntimeException("Java type not supported: " + stackKind);
        };
    }

    public static SPIRVKind fromResolvedJavaTypeToVectorKind(ResolvedJavaType type) {
        if (vectorTable.containsKey(type.getName())) {
            return vectorTable.get(type.getName());
        }
        return SPIRVKind.ILLEGAL;
    }

    public static SPIRVKind getKindFromStringClassVector(String vectorType) {
        return switch (vectorType) {
            case "Float2" -> SPIRVKind.OP_TYPE_VECTOR2_FLOAT_32;
            case "Float3" -> SPIRVKind.OP_TYPE_VECTOR3_FLOAT_32;
            case "Float4" -> SPIRVKind.OP_TYPE_VECTOR4_FLOAT_32;
            case "Float8" -> SPIRVKind.OP_TYPE_VECTOR8_FLOAT_32;
            case "Float16" -> SPIRVKind.OP_TYPE_VECTOR16_FLOAT_32;
            case "Int2" -> SPIRVKind.OP_TYPE_VECTOR2_INT_32;
            case "Int3" -> SPIRVKind.OP_TYPE_VECTOR3_INT_32;
            case "Int4" -> SPIRVKind.OP_TYPE_VECTOR4_INT_32;
            case "Int8" -> SPIRVKind.OP_TYPE_VECTOR8_INT_32;
            case "Int16" -> SPIRVKind.OP_TYPE_VECTOR16_INT_32;
            case "Double2" -> SPIRVKind.OP_TYPE_VECTOR2_FLOAT_64;
            case "Double3" -> SPIRVKind.OP_TYPE_VECTOR3_FLOAT_64;
            case "Double4" -> SPIRVKind.OP_TYPE_VECTOR4_FLOAT_64;
            case "Double8" -> SPIRVKind.OP_TYPE_VECTOR8_FLOAT_64;
            case "Double16" -> SPIRVKind.OP_TYPE_VECTOR16_FLOAT_64;
            case "Short2" -> SPIRVKind.OP_TYPE_VECTOR2_INT_16;
            case "Short3" -> SPIRVKind.OP_TYPE_VECTOR3_INT_16;
            case "Half2" -> SPIRVKind.OP_TYPE_VECTOR2_HALF_FLOAT;
            case "Half3" -> SPIRVKind.OP_TYPE_VECTOR3_HALF_FLOAT;
            case "Half4" -> SPIRVKind.OP_TYPE_VECTOR4_HALF_FLOAT;
            case "Half8" -> SPIRVKind.OP_TYPE_VECTOR8_HALF_FLOAT;
            case "Half16" -> SPIRVKind.OP_TYPE_VECTOR16_HALF_FLOAT;
            case "VectorFloat2", "VectorFloat3", "VectorFloat4", "VectorFloat8", "VectorFloat16", "VectorFloat" -> SPIRVKind.OP_TYPE_INT_64;
            default -> throw new TornadoRuntimeException("Vector type not supported: " + vectorType);
        };
    }

    @Override
    public Key getKey() {
        return key;
    }

    @Override
    public int getSizeInBytes() {
        return size;
    }

    public Class<?> getJavaClass() {
        guarantee(javaClass != null, "undefined java class for: %s", this);
        return javaClass;
    }

    @Override
    public int getVectorLength() {
        return vectorLength;
    }

    public SPIRVKind getElementKind() {
        return (isVector()) ? elementKind : ILLEGAL;
    }

    @Override
    public char getTypeChar() {
        return switch (kind) {
            case OP_TYPE_BOOL -> 'z';
            case OP_TYPE_INT_8 -> 'c';
            case OP_TYPE_INT_16 -> 's';
            case OP_TYPE_INT_32 -> 'i';
            case OP_TYPE_INT_64 -> 'l';
            case OP_TYPE_FLOAT_32 -> 'f';
            case OP_TYPE_FLOAT_64 -> 'd';
            case OP_TYPE_VECTOR2_INT_16, OP_TYPE_VECTOR2_INT_32, OP_TYPE_VECTOR2_INT_64, OP_TYPE_VECTOR3_INT_8, OP_TYPE_VECTOR3_INT_16, OP_TYPE_VECTOR3_INT_32, OP_TYPE_VECTOR3_INT_64, OP_TYPE_VECTOR4_INT_8, OP_TYPE_VECTOR4_INT_32, OP_TYPE_VECTOR4_INT_64, OP_TYPE_VECTOR8_INT_32, OP_TYPE_VECTOR8_INT_64, OP_TYPE_VECTOR2_FLOAT_16, OP_TYPE_VECTOR2_FLOAT_32, OP_TYPE_VECTOR2_FLOAT_64, OP_TYPE_VECTOR4_FLOAT_16, OP_TYPE_VECTOR4_FLOAT_32, OP_TYPE_VECTOR4_FLOAT_64, OP_TYPE_VECTOR8_FLOAT_16, OP_TYPE_VECTOR8_FLOAT_32, OP_TYPE_VECTOR8_FLOAT_64, OP_TYPE_VECTOR16_FLOAT_32, OP_TYPE_VECTOR16_INT_32, OP_TYPE_VECTOR16_FLOAT_64, OP_TYPE_VECTOR2_HALF_FLOAT, OP_TYPE_VECTOR3_HALF_FLOAT, OP_TYPE_VECTOR4_HALF_FLOAT, OP_TYPE_VECTOR8_HALF_FLOAT, OP_TYPE_VECTOR16_HALF_FLOAT -> 'v';
            default -> '-';
        };
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }

    public String getTypePrefix() {
        StringBuilder sb = new StringBuilder();
        if (isVector()) {
            sb.append('v');
            sb.append(getVectorLength());
        }
        if (isUnsigned()) {
            sb.append('u');
        }

        if (isVector()) {
            sb.append(getElementKind().getTypeChar());
        } else {
            sb.append(getTypeChar());
        }

        return sb.toString();
    }

    public boolean isUnsigned() {
        if (!isInteger()) {
            return false;
        } else {
            return kind.name().charAt(0) == 'U';
        }
    }

    private boolean isIntType() {
        return kind == OP_TYPE_INT_8 || kind == OP_TYPE_INT_16 || kind == OP_TYPE_INT_32 || kind == OP_TYPE_INT_64;
    }

    public boolean isInteger() {
        return ((kind != ILLEGAL) && isIntType());
    }

    public boolean isFloatingPoint() {
        return kind == OP_TYPE_FLOAT_32 || kind == OP_TYPE_FLOAT_64;
    }

    public boolean isVector() {
        return vectorLength > 1;
    }

    public boolean isPrimitive() {
        return (vectorLength == 1 && kind != SPIRVKind.ILLEGAL);
    }

    public JavaConstant getDefaultValue() {
        if (!isVector()) {
            return JavaConstant.defaultForKind(asJavaKind());
        }
        unimplemented();
        return JavaConstant.NULL_POINTER;
    }

    public int getByteCount() {
        return size;
    }

    public boolean isHalf() {
        if (kind == OP_TYPE_FLOAT_16 || kind == OP_TYPE_VECTOR2_HALF_FLOAT || kind == OP_TYPE_VECTOR3_HALF_FLOAT || kind == OP_TYPE_VECTOR4_HALF_FLOAT || kind == OP_TYPE_VECTOR8_HALF_FLOAT || kind == OP_TYPE_VECTOR16_HALF_FLOAT) {
            return true;
        }
        return false;
    }

    public JavaKind asJavaKind() {
        if (kind != ILLEGAL && !kind.isVector()) {
            switch (kind) {
                case OP_TYPE_VOID:
                    return JavaKind.Void;
                case OP_TYPE_BOOL:
                    return JavaKind.Boolean;
                case OP_TYPE_INT_8:
                    return JavaKind.Byte;
                case OP_TYPE_INT_16:
                    return JavaKind.Short;
                case OP_TYPE_INT_32:
                    return JavaKind.Int;
                case OP_TYPE_INT_64:
                    return JavaKind.Long;
                case OP_TYPE_FLOAT_32:
                    return JavaKind.Float;
                case OP_TYPE_FLOAT_64:
                    return JavaKind.Double;
                case OP_TYPE_FLOAT_16:
                    return JavaKind.Object;
                default:
                    shouldNotReachHere();
            }
        }
        return JavaKind.Illegal;
    }

}
