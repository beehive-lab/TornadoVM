/*
 * Copyright (c) 2020, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal.graal.lir;

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
import uk.ac.manchester.tornado.api.types.vectors.Short3;
import uk.ac.manchester.tornado.api.types.volumes.VolumeShort2;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler;

public enum MetalKind implements PlatformKind {

    // @formatter:off
    ATOMIC_ADD_INT(4, java.lang.Integer.TYPE),
    ATOMIC_ADD_FLOAT(4, java.lang.Float.TYPE),
    ATOMIC_SUB_INT(4, java.lang.Integer.TYPE),
    ATOMIC_MUL_INT(4, java.lang.Integer.TYPE),
	ATOMIC_ADD_LONG(8, java.lang.Long.TYPE),
    BOOL(1, java.lang.Boolean.TYPE),
    CHAR(1, java.lang.Byte.TYPE),
    UCHAR(1, null),
    SHORT(2, java.lang.Short.TYPE),
    USHORT(2, null),
    INT(4, java.lang.Integer.TYPE),
    UINT(4, null),
    LONG(8, java.lang.Long.TYPE),
    ULONG(8, null),
    HALF(2, java.lang.Short.TYPE),
    FLOAT(4, java.lang.Float.TYPE),
    DOUBLE(8, java.lang.Double.TYPE),
    CHAR2(2, null, CHAR),
    UCHAR2(2, null, UCHAR),
    SHORT2(2, Short2.TYPE, SHORT),
    USHORT2(2, null, USHORT),
    VOLUMESHORT2(2, VolumeShort2.TYPE, SHORT),
    INT2(2, Int2.TYPE, INT),
    UINT2(2, null, UINT),
    LONG2(2, null, LONG),
    ULONG2(2, null, ULONG),
    FLOAT2(2, Float2.TYPE, FLOAT),
    DOUBLE2(2, Double2.TYPE, DOUBLE),
    HALF2(2, Half2.TYPE, HALF),
    VECTORDOUBLE2(2, VectorDouble2.TYPE, DOUBLE),
    VECTORINT2(2, VectorInt2.TYPE, INT),
    VECTORFLOAT2(2, VectorFloat2.TYPE, FLOAT),
    VECTORHALF2(2, VectorHalf2.TYPE, HALF),

    CHAR3(3, Byte3.TYPE, CHAR),
    IMAGEBYTE3(3, ImageByte3.TYPE, CHAR),
    UCHAR3(3, null, UCHAR),
    SHORT3(3, Short3.TYPE, SHORT),
    USHORT3(3, null, USHORT),
    INT3(3, Int3.TYPE, INT),
    UINT3(3, null, UINT),
    LONG3(3, null, LONG),
    ULONG3(3, null, ULONG),
    FLOAT3(3, Float3.TYPE, FLOAT),
    DOUBLE3(3, Double3.TYPE, DOUBLE),
    HALF3(3, Half3.TYPE, HALF),
    VECTORDOUBLE3(3, VectorDouble3.TYPE, DOUBLE),
    VECTORINT3(3, VectorInt3.TYPE, INT),
    VECTORFLOAT3(3, VectorFloat3.TYPE, FLOAT),
    VECTORHALF3(3, VectorHalf3.TYPE, HALF),
    IMAGEFLOAT3(3, ImageFloat3.TYPE, FLOAT),
    CHAR4(4, Byte4.TYPE, CHAR),
    IMAGEBYTE4(4, ImageByte4.TYPE, CHAR),
    UCHAR4(4, null, UCHAR),
    SHORT4(4, null, SHORT),
    USHORT4(4, null, USHORT),
    INT4(4, Int4.TYPE, INT),
    UINT4(4, null, UINT),
    LONG4(4, null, LONG),
    ULONG4(4, null, ULONG),
    FLOAT4(4, Float4.TYPE, FLOAT),
    HALF4(4, Half4.TYPE, HALF),
    MATRIX2DFLOAT4(4, Matrix2DFloat4.TYPE, FLOAT),
    MATRIX3DFLOAT4(4, Matrix3DFloat4.TYPE, FLOAT),
    MATRIX4X4FLOAT(4, Matrix4x4Float.TYPE, FLOAT),
    IMAGEFLOAT4(4, ImageFloat4.TYPE, FLOAT),
    DOUBLE4(4, Double4.TYPE, DOUBLE),
    VECTORDOUBLE4(4, VectorDouble4.TYPE, DOUBLE),
    VECTORINT4(4, VectorInt4.TYPE, INT),
    VECTORFLOAT4(4, VectorFloat4.TYPE, FLOAT),
    VECTORHALF4(4, VectorHalf4.TYPE, HALF),
    CHAR8(8, null, CHAR),
    UCHAR8(8, null, UCHAR),
    SHORT8(8, null, SHORT),
    USHORT8(8, null, USHORT),
    INT8(8, Int8.TYPE, INT),
    UINT8(8, null, UINT),
    LONG8(8, null, LONG),
    ULONG8(8, null, ULONG),
    FLOAT8(8, Float8.TYPE, FLOAT),
    DOUBLE8(8, Double8.TYPE, DOUBLE),
    HALF8(8, Half8.TYPE, HALF),
    VECTORDOUBLE8(8, VectorDouble8.TYPE, DOUBLE),
    VECTORDOUBLE16(16, VectorDouble16.TYPE, DOUBLE),
    VECTORINT8(8, VectorInt8.TYPE, INT),
    VECTORINT16(16, VectorInt16.TYPE, INT),
    VECTORFLOAT8(8, VectorFloat8.TYPE, FLOAT),
    VECTORHALF8(8, VectorHalf8.TYPE, HALF),
    VECTORFLOAT16(16, VectorFloat16.TYPE, FLOAT),
    VECTORHALF16(16, VectorHalf16.TYPE, HALF),
    IMAGEFLOAT8(8, ImageFloat8.TYPE, FLOAT),
    CHAR16(16, null, CHAR),
    UCHAR16(16, null, UCHAR),
    SHORT16(16, null, SHORT),
    USHORT16(16, null, USHORT),
    INT16(16, Int16.TYPE, INT),
    UINT16(16, null, UINT),
    LONG16(16, null, LONG),
    ULONG16(16, null, ULONG),
    DOUBLE16(16, Double16.TYPE, DOUBLE),
    FLOAT16(16, Float16.TYPE, FLOAT),
    HALF16(16, Half16.TYPE, HALF),

    ILLEGAL(0, null),
    INTEGER_ATOMIC_JAVA(4, java.util.concurrent.atomic.AtomicInteger.class);
    // @formatter:on

    private final int size;
    private final int vectorLength;
    private final MetalKind kind;
    private final MetalKind elementKind;
    private final Class<?> javaClass;
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private final EnumKey key = new EnumKey(this);

    MetalKind(int size, Class<?> javaClass) {
        this(size, javaClass, null);
    }

    MetalKind(int size, Class<?> javaClass, MetalKind kind) {
        this.kind = this;
        this.javaClass = javaClass;
        this.elementKind = kind;
        this.size = (elementKind == null) ? size : elementKind.size * size;
        this.vectorLength = (elementKind == null) ? 1 : size;
    }

    public static MetalKind fromResolvedJavaType(ResolvedJavaType type) {
        if (!type.isArray()) {
            for (MetalKind k : MetalKind.values()) {
                if (k.javaClass != null && (k.javaClass.getSimpleName().equalsIgnoreCase(type.getJavaKind().name()) || k.javaClass.getSimpleName().equals(type.getUnqualifiedName()))) {
                    return k;
                }
            }
        }
        return ILLEGAL;
    }

    public static MetalKind fromResolvedJavaKind(JavaKind javaKind) {
        for (MetalKind k : MetalKind.values()) {
            if (k.javaClass != null && k.javaClass.getSimpleName().equalsIgnoreCase(javaKind.name())) {
                return k;
            }
        }
        return ILLEGAL;
    }

    public static MetalAssembler.MetalBinaryTemplate resolvePrivateTemplateType(JavaKind type) {
        if (type == JavaKind.Int) {
            return MetalAssembler.MetalBinaryTemplate.NEW_PRIVATE_INT_ARRAY;
        } else if (type == JavaKind.Double) {
            return MetalAssembler.MetalBinaryTemplate.NEW_PRIVATE_DOUBLE_ARRAY;
        } else if (type == JavaKind.Float) {
            return MetalAssembler.MetalBinaryTemplate.NEW_PRIVATE_FLOAT_ARRAY;
        } else if (type == JavaKind.Short) {
            return MetalAssembler.MetalBinaryTemplate.NEW_PRIVATE_SHORT_ARRAY;
        } else if (type == JavaKind.Long) {
            return MetalAssembler.MetalBinaryTemplate.NEW_PRIVATE_LONG_ARRAY;
        } else if (type == JavaKind.Char) {
            return MetalAssembler.MetalBinaryTemplate.NEW_PRIVATE_CHAR_ARRAY;
        } else if (type == JavaKind.Byte) {
            return MetalAssembler.MetalBinaryTemplate.NEW_PRIVATE_BYTE_ARRAY;
        }
        return null;
    }

    public static MetalAssembler.MetalBinaryTemplate resolvePrivatePointerTemplate(JavaKind type) {
        return switch (type) {
            case Int -> MetalAssembler.MetalBinaryTemplate.PRIVATE_INT_ARRAY_PTR;
            case Double -> MetalAssembler.MetalBinaryTemplate.PRIVATE_DOUBLE_ARRAY_PTR;
            case Float -> MetalAssembler.MetalBinaryTemplate.PRIVATE_FLOAT_ARRAY_PTR;
            case Short -> MetalAssembler.MetalBinaryTemplate.PRIVATE_SHORT_ARRAY_PTR;
            case Long -> MetalAssembler.MetalBinaryTemplate.PRIVATE_LONG_ARRAY_PTR;
            case Char -> MetalAssembler.MetalBinaryTemplate.PRIVATE_CHAR_ARRAY_PTR;
            case Byte -> MetalAssembler.MetalBinaryTemplate.PRIVATE_BYTE_ARRAY_PTR;
            default -> null;
        };
    }

    public static MetalAssembler.MetalBinaryTemplate resolvePrivatePointerCopyTemplate(JavaKind type) {
        return switch (type) {
            case Int -> MetalAssembler.MetalBinaryTemplate.PRIVATE_INT_ARRAY_PTR_COPY;
            case Double -> MetalAssembler.MetalBinaryTemplate.PRIVATE_DOUBLE_ARRAY_PTR_COPY;
            case Float -> MetalAssembler.MetalBinaryTemplate.PRIVATE_FLOAT_ARRAY_PTR_COPY;
            case Short -> MetalAssembler.MetalBinaryTemplate.PRIVATE_SHORT_ARRAY_PTR_COPY;
            case Long -> MetalAssembler.MetalBinaryTemplate.PRIVATE_LONG_ARRAY_PTR_COPY;
            case Char -> MetalAssembler.MetalBinaryTemplate.PRIVATE_CHAR_ARRAY_PTR_COPY;
            case Byte -> MetalAssembler.MetalBinaryTemplate.PRIVATE_BYTE_ARRAY_PTR_COPY;
            default -> null;
        };
    }

    public static MetalAssembler.MetalBinaryTemplate resolveTemplateType(JavaKind type) {
        if (type == JavaKind.Int) {
            return MetalAssembler.MetalBinaryTemplate.NEW_LOCAL_INT_ARRAY;
        } else if (type == JavaKind.Double) {
            return MetalAssembler.MetalBinaryTemplate.NEW_LOCAL_DOUBLE_ARRAY;
        } else if (type == JavaKind.Float) {
            return MetalAssembler.MetalBinaryTemplate.NEW_LOCAL_FLOAT_ARRAY;
        } else if (type == JavaKind.Short) {
            return MetalAssembler.MetalBinaryTemplate.NEW_LOCAL_SHORT_ARRAY;
        } else if (type == JavaKind.Long) {
            return MetalAssembler.MetalBinaryTemplate.NEW_LOCAL_LONG_ARRAY;
        } else if (type == JavaKind.Char) {
            return MetalAssembler.MetalBinaryTemplate.NEW_LOCAL_CHAR_ARRAY;
        } else if (type == JavaKind.Byte) {
            return MetalAssembler.MetalBinaryTemplate.NEW_LOCAL_BYTE_ARRAY;
        }
        return null;
    }

    public static MetalAssembler.MetalBinaryTemplate resolveTemplateType(ResolvedJavaType type) {
        return resolveTemplateType(type.getJavaKind());
    }

    public static MetalAssembler.MetalBinaryTemplate resolvePrivateTemplateType(ResolvedJavaType type) {
        return resolvePrivateTemplateType(type.getJavaKind());
    }

    public static MetalAssembler.MetalBinaryTemplate resolvePrivatePointerTemplate(ResolvedJavaType type) {
        return resolvePrivatePointerTemplate(type.getJavaKind());
    }

    public static MetalAssembler.MetalBinaryTemplate resolvePrivatePointerCopyTemplate(ResolvedJavaType type) {
        return resolvePrivatePointerCopyTemplate(type.getJavaKind());
    }

    public static MetalKind resolveToVectorKind(ResolvedJavaType type) {
        if (!type.isPrimitive() && type.getAnnotation(Vector.class) != null) {
            String typeName = type.getName();
            int index = typeName.lastIndexOf("/");
            String simpleName = typeName.substring(index + 1, typeName.length() - 1).toUpperCase();
            if (simpleName.startsWith("BYTE")) {
                simpleName = simpleName.replace("BYTE", "CHAR");
            }
            return MetalKind.valueOf(simpleName);
        }
        return MetalKind.ILLEGAL;
    }

    public static int lookupTypeIndex(MetalKind kind) {
        switch (kind) {
            case SHORT:
                return 0;
            case INT:
                return 1;
            case FLOAT:
                return 2;
            case CHAR:
                return 3;
            case DOUBLE:
                return 4;
            case HALF:
                return 5;
            default:
                return -1;
        }
    }

    public static int lookupLengthIndex(int length) {
        switch (length) {
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

    @Override
    public Key getKey() {
        return key;
    }

    @Override
    public int getSizeInBytes() {
        if (vectorLength == 3) {
            return size + elementKind.getSizeInBytes();
        }
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

    public MetalKind getElementKind() {
        return (isVector()) ? elementKind : ILLEGAL;
    }

    @Override
    public char getTypeChar() {

        switch (kind) {
            case BOOL:
                return 'z';
            case CHAR:
            case UCHAR:
                return 'c';
            case SHORT:
            case USHORT:
                return 's';
            case INT:
            case UINT:
            case ATOMIC_ADD_INT:
            case ATOMIC_SUB_INT:
            case ATOMIC_MUL_INT:
                return 'i';
            case LONG:
            case ULONG:
            case ATOMIC_ADD_LONG:
                return 'l';
            case HALF:
                return 'h';
            case FLOAT:
            case ATOMIC_ADD_FLOAT:
                return 'f';
            case DOUBLE:
                return 'd';
            case CHAR2:
            case UCHAR2:
            case SHORT2:
            case USHORT2:
            case INT2:
            case UINT2:
            case LONG2:
            case ULONG2:
            case FLOAT2:
            case DOUBLE2:
            case CHAR3:
            case UCHAR3:
            case SHORT3:
            case USHORT3:
            case INT3:
            case UINT3:
            case LONG3:
            case ULONG3:
            case FLOAT3:
            case DOUBLE3:
            case CHAR4:
            case UCHAR4:
            case SHORT4:
            case USHORT4:
            case INT4:
            case UINT4:
            case LONG4:
            case ULONG4:
            case FLOAT4:
            case DOUBLE4:
            case CHAR8:
            case UCHAR8:
            case SHORT8:
            case USHORT8:
            case INT8:
            case UINT8:
            case LONG8:
            case ULONG8:
            case FLOAT8:
            case DOUBLE8:
            case CHAR16:
            case UCHAR16:
            case SHORT16:
            case USHORT16:
            case INT16:
            case UINT16:
            case LONG16:
            case ULONG16:
            case FLOAT16:
            case DOUBLE16:
                return 'v';
            default:
                return '-';
        }
    }

    @Override
    public String toString() {
        if (this == MetalKind.ATOMIC_ADD_INT) {
            return "int";
        } else if (this == MetalKind.ATOMIC_SUB_INT) {
            return "int";
        } else if (this == MetalKind.ATOMIC_MUL_INT) {
            return "int";
        } else if (this == MetalKind.ATOMIC_ADD_LONG) {
            return "long";
        } else if (this == MetalKind.ATOMIC_ADD_FLOAT) {
            return "float";
        } else {
            return name().toLowerCase();
        }
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

    public boolean isInteger() {
        if (kind == ILLEGAL || isFloating()) {
            return false;
        }
        return true;
    }

    public boolean isFloating() {
        if (kind == FLOAT || kind == DOUBLE) {
            return true;
        }
        return false;
    }

    public boolean isHalf() {
        if (kind == HALF2 || kind == HALF3 || kind == HALF4 || kind == HALF8 || kind == HALF16) {
            return true;
        }
        return false;
    }

    public boolean isVector() {
        return vectorLength > 1;
    }

    public boolean isPrimitive() {
        return (vectorLength == 1 && kind != MetalKind.ILLEGAL);
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

    public final int lookupLengthIndex() {
        return lookupLengthIndex(getVectorLength());
    }

    public final int lookupTypeIndex() {
        return lookupTypeIndex(getElementKind());
    }

    public JavaKind asJavaKind() {
        if (kind != ILLEGAL && !kind.isVector()) {
            switch (kind) {
                case BOOL:
                    return JavaKind.Boolean;
                case CHAR:
                case UCHAR:
                    return JavaKind.Byte;
                case SHORT:
                case USHORT:
                    return JavaKind.Short;
                case HALF:
                    return JavaKind.Object;
                case INT:
                case UINT:
                    return JavaKind.Int;
                case LONG:
                case ULONG:
                    return JavaKind.Long;
                case FLOAT:
                    return JavaKind.Float;
                case DOUBLE:
                    return JavaKind.Double;
                default:
                    shouldNotReachHere();
            }
        }
        return JavaKind.Illegal;
    }
}
