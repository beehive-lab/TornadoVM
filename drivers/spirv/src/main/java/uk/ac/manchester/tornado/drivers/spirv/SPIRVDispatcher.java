package uk.ac.manchester.tornado.drivers.spirv;

public interface SPIRVDispatcher {

    int getNumPlatforms();

    SPIRVPlatform getPlatform(int index);

}
