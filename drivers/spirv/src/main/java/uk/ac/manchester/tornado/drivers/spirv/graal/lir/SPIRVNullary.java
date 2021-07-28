package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;

import uk.ac.manchester.spirvproto.lib.SPIRVInstScope;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpBranch;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpName;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpReturn;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralString;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

/**
 * LIR Operations with no inputs
 */
public class SPIRVNullary {

    protected static class NullaryConsumer extends SPIRVLIROp {

        protected NullaryConsumer(LIRKind valueKind) {
            super(valueKind);
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {

        }
    }

    public static class ReturnNoOperands extends NullaryConsumer {

        final AbstractBlockBase<?> currentBLock;

        public ReturnNoOperands(LIRKind valueKind, AbstractBlockBase<?> currentBLock) {
            super(valueKind);
            this.currentBLock = currentBLock;
        }

        @Override
        public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
            SPIRVLogger.traceCodeGen("emit SPIRVOpReturn for block: " + currentBLock.toString());

            if (TornadoOptions.SPIRV_RETURN_LABEL) {
                SPIRVInstScope blockScope = asm.currentBlockScope();
                if (asm.returnLabel == null) {
                    asm.returnLabel = asm.module.getNextId();
                    asm.module.add(new SPIRVOpName(asm.returnLabel, new SPIRVLiteralString("return")));
                }
                blockScope.add(new SPIRVOpBranch(asm.returnLabel));
            } else {
                // Search the block
                SPIRVInstScope blockScope = asm.blockTable.get(currentBLock.toString());
                // Add Block Return
                blockScope.add(new SPIRVOpReturn());
            }
        }
    }
}
