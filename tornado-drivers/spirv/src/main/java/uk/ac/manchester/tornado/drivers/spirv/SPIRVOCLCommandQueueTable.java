package uk.ac.manchester.tornado.drivers.spirv;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.opencl.OCLCommandQueue;
import uk.ac.manchester.tornado.drivers.opencl.OCLContext;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.OCLException;

public class SPIRVOCLCommandQueueTable {

    private final Map<SPIRVOCLDevice, ThreadCommandQueueTable> deviceCommandMap;

    public SPIRVOCLCommandQueueTable() {
        deviceCommandMap = new ConcurrentHashMap<>();
    }

    public OCLCommandQueue get(SPIRVOCLDevice device, OCLContext context) {
        if (!deviceCommandMap.containsKey(device)) {
            ThreadCommandQueueTable table = new ThreadCommandQueueTable();
            table.get(Thread.currentThread().threadId(), device, context);
            deviceCommandMap.put(device, table);
        }
        return deviceCommandMap.get(device).get(Thread.currentThread().threadId(), device, context);
    }

    private static class ThreadCommandQueueTable {
        private final Map<Long, OCLCommandQueue> commandQueueMap;

        ThreadCommandQueueTable() {
            commandQueueMap = new ConcurrentHashMap<>();
        }

        public OCLCommandQueue get(long threadId, SPIRVOCLDevice device, OCLContext context) {
            if (!commandQueueMap.containsKey(threadId)) {
                final int deviceVersion = device.deviceVersion();
                long commandProperties = context.getProperties();
                long commandQueuePtr;
                try {
                    commandQueuePtr = context.clCreateCommandQueue(context.getContextId(), device.getId(), commandProperties);
                } catch (OCLException e) {
                    throw new TornadoRuntimeException(e);
                }
                OCLCommandQueue commandQueue = new OCLCommandQueue(commandQueuePtr, commandProperties, deviceVersion);
                commandQueueMap.put(threadId, commandQueue);
            }
            return commandQueueMap.get(threadId);
        }
    }
}