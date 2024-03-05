package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

public non-sealed class QInt32 extends DType {
    public QInt32() {
        super(4, ValueLayout.JAVA_INT);
    }

    @Override
    public String getDType() {
        return null;
    }
}