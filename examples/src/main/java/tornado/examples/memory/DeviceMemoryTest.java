package tornado.examples.memory;

import tornado.api.Parallel;
import tornado.api.ReadWrite;
import static tornado.common.RuntimeUtilities.humanReadableByteCount;
import tornado.drivers.opencl.OpenCL;
import tornado.drivers.opencl.mm.OCLMemoryManager;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;
import tornado.runtime.api.TaskGraph;

public class DeviceMemoryTest {
    
    public static void main(final String[] args){
        
        final OCLDeviceMapping device = OpenCL.defaultDevice();
        final OCLMemoryManager mm = device.getDeviceContext().getMemoryManager();
        
        final long heapSize = mm.getHeapSize() - 1024;
        
        final int numWords = (int) (heapSize >> 2);
        
        System.out.printf("device memory test:\n\tdevice: %s\n\tmax heap=%s\n\tnum words=%d\n",device.getDevice().getName(),humanReadableByteCount(heapSize,false),numWords);
        
        final int[] data = new int[numWords];
        
        
        
        final TaskGraph graph = new TaskGraph()
                .streamIn(data)
                .add(DeviceMemoryTest::fill,data)
                .streamOut(data)
                .mapAllTo(device);
        
        graph.warmup();
        
        
        intialise(data);
        graph.schedule().waitOn();
        
        validate(data);
        
        
        
    }
    
    private static void fill(@ReadWrite int[] data){
        for(@Parallel int i=0;i<data.length;i++){
            data[i] = i;
        }
    }

    private static void intialise(int[] data) {
        for(int i=0;i<data.length;i++){
            data[i] = 0;
        }
    }

    private static void validate(int[] data) {
        int errors = 0;
        int first = -1;
        for(int i=0;i<data.length;i++){
            if(data[i] != i){
                errors++;
                if(first == -1){
                    first = i;
                }
            }
        }
        
        System.out.printf("data=%s, errors=%d, first=%d\n",humanReadableByteCount(data.length << 2, false),errors,first);
    }
    
}
