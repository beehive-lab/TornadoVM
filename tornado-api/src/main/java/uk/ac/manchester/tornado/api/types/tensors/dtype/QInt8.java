package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

class QInt8 extends DType {
    public QInt8() {
        super(1, ValueLayout.JAVA_BYTE);
    }
}