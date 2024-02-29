package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

public class HalfFloat extends DType {
    public HalfFloat() {
        super(2, ValueLayout.JAVA_SHORT);
    }
}
