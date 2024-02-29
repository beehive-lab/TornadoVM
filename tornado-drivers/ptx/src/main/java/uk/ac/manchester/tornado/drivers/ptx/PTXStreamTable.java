package uk.ac.manchester.tornado.drivers.ptx;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PTXStreamTable {

    private final Map<PTXDevice, ThreadStreamTable> deviceStream;

    PTXStreamTable() {
        deviceStream = new ConcurrentHashMap<>();
    }

    public PTXStream get(PTXDevice device) {
        if (Thread.currentThread().getName().equals("PTX-Cleanup-Thread")) {
            return null;
        }
        if (!deviceStream.containsKey(device)) {
            ThreadStreamTable threadStreamTable = new ThreadStreamTable();
            threadStreamTable.get(Thread.currentThread().threadId());
            deviceStream.put(device, threadStreamTable);
        }
        return deviceStream.get(device).get(Thread.currentThread().threadId());
    }

    private static class ThreadStreamTable {

        private final Map<Long, PTXStream> streamTable;

        ThreadStreamTable() {
            streamTable = new ConcurrentHashMap<>();
        }

        public PTXStream get(long threadId) {
            if (!streamTable.containsKey(threadId)) {
                PTXStream stream = new PTXStream();
                streamTable.put(threadId, stream);
            }
            return streamTable.get(threadId);
        }

    }
}
