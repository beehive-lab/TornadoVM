package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

public class QInt32 extends DType {
    public QInt32() {
        super(4, ValueLayout.JAVA_INT);
    }
}