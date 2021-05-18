package uk.ac.manchester.tornado.drivers.spirv.graal;

import java.nio.ByteBuffer;

import jdk.vm.ci.code.InstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVModule;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;

public abstract class SPIRVInstalledCode extends InstalledCode implements TornadoInstalledCode {

    protected SPIRVDeviceContext deviceContext;
    protected SPIRVModule spirvModule;
    protected final ByteBuffer buffer = ByteBuffer.allocate(32);

    public SPIRVInstalledCode(String name, SPIRVModule spirvModule, SPIRVDeviceContext deviceContext) {
        super(name);
        this.deviceContext = deviceContext;
        this.spirvModule = spirvModule;
        buffer.order(deviceContext.getDevice().getByteOrder());
    }

    public SPIRVModule getSPIRVModule() {
        return this.spirvModule;
    }

    public SPIRVDeviceContext getDeviceContext() {
        return this.deviceContext;
    }

    public String getGeneratedSourceCode() {
        return " NOT IMPLEMENTED YET";
    }
}
