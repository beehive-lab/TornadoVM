package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import jdk.vm.ci.meta.PlatformKind;

public enum PTXKind implements PlatformKind {
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
    HALF(2, null),
    FLOAT(4, java.lang.Float.TYPE),
    DOUBLE(8, java.lang.Double.TYPE),
    CHAR2(2, null, CHAR),
    UCHAR2(2, null, UCHAR),
    SHORT2(2, uk.ac.manchester.tornado.api.collections.types.Short2.TYPE, SHORT),
    USHORT2(2, null, USHORT),
    INT2(2, uk.ac.manchester.tornado.api.collections.types.Int2.TYPE, INT),
    UINT2(2, null, UINT),
    LONG2(2, null, LONG),
    ULONG2(2, null, ULONG),
    FLOAT2(2, uk.ac.manchester.tornado.api.collections.types.Float2.TYPE, FLOAT),
    DOUBLE2(2, uk.ac.manchester.tornado.api.collections.types.Double2.TYPE, DOUBLE),
    CHAR3(3, uk.ac.manchester.tornado.api.collections.types.Byte3.TYPE, CHAR),
    UCHAR3(3, null, UCHAR),
    SHORT3(3, uk.ac.manchester.tornado.api.collections.types.Short3.TYPE, SHORT),
    USHORT3(3, null, USHORT),
    INT3(3, uk.ac.manchester.tornado.api.collections.types.Int3.TYPE, INT),
    UINT3(3, null, UINT),
    LONG3(3, null, LONG),
    ULONG3(3, null, ULONG),
    FLOAT3(3, uk.ac.manchester.tornado.api.collections.types.Float3.TYPE, FLOAT),
    DOUBLE3(3, uk.ac.manchester.tornado.api.collections.types.Double3.TYPE, DOUBLE),
    CHAR4(4, uk.ac.manchester.tornado.api.collections.types.Byte4.TYPE, CHAR),
    UCHAR4(4, null, UCHAR),
    SHORT4(4, null, SHORT),
    USHORT4(4, null, USHORT),
    INT4(4, uk.ac.manchester.tornado.api.collections.types.Int4.TYPE, INT),
    UINT4(4, null, UINT),
    LONG4(4, null, LONG),
    ULONG4(4, null, ULONG),
    FLOAT4(4, uk.ac.manchester.tornado.api.collections.types.Float4.TYPE, FLOAT),
    DOUBLE4(4, uk.ac.manchester.tornado.api.collections.types.Double4.TYPE, DOUBLE),
    CHAR8(8, null, CHAR),
    UCHAR8(8, null, UCHAR),
    SHORT8(8, null, SHORT),
    USHORT8(8, null, USHORT),
    INT8(8, null, INT),
    UINT8(8, null, UINT),
    LONG8(8, null, LONG),
    ULONG8(8, null, ULONG),
    FLOAT8(8, uk.ac.manchester.tornado.api.collections.types.Float8.TYPE, FLOAT),
    DOUBLE8(8, uk.ac.manchester.tornado.api.collections.types.Double8.TYPE, DOUBLE),
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
        return 0;
    }
}
