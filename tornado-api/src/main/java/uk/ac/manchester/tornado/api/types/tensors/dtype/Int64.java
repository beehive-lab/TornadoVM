package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

non-sealed class Int64 extends DType {
    private final String dTypeName = "Int64";

    public Int64() {
        super(8, ValueLayout.JAVA_LONG);
    }

    @Override
    public String getDType() {
        return dTypeName;
    }
}
