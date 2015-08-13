package orca.nodeagent2.oscarslib;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import net.es.oscars.api.soap.gen.v06.ResDetails;
import net.es.oscars.common.soap.gen.OSCARSFaultMessage;
import orca.nodeagent2.agentlib.Plugin;
import orca.nodeagent2.agentlib.PluginException;
import orca.nodeagent2.agentlib.PluginReturn;
import orca.nodeagent2.agentlib.Properties;
import orca.nodeagent2.agentlib.ReservationId;
import orca.nodeagent2.agentlib.Util;
import orca.nodeagent2.oscarslib.driver.Driver;

import org.apache.commons.logging.Log;

public class Main implements Plugin {

	private static final String ENDTIME_PROP = "oscars.end.time";
	private static final String CREATETIME_PROP = "oscars.create.time";
	private static final String STATUS_PROP = "oscars.status";
	private static final String GRI_PROP = "oscars.gri";
	private static final String TAGZ_PROP = "oscars.tagZ";
	private static final String TAGA_PROP = "oscars.tagA";
	private static final String EPZ_PROP = "oscars.endpointZ";
	private static final String EPA_PROP = "oscars.endpointA";
	private static final String BW_PROP = "oscars.bw";
	private static final String IDC_PROP = "oscars.ctrl.to.call";
	private static final String PASSWORD_PROP = "oscars.password";
	private static final String ALIAS_PROP = "oscars.alias";
	private static final String KEYSTORE_PROP = "oscars.keystore";
	private static final String TRUSTSTORE_PROP = "oscars.truststore";
	private static final String DESCRIPTION_PROP = "oscars.description";
	private static final String LOGGING_PROP = "oscars.logging";
	private static final int POLL_INTERVAL=10;
	Log log;
	Map<String, Driver> driverCache;
	Properties configProperties;
	boolean oscarsLogging = false;

	public void initialize(String config, Properties configProps)
			throws PluginException {
		try {
			log = Util.getLog("oscars");
			//log = LogFactory.getLog("this");
			log.info("Initializing OSCARS plugin");

			driverCache = new HashMap<String, Driver>();
			// save for the future
			configProperties = configProps;

			// validate required properties
			if (!configProperties.containsKey(TRUSTSTORE_PROP) ||
					!configProperties.containsKey(KEYSTORE_PROP) ||
					!configProperties.containsKey(ALIAS_PROP) ||
					!configProperties.containsKey(PASSWORD_PROP) || 
					!configProperties.containsKey(DESCRIPTION_PROP))
				throw new PluginException("Plugin configuration must specify the following properties: " + 
						TRUSTSTORE_PROP + ", " + KEYSTORE_PROP + ", " + ALIAS_PROP + ", " + PASSWORD_PROP + ", " + DESCRIPTION_PROP);
			
			if (configProperties.containsKey(LOGGING_PROP) &&
					"true".equalsIgnoreCase(configProperties.get(LOGGING_PROP)))
				oscarsLogging = true;
		} catch (Exception e) {
			throw new PluginException("Unable to initialize oscars: " + e);
		}
		log.debug("Initializing oscars plugin with properties " + configProps);
	}

	/** 
	 * Find or create a driver for this idc url
	 * @param idcUrl
	 */
	private Driver getDriver(String idcUrl) {
		if (!driverCache.containsKey(idcUrl)) {
			log.info("Creating driver for " + idcUrl + " " + configProperties.get(TRUSTSTORE_PROP) + " " + configProperties.get(KEYSTORE_PROP) + " " + configProperties.get(ALIAS_PROP));
			driverCache.put(idcUrl,  
					new Driver(idcUrl, 
							configProperties.get(TRUSTSTORE_PROP),
							configProperties.get(KEYSTORE_PROP),
							configProperties.get(ALIAS_PROP),
							configProperties.get(PASSWORD_PROP), oscarsLogging));
		}
		return driverCache.get(idcUrl);
	}

	/**
	 * Try to find IDC among caller and schedule properties (try sched properties first)
	 * @param primary
	 * @param secondary
	 * @return
	 * @throws PluginException
	 */
	private Driver getDriver(Properties callerProps, Properties schedProps) throws PluginException {
		String idc = null;
		
		if (schedProps != null)
			idc = schedProps.get(IDC_PROP);

		if ((schedProps == null) || (schedProps.get(IDC_PROP) == null)) {
			if (callerProps != null)
				idc = callerProps.get(IDC_PROP);
		}

		if (idc == null) {
			log.debug("Caller Properties: " + callerProps);
			log.debug("Schedule Properties: " + schedProps);
			throw new PluginException("Unable to determine IDC URL - not found in saved or caller properties");
		}
		
		// get the saved IDC info
		return getDriver(idc);
	}

	/**
	 * Create OSCARS circuit
	 */
	public PluginReturn join(Date end, Properties callerProps) throws PluginException {

		if (!callerProps.containsKey(IDC_PROP) ||
				!callerProps.containsKey(BW_PROP) ||
				!callerProps.containsKey(EPA_PROP) ||
				!callerProps.containsKey(EPZ_PROP) ||
				!callerProps.containsKey(TAGA_PROP) ||
				!callerProps.containsKey(TAGZ_PROP))
			throw new PluginException("Insufficient configuration prameters for join operation: idc, bw, endpoint[AZ] and tag[AZ] must be specified");

		if ((callerProps.get(IDC_PROP).length() == 0) ||
				(callerProps.get(BW_PROP).length() == 0) ||
				(callerProps.get(EPA_PROP).length() == 0) ||
				(callerProps.get(EPZ_PROP).length() == 0) ||
				(callerProps.get(TAGA_PROP).length() == 0) ||
				(callerProps.get(TAGZ_PROP).length() == 0))
			throw new PluginException("Insufficient configuration prameters for join operation: idc, bw, endpoint[AZ] and tag[AZ] must be non-zero length");
		
		Driver d = getDriver(callerProps, null);

		try {
			// convert bandwidth and vlan tags to ints
			int bw = Integer.parseInt(callerProps.get(BW_PROP));
			int tagA = Integer.parseInt(callerProps.get(TAGA_PROP));
			int tagZ = Integer.parseInt(callerProps.get(TAGZ_PROP));

			log.info("Creating OSCARS reservation from " + callerProps.get(EPA_PROP) + "/" + tagA + " to " + callerProps.get(EPZ_PROP) + "/" + tagZ + " with bandwidth " + bw);

			String gri = d.createReservationPoll(configProperties.get(DESCRIPTION_PROP), 
					new Date(), end, bw, 
					callerProps.get(EPA_PROP), tagA, 
					callerProps.get(EPZ_PROP), tagZ, 
					POLL_INTERVAL);

			// save properties
			Properties joinProps = new Properties();
			joinProps.putAll(callerProps);
			joinProps.put(GRI_PROP, gri);

			log.info("Created OSCARS reservation " + gri);

			String resId = gri;

			// save some properties on the PluginReturn
			PluginReturn pr = new PluginReturn(new ReservationId(resId), joinProps);

			return pr;

		} catch (OSCARSFaultMessage ofe) {
			throw new PluginException("Error: OSCARS Fault: " + ofe.getMessage() + " due to " + ofe.getCause());
		} catch (Exception e) {
			e.printStackTrace();
			throw new PluginException("Error: Generic exception: " + e.getMessage() + " due to " + e.getCause());
		}
	}


	/**
	 * Delete OSCARS circuit
	 */
	public PluginReturn leave(ReservationId rid, Properties callerProps, Properties schedProps) throws PluginException {

		Driver d = getDriver(callerProps, schedProps);

		try {
			if (schedProps.get(GRI_PROP) == null)
				throw new PluginException("Unable to cancel reservation " + rid + ": unable to find GRI in schedule properties");

			log.info("Canceling OSCARS reservation " + schedProps.get(GRI_PROP));
			d.cancelResrervation(schedProps.get(GRI_PROP), POLL_INTERVAL);

			PluginReturn pr = new PluginReturn(rid, schedProps);

			return pr;

		} catch (OSCARSFaultMessage ofe) {
			throw new PluginException("Error: OSCARS Fault: " + ofe.getMessage() + " due to " + ofe.getCause());
		} catch (Exception e) {
			throw new PluginException("Error: Generic exception: " + e.getMessage() + " due to " + e.getCause());
		}
	}

	public PluginReturn modify(ReservationId arg0, Properties arg1,
			Properties arg2) throws PluginException {
		throw new PluginException("Functionality not implemented in OSCARS");
	}

	public PluginReturn renew(ReservationId rid, Date newEnd, Properties callerProps, Properties schedProps) throws PluginException {

		Driver d = getDriver(callerProps, schedProps);

		try {
			if (schedProps.get(GRI_PROP) == null)
				throw new PluginException("Unable to renew reservation " + rid + ": unable to find GRI in schedule properties");

			log.info("Renewing OSCARS reservation " + schedProps.get(GRI_PROP) + " until " + newEnd);

			d.extendReservation(schedProps.get(GRI_PROP), newEnd, POLL_INTERVAL);

			PluginReturn pr = new PluginReturn(rid, schedProps);

			return pr;
		} catch (OSCARSFaultMessage ofe) {
			throw new PluginException("Error: OSCARS Fault: " + ofe.getMessage() + " due to " + ofe.getCause());
		} catch (Exception e) {
			throw new PluginException("Error: Generic exception: " + e.getMessage() + " due to " + e.getCause());
		}
	}

	public PluginReturn status(ReservationId rid, Properties schedProps) throws PluginException {

		Driver d = getDriver(null, schedProps);

		try {
			if (schedProps.get(GRI_PROP) == null)
				throw new PluginException("Unable to get status of reservation " + rid + ": unable to find GRI in schedule properties");

			log.info("Querying OSCARS reservation " + schedProps.get(GRI_PROP));

			ResDetails rd = d.queryReservation(schedProps.get(GRI_PROP));

			schedProps.put(STATUS_PROP, rd.getStatus());
			Date ct = new Date(rd.getUserRequestConstraint().getStartTime()*1000);
			Date et = new Date(rd.getUserRequestConstraint().getEndTime()*1000);
			schedProps.put(CREATETIME_PROP, ct.toString());
			schedProps.put(ENDTIME_PROP, et.toString());

			PluginReturn pr = new PluginReturn(rid, schedProps);

			return pr;
		} catch (OSCARSFaultMessage ofe) {
			throw new PluginException("Error: OSCARS Fault: " + ofe.getMessage() + " due to " + ofe.getCause());
		} catch (Exception e) {
			throw new PluginException("Error: Generic exception: " + e.getMessage() + " due to " + e.getCause());
		}
	}

	public static void main(String[] argv) {
		Properties all = new Properties();

		all.put(TRUSTSTORE_PROP, "");
		all.put(KEYSTORE_PROP, "");
		all.put(ALIAS_PROP, "");
		all.put(PASSWORD_PROP, "");
		all.put(DESCRIPTION_PROP, "");

		all.put(IDC_PROP, "https://al2s.net.internet2.edu:9001/OSCARS");
		all.put(EPA_PROP, "urn:ogf:network:domain=al2s.net.internet2.edu:node=sdn-sw.star.net.internet2.edu:port=eth5/2:link=*");
		all.put(EPZ_PROP, "urn:ogf:network:domain=al2s.net.internet2.edu:node=sdn-sw.houh.net.internet2.edu:port=eth7/1:link=*");
		all.put(TAGA_PROP, "1700");
		all.put(TAGZ_PROP, "880");
		all.put(BW_PROP, "100");

		boolean join=false, renew=false, leave=true;

		if (join) {
			try {
				Main p = new Main();
				p.initialize(null, all);

				Calendar c = Calendar.getInstance();
				c.add(Calendar.MINUTE, 10);
				p.join(c.getTime(), all);

			} catch (Exception ee) {
				System.err.println(ee);
				ee.printStackTrace();
			}

		}

		if (renew){
			Properties sched = new Properties();
			sched.put(GRI_PROP, argv[0]);

			Main p = new Main();
			try {
				p.initialize(null, all);
				Calendar c = Calendar.getInstance();
				c.add(Calendar.MINUTE, 30);
				p.renew(new ReservationId("some id"), c.getTime(), all, sched);
			} catch (Exception e) {
				System.err.println(e);
			}
		}
		
		if (leave){
			Properties sched = new Properties();
			sched.put(GRI_PROP, argv[0]);

			Main p = new Main();
			try {
				p.initialize(null, all);
				Calendar c = Calendar.getInstance();
				c.add(Calendar.MINUTE, 30);
				p.leave(new ReservationId("some id"), all, sched);
			} catch (Exception e) {
				System.err.println(e);
			}
		}

	}


}
