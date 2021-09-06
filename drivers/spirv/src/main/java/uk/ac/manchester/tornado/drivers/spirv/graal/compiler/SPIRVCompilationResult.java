package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;

import org.graalvm.compiler.code.CompilationResult;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

/**
 * Object that represents the result of a SPIRV compilation (from GraalIR to
 * SPIR-V binary) after all optimizations phases.
 * 
 * This object stores the set of methods that were compiled as well as the
 * compilation ID that TornadoVM sets for each task within the task-schedule.
 */
public class SPIRVCompilationResult extends CompilationResult {

    private Set<ResolvedJavaMethod> nonInlinedMethods;
    private TaskMetaData taskMetaData;
    private String id;
    private ByteBuffer spirvBinary;

    public SPIRVCompilationResult(String id, String methodName, TaskMetaData taskMetaData) {
        super(methodName);
        this.id = id;
        this.taskMetaData = taskMetaData;
    }

    public Set<ResolvedJavaMethod> getNonInlinedMethods() {
        return nonInlinedMethods;
    }

    public void setNonInlinedMethods(Set<ResolvedJavaMethod> value) {
        nonInlinedMethods = value;
    }

    // FIXME: <REFACTOR> Common in the three backends
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
        return id;
    }

    public byte[] getSPIRVBinary() {
        return spirvBinary.array();
    }

    public void setSPIRVBinary(ByteBuffer spirvByteBuffer) {
        this.spirvBinary = spirvByteBuffer;
    }
}
