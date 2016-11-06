/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tornado.drivers.opencl.graal.asm;

import com.oracle.graal.compiler.common.LIRKind;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import tornado.drivers.opencl.graal.lir.OCLEmitable;

public class OCLConstantValue extends OCLEmitable {

    private final String value;

    public OCLConstantValue(String value) {
        super(LIRKind.Illegal);
        this.value = value;
    }

    @Override
    public void emit(OCLCompilationResultBuilder crb, OCLAssembler asm) {
        asm.emit(value);
    }

    public String getValue() {
        return value;
    }
}
