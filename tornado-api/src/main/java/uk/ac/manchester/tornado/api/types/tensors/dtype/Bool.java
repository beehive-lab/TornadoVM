package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

non-sealed class Bool extends DType {
    private final String dTypeName = "Bool";

    public Bool() {
        super(1, ValueLayout.JAVA_BYTE);
    }

    @Override
    public String getDType() {
        return dTypeName;
    }
}