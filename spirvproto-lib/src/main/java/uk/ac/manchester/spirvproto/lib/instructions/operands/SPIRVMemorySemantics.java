package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVMemorySemantics extends SPIRVEnum {

    protected SPIRVMemorySemantics(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }

    public void add(SPIRVMemorySemantics other) {
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

    public static SPIRVMemorySemantics Relaxed() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVMemorySemantics(0x0000, "Relaxed", params);
    }
    public static SPIRVMemorySemantics None() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVMemorySemantics(0x0000, "None", params);
    }
    public static SPIRVMemorySemantics Acquire() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVMemorySemantics(0x0002, "Acquire", params);
    }
    public static SPIRVMemorySemantics Release() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVMemorySemantics(0x0004, "Release", params);
    }
    public static SPIRVMemorySemantics AcquireRelease() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVMemorySemantics(0x0008, "AcquireRelease", params);
    }
    public static SPIRVMemorySemantics SequentiallyConsistent() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVMemorySemantics(0x0010, "SequentiallyConsistent", params);
    }
    public static SPIRVMemorySemantics UniformMemory() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVMemorySemantics(0x0040, "UniformMemory", params, SPIRVCapability.Shader());
    }
    public static SPIRVMemorySemantics SubgroupMemory() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVMemorySemantics(0x0080, "SubgroupMemory", params);
    }
    public static SPIRVMemorySemantics WorkgroupMemory() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVMemorySemantics(0x0100, "WorkgroupMemory", params);
    }
    public static SPIRVMemorySemantics CrossWorkgroupMemory() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVMemorySemantics(0x0200, "CrossWorkgroupMemory", params);
    }
    public static SPIRVMemorySemantics AtomicCounterMemory() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVMemorySemantics(0x0400, "AtomicCounterMemory", params, SPIRVCapability.AtomicStorage());
    }
    public static SPIRVMemorySemantics ImageMemory() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVMemorySemantics(0x0800, "ImageMemory", params);
    }
}
