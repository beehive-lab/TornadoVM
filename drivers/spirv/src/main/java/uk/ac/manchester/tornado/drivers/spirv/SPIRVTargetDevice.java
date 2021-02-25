package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.api.TornadoTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;

import java.nio.ByteOrder;

public interface SPIRVTargetDevice extends TornadoTargetDevice {

    long getId();

    String getVersion();

    int getIndex();

    int getWordSize();

    ByteOrder getByteOrder();

    boolean isDeviceDoubleFPSupported();

    String getDeviceExtensions();

    OCLDeviceType getDeviceType();

    String getDeviceVendor();

    boolean isDeviceAvailable();

    String getDeviceOpenCLCVersion();

    boolean isLittleEndian();
}
