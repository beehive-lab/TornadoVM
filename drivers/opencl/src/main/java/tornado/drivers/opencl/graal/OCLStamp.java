package tornado.drivers.opencl.graal;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.compiler.common.spi.LIRKindTool;
import com.oracle.graal.compiler.common.type.AbstractPointerStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import jdk.vm.ci.meta.*;
import tornado.drivers.opencl.graal.lir.OCLKind;

import static tornado.common.Tornado.warn;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLStamp extends AbstractPointerStamp {

    private OCLKind oclKind;

    public OCLStamp(OCLKind lirKind) {
        super(true, false);
        this.oclKind = lirKind;
    }

    @Override
    public Stamp constant(Constant cnstnt, MetaAccessProvider map) {
        shouldNotReachHere();
        return this;
    }

    @Override
    public Stamp empty() {
        shouldNotReachHere();
        return this;
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool lirKindTool) {
        return LIRKind.value(oclKind);
    }

    public OCLKind getOCLKind() {
        return oclKind;
    }

    @Override
    public JavaKind getStackKind() {
        if (oclKind.isPrimitive()) {
            switch (oclKind) {
                case BOOL:
                    return JavaKind.Boolean;
                case CHAR:
                case UCHAR:
                    return JavaKind.Byte;
                case SHORT:
                case USHORT:
                    return JavaKind.Short;
                case UINT:
                case INT:
                    return JavaKind.Int;
                case LONG:
                case ULONG:
                    return JavaKind.Long;
                case FLOAT:
                    return JavaKind.Float;
                case DOUBLE:
                    return JavaKind.Double;
                default:
                    return JavaKind.Illegal;
            }
        } else if (oclKind.isVector()) {
            return JavaKind.Illegal;
        }
        return JavaKind.Illegal;
    }

    @Override
    public boolean hasValues() {
        //shouldNotReachHere();
        return true;
    }

    @Override
    public Stamp improveWith(Stamp stamp) {
        shouldNotReachHere();
        return this;
    }

    @Override
    public boolean isCompatible(Constant constant) {

        shouldNotReachHere();
        return false;
    }

    @Override
    public boolean isCompatible(Stamp stamp) {
        shouldNotReachHere();
        return false;
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        if (oclKind.getJavaClass() != null) {
            return metaAccess.lookupJavaType(oclKind.getJavaClass());
        }
        shouldNotReachHere();

        return null;
    }

    @Override
    public Stamp join(Stamp stamp) {
        if (stamp instanceof OCLStamp && ((OCLStamp) stamp).oclKind == oclKind) {
            return this;
        }

        warn("stamp join: %s + %s", this, stamp);
        return this;
    }

    @Override
    public Stamp meet(Stamp stamp) {
        shouldNotReachHere();
        return this;
    }

    @Override
    public Constant readConstant(MemoryAccessProvider metaAccess, Constant constant, long displacment) {
        shouldNotReachHere();
        return null;
    }

    @Override
    public String toString() {
        return "ocl: " + oclKind.name();
    }

    @Override
    public Stamp unrestricted() {
        shouldNotReachHere();
        return this;
    }

    @Override
    protected AbstractPointerStamp copyWith(boolean bln, boolean bln1) {
        unimplemented();
        return null;
    }
}
