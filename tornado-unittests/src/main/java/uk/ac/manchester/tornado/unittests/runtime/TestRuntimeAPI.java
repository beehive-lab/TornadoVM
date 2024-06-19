package uk.ac.manchester.tornado.unittests.runtime;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TornadoRuntime;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestRuntimeAPI extends TornadoTestBase {

    @Test
    public void test01() {

        TornadoRuntime runtimeInterface = TornadoRuntimeProvider.getTornadoRuntime();

        runtimeInterface.getBackend(0).getNumDevices();
    }
}
