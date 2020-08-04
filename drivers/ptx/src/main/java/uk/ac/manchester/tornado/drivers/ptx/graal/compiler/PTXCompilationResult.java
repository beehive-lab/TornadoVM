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

import static uk.ac.manchester.tornado.drivers.ptx.graal.PTXCodeUtil.appendToTargetCodeBegin;
import static uk.ac.manchester.tornado.drivers.ptx.graal.PTXCodeUtil.getCodeWithPTXHeader;

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
        byte[] newCode = appendToTargetCodeBegin(getTargetCode(), code);
        setTargetCode(newCode, newCode.length);
    }

    public void addPTXHeader(PTXBackend backend) {
        byte[] newCode = getCodeWithPTXHeader(getTargetCode(), backend);
        setTargetCode(newCode, newCode.length);
    }

    public TaskMetaData getTaskMeta() {
        return taskMetaData;
    }
}
