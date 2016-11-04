package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.gen.LIRGenerationResultBase;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static tornado.common.exceptions.TornadoInternalError.guarantee;
import tornado.drivers.opencl.graal.lir.OCLKind;


public class OpenCLLIRGenerationResult extends LIRGenerationResultBase {

	private final Map<OCLKind,Set<Variable>> variableTable;
    
        public OpenCLLIRGenerationResult(
			String compilationUnitName,
			LIR lir,
			FrameMapBuilder frameMapBuilder) {
		super(compilationUnitName, lir, frameMapBuilder);
                variableTable = new HashMap<>();
	}
        
        public void insertVariable(Variable variable){
            guarantee(variable.getPlatformKind() instanceof OCLKind, "invalid variable kind: %s",variable.getLIRKind());
            OCLKind kind = (OCLKind) variable.getPlatformKind();
            
            variableTable.computeIfAbsent(kind, k -> new HashSet<>()).add(variable);
        }
        
        public Map<OCLKind,Set<Variable>> getVariableTable(){
            return variableTable;
        }
        
        

}
