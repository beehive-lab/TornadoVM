package uk.ac.manchester.tornado.drivers.spirv.graal;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.word.WordTypes;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;

public class SPIRVProviders extends Providers {

    private final TornadoSuitesProvider suites;

    public SPIRVProviders(MetaAccessProvider metaAccess, //
            CodeCacheProvider codeCache, //
            ConstantReflectionProvider constantReflection, //
            SnippetReflectionProvider snippetReflection, //
            ConstantFieldProvider constantFieldProvider, //
            ForeignCallsProvider foreignCalls, //
            LoweringProvider lowerer, //
            Replacements replacements, //
            StampProvider stampProvider, //
            GraphBuilderConfiguration.Plugins plugins, //
            TornadoSuitesProvider suites, //
            PlatformConfigurationProvider platformConfigurationProvider, //
            MetaAccessExtensionProvider metaAccessExtensionProvider, //
            WordTypes wordTypes) {

        super(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, //
                lowerer, replacements, stampProvider, platformConfigurationProvider, //
                metaAccessExtensionProvider, snippetReflection, wordTypes);

        this.suites = suites;
    }

    public TornadoSuitesProvider getSuitesProvider() {
        return suites;
    }
}
