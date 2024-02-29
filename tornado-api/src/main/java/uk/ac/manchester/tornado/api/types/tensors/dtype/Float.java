package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

public class Float extends DType {
    public Float() {
        super(4, ValueLayout.JAVA_FLOAT);
    }
}