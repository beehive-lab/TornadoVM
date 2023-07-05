package uk.ac.manchester.tornado.drivers.opencl.graal.compiler;

import org.graalvm.compiler.lir.Variable;

import jdk.vm.ci.meta.ValueKind;

public class OCLVariable extends Variable {
    /**
     * The identifier of the variable. This is a non-zero index in a contiguous
     * 0-based name space.
     */
    public final int index;

    private String name;

    /**
     * Creates a new variable.
     *
     * @param kind
     * @param index
     */
    public OCLVariable(ValueKind<?> kind, int index) {
        super(kind, index);
        assert index >= 0;
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        if (name != null) {
            return name;
        } else {
            return "v" + index + getKindSuffix();
        }
    }

    @Override
    public int hashCode() {
        return 71 * super.hashCode() + index;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Variable) {
            Variable other = (Variable) obj;
            return super.equals(other) && index == other.index;
        }
        return false;
    }

}
