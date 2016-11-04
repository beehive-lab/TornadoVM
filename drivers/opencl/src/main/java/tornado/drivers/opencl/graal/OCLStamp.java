package tornado.drivers.opencl.graal;

import com.oracle.graal.api.meta.Constant;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.MemoryAccessProvider;
import com.oracle.graal.api.meta.MetaAccessProvider;
import com.oracle.graal.api.meta.ResolvedJavaType;
import com.oracle.graal.compiler.common.spi.LIRKindTool;
import com.oracle.graal.compiler.common.type.AbstractPointerStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;
import tornado.drivers.opencl.graal.lir.OCLKind;

public class OCLStamp extends AbstractPointerStamp {

    private OCLKind oclKind;
    
    public OCLStamp(OCLKind lirKind){
        super(true,false);
        this.oclKind = lirKind;
    }
    
    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        if(oclKind.getJavaClass() != null){
            return metaAccess.lookupJavaType(oclKind.getJavaClass());
        }
        shouldNotReachHere();
        
        return null;
    }
    
    public OCLKind getOCLKind(){
        return oclKind;
    }

    @Override
    public Kind getStackKind() {
        if(oclKind.isPrimitive()){
            switch(oclKind){
                case BOOL:
                    return Kind.Boolean;
                case CHAR:
                case UCHAR:
                    return Kind.Byte;
                case SHORT:
                case USHORT:
                    return Kind.Short;
                case UINT:
                case INT:
                    return Kind.Int;
                case LONG:
                case ULONG:
                    return Kind.Long;
                case FLOAT:
                    return Kind.Float;
                case DOUBLE:
                    return Kind.Double;
                default:
                    return Kind.Illegal;
            }
        } else if(oclKind.isVector()){
           // shouldNotReachHere();
            return Kind.Illegal;
        } 
        return Kind.Illegal;
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool lirKindTool) {
        return LIRKind.value(oclKind);
    }

    @Override
    public Stamp meet(Stamp stamp) {
        shouldNotReachHere();
        return this;
    }

    @Override
    public Stamp join(Stamp stamp) {
        if(stamp instanceof OCLStamp && ((OCLStamp)stamp).oclKind == oclKind){
            return this;
        }
        
        unimplemented("join: %s + %s",this,stamp);
        return this;
    }

    @Override
    public Stamp unrestricted() {
        shouldNotReachHere();
        return this;
    }

    @Override
    public Stamp empty() {
        shouldNotReachHere();
        return this;
    }

    @Override
    public Stamp constant(Constant cnstnt, MetaAccessProvider map) {
        shouldNotReachHere();
        return this;
    }

    @Override
    public boolean isCompatible(Stamp stamp) {
        shouldNotReachHere();
        return false;
    }

    @Override
    public boolean hasValues() {
        //shouldNotReachHere();
        return true;
    }

    @Override
    public Constant readConstant(MemoryAccessProvider metaAccess, Constant constant, long displacment) {
        shouldNotReachHere();
        return null;
    }

    @Override
    public Stamp improveWith(Stamp stamp) {
        shouldNotReachHere();
        return this;
    }
    
    @Override
    public String toString(){
        return "ocl: " + oclKind.name();
    }

    @Override
    protected AbstractPointerStamp copyWith(boolean bln, boolean bln1) {
        unimplemented();
        return null;
    }
}
