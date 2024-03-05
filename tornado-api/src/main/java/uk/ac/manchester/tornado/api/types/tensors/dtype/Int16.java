package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

non-sealed class Int16 extends DType {
    private final String dTypeName = "Int16";

    public Int16() {
        super(2, ValueLayout.JAVA_SHORT);
    }

    @Override
    public String getDType() {
        return dTypeName;
    }
}