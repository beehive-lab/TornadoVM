package uk.ac.manchester.tornado.drivers.opencl.graal.lir;

import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class OCLValue extends Value {
    /**
     * Initializes a new value of the specified kind.
     *
     * @param valueKind
     *            the kind
     */
    protected OCLValue(ValueKind<?> valueKind) {
        super(valueKind);
    }
}
