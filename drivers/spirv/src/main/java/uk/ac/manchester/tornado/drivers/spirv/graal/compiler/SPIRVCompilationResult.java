package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;

import org.graalvm.compiler.code.CompilationResult;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVCompilationResult extends CompilationResult {

    private Set<ResolvedJavaMethod> nonInlinedMethods;
    private TaskMetaData taskMetaData;

    public SPIRVCompilationResult(String methodName, TaskMetaData taskMetaData) {
        super(methodName);
        this.taskMetaData = taskMetaData;
    }

    public Set<ResolvedJavaMethod> getNonInlinedMethods() {
        return nonInlinedMethods;
    }

    public void setNonInlinedMethods(Set<ResolvedJavaMethod> value) {
        nonInlinedMethods = value;
    }

    private byte[] prependToTargetCode(byte[] targetCode, byte[] codeToPrepend) {
        final int size = targetCode.length + codeToPrepend.length + 1;

        final byte[] newCode = new byte[size];
        Arrays.fill(newCode, (byte) 0);

        final ByteBuffer buffer = ByteBuffer.wrap(newCode);
        buffer.put(codeToPrepend);
        buffer.put((byte) '\n');
        buffer.put(targetCode);
        return newCode;
    }

    public void addCompiledMethodCode(byte[] code) {
        byte[] newCode = prependToTargetCode(getTargetCode(), code);
        setTargetCode(newCode, newCode.length);
    }

    public TaskMetaData getTaskMetaData() {
        return this.taskMetaData;
    }

    public TaskMetaData getMeta() {
        return taskMetaData;
    }

    public String getId() {
        return getCompilationId().toString();
    }
}
