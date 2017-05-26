package org.renci.nodeagent2.oscarslib.driver;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.apache.wss4j.common.ext.WSPasswordCallback;

public class ConfiguredCallbackHandler implements CallbackHandler {

    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {

        ClientConfig cc = ClientConfigHolder.getInstance().getActiveClientConfig();
        String keyPassword = cc.getKeyPassword();

        //for (Object o: callbacks) {
        //	System.out.println("TYPE OF " + o.getClass().getName());
        //}
        
        WSPasswordCallback pc = (WSPasswordCallback) callbacks[0];
        pc.setPassword(keyPassword);
        //System.out.println("EXITING");
    }

}
