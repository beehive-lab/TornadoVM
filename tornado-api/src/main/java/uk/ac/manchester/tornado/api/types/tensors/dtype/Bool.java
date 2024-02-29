package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

class Bool extends DType {
    public Bool() {
        super(1, ValueLayout.JAVA_BYTE);
    }
}