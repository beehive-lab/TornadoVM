package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.mm.TornadoMemoryProvider;
import uk.ac.manchester.tornado.drivers.cuda.runtime.CUDATornadoDevice;
import uk.ac.manchester.tornado.runtime.common.Initialisable;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class CUDADeviceContext
        extends TornadoLogger implements Initialisable, TornadoDeviceContext {
    @Override public TornadoMemoryProvider getMemoryManager() {
        return null;
    }

    @Override public boolean needsBump() {
        return false;
    }

    @Override public boolean isInitialised() {
        return false;
    }

    public CUDATornadoDevice asMapping() {
        return null;
    }
}
