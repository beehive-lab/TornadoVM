package uk.ac.manchester.tornado.drivers.opencl.graal;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.Stamp;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;

public class HalfFloatStamp extends Stamp {
    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        return metaAccess.lookupJavaType(java.lang.Short.TYPE);
    }

    @Override
    public JavaKind getStackKind() {
        return JavaKind.Short;
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        return LIRKind.value(OCLKind.HALF);
    }

    @Override
    public Stamp meet(Stamp other) {
        return this;
    }

    @Override
    public Stamp join(Stamp other) {
        return this;
    }

    @Override
    public Stamp unrestricted() {
        return this;
    }

    @Override
    public Stamp empty() {
        return this;
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        return null;
    }

    @Override
    public boolean isCompatible(Stamp other) {
        return true;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        return true;
    }

    @Override
    public boolean hasValues() {
        return true;
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        return null;
    }

    @Override
    public Stamp improveWith(Stamp other) {
        return null;
    }

    @Override
    public String toString() {
        return "half";
    }

    @Override
    public void accept(Visitor v) {

    }
}
