package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import org.graalvm.compiler.code.CompilationResult;

public class PTXCompilationResult extends CompilationResult {

    public PTXCompilationResult(String functionName, String s) {
        super(functionName);
        byte[] code = s.getBytes();
        this.setTargetCode(code, code.length);
    }
}
