
package tornado.drivers.opencl.graal;

import tornado.drivers.opencl.graal.lir.OCLKind;


public class OCLStampFactory {
    
    private static final OCLStamp[] stamps = new OCLStamp[OCLKind.values().length];
    
    public static OCLStamp getStampFor(OCLKind kind){
        int index = 0;
        for(OCLKind oclKind : OCLKind.values()){
            if(oclKind == kind){
                break;
            }
            index++;
        }
        
        if(stamps[index] == null){
            stamps[index] = new OCLStamp(kind);
        }
        
        return stamps[index];
    }
    
}
