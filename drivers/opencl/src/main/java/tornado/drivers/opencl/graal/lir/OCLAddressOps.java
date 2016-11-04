package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.PlatformKind;
import com.oracle.graal.api.meta.Value;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler;
import tornado.drivers.opencl.graal.asm.OpenCLAssemblerConstants;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.AbstractInstruction;
import tornado.graal.nodes.vector.VectorKind;

public class OCLAddressOps {

    @Opcode("VSEL")
    public static class OCLVectorElementSelect extends AbstractInstruction implements Value {

        public static final LIRInstructionClass<OCLVectorElementSelect> TYPE = LIRInstructionClass
                .create(OCLVectorElementSelect.class);

        @Use final Value vector;
        @Use
        private final Value selection;
        private final LIRKind lirKind;

        public OCLVectorElementSelect(LIRKind lirKind, Value vector,Value selection) {
            super(TYPE);
            this.lirKind = lirKind;
            this.vector = vector;
            this.selection = selection;
        }

        @Override
        public LIRKind getLIRKind() {
            return lirKind;
        }

        @Override
        public PlatformKind getPlatformKind() {
            return lirKind.getPlatformKind();
        }

        @Override
        public Kind getKind() {
            return Kind.Illegal;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            OpenCLAssembler asm = crb.getAssembler();
            asm.value(crb, vector);
            asm.emitSymbol(".");
            asm.value(crb, selection);
        }

    }

    @Opcode("VMOV")
    public static class OCLVectorElement extends AbstractInstruction implements Value {

        public static final LIRInstructionClass<OCLVectorElement> TYPE = LIRInstructionClass
                .create(OCLVectorElement.class);

        private final OCLKind kind;
        @Use
        private final Value vector;
        private final int laneId;

        public OCLVectorElement(OCLKind kind, Value vector, int laneId) {
            super(TYPE);
            this.kind = kind;
            this.vector = vector;
            this.laneId = laneId;
        }

        @Override
        public Kind getKind() {
            return Kind.Illegal;
        }

        @Override
        public LIRKind getLIRKind() {
            return LIRKind.value(kind);
        }

        @Override
        public PlatformKind getPlatformKind() {
            return kind;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            final OpenCLAssembler asm = crb.getAssembler();
            asm.value(crb, vector);
            asm.emit(".s");
            asm.emit(Integer.toHexString(laneId).toLowerCase());
        }

    }

    @Deprecated
    public static class OCLDereference extends AbstractInstruction {

        public static final LIRInstructionClass<OCLDereference> TYPE = LIRInstructionClass
                .create(OCLDereference.class);

        private Kind kind;
        @Use(REG)
        private OCLAddressValue address;

        public OCLDereference(Kind kind, OCLAddressValue address) {
            super(TYPE);
            this.kind = kind;
            this.address = address;
            address.setAccessKind(kind);
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            final OpenCLAssembler asm = crb.getAssembler();
            asm.emit("*(");
            address.toAddress().emit(crb);
            asm.emit(")");
        }

        @Override
        public Kind getKind() {
            return kind;
        }

        @Override
        public LIRKind getLIRKind() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public PlatformKind getPlatformKind() {
            // TODO Auto-generated method stub
            return null;
        }

    }

    public static class OCLAddress extends AbstractInstruction {

        public static final LIRInstructionClass<OCLAddress> TYPE = LIRInstructionClass
                .create(OCLAddress.class);
        private PlatformKind kind;
        @Use
        protected Value base;
        @Use
        protected Value index;
        private long displacement;
        private int scale;

        public OCLAddress(PlatformKind kind, Value base, Value index, long displacement, int scale) {
            super(TYPE);
            this.kind = kind;
            this.base = base;
            this.index = index;
            this.scale = scale;
            this.displacement = displacement;
        }

        @Override
        public void emit(OCLCompilationResultBuilder crb) {
            final OpenCLAssembler asm = crb.getAssembler();
            asm.emit(toValueString(asm));
        }

        public String toValueString(OpenCLAssembler asm) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("(%s %s *) (", OpenCLAssemblerConstants.GLOBAL_MEM_MODIFIER,
                    OCLBackend.platformKindToOpenCLKind(kind)));
            sb.append(asm.toString(base));
            if (displacement > 0) {
                sb.append(String.format(" + %d", displacement));
            }

            if (index != Value.ILLEGAL) {
                sb.append(" + ( ");
                if (scale != 1) {
                    sb.append("(");
                }
                sb.append(asm.toString(index));
                sb.append(")");
                if (scale != 1) {
                    sb.append(String.format(" * %d)", scale));
                }

            }

            sb.append(")");
            return sb.toString();
        }

        public String toString() {
            return String
                    .format("address: (%s + %d) + [%s * %d]", base, displacement, index, scale);
        }
    }

    public static final class OCLVectorAddress extends OCLAddress {

        public static final LIRInstructionClass<OCLAddress> TYPE = LIRInstructionClass
                .create(OCLAddress.class);

        public OCLVectorAddress(
                VectorKind kind,
                Value base,
                Value index,
                long displacement,
                int scale) {
            super(kind.getElementKind(), base, index, displacement, scale);
        }
    }
}
