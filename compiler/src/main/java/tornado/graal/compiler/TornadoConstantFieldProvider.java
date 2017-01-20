package tornado.graal.compiler;

import com.oracle.graal.compiler.common.spi.ConstantFieldProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

public class TornadoConstantFieldProvider implements ConstantFieldProvider {

    @Override
    public <T> T readConstantField(ResolvedJavaField resolvedField, ConstantFieldTool<T> tool) {
        JavaConstant ret = tool.readValue();
        return tool.foldConstant(ret);

    }

}
