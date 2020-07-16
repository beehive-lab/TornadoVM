package uk.ac.manchester.tornado.drivers.ptx.graal.compiler;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.code.CompilationResult;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PTXCompilationResult extends CompilationResult {

    private Set<ResolvedJavaMethod> nonInlinedMethods;
    private TaskMetaData taskMetaData;

    public PTXCompilationResult(String functionName, TaskMetaData taskMetaData) {
        super(functionName);
        this.taskMetaData = taskMetaData;
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

    public TaskMetaData getTaskMeta() {
        return taskMetaData;
    }
}
