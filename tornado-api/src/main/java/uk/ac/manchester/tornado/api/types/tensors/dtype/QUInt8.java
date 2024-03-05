package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

non-sealed class QUInt8 extends DType {
    public QUInt8() {
        super(1, ValueLayout.JAVA_BYTE);
    }

    @Override
    public String getDType() {
        return null;
    }
}
