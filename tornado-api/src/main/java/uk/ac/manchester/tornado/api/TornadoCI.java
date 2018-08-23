package uk.ac.manchester.tornado.api;

public interface TornadoCI {

    public void setTornadoProperty(String key, String value);

    public String getTornadoProperty(String key);

    public String getTornadoProperty(String key, String defaultValue);

    public void loadTornadoSettngs(String filename);

}
