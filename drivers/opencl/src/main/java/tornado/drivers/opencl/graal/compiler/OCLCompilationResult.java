package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.code.CompilationResult;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.meta.Meta;

public class OCLCompilationResult extends CompilationResult {

    protected Set<ResolvedJavaMethod> nonInlinedMethods;
    protected Meta meta;
    protected OCLBackend backend;
    protected String id;

    public OCLCompilationResult(String id, String name, Meta meta, OCLBackend backend) {
        super(name);
        this.id = id;
        this.meta = meta;
        this.backend = backend;
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

    public Meta getMeta() {
        return meta;
    }

    public OCLBackend getBackend() {
        return backend;
    }

    public String getId() {
        return id;
    }
}
