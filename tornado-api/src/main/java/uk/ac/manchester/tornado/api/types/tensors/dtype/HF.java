package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

public non-sealed class HF extends DType {

    private final String dTypeName = "HalfFloat";

    public HF() {
        super(2, ValueLayout.JAVA_SHORT);
    }

    @Override
    public String getDType() {
        return dTypeName;
    }
}
