package uk.ac.manchester.tornado.drivers.graal;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.compiler.word.WordTypes;

public class TornadoWordTypes extends WordTypes {

    public TornadoWordTypes(MetaAccessProvider metaAccess, JavaKind wordKind) {
        super(metaAccess, wordKind);
    }
}
