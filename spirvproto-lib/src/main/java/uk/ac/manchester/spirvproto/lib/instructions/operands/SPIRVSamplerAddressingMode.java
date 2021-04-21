package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVSamplerAddressingMode extends SPIRVEnum {

    protected SPIRVSamplerAddressingMode(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVSamplerAddressingMode None() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVSamplerAddressingMode(0, "None", params, SPIRVCapability.Kernel());
    }
    public static SPIRVSamplerAddressingMode ClampToEdge() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVSamplerAddressingMode(1, "ClampToEdge", params, SPIRVCapability.Kernel());
    }
    public static SPIRVSamplerAddressingMode Clamp() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVSamplerAddressingMode(2, "Clamp", params, SPIRVCapability.Kernel());
    }
    public static SPIRVSamplerAddressingMode Repeat() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVSamplerAddressingMode(3, "Repeat", params, SPIRVCapability.Kernel());
    }
    public static SPIRVSamplerAddressingMode RepeatMirrored() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVSamplerAddressingMode(4, "RepeatMirrored", params, SPIRVCapability.Kernel());
    }
}
