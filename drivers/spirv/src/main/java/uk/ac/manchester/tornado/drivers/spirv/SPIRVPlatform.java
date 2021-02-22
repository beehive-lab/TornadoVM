package uk.ac.manchester.tornado.drivers.spirv;

public interface SPIRVPlatform {

    int getNumDevices();

    SPIRVDevice getDevice(int k);
}
