package orca.nodeagent2.oscarslib;


import java.util.List;

import orca.nodeagent2.oscarslib.driver.ClientConfig;
import orca.nodeagent2.oscarslib.driver.ClientConfigHolder;
import orca.nodeagent2.oscarslib.driver.ClientUtil;
import orca.nodeagent2.oscarslib.driver.ResvStatus;
import net.es.oscars.api.soap.gen.v06.ListReply;
import net.es.oscars.api.soap.gen.v06.ListRequest;
import net.es.oscars.api.soap.gen.v06.OSCARS;
import net.es.oscars.api.soap.gen.v06.ResCreateContent;
import net.es.oscars.api.soap.gen.v06.ResDetails;


public class ListCmd {

    public void run(String[] args) throws Exception {
        for (String arg : args) {
            System.out.println("list: "+arg);
        }
        
        ListRequest request = new ListRequest();
        request.getResStatus().add(ResvStatus.STATUS_ACTIVE);
        //request.setResRequested(1);

        //String serviceUrl = "https://oscars.es.net/OSCARS";
        String serviceUrl = "https://al2s.net.internet2.edu:9001/OSCARS";

        ClientConfigHolder cch = ClientConfigHolder.getInstance();
        ClientConfig cc = new ClientConfig();
        cc.setKeyPassword("VeryTough55!");
        cch.addClientConfig(serviceUrl, cc);
        cch.setActiveClientConfig(serviceUrl);

        OSCARS client = ClientUtil.getInstance().getOSCARSPort(serviceUrl, 
        		"/Users/ibaldin/workspace-oscars/oscars-lib/oscars-client/config/samples/oscars-orca3.jks", 
        		"/Users/ibaldin/workspace-oscars/oscars-lib/oscars-client/config/samples/oscars-orca3.jks",
        		"orca", "VeryTough55!", true);
        //send request
        ListReply reply = client.listReservations(request);
        
        ResCreateContent rcc;
        
        List<ResDetails> resd = reply.getResDetails();
        
        for(ResDetails rs: resd) {
        	System.out.println(rs.getGlobalReservationId() + " " + rs.getCreateTime());
        }
    }
    
    public static void main(String[] argv) {
    	
    	ListCmd l = new ListCmd();
    	
    	try {
    		l.run(argv);
    	} catch (Exception e) {
    		System.err.println("Exception: " + e);
    	}
    }
}