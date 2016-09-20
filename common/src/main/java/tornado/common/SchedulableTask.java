package tornado.common;

import tornado.common.enums.Access;
import tornado.meta.Meta;

public interface SchedulableTask  {
    public Object[] getArguments();

    public Access[] getArgumentsAccess();

    public Meta meta();

    public SchedulableTask mapTo(DeviceMapping mapping);

    public DeviceMapping getDeviceMapping();
    
    public String getName();

}
