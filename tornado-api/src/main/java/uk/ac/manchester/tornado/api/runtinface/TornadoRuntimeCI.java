package uk.ac.manchester.tornado.api.runtinface;

import uk.ac.manchester.tornado.api.TornadoObjectState;
import uk.ac.manchester.tornado.api.common.GenericDevice;

public interface TornadoRuntimeCI {

    public TornadoRuntimeCI callRuntime();

    public void clearObjectState();

    public TornadoGenericDriver getDriver(int index);

    public <D extends TornadoGenericDriver> D getDriver(Class<D> type);

    public int getNumDrivers();

    public GenericDevice getDefaultDevice();

    public TornadoObjectState resolveObject(Object object);

}
