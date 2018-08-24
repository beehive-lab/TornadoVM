package uk.ac.manchester.tornado.api.mm;

import java.util.List;

import uk.ac.manchester.tornado.api.common.TornadoEvents;

public interface TaskMetaDataInterface {

    public List<TornadoEvents> getProfiles();

    // Think a better place for these methods
    public String getCompilerFlags();

    public void setOpenclCompilerFlags(String flags);

    public void setGlobalWork(long[] global);

    public void setLocalWork(long[] local);

    public long[] getGlobalWork();

    public long[] getLocalWork();
}
