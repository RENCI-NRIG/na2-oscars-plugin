package org.renci.nodeagent2.oscarslib.driver;

import java.util.HashMap;

public class ClientConfigHolder {
    private ClientConfigHolder() {

    }
    private static ClientConfigHolder instance = null;
    public static ClientConfigHolder getInstance() {
        if (instance == null) {
            instance = new ClientConfigHolder();
        }
        return instance;
    }

    private HashMap<String, ClientConfig> clientConfigs = new HashMap<String, ClientConfig>();
    private ClientConfig activeClientConfig;

    public ClientConfig getActiveClientConfig() {
        return activeClientConfig;
    }

    public void addClientConfig(String url, ClientConfig cc) {
        clientConfigs.put(url, cc);
    }

    public void setActiveClientConfig(String url) {
        activeClientConfig = clientConfigs.get(url);
    }


}
