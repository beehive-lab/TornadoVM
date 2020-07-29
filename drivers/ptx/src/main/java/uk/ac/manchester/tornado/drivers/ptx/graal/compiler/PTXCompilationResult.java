package uk.ac.manchester.tornado.drivers.ptx.graal.compiler;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.code.CompilationResult;
import uk.ac.manchester.tornado.drivers.ptx.PTXDevice;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants;
import uk.ac.manchester.tornado.drivers.ptx.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PTXCompilationResult extends CompilationResult {

    private static final String PTX_HEADER_FORMAT =
            PTXAssemblerConstants.COMPUTE_VERSION + " %s \n" +
            PTXAssemblerConstants.TARGET_ARCH + " %s \n" +
            PTXAssemblerConstants.ADDRESS_HEADER + " %s \n";

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
        appendToTargetCodeBegin(code);
    }

    public void addPTXHeader(PTXBackend backend) {
        PTXDevice device = backend.getDeviceContext().getDevice();
        String header = String.format(PTX_HEADER_FORMAT, device.getTargetPTXVersion(), device.getTargetArchitecture(), backend.getTarget().getArch().getWordSize() * 8);

        appendToTargetCodeBegin(header.getBytes());
    }

    private void appendToTargetCodeBegin(byte[] code) {
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
