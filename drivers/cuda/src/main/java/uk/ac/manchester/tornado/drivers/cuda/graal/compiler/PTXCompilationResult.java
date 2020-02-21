package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.code.CompilationResult;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PTXCompilationResult extends CompilationResult {

    private Set<ResolvedJavaMethod> nonInlinedMethods;

    public PTXCompilationResult(String functionName) {
        super(functionName);
    }

    public PTXCompilationResult(String functionName, String s) {
        super(functionName);
        byte[] code = s.getBytes();
        this.setTargetCode(code, code.length);
    }

    public void setNonInlinedMethods(Set<ResolvedJavaMethod> value) {
        nonInlinedMethods = value;
    }

    public Set<ResolvedJavaMethod> getNonInlinedMethods() {
        return (nonInlinedMethods != null) ? nonInlinedMethods: new HashSet<>();
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
