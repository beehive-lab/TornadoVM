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
import uk.ac.manchester.tornado.api.type.annotations.Vector;

/**
 * SPIR-V Types:
 * https://www.khronos.org/registry/spir-v/specs/unified1/SPIRV.html#_types
 * 
 * Compatible with Level Zero:
 * https://spec.oneapi.com/level-zero/latest/core/SPIRV.html
 * 
 * Note: Floating-point types are represented and stored using IEEE-754
 * semantics. All integer formats are represented and stored using
 * 2’s-complement format. 2’s-complement format.
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
    //OP_TYPE_VECTOR2_INT_8(2, uk.ac.manchester.tornado.api.collections.types.Byte2.TYPE, OP_TYPE_INT_8),
    OP_TYPE_VECTOR2_INT_16(2, uk.ac.manchester.tornado.api.collections.types.Short2.TYPE, OP_TYPE_INT_16),
    OP_TYPE_VECTOR2_INT_32(2, uk.ac.manchester.tornado.api.collections.types.Int2.TYPE, OP_TYPE_INT_32),
    OP_TYPE_VECTOR2_INT_64(2, uk.ac.manchester.tornado.api.collections.types.Int2.TYPE, OP_TYPE_INT_64),

    // OP_TYPE_VECTOR 3
    OP_TYPE_VECTOR3_INT_8(3, uk.ac.manchester.tornado.api.collections.types.Byte3.TYPE, OP_TYPE_INT_8),
    OP_TYPE_VECTOR3_INT_16(3, uk.ac.manchester.tornado.api.collections.types.Short3.TYPE, OP_TYPE_INT_16),
    OP_TYPE_VECTOR3_INT_32(3, uk.ac.manchester.tornado.api.collections.types.Int3.TYPE, OP_TYPE_INT_32),
    OP_TYPE_VECTOR3_INT_64(3, uk.ac.manchester.tornado.api.collections.types.Int3.TYPE, OP_TYPE_INT_64),

    // OP_TYPE_VECTOR 4
    OP_TYPE_VECTOR4_INT_8(4, uk.ac.manchester.tornado.api.collections.types.Byte4.TYPE, OP_TYPE_INT_8),
    //OP_TYPE_VECTOR4_INT_16(4, uk.ac.manchester.tornado.api.collections.types.Short4.TYPE, OP_TYPE_INT_16),
    OP_TYPE_VECTOR4_INT_32(4, uk.ac.manchester.tornado.api.collections.types.Int4.TYPE, OP_TYPE_INT_32),
    OP_TYPE_VECTOR4_INT_64(4, uk.ac.manchester.tornado.api.collections.types.Int4.TYPE, OP_TYPE_INT_64),

    // OP_TYPE_VECTOR 8
    //OP_TYPE_VECTOR8_INT_8(8, uk.ac.manchester.tornado.api.collections.types.Byte8.TYPE, OP_TYPE_INT_8),
    //OP_TYPE_VECTOR8_INT_16(8, uk.ac.manchester.tornado.api.collections.types.Short8.TYPE, OP_TYPE_INT_16),
    OP_TYPE_VECTOR8_INT_32(8, uk.ac.manchester.tornado.api.collections.types.Int8.TYPE, OP_TYPE_INT_32),
    OP_TYPE_VECTOR8_INT_64(8, uk.ac.manchester.tornado.api.collections.types.Int8.TYPE, OP_TYPE_INT_64),

    // OP_TYPE_VECTOR 16
//    OP_TYPE_VECTOR16_INT_8(16, uk.ac.manchester.tornado.api.collections.types.Byte8.TYPE, OP_TYPE_INT_8),
//    OP_TYPE_VECTOR16_INT_16(16, uk.ac.manchester.tornado.api.collections.types.Short8.TYPE, OP_TYPE_INT_16),
//    OP_TYPE_VECTOR16_INT_32(16, uk.ac.manchester.tornado.api.collections.types.Int1.TYPE, OP_TYPE_INT_32),
//    OP_TYPE_VECTOR16_INT_64(16, uk.ac.manchester.tornado.api.collections.types.Int8.TYPE, OP_TYPE_INT_64),

    // OP_TYPE_VECTOR2 Float
    OP_TYPE_VECTOR2_FLOAT_16(2, uk.ac.manchester.tornado.api.collections.types.Float2.TYPE, OP_TYPE_FLOAT_16),  // Half float
    OP_TYPE_VECTOR2_FLOAT_32(2, uk.ac.manchester.tornado.api.collections.types.Float2.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_VECTOR2_FLOAT_64(2, uk.ac.manchester.tornado.api.collections.types.Double2.TYPE, OP_TYPE_FLOAT_64),

    // OP_TYPE_VECTOR3 Float
    OP_TYPE_VECTOR3_FLOAT_16(3, uk.ac.manchester.tornado.api.collections.types.Float3.TYPE, OP_TYPE_FLOAT_16),  // Half float
    OP_TYPE_VECTOR3_FLOAT_32(3, uk.ac.manchester.tornado.api.collections.types.Float3.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_VECTOR3_FLOAT_64(3, uk.ac.manchester.tornado.api.collections.types.Double3.TYPE, OP_TYPE_FLOAT_64),

    // OP_TYPE_VECTOR4 Float
    OP_TYPE_VECTOR4_FLOAT_16(4, uk.ac.manchester.tornado.api.collections.types.Float4.TYPE, OP_TYPE_FLOAT_16),  // Half float
    OP_TYPE_VECTOR4_FLOAT_32(4, uk.ac.manchester.tornado.api.collections.types.Float4.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_VECTOR4_FLOAT_64(4, uk.ac.manchester.tornado.api.collections.types.Double4.TYPE, OP_TYPE_FLOAT_64),
    
    // OP_TYPE_VECTOR8 Float
    OP_TYPE_VECTOR8_FLOAT_16(8, uk.ac.manchester.tornado.api.collections.types.Float8.TYPE, OP_TYPE_FLOAT_16),  // Half float
    OP_TYPE_VECTOR8_FLOAT_32(8, uk.ac.manchester.tornado.api.collections.types.Float8.TYPE, OP_TYPE_FLOAT_32),
    OP_TYPE_VECTOR8_FLOAT_64(8, uk.ac.manchester.tornado.api.collections.types.Double8.TYPE, OP_TYPE_FLOAT_64),

    OP_TYPE_VOID(0, java.lang.Void.TYPE),
    
    // A pointer is represented as a long value (8 bytes)
    OP_TYPE_POINTER(8, java.lang.Long.TYPE),
    
    ILLEGAL(0, null),
    
    // Atomics
    INTEGER_ATOMIC_JAVA(4, java.util.concurrent.atomic.AtomicInteger.class);
    
    // @formatter:on

    private final int size;
    private final int vectorLength;

    private final SPIRVKind kind;
    private final SPIRVKind elementKind;
    private final Class<?> javaClass;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private final EnumKey key = new EnumKey(this);

    // public static SPIRVKind fromResolvedJavaType(ResolvedJavaType type) {
    // if (!type.isArray()) {
    // for (SPIRVKind kind : SPIRVKind.values()) {
    // if (kind.javaClass != null &&
    // (kind.javaClass.getSimpleName().equalsIgnoreCase(type.getJavaKind().name())
    // || kind.javaClass.getSimpleName().equals(type.getUnqualifiedName()))) {
    // return kind;
    // }
    // }
    // }
    // return ILLEGAL;
    // }

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
        switch (stackKind) {
            case Boolean:
                return SPIRVKind.OP_TYPE_BOOL;
            case Byte:
                return SPIRVKind.OP_TYPE_INT_8;
            case Short:
                return SPIRVKind.OP_TYPE_INT_16;
            case Int:
                return SPIRVKind.OP_TYPE_INT_32;
            case Long:
                return SPIRVKind.OP_TYPE_INT_64;
            case Float:
                return SPIRVKind.OP_TYPE_FLOAT_32;
            case Double:
                return SPIRVKind.OP_TYPE_FLOAT_64;
            default:
                throw new RuntimeException("Java type not supported: " + stackKind);
        }
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
        switch (kind) {
            case OP_TYPE_BOOL:
                return 'z';
            case OP_TYPE_INT_16:
                return 's';
            case OP_TYPE_INT_32:
                return 'i';
            case OP_TYPE_INT_64:
                return 'l';
            case OP_TYPE_FLOAT_32:
                return 'f';
            case OP_TYPE_FLOAT_64:
                return 'd';
            default:
                throw new RuntimeException("SPIRVType Not implemented yet");
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

    private boolean isIntType() {
        return kind == OP_TYPE_INT_16 || kind == OP_TYPE_INT_32 || kind == OP_TYPE_INT_64;
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

    // FIXME Could be deprecated
    public static SPIRVKind resolveToVectorKind(ResolvedJavaType type) {
        if (!type.isPrimitive() && type.getAnnotation(Vector.class) != null) {
            String typeName = type.getName();
            int index = typeName.lastIndexOf("/");
            String simpleName = typeName.substring(index + 1, typeName.length() - 1).toUpperCase();
            if (simpleName.startsWith("BYTE")) {
                simpleName = simpleName.replace("BYTE", "CHAR");
            }
            return SPIRVKind.valueOf(simpleName);
        }
        return SPIRVKind.ILLEGAL;
    }

    private static Map<String, SPIRVKind> vectorTable;

    static {
        vectorTable = new HashMap<>();
        // Integers
        vectorTable.put("Luk/ac/manchester/tornado/api/collections/types/Int2;", SPIRVKind.OP_TYPE_VECTOR2_INT_32);
        vectorTable.put("Luk/ac/manchester/tornado/api/collections/types/Int3;", SPIRVKind.OP_TYPE_VECTOR3_INT_32);
        vectorTable.put("Luk/ac/manchester/tornado/api/collections/types/Int4;", SPIRVKind.OP_TYPE_VECTOR4_INT_32);
        vectorTable.put("Luk/ac/manchester/tornado/api/collections/types/Int8;", SPIRVKind.OP_TYPE_VECTOR8_INT_32);

        // Floats
        vectorTable.put("Luk/ac/manchester/tornado/api/collections/types/Float2;", SPIRVKind.OP_TYPE_VECTOR2_FLOAT_32);
        vectorTable.put("Luk/ac/manchester/tornado/api/collections/types/Float3;", SPIRVKind.OP_TYPE_VECTOR3_FLOAT_32);
        vectorTable.put("Luk/ac/manchester/tornado/api/collections/types/Float4;", SPIRVKind.OP_TYPE_VECTOR4_FLOAT_32);
        vectorTable.put("Luk/ac/manchester/tornado/api/collections/types/Float8;", SPIRVKind.OP_TYPE_VECTOR8_FLOAT_32);

        // Double
        vectorTable.put("Luk/ac/manchester/tornado/api/collections/types/Double2;", SPIRVKind.OP_TYPE_VECTOR2_FLOAT_64);
        vectorTable.put("Luk/ac/manchester/tornado/api/collections/types/Double3;", SPIRVKind.OP_TYPE_VECTOR3_FLOAT_64);
        vectorTable.put("Luk/ac/manchester/tornado/api/collections/types/Double4;", SPIRVKind.OP_TYPE_VECTOR4_FLOAT_64);
        vectorTable.put("Luk/ac/manchester/tornado/api/collections/types/Double8;", SPIRVKind.OP_TYPE_VECTOR8_FLOAT_64);

    }

    public static SPIRVKind fromResolvedJavaTypeToVectorKind(ResolvedJavaType type) {
        if (vectorTable.containsKey(type.getName())) {
            return vectorTable.get(type.getName());
        }
        return SPIRVKind.ILLEGAL;
    }

    public int getByteCount() {
        return size;
    }

    public static int lookupTypeIndex(SPIRVKind kind) {
        switch (kind) {
            case OP_TYPE_INT_16:
                return 0;
            case OP_TYPE_INT_32:
                return 1;
            case OP_TYPE_FLOAT_32:
                return 2;
            case OP_TYPE_INT_8:
                return 3;
            case OP_TYPE_FLOAT_64:
                return 4;
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
                default:
                    shouldNotReachHere();
            }
        }
        return JavaKind.Illegal;
    }

}
