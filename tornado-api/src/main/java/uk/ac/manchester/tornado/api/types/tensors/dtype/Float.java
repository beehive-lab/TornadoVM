package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

public non-sealed class Float extends DType {

    private final String dTypeName = "Float";

    public Float() {
        super(4, ValueLayout.JAVA_FLOAT);
    }

    @Override
    public String getDType() {
        return dTypeName;
    }
}