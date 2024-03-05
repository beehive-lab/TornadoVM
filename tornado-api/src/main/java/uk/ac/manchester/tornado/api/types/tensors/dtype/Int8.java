package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

non-sealed class Int8 extends DType {

    private final String dTypeName = "Int8";

    public Int8() {
        super(1, ValueLayout.JAVA_BYTE);
    }

    @Override
    public String getDType() {
        return dTypeName;
    }
}