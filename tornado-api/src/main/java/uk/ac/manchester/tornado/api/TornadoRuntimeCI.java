package uk.ac.manchester.tornado.api;

import uk.ac.manchester.tornado.api.common.GenericDevice;
import uk.ac.manchester.tornado.api.mm.TornadoObjectState;

public interface TornadoRuntimeCI {

    public TornadoRuntimeCI callRuntime();

    public void clearObjectState();

    public TornadoGenericDriver getDriver(int index);

    public <D extends TornadoGenericDriver> D getDriver(Class<D> type);

    public int getNumDrivers();

    public GenericDevice getDefaultDevice();

    public TornadoObjectState resolveObject(Object object);

}
