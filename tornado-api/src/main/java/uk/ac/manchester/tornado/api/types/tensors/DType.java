package uk.ac.manchester.tornado.api.types.tensors;

import java.lang.foreign.ValueLayout;

public enum DType {
    // @formatter:off
    HALF_FLOAT(2, ValueLayout.JAVA_SHORT),
    FLOAT(4, ValueLayout.JAVA_FLOAT),
    DOUBLE(8, ValueLayout.JAVA_DOUBLE),
    INT8(1, ValueLayout.JAVA_BYTE),
    INT16(2, ValueLayout.JAVA_SHORT),
    INT32(4, ValueLayout.JAVA_INT),
    INT64(8, ValueLayout.JAVA_LONG),
    UINT8(1, ValueLayout.JAVA_BYTE),
    BOOL(1, ValueLayout.JAVA_BYTE),
    QINT8(1, ValueLayout.JAVA_BYTE),
    QUINT8(1, ValueLayout.JAVA_BYTE),
    QINT32(4, ValueLayout.JAVA_INT);
    // @formatter:on

    private final int size;
    private final ValueLayout layout;

    DType(int size, ValueLayout layout) {
        this.size = size;
        this.layout = layout;
    }

    public int getByteSize() {
        return size;
    }

    public ValueLayout getLayout() {
        return layout;
    }

}
