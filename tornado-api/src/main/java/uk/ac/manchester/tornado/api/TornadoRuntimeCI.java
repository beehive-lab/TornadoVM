package uk.ac.manchester.tornado.api;

import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.mm.TornadoGlobalObjectState;

public interface TornadoRuntimeCI {

    public TornadoRuntimeCI callRuntime();

    public void clearObjectState();

    public TornadoDriver getDriver(int index);

    public <D extends TornadoDriver> D getDriver(Class<D> type);

    public int getNumDrivers();

    public TornadoDevice getDefaultDevice();

    public TornadoGlobalObjectState resolveObject(Object object);

}
