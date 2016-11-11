package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.code.CompilationResult;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class OCLCompilationResult extends CompilationResult {

    protected Set<ResolvedJavaMethod> nonInlinedMethods;

    public OCLCompilationResult(String name) {
        super(name);
    }

    public Set<ResolvedJavaMethod> getNonInlinedMethods() {
        return nonInlinedMethods;
    }

    public void setNonInlinedMethods(Set<ResolvedJavaMethod> value) {
        nonInlinedMethods = value;
    }

    public void addCompiledMethodCode(byte[] code) {
        final byte[] oldCode = getTargetCode();
        final int size = oldCode.length + code.length + 1;

        final byte[] newCode = new byte[size];
        Arrays.fill(newCode, (byte) 0);

        final ByteBuffer buffer = ByteBuffer.wrap(newCode);
        buffer.put(code);
        buffer.put((byte) '\n');
        buffer.put(oldCode);
        setTargetCode(newCode, size);
    }
}
