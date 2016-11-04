/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.PlatformKind;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.lir.Opcode;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;

@Opcode("TPRINTF")
public class OCLTPrintf implements OCLEmitable {

    private Value[] inputs;
    
    public OCLTPrintf(Value[] inputs){
        this.inputs = inputs;
    }
    
    @Override
    public void emit(OCLCompilationResultBuilder crb) {
        OCLAssembler asm = crb.getAssembler();
        asm.emit("if( ");
        asm.emit("get_global_id(0) == ");
        asm.value(crb, inputs[0]);
        
        asm.emit(" && get_global_id(1) == ");
        asm.value(crb, inputs[1]);
        
        asm.emit(" && get_global_id(2) == ");
        asm.value(crb, inputs[2]);
        asm.emit(" )");
        asm.beginScope();
        
        asm.indent();
        asm.emit("printf( \"tornado[%3d,%3d,%3d]> ");
        asm.value(crb, inputs[3]);
        asm.emit("\", ");
        for(int i=0;i<3;i++){
            asm.value(crb, inputs[i]);
            asm.emit(", ");
        }
        for(int i=4;i<inputs.length-1;i++){
            asm.value(crb, inputs[i]);
            asm.emit(", ");
        }
        asm.value(crb, inputs[inputs.length-1]);
        asm.emit(")");
        asm.delimiter();
        asm.eol();
        asm.endScope();
        
    }

    @Override
    public LIRKind getLIRKind() {
        return LIRKind.Illegal;
    }

    @Override
    public PlatformKind getPlatformKind() {
        return OCLKind.ILLEGAL;
    }

    @Override
    public Kind getKind() {
        return Kind.Illegal;
    }
    
    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<%s,%s,%s> tprintf( %s",inputs[0],inputs[1],inputs[2], inputs[3]));
        for(int i=4;i<inputs.length-1;i++){
            sb.append(inputs[i]);
            sb.append(", ");
        }
        sb.append( inputs[inputs.length-1]);
        sb.append(" )");
        return sb.toString();
    }
    
}
