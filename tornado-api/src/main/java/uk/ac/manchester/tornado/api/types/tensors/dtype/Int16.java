package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

class Int16 extends DType {
    public Int16() {
        super(2, ValueLayout.JAVA_SHORT);
    }
}