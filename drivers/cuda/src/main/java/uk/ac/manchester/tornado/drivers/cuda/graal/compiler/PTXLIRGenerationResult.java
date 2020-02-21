package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import jdk.vm.ci.code.CallingConvention;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PTXLIRGenerationResult extends LIRGenerationResult {

    private final Map<PTXKind, Set<Variable>> variableTable;

    public PTXLIRGenerationResult(CompilationIdentifier identifier,
                                  LIR lir,
                                  FrameMapBuilder frameMapBuilder,
                                  RegisterAllocationConfig registerAllocationConfig,
                                  CallingConvention callingConvention) {
        super(identifier, lir, frameMapBuilder, registerAllocationConfig, callingConvention);

        variableTable = new HashMap<>();
    }
}
