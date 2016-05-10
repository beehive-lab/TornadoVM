package tornado.drivers.opencl.graal.lir;

import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

import com.oracle.graal.api.meta.Value;

public interface OCLEmitable  extends Value {

     public void emit(OCLCompilationResultBuilder crb);
     
}
