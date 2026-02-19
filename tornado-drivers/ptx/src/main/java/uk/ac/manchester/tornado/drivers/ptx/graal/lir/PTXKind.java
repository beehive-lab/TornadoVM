/*
 * Copyright (c) 2020, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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

package uk.ac.manchester.tornado.drivers.ptx.graal.lir;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble16;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble2;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble3;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble4;
import uk.ac.manchester.tornado.api.types.collections.VectorDouble8;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat16;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat2;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat3;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat4;
import uk.ac.manchester.tornado.api.types.collections.VectorFloat8;
import uk.ac.manchester.tornado.api.types.collections.VectorHalf16;
import uk.ac.manchester.tornado.api.types.collections.VectorHalf2;
import uk.ac.manchester.tornado.api.types.collections.VectorHalf3;
import uk.ac.manchester.tornado.api.types.collections.VectorHalf4;
import uk.ac.manchester.tornado.api.types.collections.VectorHalf8;
import uk.ac.manchester.tornado.api.types.collections.VectorInt16;
import uk.ac.manchester.tornado.api.types.collections.VectorInt2;
import uk.ac.manchester.tornado.api.types.collections.VectorInt3;
import uk.ac.manchester.tornado.api.types.collections.VectorInt4;
import uk.ac.manchester.tornado.api.types.collections.VectorInt8;
import uk.ac.manchester.tornado.api.types.images.ImageByte3;
import uk.ac.manchester.tornado.api.types.images.ImageByte4;
import uk.ac.manchester.tornado.api.types.images.ImageFloat3;
import uk.ac.manchester.tornado.api.types.images.ImageFloat4;
import uk.ac.manchester.tornado.api.types.images.ImageFloat8;
import uk.ac.manchester.tornado.api.types.matrix.Matrix2DFloat4;
import uk.ac.manchester.tornado.api.types.matrix.Matrix3DFloat4;
import uk.ac.manchester.tornado.api.types.matrix.Matrix4x4Float;
import uk.ac.manchester.tornado.api.types.vectors.Byte3;
import uk.ac.manchester.tornado.api.types.vectors.Byte4;
import uk.ac.manchester.tornado.api.types.vectors.Double16;
import uk.ac.manchester.tornado.api.types.vectors.Double2;
import uk.ac.manchester.tornado.api.types.vectors.Double3;
import uk.ac.manchester.tornado.api.types.vectors.Double4;
import uk.ac.manchester.tornado.api.types.vectors.Double8;
import uk.ac.manchester.tornado.api.types.vectors.Float16;
import uk.ac.manchester.tornado.api.types.vectors.Float2;
import uk.ac.manchester.tornado.api.types.vectors.Float3;
import uk.ac.manchester.tornado.api.types.vectors.Float4;
import uk.ac.manchester.tornado.api.types.vectors.Float8;
import uk.ac.manchester.tornado.api.types.vectors.Half16;
import uk.ac.manchester.tornado.api.types.vectors.Half2;
import uk.ac.manchester.tornado.api.types.vectors.Half3;
import uk.ac.manchester.tornado.api.types.vectors.Half4;
import uk.ac.manchester.tornado.api.types.vectors.Half8;
import uk.ac.manchester.tornado.api.types.vectors.Int16;
import uk.ac.manchester.tornado.api.types.vectors.Int2;
import uk.ac.manchester.tornado.api.types.vectors.Int3;
import uk.ac.manchester.tornado.api.types.vectors.Int4;
import uk.ac.manchester.tornado.api.types.vectors.Int8;
import uk.ac.manchester.tornado.api.types.vectors.Short2;
import uk.ac.manchester.tornado.api.types.volumes.VolumeShort2;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;

public enum PTXKind implements PlatformKind {
    // @formatter:off
    PRED(1, Boolean.TYPE),

    S8(1, Byte.TYPE),
    U8(1, null),
    B8(1, null),

    S16(2, Short.TYPE),
    F16(2, Short.TYPE),
    U16(2, Character.TYPE),
    B16(2, Short.TYPE),

    S32(4, Integer.TYPE),
    F32(4, Float.TYPE),
    U32(4, Integer.TYPE),
    B32(4, null),

    S64(8, Long.TYPE),
    F64(8, Double.TYPE),
    U64(8, null),
    B64(8, null),

    CHAR2(2, null, U8),
    CHAR3(3, Byte3.TYPE, U8),
    IMAGEBYTE3(3, ImageByte3.TYPE, U8),
    IMAGEBYTE4(4, ImageByte4.TYPE, U8),
    CHAR4(4, Byte4.TYPE, U8),

    SHORT2(2, Short2.TYPE, S16),
    VOLUMESHORT2(2, VolumeShort2.TYPE, S16),

    INT2(2, Int2.TYPE, S32),
    INT3(3, Int3.TYPE, S32),
    INT4(4, Int4.TYPE, S32),
    INT8(8, Int8.TYPE, S32),
    INT16(16, Int16.TYPE, S32),
    VECTORINT2(2, VectorInt2.TYPE, S32),
    VECTORINT3(3, VectorInt3.TYPE, S32),
    VECTORINT4(4, VectorInt4.TYPE, S32),
    VECTORINT8(8, VectorInt8.TYPE, S32),
    VECTORINT16(16, VectorInt16.TYPE, S32),
    FLOAT2(2, Float2.TYPE, F32),
    FLOAT3(3, Float3.TYPE, F32),
    FLOAT4(4, Float4.TYPE, F32),
    FLOAT8(8, Float8.TYPE, F32),
    FLOAT16(16, Float16.TYPE, F32),
    VECTORFLOAT2(2, VectorFloat2.TYPE, F32),
    VECTORFLOAT3(3, VectorFloat3.TYPE, F32),
    VECTORFLOAT4(4, VectorFloat4.TYPE, F32),
    VECTORFLOAT8(8, VectorFloat8.TYPE, F32),
    VECTORFLOAT16(16, VectorFloat16.TYPE, F32),
    MATRIX2DFLOAT4(4, Matrix2DFloat4.TYPE, F32),
    MATRIX3DFLOAT4(4, Matrix3DFloat4.TYPE, F32),
    MATRIX4X4FLOAT(4, Matrix4x4Float.TYPE, F32),
    IMAGEFLOAT3(3, ImageFloat3.TYPE, F32),
    IMAGEFLOAT4(4, ImageFloat4.TYPE, F32),
    IMAGEFLOAT8(8, ImageFloat8.TYPE, F32),
    DOUBLE2(2, Double2.TYPE, F64),
    DOUBLE3(3, Double3.TYPE, F64),
    DOUBLE4(4, Double4.TYPE, F64),
    DOUBLE8(8, Double8.TYPE, F64),
    DOUBLE16(16, Double16.TYPE, F64),
    VECTORDOUBLE2(2, VectorDouble2.TYPE, F64),
    VECTORDOUBLE3(3, VectorDouble3.TYPE, F64),
    VECTORDOUBLE4(4, VectorDouble4.TYPE, F64),
    VECTORDOUBLE8(8, VectorDouble8.TYPE, F64),
    VECTORDOUBLE16(16, VectorDouble16.TYPE, F64),
    HALF2(2, Half2.TYPE, B16),
    HALF3(3, Half3.TYPE, B16),
    HALF4(4, Half4.TYPE, B16),
    HALF8(8, Half8.TYPE, B16),
    HALF16(16, Half16.TYPE, B16),
    VECTORHALF2(2, VectorHalf2.TYPE, B16),
    VECTORHALF3(3, VectorHalf3.TYPE, B16),
    VECTORHALF4(4, VectorHalf4.TYPE, B16),
    VECTORHALF8(8, VectorHalf8.TYPE, B16),
    VECTORHALF16(16, VectorHalf16.TYPE, B16),
    ILLEGAL(0, null);
    // @formatter:on

    private final int size;
    private final int vectorLength;
    private final PTXKind kind;
    private final PTXKind elementKind;
    private final Class<?> javaClass;

    PTXKind(int size, Class<?> javaClass) {
        this(size, javaClass, null);
    }

    PTXKind(int size, Class<?> javaClass, PTXKind kind) {
        this.kind = this;
        this.javaClass = javaClass;
        this.elementKind = kind;
        this.size = (elementKind == null) ? size : elementKind.size * size;
        this.vectorLength = (elementKind == null) ? 1 : size;
    }

    public static PTXKind fromResolvedJavaType(ResolvedJavaType elementType) {
        if (!elementType.isArray()) {
            for (PTXKind k : PTXKind.values()) {
                if (k.javaClass != null && (k.javaClass.getSimpleName().equalsIgnoreCase(elementType.getJavaKind().name()) || k.javaClass.getSimpleName().equals(elementType.getUnqualifiedName()))) {
                    return k;
                }
            }
        }
        return ILLEGAL;
    }

    public static PTXKind fromResolvedJavaKind(JavaKind javaKind) {
        for (PTXKind k : PTXKind.values()) {
            if (k.javaClass != null && k.javaClass.getSimpleName().equalsIgnoreCase(javaKind.name())) {
                return k;
            }
        }
        return ILLEGAL;
    }

    public static PTXAssembler.PTXBinaryTemplate resolveTemplateType(ResolvedJavaType type, PTXKind kind) {
        return resolveTemplateType(type.getJavaKind(), kind);
    }

    public static PTXAssembler.PTXBinaryTemplate resolveTemplateType(JavaKind type, PTXKind kind) {
        if (type == JavaKind.Int) {
            return PTXAssembler.PTXBinaryTemplate.NEW_SHARED_INT_ARRAY;
        } else if (type == JavaKind.Double) {
            return PTXAssembler.PTXBinaryTemplate.NEW_SHARED_DOUBLE_ARRAY;
        } else if (type == JavaKind.Float) {
            return PTXAssembler.PTXBinaryTemplate.NEW_SHARED_FLOAT_ARRAY;
        } else if (type == JavaKind.Short) {
            if (kind == PTXKind.F16) {
                return PTXAssembler.PTXBinaryTemplate.NEW_SHARED_HALF_FLOAT_ARRAY;
            } else {
                return PTXAssembler.PTXBinaryTemplate.NEW_SHARED_SHORT_ARRAY;
            }
        } else if (type == JavaKind.Long) {
            return PTXAssembler.PTXBinaryTemplate.NEW_SHARED_LONG_ARRAY;
        } else if (type == JavaKind.Char) {
            return PTXAssembler.PTXBinaryTemplate.NEW_SHARED_CHAR_ARRAY;
        } else if (type == JavaKind.Byte) {
            return PTXAssembler.PTXBinaryTemplate.NEW_SHARED_BYTE_ARRAY;
        }
        return null;
    }

    public static PTXAssembler.PTXBinaryTemplate resolvePrivateTemplateType(ResolvedJavaType type) {
        return resolvePrivateTemplateType(type.getJavaKind());
    }

    public static PTXAssembler.PTXBinaryTemplate resolvePrivateTemplateType(JavaKind type) {
        if (type == JavaKind.Int) {
            return PTXAssembler.PTXBinaryTemplate.NEW_LOCAL_INT_ARRAY;
        } else if (type == JavaKind.Double) {
            return PTXAssembler.PTXBinaryTemplate.NEW_LOCAL_DOUBLE_ARRAY;
        } else if (type == JavaKind.Float) {
            return PTXAssembler.PTXBinaryTemplate.NEW_LOCAL_FLOAT_ARRAY;
        } else if (type == JavaKind.Short) {
            return PTXAssembler.PTXBinaryTemplate.NEW_LOCAL_SHORT_ARRAY;
        } else if (type == JavaKind.Long) {
            return PTXAssembler.PTXBinaryTemplate.NEW_LOCAL_LONG_ARRAY;
        } else if (type == JavaKind.Char) {
            return PTXAssembler.PTXBinaryTemplate.NEW_LOCAL_CHAR_ARRAY;
        } else if (type == JavaKind.Byte) {
            return PTXAssembler.PTXBinaryTemplate.NEW_LOCAL_BYTE_ARRAY;
        }
        return null;
    }

    public static PTXAssembler.PTXBinaryTemplate resolvePrivatePointerCopyTemplate(ResolvedJavaType type) {
        return resolvePrivatePointerCopyTemplate(type.getJavaKind());
    }

    public static PTXAssembler.PTXBinaryTemplate resolvePrivatePointerCopyTemplate(JavaKind type) {
        return switch (type) {
            case Int -> PTXAssembler.PTXBinaryTemplate.LOCAL_INT_ARRAY_PTR_COPY;
            case Double -> PTXAssembler.PTXBinaryTemplate.LOCAL_DOUBLE_ARRAY_PTR_COPY;
            case Float -> PTXAssembler.PTXBinaryTemplate.LOCAL_FLOAT_ARRAY_PTR_COPY;
            case Short -> PTXAssembler.PTXBinaryTemplate.LOCAL_SHORT_ARRAY_PTR_COPY;
            case Long -> PTXAssembler.PTXBinaryTemplate.LOCAL_LONG_ARRAY_PTR_COPY;
            case Char -> PTXAssembler.PTXBinaryTemplate.LOCAL_CHAR_ARRAY_PTR_COPY;
            case Byte -> PTXAssembler.PTXBinaryTemplate.LOCAL_BYTE_ARRAY_PTR_COPY;
            default -> null;
        };
    }

    public static PTXKind resolveToVectorKind(ResolvedJavaType type) {
        if (!type.isPrimitive() && type.getAnnotation(Vector.class) != null) {
            String typeName = type.getName();
            int index = typeName.lastIndexOf("/");
            String simpleName = typeName.substring(index + 1, typeName.length() - 1).toUpperCase();
            if (simpleName.startsWith("BYTE")) {
                simpleName = simpleName.replace("BYTE", "CHAR");
            }
            return PTXKind.valueOf(simpleName);
        }
        return PTXKind.ILLEGAL;
    }

    private static int lookupTypeIndex(PTXKind kind) {
        switch (kind) {
            case S16:
                return 0;
            case S32:
                return 1;
            case F32:
                return 2;
            case U8:
                return 3;
            case F64:
                return 4;
            default:
                return -1;
        }
    }

    public JavaKind asJavaKind() {
        if (kind == ILLEGAL || kind.isVector()) {
            return JavaKind.Illegal;
        } else {
            switch (kind) {
                case U8:
                case S8:
                case B8:
                    return JavaKind.Byte;
                case S16:
                case U16:
                case F16:
                    return JavaKind.Short;
                case S32:
                case U32:
                case B32:
                    return JavaKind.Int;
                case S64:
                case U64:
                case B64:
                    return JavaKind.Long;
                case F32:
                    return JavaKind.Float;
                case F64:
                    return JavaKind.Double;
                case B16:
                    return JavaKind.Object;
                default:
                    shouldNotReachHere();
            }
            return JavaKind.Illegal;
        }
    }

    public JavaConstant getDefaultValue() {
        if (!isVector()) {
            return JavaConstant.defaultForKind(asJavaKind());
        }
        unimplemented();
        return JavaConstant.NULL_POINTER;
    }

    @Override
    public Key getKey() {
        return null;
    }

    @Override
    public int getSizeInBytes() {
        return size;
    }

    @Override
    public int getVectorLength() {
        return vectorLength;
    }

    public String getRegisterTypeString() {
        return "r" + getTypeChar() + getSizeChar();
    }

    public char getSizeChar() {
        int actualSize = elementKind == null ? size : elementKind.size;
        switch (actualSize) {
            case 1:
                return 'b';
            case 2:
                return 'h';
            case 4:
                return 'i';
            case 8:
                return 'd';
            default:
                shouldNotReachHere("size = " + actualSize);
        }
        return 0;
    }

    @Override
    public char getTypeChar() {
        if (this == PTXKind.PRED) {
            return 'p';
        }
        if (isFloating()) {
            return 'f';
        }
        if (isInteger()) {
            if (isUnsigned()) {
                return 'u';
            }
            return 's';
        }
        return 'b';
    }

    private boolean isUnsigned() {
        boolean vectorUnsigned = kind.isVector() && kind.elementKind.isUnsigned();
        if (vectorUnsigned) {
            return true;
        }

        switch (kind) {
            case U8:
            case U16:
            case U32:
            case U64:
                return true;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }

    public Class<?> getJavaClass() {
        guarantee(javaClass != null, "undefined java class for: %s", this);
        return javaClass;
    }

    public boolean isVector() {
        return vectorLength > 1;
    }

    public boolean isPrimitive() {
        return vectorLength == 1 && kind != PTXKind.ILLEGAL;
    }

    public int lookupLengthIndex() {
        return lookupLengthIndex(getVectorLength());
    }

    private int lookupLengthIndex(int vectorLength) {
        switch (vectorLength) {
            case 2:
                return 0;
            case 3:
                return 1;
            case 4:
                return 2;
            case 8:
                return 3;
            case 16:
                return 4;
            default:
                return -1;
        }
    }

    public final int lookupTypeIndex() {
        return lookupTypeIndex(getElementKind());
    }

    public PTXKind getElementKind() {
        return (isVector()) ? elementKind : ILLEGAL;
    }

    public boolean isInteger() {
        boolean vectorInteger = kind.isVector() && kind.elementKind.isInteger();
        return vectorInteger || kind == S8 || kind == U8 || kind == S16 || kind == U16 || kind == S32 || kind == U32 || kind == S64 || kind == U64;
    }

    public boolean isHalf() {
        if (kind == HALF2 || kind == HALF3 || kind == HALF4 || kind == HALF8 || kind == HALF16) {
            return true;
        }
        return false;
    }

    public boolean isFloating() {
        boolean vectorFloating = kind.isVector() && kind.elementKind.isFloating();
        return vectorFloating || kind == F16 || kind == F32 || kind == F64;
    }

    public boolean is8Bit() {
        return kind == U8 || kind == S8 || kind == B8;
    }

    public boolean isF64() {
        return kind == F64;
    }

    public boolean isF32() {
        return kind == F32;
    }

    public boolean isU32() {
        return kind == U32;
    }

    public boolean isF16() {
        return kind == F16;
    }

    public boolean isB16() {
        return kind == B16;
    }

    public boolean isU64() {
        return kind == U64;
    }

    public boolean isS32() {
        return kind == S32;
    }

    public boolean isS64() {
        return kind == S64;
    }

    public boolean is64Bit() {
        return size == 8 && !isVector();
    }

    public PTXKind toUntyped() {
        switch (size) {
            case 1:
                return this == PRED ? PTXKind.PRED : PTXKind.B8;
            case 2:
                return PTXKind.B16;
            case 4:
                return PTXKind.B32;
            case 8:
                return PTXKind.B64;
            default:
                shouldNotReachHere("can't cast to untyped: " + this);
                return null;
        }
    }
}
