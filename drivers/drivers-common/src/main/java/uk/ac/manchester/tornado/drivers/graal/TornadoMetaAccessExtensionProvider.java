package uk.ac.manchester.tornado.drivers.graal;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

public class TornadoMetaAccessExtensionProvider implements MetaAccessExtensionProvider {

    @Override
    public JavaKind getStorageKind(JavaType type) {
        return type.getJavaKind();
    }

    @Override
    public boolean canConstantFoldDynamicAllocation(ResolvedJavaType type) {
        unimplemented();
        return false;
    }
}
