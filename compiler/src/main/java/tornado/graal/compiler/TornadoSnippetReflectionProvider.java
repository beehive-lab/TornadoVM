package tornado.graal.compiler;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class TornadoSnippetReflectionProvider implements SnippetReflectionProvider {

    @Override
    public <T> T asObject(Class<T> type, JavaConstant jc) {
        unimplemented();
        return null;
    }

    @Override
    public Object asObject(ResolvedJavaType type, JavaConstant constant) {
        unimplemented();
        return null;
    }

    @Override
    public JavaConstant forBoxed(JavaKind kind, Object value) {
        unimplemented();
        return null;
    }

    @Override
    public JavaConstant forObject(Object object) {
        unimplemented();
        return null;
    }

    @Override
    public <T> T getInjectedNodeIntrinsicParameter(Class<T> type) {
        unimplemented();
        return null;
    }

}
