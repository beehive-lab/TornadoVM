package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

class Float extends DType {
    public Float() {
        super(4, ValueLayout.JAVA_FLOAT);
    }
}