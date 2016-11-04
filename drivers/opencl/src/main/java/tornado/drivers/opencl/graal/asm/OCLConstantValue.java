/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tornado.drivers.opencl.graal.asm;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.PlatformKind;
import com.oracle.graal.api.meta.Value;
import tornado.drivers.opencl.graal.lir.OCLKind;

public class OCLConstantValue implements Value {

    
    
    private final String value;
    
    public OCLConstantValue(String value){
        this.value = value;
    }
    
    public String getValue(){
        return value;
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
    
}
