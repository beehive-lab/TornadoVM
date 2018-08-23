package uk.ac.manchester.tornado.api.runtinface;

public interface TornadoCI {

    public void setTornadoProperty(String key, String value);

    public String getTorandoProperty(String key);

    public String getTornadoProperty(String key, String defaultValue);

}
