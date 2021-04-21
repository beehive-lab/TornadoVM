package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVExecutionModel extends SPIRVEnum {

    protected SPIRVExecutionModel(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVExecutionModel Vertex() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionModel(0, "Vertex", params, SPIRVCapability.Shader());
    }
    public static SPIRVExecutionModel TessellationControl() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionModel(1, "TessellationControl", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVExecutionModel TessellationEvaluation() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionModel(2, "TessellationEvaluation", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVExecutionModel Geometry() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionModel(3, "Geometry", params, SPIRVCapability.Geometry());
    }
    public static SPIRVExecutionModel Fragment() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionModel(4, "Fragment", params, SPIRVCapability.Shader());
    }
    public static SPIRVExecutionModel GLCompute() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionModel(5, "GLCompute", params, SPIRVCapability.Shader());
    }
    public static SPIRVExecutionModel Kernel() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVExecutionModel(6, "Kernel", params, SPIRVCapability.Kernel());
    }
}
