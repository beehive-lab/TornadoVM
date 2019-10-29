package uk.ac.manchester.tornado.drivers.cuda.graal;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.phases.util.Providers;

public class PTXProviders extends Providers {
    public PTXProviders(MetaAccessProvider metaAccess, CodeCacheProvider codeCache, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider,
            ForeignCallsProvider foreignCalls, LoweringProvider lowerer, Replacements replacements, StampProvider stampProvider) {
        super(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider);
    }
}
