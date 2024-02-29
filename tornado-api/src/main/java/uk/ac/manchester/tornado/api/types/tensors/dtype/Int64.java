package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

class Int64 extends DType {
    public Int64() {
        super(8, ValueLayout.JAVA_LONG);
    }
}
