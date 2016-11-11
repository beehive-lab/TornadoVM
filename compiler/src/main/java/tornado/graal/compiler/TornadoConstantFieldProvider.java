package tornado.graal.compiler;

import com.oracle.graal.compiler.common.spi.ConstantFieldProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class TornadoConstantFieldProvider implements ConstantFieldProvider {

    @Override
    public <T> T readConstantField(ResolvedJavaField resolvedField, ConstantFieldTool<T> tool) {
        unimplemented();
        return null;
    }

}
