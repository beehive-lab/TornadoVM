package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVLoopControl extends SPIRVEnum {

    protected SPIRVLoopControl(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }

    public void add(SPIRVLoopControl other) {
        if (this.value == 0) this.name = other.name;
        else if (other.value != 0) this.name += "|" + other.name;

        this.value |= other.value;
        this.parameters.addAll(other.parameters);
        SPIRVCapability[] oldCapabilities = this.capabilities;
        this.capabilities = new SPIRVCapability[oldCapabilities.length + other.capabilities.length];
        int capPos = 0;
        for (SPIRVCapability capability : oldCapabilities) {
            this.capabilities[capPos++] = capability;
        }
        for (SPIRVCapability capability : other.capabilities) {
            this.capabilities[capPos++] = capability;
        }
    }

    public static SPIRVLoopControl None() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVLoopControl(0x0000, "None", params);
    }
    public static SPIRVLoopControl Unroll() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVLoopControl(0x0001, "Unroll", params);
    }
    public static SPIRVLoopControl DontUnroll() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVLoopControl(0x0002, "DontUnroll", params);
    }
    public static SPIRVLoopControl DependencyInfinite() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVLoopControl(0x0004, "DependencyInfinite", params);
    }
    public static SPIRVLoopControl DependencyLength(SPIRVLiteralInteger parameter0) {
        List<SPIRVOperand> params = new ArrayList<>(1);
        params.add(parameter0);
        return new SPIRVLoopControl(0x0008, "DependencyLength", params);
    }
}
