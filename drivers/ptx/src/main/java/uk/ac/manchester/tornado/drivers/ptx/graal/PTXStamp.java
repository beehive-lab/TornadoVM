package uk.ac.manchester.tornado.drivers.ptx.graal;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

public class PTXStamp extends ObjectStamp {

    private PTXKind kind;

    public PTXStamp(PTXKind kind) {
        super(null, true, true, false);
        this.kind = kind;
    }

    @Override
    public Stamp constant(Constant cnstnt, MetaAccessProvider map) {
        shouldNotReachHere();
        return this;
    }

    @Override
    public Stamp empty() {
        return this;
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool lirKindTool) {
        return LIRKind.value(kind);
    }

    public PTXKind getPTXKind() {
        return kind;
    }

    @Override
    public JavaKind getStackKind() {
        if (kind.isPrimitive()) {
            switch (kind) {
                case PRED:
                    return JavaKind.Boolean;
                case S8:
                case U8:
                    return JavaKind.Byte;
                case S16:
                case U16:
                    return JavaKind.Short;
                case S32:
                case U32:
                    return JavaKind.Int;
                case S64:
                case U64:
                    return JavaKind.Long;
                case F32:
                    return JavaKind.Float;
                case F64:
                    return JavaKind.Double;
                default:
                    return JavaKind.Illegal;
            }
        } else if (kind.isVector()) {
            return JavaKind.Object;
        }
        return JavaKind.Illegal;
    }

    @Override
    public boolean hasValues() {
        // shouldNotReachHere();
        return true;
    }

    @Override
    public Stamp improveWith(Stamp stamp) {
        return this;
    }

    @Override
    public boolean isCompatible(Constant constant) {

        shouldNotReachHere();
        return false;
    }

    @Override
    public boolean isCompatible(Stamp stamp) {
        if (stamp instanceof PTXStamp && ((PTXStamp) stamp).kind == kind) {
            return true;
        }

        unimplemented("stamp is compat: %s + %s", this, stamp);
        return false;
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        if (kind.getJavaClass() != null) {
            return metaAccess.lookupJavaType(kind.getJavaClass());
        }
        shouldNotReachHere();

        return null;
    }

    @Override
    public Stamp join(Stamp stamp) {
        return this;
    }

    @Override
    public Stamp meet(Stamp stamp) {
        return this;
    }

    @Override
    public Constant readConstant(MemoryAccessProvider metaAccess, Constant constant, long displacment) {
        shouldNotReachHere();
        return null;
    }

    @Override
    public String toString() {
        return "ptx: " + kind.name();
    }

    @Override
    public Stamp unrestricted() {
        return this;
    }

    @Override
    protected ObjectStamp copyWith(ResolvedJavaType rjt, boolean bln, boolean bln1, boolean bln2) {
        return this;
    }
}
