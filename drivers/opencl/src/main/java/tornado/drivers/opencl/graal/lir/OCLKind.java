/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.drivers.opencl.graal.lir;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import tornado.api.Vector;

import static tornado.common.exceptions.TornadoInternalError.*;

public enum OCLKind implements PlatformKind {

    BOOL(1, java.lang.Boolean.TYPE),
    CHAR(1, java.lang.Byte.TYPE),
    UCHAR(1, null),
    SHORT(2, java.lang.Short.TYPE),
    USHORT(2, null),
    INT(4, java.lang.Integer.TYPE),
    UINT(4, null),
    LONG(8, java.lang.Long.TYPE),
    ULONG(8, null),
    HALF(2, null),
    FLOAT(4, java.lang.Float.TYPE),
    DOUBLE(8, java.lang.Double.TYPE),
    CHAR2(2, null, CHAR),
    UCHAR2(2, null, UCHAR),
    SHORT2(2, tornado.collections.types.Short2.TYPE, SHORT),
    USHORT2(2, null, USHORT),
    INT2(2, tornado.collections.types.Int2.TYPE, INT),
    UINT2(2, null, UINT),
    LONG2(2, null, LONG),
    ULONG2(2, null, ULONG),
    FLOAT2(2, tornado.collections.types.Float2.TYPE, FLOAT),
    DOUBLE2(2, null, DOUBLE),
    CHAR3(3, tornado.collections.types.Byte3.TYPE, CHAR),
    UCHAR3(3, null, UCHAR),
    SHORT3(3, tornado.collections.types.Short3.TYPE, SHORT),
    USHORT3(3, null, USHORT),
    INT3(3, tornado.collections.types.Int3.TYPE, INT),
    UINT3(3, null, UINT),
    LONG3(3, null, LONG),
    ULONG3(3, null, ULONG),
    FLOAT3(3, tornado.collections.types.Float3.TYPE, FLOAT),
    DOUBLE3(3, null, DOUBLE),
    CHAR4(4, tornado.collections.types.Byte4.TYPE, CHAR),
    UCHAR4(4, null, UCHAR),
    SHORT4(4, null, SHORT),
    USHORT4(4, null, USHORT),
    INT4(4, tornado.collections.types.Int4.TYPE, INT),
    UINT4(4, null, UINT),
    LONG4(4, null, LONG),
    ULONG4(4, null, ULONG),
    FLOAT4(4, tornado.collections.types.Float4.TYPE, FLOAT),
    DOUBLE4(4, null, DOUBLE),
    CHAR8(8, null, CHAR),
    UCHAR8(8, null, UCHAR),
    SHORT8(8, null, SHORT),
    USHORT8(8, null, USHORT),
    INT8(8, null, INT),
    UINT8(8, null, UINT),
    LONG8(8, null, LONG),
    ULONG8(8, null, ULONG),
    FLOAT8(8, tornado.collections.types.Float8.TYPE, FLOAT),
    DOUBLE8(8, null, DOUBLE),
    CHAR16(16, null, CHAR),
    UCHAR16(16, null, UCHAR),
    SHORT16(16, null, SHORT),
    USHORT16(16, null, USHORT),
    INT16(16, null, INT),
    UINT16(16, null, UINT),
    LONG16(16, null, LONG),
    ULONG16(16, null, ULONG),
    FLOAT16(16, null, FLOAT),
    DOUBLE16(16, null, DOUBLE),
    ILLEGAL(0, null);

    public static OCLKind fromResolvedJavaType(ResolvedJavaType type) {
        if (!type.isArray()) {
            for (OCLKind k : OCLKind.values()) {
                if (k.javaClass != null && k.javaClass.getSimpleName().equals(type.getUnqualifiedName())) {
                    return k;
                }
            }
        }
        return ILLEGAL;
    }

    public static OCLKind fromClass(Class<?> type) {
        if (!type.isArray()) {
            for (OCLKind k : OCLKind.values()) {
                if (k.javaClass != null && k.javaClass.getSimpleName().equals(type.getSimpleName())) {
                    return k;
                }
            }
        }
        return ILLEGAL;
    }

    private final int size;
    private final int vectorLength;

    private final OCLKind kind;
    private final OCLKind elementKind;
    private final Class<?> javaClass;
    private final EnumKey key = new EnumKey(this);

    OCLKind(int size, Class<?> javaClass) {
        this(size, javaClass, null);
    }

    OCLKind(int size, Class<?> javaClass, OCLKind kind) {
        this.kind = this;
        this.javaClass = javaClass;
        this.elementKind = kind;
        this.size = (elementKind == null) ? size : elementKind.size * size;
        this.vectorLength = (elementKind == null) ? 1 : size;
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

    public OCLKind getElementKind() {
        return (isVector()) ? elementKind : ILLEGAL;
    }

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
                return 'i';
            case LONG:
            case ULONG:
                return 'l';
            case HALF:
                return 'h';
            case FLOAT:
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

    public boolean isInteger() {
        // TODO are vectors integers?
        if (kind == ILLEGAL || isFloating()) {
            return false;
        }
        return true;
    }

    public boolean isFloating() {
        // TODO are vectors integers?
        if (kind == FLOAT || kind == DOUBLE) {
            return true;
        }
        return false;
    }

    public boolean isVector() {
        return vectorLength > 1;
    }

    public boolean isPrimitive() {
        return (vectorLength == 1 && kind != OCLKind.ILLEGAL);
    }

    public JavaConstant getDefaultValue() {
        if (!isVector()) {
            return JavaConstant.defaultForKind(asJavaKind());
        }
        unimplemented();
        return JavaConstant.NULL_POINTER;
    }

    public static final OCLKind resolveToVectorKind(ResolvedJavaType type) {
        if (!type.isPrimitive() && type.getAnnotation(Vector.class) != null) {

            String typeName = type.getName();
            int index = typeName.lastIndexOf("/");
            String simpleName = typeName.substring(index + 1, typeName.length() - 1).toUpperCase();
            if (simpleName.startsWith("BYTE")) {
                simpleName = simpleName.replace("BYTE", "CHAR");
            }
            return OCLKind.valueOf(simpleName);
        }
        return OCLKind.ILLEGAL;
    }

    public int getByteCount() {
        return size;
    }

    public final static int lookupTypeIndex(OCLKind kind) {
        switch (kind) {
            case SHORT:
                return 0;
            case INT:
                return 1;
            case FLOAT:
                return 2;
            case CHAR:
                return 3;
            default:
                return -1;
        }
    }

    public final int lookupLengthIndex() {
        return lookupLengthIndex(getVectorLength());
    }

    public final int lookupTypeIndex() {
        return lookupTypeIndex(getElementKind());
    }

    public final static int lookupLengthIndex(int length) {
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

    public JavaKind asJavaKind() {
        if (kind == ILLEGAL || kind.isVector()) {
            return JavaKind.Illegal;
        } else {
            switch (kind) {
                case BOOL:
                    return JavaKind.Boolean;
                case CHAR:
                case UCHAR:
                    return JavaKind.Byte;
                case SHORT:
                case USHORT:
                    return JavaKind.Short;
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
            return JavaKind.Illegal;
        }
    }
}
