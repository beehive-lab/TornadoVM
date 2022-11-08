/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
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
import uk.ac.manchester.tornado.api.type.annotations.Vector;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;

public enum PTXKind implements PlatformKind {
    // @formatter:off
    PRED(1, Boolean.TYPE),

    S8(1, Byte.TYPE),
    U8(1, null),
    B8(1, null),

    S16(2, Short.TYPE),
    F16(2, null),
    U16(2, Character.TYPE),
    B16(2, null),

    S32(4, Integer.TYPE),
    F32(4, Float.TYPE),
    U32(4, null),
    B32(4, null),

    S64(8, Long.TYPE),
    F64(8, Double.TYPE),
    U64(8, null),
    B64(8, null),

    CHAR2(2, null, U8),
    CHAR3(3, uk.ac.manchester.tornado.api.collections.types.Byte3.TYPE, U8),
    CHAR4(4, uk.ac.manchester.tornado.api.collections.types.Byte4.TYPE, U8),

    SHORT2(2, uk.ac.manchester.tornado.api.collections.types.Short2.TYPE, S16),

    INT2(2, uk.ac.manchester.tornado.api.collections.types.Int2.TYPE, S32),
    INT3(3, uk.ac.manchester.tornado.api.collections.types.Int3.TYPE, S32),
    INT4(4, uk.ac.manchester.tornado.api.collections.types.Int4.TYPE, S32),
    INT8(8, uk.ac.manchester.tornado.api.collections.types.Int8.TYPE, S32),

    FLOAT2(2, uk.ac.manchester.tornado.api.collections.types.Float2.TYPE, F32),
    FLOAT3(3, uk.ac.manchester.tornado.api.collections.types.Float3.TYPE, F32),
    FLOAT4(4, uk.ac.manchester.tornado.api.collections.types.Float4.TYPE, F32),
    FLOAT8(8, uk.ac.manchester.tornado.api.collections.types.Float8.TYPE, F32),

    DOUBLE2(2, uk.ac.manchester.tornado.api.collections.types.Double2.TYPE, F64),
    DOUBLE3(3, uk.ac.manchester.tornado.api.collections.types.Double3.TYPE, F64),
    DOUBLE4(4, uk.ac.manchester.tornado.api.collections.types.Double4.TYPE, F64),
    DOUBLE8(8, uk.ac.manchester.tornado.api.collections.types.Double8.TYPE, F64),

    ILLEGAL(0, null);
    // @formatter:on

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
                case B16:
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
                default:
                    shouldNotReachHere();
            }
            return JavaKind.Illegal;
        }
    }

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

    public static PTXAssembler.PTXBinaryTemplate resolveTemplateType(ResolvedJavaType type) {
        return resolveTemplateType(type.getJavaKind());
    }

    public static PTXAssembler.PTXBinaryTemplate resolveTemplateType(JavaKind type) {
        if (type == JavaKind.Int) {
            return PTXAssembler.PTXBinaryTemplate.NEW_SHARED_INT_ARRAY;
        } else if (type == JavaKind.Double) {
            return PTXAssembler.PTXBinaryTemplate.NEW_SHARED_DOUBLE_ARRAY;
        } else if (type == JavaKind.Float) {
            return PTXAssembler.PTXBinaryTemplate.NEW_SHARED_FLOAT_ARRAY;
        } else if (type == JavaKind.Short) {
            return PTXAssembler.PTXBinaryTemplate.NEW_SHARED_SHORT_ARRAY;
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

    public JavaConstant getDefaultValue() {
        if (!isVector()) {
            return JavaConstant.defaultForKind(asJavaKind());
        }
        unimplemented();
        return JavaConstant.NULL_POINTER;
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

    public PTXKind getElementKind() {
        return (isVector()) ? elementKind : ILLEGAL;
    }

    public boolean isInteger() {
        boolean vectorInteger = kind.isVector() && kind.elementKind.isInteger();
        return vectorInteger || kind == S8 || kind == U8 || kind == S16 || kind == U16 || kind == S32 || kind == U32 || kind == S64 || kind == U64;
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

    public boolean isF16() {
        return kind == F16;
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
