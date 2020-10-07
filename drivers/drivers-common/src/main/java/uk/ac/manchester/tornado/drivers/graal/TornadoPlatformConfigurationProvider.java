package uk.ac.manchester.tornado.drivers.graal;

import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;

public class TornadoPlatformConfigurationProvider implements PlatformConfigurationProvider {

    @Override
    public BarrierSet getBarrierSet() {
        return null;
    }

    @Override
    public boolean canVirtualizeLargeByteArrayAccess() {
        return false;
    }
}
