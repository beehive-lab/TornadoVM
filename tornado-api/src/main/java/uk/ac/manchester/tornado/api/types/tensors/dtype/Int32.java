package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

class Int32 extends DType {
    public Int32() {
        super(4, ValueLayout.JAVA_INT);
    }
}