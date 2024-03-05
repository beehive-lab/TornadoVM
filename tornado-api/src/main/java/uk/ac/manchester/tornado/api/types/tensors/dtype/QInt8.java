package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

non-sealed class QInt8 extends DType {
    private final String dTypeName = "QInt8";

    public QInt8() {
        super(1, ValueLayout.JAVA_BYTE);
    }

    @Override
    public String getDType() {
        return dTypeName;
    }
}