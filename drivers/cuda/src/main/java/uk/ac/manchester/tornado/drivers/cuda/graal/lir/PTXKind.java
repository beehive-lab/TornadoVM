package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

public enum PTXKind implements PlatformKind {
    // @formatter:off
    PRED(1, Boolean.TYPE),

    S8(1, Byte.TYPE),
    U8(1, null),
    B8(1, null),

    S16(2, Short.TYPE),
    F16(2, null),
    U16(2, null),
    B16(2, null),

    S32(4, Integer.TYPE),
    F32(4, Float.TYPE),
    U32(4, null),
    B32(4, null),

    S64(8, Long.TYPE),
    F64(8, Double.TYPE),
    U64(8, null),
    B64(8, null),

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
                if (k.javaClass != null && k.javaClass.getSimpleName().equals(elementType.getUnqualifiedName())) {
                    return k;
                }
            }
        }
        return ILLEGAL;
    }

    public static PTXAssembler.PTXBinaryTemplate resolveTemplateType(ResolvedJavaType type) {
        return resolveTemplateType(type.getJavaKind());
    }

    private static PTXAssembler.PTXBinaryTemplate resolveTemplateType(JavaKind type) {
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
        } else if (type == JavaKind.Byte){
            return PTXAssembler.PTXBinaryTemplate.NEW_LOCAL_BYTE_ARRAY;
        }
        return null;
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

    @Override
    public char getTypeChar() {
        if (this == PTXKind.PRED) return 'p';
        switch (this.size) {
            case 1: return 'b';
            case 2: return 's';
            case 4: return 0;
            case 8: return 'd';
            default: shouldNotReachHere();
        }

        return 0;
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

    public PTXKind getElementKind() {
        return (isVector()) ? elementKind : ILLEGAL;
    }

    public boolean isInteger() {
        return kind != ILLEGAL && !isFloating();
    }

    public boolean isFloating() {
        return kind == F16 || kind == F32 || kind == F64;
    }
}
