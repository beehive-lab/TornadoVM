package tornado.drivers.opencl.graal;

import com.oracle.graal.api.code.Architecture;
import static com.oracle.graal.api.code.MemoryBarriers.*;
import com.oracle.graal.api.code.Register;
import com.oracle.graal.api.code.Register.RegisterCategory;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.PlatformKind;
import java.nio.ByteOrder;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static tornado.drivers.opencl.graal.asm.OpenCLAssemblerConstants.CONSTANT_REGION_NAME;
import static tornado.drivers.opencl.graal.asm.OpenCLAssemblerConstants.HEAP_REF_NAME;
import static tornado.drivers.opencl.graal.asm.OpenCLAssemblerConstants.LOCAL_REGION_NAME;
import static tornado.drivers.opencl.graal.asm.OpenCLAssemblerConstants.PRIVATE_REGION_NAME;
import static tornado.drivers.opencl.graal.asm.OpenCLAssemblerConstants.STACK_REF_NAME;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.meta.OCLMemorySpace;

public class OCLArchitecture extends Architecture {

    public static final RegisterCategory OCL_ABI = new RegisterCategory("abi");

    public static class OCLRegister {

        public final int number;
        public final String name;
        public final OCLKind lirKind;

        public OCLRegister(int number, String name, OCLKind lirKind) {
            this.number = number;
            this.name = name;
            this.lirKind = lirKind;

        }

        public Register asRegister() {
            return new Register(number, 0, name, OCL_ABI);
        }
        
        public String getDeclaration(){
            return String.format("%s %s",lirKind.toString(),name);
        }
        
    }

    public static class OCLMemoryBase extends OCLRegister {

        public final OCLMemorySpace memorySpace;

        public OCLMemoryBase(int number, String name, OCLMemorySpace memorySpace) {
            super(number, name, OCLKind.UCHAR);
            this.memorySpace = memorySpace;
        }
        
        @Override
         public String getDeclaration(){
            return String.format("%s %s *%s",memorySpace.name(),lirKind.toString(),name);
        }
         
    }

    public static final OCLMemoryBase hp = new OCLMemoryBase(0, HEAP_REF_NAME, OCLMemorySpace.GLOBAL);
    public static OCLRegister sp;
    public static final OCLMemoryBase cp = new OCLMemoryBase(2, CONSTANT_REGION_NAME, OCLMemorySpace.CONSTANT);
    public static final OCLMemoryBase lp = new OCLMemoryBase(3, LOCAL_REGION_NAME, OCLMemorySpace.LOCAL);
    public static final OCLMemoryBase pp = new OCLMemoryBase(4, PRIVATE_REGION_NAME, OCLMemorySpace.GLOBAL);

    public static OCLRegister[] abiRegisters;

    public OCLArchitecture(final int wordSize, final ByteOrder byteOrder) {
        super("Tornado OpenCL", wordSize, byteOrder, false, null, LOAD_STORE | STORE_STORE, 1, 0, wordSize);
        sp = new OCLRegister(1, STACK_REF_NAME, getWordKind());
        abiRegisters = new OCLRegister[] {hp, sp, cp, lp, pp};
    }

    @Override
    public int getReturnAddressSize() {
        return this.getWordSize();
    }

    @Override
    public boolean canStoreValue(RegisterCategory category, PlatformKind platformKind) {

        return false;
    }

    @Override
    public PlatformKind getLargestStorableKind(RegisterCategory category) {
        return Kind.Long;
    }
    
    public String getABI(){
        StringBuilder sb = new StringBuilder();
        for(int i=0;i<abiRegisters.length;i++){
            sb.append(abiRegisters[i].getDeclaration());
            if(i < abiRegisters.length - 1){
                sb.append(", ");
            }
        }
        return sb.toString();
    }
    
    public final OCLKind getWordKind(){
        if(getWordSize() == 4){
            return OCLKind.UINT;
        } else if(getWordSize() == 8){
            return OCLKind.ULONG;
        } else {
            shouldNotReachHere();
        }
        return OCLKind.ILLEGAL;
    }

}
