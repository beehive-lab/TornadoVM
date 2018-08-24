package uk.ac.manchester.tornado.api;

import uk.ac.manchester.tornado.api.mm.TornadoMemoryProvider;

public interface TornadoDeviceContext {

    TornadoMemoryProvider getMemoryManager();

    public boolean needsBump();

}
