package uk.ac.manchester.tornado.drivers.spirv;

public interface SPIRVDispatcher {

    void init();

    int getNumPlatforms();

    SPIRVPlatform getPlatform(int index);

}
