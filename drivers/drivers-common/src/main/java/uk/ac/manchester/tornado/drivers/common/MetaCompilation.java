package uk.ac.manchester.tornado.drivers.common;

import jdk.vm.ci.code.InstalledCode;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class MetaCompilation {
    TaskMetaData taskMeta;
    InstalledCode installedCode;

    public MetaCompilation(TaskMetaData taskMeta, InstalledCode installedCode) {
        this.taskMeta = taskMeta;
        this.installedCode = installedCode;
    }

    public TaskMetaData getTaskMeta() {
        return taskMeta;
    }

    public InstalledCode getInstalledCode() {
        return installedCode;
    }
}