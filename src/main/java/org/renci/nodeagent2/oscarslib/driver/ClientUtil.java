package org.renci.nodeagent2.oscarslib.driver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.es.oscars.api.soap.gen.v06.OSCARS;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.binding.soap.SoapBindingFactory;
import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.ws.security.handler.WSHandlerConstants;

public class ClientUtil {
    private static ClientUtil instance;

    HashMap<String, OSCARS> oscarsPorts = new HashMap<String, OSCARS>();

    public static ClientUtil getInstance() {
        if (instance == null) {
            instance = new ClientUtil();
        }
        return instance;
    }

    public synchronized OSCARS getOSCARSPort(String url, 
    		String keystorePath, 
    		String truststorePath,
    		String alias, 
    		String pass, boolean logging) {
        if (oscarsPorts.get(url) == null) {
            oscarsPorts.put(url, createOscarsPort(url, keystorePath, truststorePath, alias, pass, logging));
        }
        return oscarsPorts.get(url);
    }

    protected OSCARS createOscarsPort(String url, String keystorePath, String truststorePath, 
    		String alias, String pass, boolean logging) {

    	System.setProperty("javax.net.ssl.trustStore", truststorePath);

        CXFBusFactory bf = new CXFBusFactory();
        Bus bus = bf.createBus();
        
        BusFactory.setDefaultBus(bus);

        JaxWsProxyFactoryBean fb = new JaxWsProxyFactoryBean();
        fb.setBindingId(SoapBindingFactory.SOAP_12_BINDING);

        fb.setServiceClass(OSCARS.class);
        fb.setAddress(url);
        
        Map<String, Object> interProps = new HashMap<String, Object>();
        interProps.put(WSHandlerConstants.SIGNATURE_USER, alias);
        interProps.put(WSHandlerConstants.ACTION, WSHandlerConstants.TIMESTAMP + " " + WSHandlerConstants.SIGNATURE);
        interProps.put(WSHandlerConstants.TTL_TIMESTAMP, "60");
        interProps.put(WSHandlerConstants.SIG_KEY_ID, "DirectReference");
        interProps.put(WSHandlerConstants.SIG_ALGO, "http://www.w3.org/2000/09/xmldsig#rsa-sha1");
        interProps.put(WSHandlerConstants.SIG_DIGEST_ALGO, "http://www.w3.org/2000/09/xmldsig#sha1");
        interProps.put(WSHandlerConstants.PW_CALLBACK_CLASS, ConfiguredCallbackHandler.class.getName());
        
        // fill out crypto properties
        Properties cp = new Properties();
        cp.put("org.apache.ws.security.crypto.provider", "org.apache.ws.security.components.crypto.Merlin");
        cp.put("org.apache.ws.security.crypto.merlin.keystore.type", "jks");
        cp.put("org.apache.ws.security.crypto.merlin.keystore.file", keystorePath);
        cp.put("org.apache.ws.security.crypto.merlin.keystore.password", pass);
        cp.put("org.apache.ws.security.crypto.merlin.keystore.alias", alias);
        cp.put("org.apache.ws.security.crypto.merlin.load.cacerts", "false");
        cp.put("org.apache.ws.security.crypto.merlin.truststore.type", "jks");
        cp.put("org.apache.ws.security.crypto.merlin.truststore.file", truststorePath);
        cp.put("org.apache.ws.security.crypto.merlin.truststore.password", pass);

        interProps.put(WSHandlerConstants.SIG_PROP_REF_ID, "cryptoProps");
        interProps.put("cryptoProps", cp);
        
        WSS4JOutInterceptor inter = new WSS4JOutInterceptor(interProps);
        List<Interceptor<?>> li = new ArrayList<Interceptor<?>>();
        li.add(inter);
        
        fb.setOutInterceptors(li);
        
        if (logging) enableLogging(fb);

        OSCARS port = (OSCARS) fb.create();

        //System.setProperty("javax.net.ssl.trustStore", oldVal);
        
        return port;
    }

    private void enableLogging(JaxWsProxyFactoryBean fb) {
        LoggingInInterceptor in = new LoggingInInterceptor();
        LoggingOutInterceptor out = new LoggingOutInterceptor();

        in.setPrettyLogging(true);
        out.setPrettyLogging(true);

        fb.getInInterceptors().add(in);
        fb.getOutInterceptors().add(out);
        fb.getInFaultInterceptors().add(in);
        fb.getOutFaultInterceptors().add(out);
    }

    /**
     * Configures SSL and other basic client settings
     * @param urlString the URL of the server to contact
     */
    private void prepareBus(String urlString, String busFile) {

        System.setProperty("javax.net.ssl.trustStore","DoNotUsecacerts");

        CXFBusFactory bf = new CXFBusFactory();
        Bus bus = bf.createBus();
        
        bf.setDefaultBus(bus);
    }

}
