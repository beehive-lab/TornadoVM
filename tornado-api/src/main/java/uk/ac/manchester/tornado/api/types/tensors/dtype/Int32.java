package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

non-sealed class Int32 extends DType {
    private final String dTypeName = "Int32";

    public Int32() {
        super(4, ValueLayout.JAVA_INT);
    }

    @Override
    public String getDType() {
        return dTypeName;
    }
}