package uk.ac.manchester.tornado.drivers.cuda.graal;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Variable;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.meta.PTXMemorySpace;

import java.nio.ByteOrder;

import static jdk.vm.ci.code.MemoryBarriers.LOAD_STORE;
import static jdk.vm.ci.code.MemoryBarriers.STORE_STORE;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

public class PTXArchitecture extends Architecture {

    public static final RegisterCategory PTX_ABI = new RegisterCategory("abi");

    public static final PTXMemoryBase globalSpace = new PTXMemoryBase(0, PTXMemorySpace.GLOBAL);
    public static final PTXMemoryBase paramSpace = new PTXMemoryBase(1, PTXMemorySpace.PARAM);
    public static final PTXMemoryBase sharedSpace = new PTXMemoryBase(2, PTXMemorySpace.SHARED);
    public static final PTXMemoryBase localSpace = new PTXMemoryBase(2, PTXMemorySpace.LOCAL);

    public static PTXParam STACK_POINTER;
    public static PTXParam[] abiRegisters;

    public static PTXBuiltInRegister ThreadIDX = new PTXBuiltInRegister("%tid.x");
    public static PTXBuiltInRegister ThreadIDY = new PTXBuiltInRegister("%tid.y");
    public static PTXBuiltInRegister ThreadIDZ = new PTXBuiltInRegister("%tid.z");

    public static PTXBuiltInRegister BlockDimX = new PTXBuiltInRegister("%ntid.x");
    public static PTXBuiltInRegister BlockDimY = new PTXBuiltInRegister("%ntid.y");
    public static PTXBuiltInRegister BlockDimZ = new PTXBuiltInRegister("%ntid.z");

    public static PTXBuiltInRegister BlockIDX = new PTXBuiltInRegister("%ctaid.x");
    public static PTXBuiltInRegister BlockIDY = new PTXBuiltInRegister("%ctaid.y");
    public static PTXBuiltInRegister BlockIDZ = new PTXBuiltInRegister("%ctaid.z");

    public static PTXBuiltInRegister GridDimX = new PTXBuiltInRegister("%nctaid.x");
    public static PTXBuiltInRegister GridDimY = new PTXBuiltInRegister("%nctaid.y");
    public static PTXBuiltInRegister GridDimZ = new PTXBuiltInRegister("%nctaid.z");

    public PTXArchitecture(PTXKind wordKind, ByteOrder byteOrder) {
        super("Tornado PTX",
                wordKind,
                byteOrder,
                false,
                null,
                LOAD_STORE | STORE_STORE,
                0,
                0
        );

        STACK_POINTER = new PTXParam(PTXAssemblerConstants.STACK_PTR_NAME, wordKind);

        abiRegisters = new PTXParam[]{STACK_POINTER};
    }

    @Override
    public boolean canStoreValue(RegisterCategory category, PlatformKind kind) {
        return false;
    }

    @Override
    public PlatformKind getLargestStorableKind(RegisterCategory category) {
        return null;
    }

    @Override
    public PlatformKind getPlatformKind(JavaKind javaKind) {
        PTXKind ptxKind = PTXKind.ILLEGAL;
        switch (javaKind) {
            case Boolean:
                ptxKind = PTXKind.U8;
                break;
            case Byte:
                ptxKind = PTXKind.S8;
                break;
            case Short:
                ptxKind = (javaKind.isUnsigned()) ? PTXKind.U16 : PTXKind.S16;
                break;
            case Char:
                ptxKind = PTXKind.U16;
                break;
            case Int:
                ptxKind = (javaKind.isUnsigned()) ? PTXKind.U32 : PTXKind.S32;
                break;
            case Long:
                ptxKind = (javaKind.isUnsigned()) ? PTXKind.U64 : PTXKind.S64;
                break;
            case Float:
                ptxKind = PTXKind.F32;
                break;
            case Double:
                ptxKind = PTXKind.F64;
                break;
            case Object:
                ptxKind = (PTXKind) getWordKind();
                break;
            case Void:
            case Illegal:
                ptxKind = PTXKind.ILLEGAL;
                break;
            default:
                shouldNotReachHere("illegal java type for %s", javaKind.name());
        }

        return ptxKind;
    }

    public String getABI() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < abiRegisters.length; i++) {
            sb.append(abiRegisters[i].getDeclaration());
            if (i < abiRegisters.length - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    public static class PTXRegister {
        public final int number;
        protected String name;
        public final PTXKind ptxKind;

        public PTXRegister(int number, PTXKind ptxKind) {
            this.number = number;
            this.ptxKind = ptxKind;
            this.name = "r" + ptxKind.getTypeChar() + number;
        }

        public String getDeclaration() {
            return String.format(".reg .%s %s", ptxKind.toString(), name);
        }

        public String getName() {
            return name;
        }
    }

    public static class PTXParam extends PTXRegister {

        public PTXParam(String name, PTXKind lirKind) {
            super(0, lirKind);
            this.name = name;
        }

        @Override
        public String getDeclaration() {
            return String.format(".param .%s %s", ptxKind.toString(), name);
        }
    }

    public static class PTXMemoryBase extends PTXRegister {

        public final PTXMemorySpace memorySpace;

        public PTXMemoryBase(int number, PTXMemorySpace memorySpace) {
            super(number, PTXKind.B64);
            this.memorySpace = memorySpace;
        }
    }

    public static class PTXBuiltInRegister extends Variable {

        protected PTXBuiltInRegister(String name) {
            super(LIRKind.value(PTXKind.U32), 0);
            setName(name);
        }
    }

    public static class PTXBuiltInRegisterArray {
        public final PTXBuiltInRegister threadID;
        public final PTXBuiltInRegister blockID;
        public final PTXBuiltInRegister blockDim;
        public final PTXBuiltInRegister gridDim;

        public PTXBuiltInRegisterArray(int dim) {
            switch (dim) {
                case 0:
                    threadID = PTXArchitecture.ThreadIDX;
                    blockDim = PTXArchitecture.BlockDimX;
                    blockID = PTXArchitecture.BlockIDX;
                    gridDim = PTXArchitecture.GridDimX;
                    break;
                case 1:
                    threadID = PTXArchitecture.ThreadIDY;
                    blockDim = PTXArchitecture.BlockDimY;
                    blockID = PTXArchitecture.BlockIDY;
                    gridDim = PTXArchitecture.GridDimY;
                    break;
                case 2:
                    threadID = PTXArchitecture.ThreadIDZ;
                    blockDim = PTXArchitecture.BlockDimZ;
                    blockID = PTXArchitecture.BlockIDZ;
                    gridDim = PTXArchitecture.GridDimZ;
                    break;
                default:
                    shouldNotReachHere("Too many dimensions: %d", dim);
                    threadID = null;
                    blockDim = null;
                    blockID = null;
                    gridDim = null;
                    break;
            }
        }
    }
}
