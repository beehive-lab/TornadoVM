package uk.ac.manchester.tornado.api.types.tensors.dtype;

import java.lang.foreign.ValueLayout;

non-sealed class Double extends DType {
    public Double() {
        super(8, ValueLayout.JAVA_DOUBLE);
    }

    @Override
    public String getDType() {
        return null;
    }
}
