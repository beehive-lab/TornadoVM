package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.Variable;

import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;

@Opcode("VSEL")
public class SPIRVVectorElementSelect extends SPIRVLIROp {

    private final Variable vector;
    private final int laneId;

    public SPIRVVectorElementSelect(LIRKind lirKind, Variable vector, int laneId) {
        super(lirKind);
        this.vector = vector;
        this.laneId = laneId;
    }

    @Override
    public void emit(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm) {
        throw new RuntimeException("Vector Select empty implemenmtation");
    }
}
