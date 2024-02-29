package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

class Int8 extends DType {
    public Int8() {
        super(1, ValueLayout.JAVA_BYTE);
    }
}