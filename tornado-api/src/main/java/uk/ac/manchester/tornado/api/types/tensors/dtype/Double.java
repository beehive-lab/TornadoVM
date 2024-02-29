package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

class Double extends DType {
    public Double() {
        super(8, ValueLayout.JAVA_DOUBLE);
    }
}
