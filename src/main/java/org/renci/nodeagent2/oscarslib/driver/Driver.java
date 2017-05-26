package org.renci.nodeagent2.oscarslib.driver;

import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import net.es.oscars.api.soap.gen.v06.CancelResContent;
import net.es.oscars.api.soap.gen.v06.CancelResReply;
import net.es.oscars.api.soap.gen.v06.CreatePathContent;
import net.es.oscars.api.soap.gen.v06.CreatePathResponseContent;
import net.es.oscars.api.soap.gen.v06.CreateReply;
import net.es.oscars.api.soap.gen.v06.Layer2Info;
import net.es.oscars.api.soap.gen.v06.ListReply;
import net.es.oscars.api.soap.gen.v06.ListRequest;
import net.es.oscars.api.soap.gen.v06.ModifyResContent;
import net.es.oscars.api.soap.gen.v06.ModifyResReply;
import net.es.oscars.api.soap.gen.v06.OSCARS;
import net.es.oscars.api.soap.gen.v06.PathInfo;
import net.es.oscars.api.soap.gen.v06.QueryResContent;
import net.es.oscars.api.soap.gen.v06.QueryResReply;
import net.es.oscars.api.soap.gen.v06.ResCreateContent;
import net.es.oscars.api.soap.gen.v06.ResDetails;
import net.es.oscars.api.soap.gen.v06.TeardownPathContent;
import net.es.oscars.api.soap.gen.v06.TeardownPathResponseContent;
import net.es.oscars.api.soap.gen.v06.UserRequestConstraintType;
import net.es.oscars.api.soap.gen.v06.VlanTag;
import net.es.oscars.common.soap.gen.OSCARSFaultMessage;
import net.es.oscars.common.soap.gen.OSCARSFaultReport;

/**
 * createReservationPoll creates a reservation for the path and polls for success or failure. 
 * Path is provisioned when start time arrives, unless 
 * pathSetupMode field in the createReservtion message set to to signal-xml. Then need to use createPath call to
 * activate provisioning
 * 
 * createPath() and teardownPath() can be used to provision/unprovision the path (only if signal-xml option is set
 * on reservation)
 * 
 * cancelReservation() cancels the reservation and tears down path if provisioned.
 * 
 * @author ibaldin
 *
 */
public class Driver {

	private OSCARS client = null;
	private String serverUrl = null;
	private String keyAlias = null;
	private ReentrantLock fairLock = new ReentrantLock(true);

	public Driver(String url, String certFile, String keyFile, String alias, String passWord, boolean logging) {

		String serviceUrl = url;

		ClientConfigHolder cch = ClientConfigHolder.getInstance();
		ClientConfig cc = new ClientConfig();
		cc.setKeyPassword(passWord);
		cch.addClientConfig(serviceUrl, cc);
		cch.setActiveClientConfig(serviceUrl);

		client = ClientUtil.getInstance().getOSCARSPort(serviceUrl, 
				certFile, keyFile, alias, passWord, logging);
	}

	/**
	 * Get OSCARS reservations
	 * @return
	 * @throws OSCARSFaultMessage
	 * @throws OSCARSClientException
	 * @throws Exception
	 */
	public List<ResDetails> getReservations() throws OSCARSFaultMessage, Exception {
		//Build request that asks for all ACTIVE, RESERVED and FINISHED reservations
		ListRequest request = new ListRequest();
		request.getResStatus().add(ResvStatus.STATUS_ACTIVE);
		request.getResStatus().add(ResvStatus.STATUS_RESERVED);
		request.getResStatus().add(ResvStatus.STATUS_FINISHED);
		request.getResStatus().add(ResvStatus.STATUS_FAILED);
		request.getResStatus().add(ResvStatus.STATUS_CANCELLED);

		//send request
		ListReply reply = client.listReservations(request);

		return reply.getResDetails();
	}

	/**
	 * List the desired states of interest. Dangerous function because these are just strings,
	 * no easy way to test what is allowed.
	 * @param states
	 * @return
	 * @throws OSCARSFaultMessage
	 * @throws Exception
	 */
	public List<ResDetails> getReservations(String ... states) throws OSCARSFaultMessage, Exception {
		ListRequest request = new ListRequest();
		for(String s: states) {
			if (s != null) 
				request.getResStatus().add(s);
		}

		//send request
		ListReply reply = client.listReservations(request);

		return reply.getResDetails();
	}
	
	/**
	 * List only active reservations
	 * @return
	 * @throws OSCARSFaultMessage
	 * @throws OSCARSClientException
	 * @throws Exception
	 */
	public List<ResDetails> getActiveReservations() throws OSCARSFaultMessage, Exception {
		//Build request that asks for all ACTIVE, RESERVED and FINISHED reservations
		ListRequest request = new ListRequest();
		request.getResStatus().add(ResvStatus.STATUS_ACTIVE);

		//send request
		ListReply reply = client.listReservations(request);

		return reply.getResDetails();
	}

	/**
	 * Get details of the reservation
	 * @param gri
	 * @return
	 * @throws OSCARSFaultMessage
	 * @throws OSCARSClientException
	 * @throws Exception
	 */
	public ResDetails queryReservation(String gri) throws OSCARSFaultMessage, Exception {
		QueryResContent queryRequest = new QueryResContent();
		queryRequest.setGlobalReservationId(gri);
		
		QueryResReply queryResponse = client.queryReservation(queryRequest);

		ResDetails rd = queryResponse.getReservationDetails();
		
		return rd;
	}

	/**
	 * Create a reservation for a path between A and Z with specific tags
	 * 
	 * @param desc - description
	 * @param start - start date
	 * @param end - end date
	 * @param bw - bandwidth in Mbps
	 * @param pointA - interface urn 
	 * @param tagA - vlan tag
	 * @param pointZ - interface urn
	 * @param tagZ - vlan tag
	 * @param pollInterval - poll interval in seconds
	 * @throws OSCARSFaultMessage 
	 * @throws OSCARSClientException
	 * @throws Exception
	 */
	public String createReservationPoll(String desc, Date start, Date end, int bw, 
			String pointA, int tagA, String pointZ, int tagZ, int pollInterval) 
					throws OSCARSFaultMessage, Exception {
		
		ResCreateContent request = new ResCreateContent();
		request.setDescription(desc);
		UserRequestConstraintType userConstraint = new UserRequestConstraintType();
		
		if (end.getTime() <= start.getTime()) 
			throw new Exception("End date before start date in creating OSCARS reservation");
		
		userConstraint.setStartTime(start.getTime()/1000); 
		userConstraint.setEndTime(end.getTime()/1000); 
		if (bw > 0)
			userConstraint.setBandwidth(bw); 

		PathInfo pathInfo = new PathInfo();
		Layer2Info layer2Info = new Layer2Info();

		// src
		layer2Info.setSrcEndpoint(pointA);
		VlanTag vtA = new VlanTag();
		vtA.setTagged(true);
		vtA.setValue(tagA + "");
		layer2Info.setSrcVtag(vtA);

		// dst
		layer2Info.setDestEndpoint(pointZ);
		VlanTag vtZ = new VlanTag();
		vtZ.setTagged(true);
		vtZ.setValue(tagZ + "");
		layer2Info.setDestVtag(vtZ);

		pathInfo.setLayer2Info(layer2Info );
		userConstraint.setPathInfo(pathInfo);
		request.setUserRequestConstraint(userConstraint);

		String gri;

		//send request
		fairLock.lock();
		try {
			CreateReply reply = client.createReservation(request);
			if (!reply.getStatus().equals(ResvStatus.STATUS_OK)) {
				throw new Exception("OSCARS returned non-OK status " + reply.getStatus());
			}

			// poll until circuit is ACTIVE
			String resvStatus = "";
			gri = reply.getGlobalReservationId();
			while (!resvStatus.equals(ResvStatus.STATUS_ACTIVE)) {

				//sleep for an interval
				try {
					Thread.sleep(pollInterval * 1000);
				} catch (InterruptedException e) {
					throw new Exception("OSCARS reservation query sleep interrupted");
				}

				//send query
				QueryResContent queryRequest = new QueryResContent();
				queryRequest.setGlobalReservationId(gri);
				QueryResReply queryResponse = client.queryReservation(queryRequest);

				//check status
				resvStatus = queryResponse.getReservationDetails().getStatus();
				if (resvStatus.equals(ResvStatus.STATUS_FAILED)) {
					List<OSCARSFaultReport> errors = queryResponse.getErrorReport();
					StringBuilder esb = new StringBuilder();
					for (OSCARSFaultReport err : errors) {
						esb.append(err.getErrorMsg());
						esb.append(" ");
					}
					throw new Exception("OSCARS reservation query failed with status " + resvStatus + " due to " + esb.toString());
				}
			}
		} finally {
			fairLock.unlock();
		}
		
		return gri;
	}

	/**
	 * Create a reservation with unspecified VLAN tags
	 * @param desc
	 * @param start
	 * @param end
	 * @param bw
	 * @param pointA
	 * @param pointZ
	 * @param pollInterval
	 * @throws OSCARSFaultMessage
	 * @throws OSCARSClientException
	 * @throws Exception
	 */
	public String createReservationPoll(String desc, Date start, Date end, 
			int bw, String pointA, String pointZ, int pollInterval) 
					throws OSCARSFaultMessage, Exception {

		if (end.getTime() <= start.getTime()) 
			throw new Exception("End date before start date in creating OSCARS reservation");
		
		ResCreateContent request = new ResCreateContent();
		request.setDescription(desc);
		UserRequestConstraintType userConstraint = new UserRequestConstraintType();
		userConstraint.setStartTime(start.getTime()/1000); 
		userConstraint.setEndTime(end.getTime()/1000); 
		userConstraint.setBandwidth(bw); 

		PathInfo pathInfo = new PathInfo();
		Layer2Info layer2Info = new Layer2Info();

		// src
		layer2Info.setSrcEndpoint(pointA);

		// dst
		layer2Info.setDestEndpoint(pointZ);

		pathInfo.setLayer2Info(layer2Info );
		userConstraint.setPathInfo(pathInfo);
		request.setUserRequestConstraint(userConstraint);

		String gri;

		//send request
		fairLock.lock();
		try {
			CreateReply reply = client.createReservation(request);
			if (!reply.getStatus().equals(ResvStatus.STATUS_OK)) {
				throw new Exception("OSCARS returned non-OK status " + reply.getStatus());
			}

			// poll until circuit is ACTIVE
			String resvStatus = "";
			gri = reply.getGlobalReservationId();
			while (!resvStatus.equals(ResvStatus.STATUS_ACTIVE)) {

				//sleep for an interval
				try {
					Thread.sleep(pollInterval * 1000);
				} catch (InterruptedException e) {
					throw new Exception("Sleep interrupted");
				}

				//send query
				QueryResContent queryRequest = new QueryResContent();
				queryRequest.setGlobalReservationId(gri);
				QueryResReply queryResponse = client.queryReservation(queryRequest);

				//check status
				resvStatus = queryResponse.getReservationDetails().getStatus();
				if (resvStatus.equals(ResvStatus.STATUS_FAILED)) {
					List<OSCARSFaultReport> errors = queryResponse.getErrorReport();
					StringBuilder esb = new StringBuilder();
					for (OSCARSFaultReport err : errors) {
						esb.append(err.getErrorMsg());
						esb.append(" ");
					}
					throw new Exception("OSCARS reservation failed with status " + resvStatus + " due to " + esb.toString());
				}
			}
		} finally {
			fairLock.unlock();
		}

		return gri;
	}

	/**
	 * Modify the end date of the reservation
	 * @param gri
	 * @param newDate
	 * @param pollInterval
	 * @throws OSCARSFaultMessage
	 * @throws OSCARSClientException
	 * @throws Exception
	 */
	public void extendReservation(String gri, Date newEnd, int pollInterval) throws OSCARSFaultMessage, Exception {
		Date now = new Date();
		
		if (newEnd.getTime() <= now.getTime()) 
			throw new Exception("Extend date before current date in extending OSCARS reservation");
		
		//create modify request
		ModifyResContent request = new ModifyResContent();
		request.setGlobalReservationId(gri);
		
		UserRequestConstraintType userConstraint = new UserRequestConstraintType();
		userConstraint.setEndTime(newEnd.getTime()/1000); 

		request.setUserRequestConstraint(userConstraint);

		//send modify request
		fairLock.lock();
		try {
			ModifyResReply response = client.modifyReservation(request);

			// poll until circuit is ACTIVE
			String resvStatus = "";
			while (!resvStatus.equals(ResvStatus.STATUS_ACTIVE)) {
				//sleep for an interval
				try {
					Thread.sleep(pollInterval * 1000);
				} catch (InterruptedException e) {
					throw new Exception("Sleep interrupted");
				}

				//send query
				QueryResContent queryRequest = new QueryResContent();
				queryRequest.setGlobalReservationId(gri);
				QueryResReply queryResponse = client.queryReservation(queryRequest);

				//check status
				resvStatus = queryResponse.getReservationDetails().getStatus();
				if (resvStatus.equals(ResvStatus.STATUS_FAILED)) {
					List<OSCARSFaultReport> errors = queryResponse.getErrorReport();
					StringBuilder esb = new StringBuilder();
					for (OSCARSFaultReport err : errors) {
						esb.append(err.getErrorMsg());
						esb.append(" ");
					}
					throw new Exception("OSCARS extend for " + gri + " failed with status " + resvStatus + " due to " + esb.toString());
				}
			}
		} finally {
			fairLock.unlock();
		}

	}

	/**
	 * Provision a path in existing reservation
	 * @param gri
	 * @param pollInterval
	 * @throws OSCARSFaultMessage
	 * @throws OSCARSClientException
	 * @throws Exception
	 */
	public void createPath(String gri, int pollInterval) throws OSCARSFaultMessage, Exception {

		//create request
		CreatePathContent request = new CreatePathContent();
		request.setGlobalReservationId(gri);

		String resvStatus = "";

		fairLock.lock();
		try {
			CreatePathResponseContent response = client.createPath(request);

			//display result
			if (!ResvStatus.STATUS_OK.equals(response.getStatus())) {
				throw new Exception("The create request for " + gri + " returned status " + response.getStatus());
			}

			//poll until reservation is setup
			while (resvStatus.equals(ResvStatus.STATUS_INSETUP)) {
				//send query
				QueryResContent queryRequest = new QueryResContent();
				queryRequest.setGlobalReservationId(gri);
				QueryResReply queryResponse = client.queryReservation(queryRequest);
				resvStatus = queryResponse.getReservationDetails().getStatus();

				try {
					Thread.sleep(pollInterval * 1000);
				} catch (InterruptedException e) {
					throw new Exception("Sleep interrupted");
				}
			}
		} finally {
			fairLock.unlock();
		}
		
		if (ResvStatus.STATUS_ACTIVE.equals(resvStatus)){
			return;
		}else{
			throw new Exception("Creat path of gri " + gri + " failed");
		}
	}

	/**
	 * Teardown a previously established path
	 * @param gri
	 * @param pollInterval
	 * @throws OSCARSFaultMessage
	 * @throws OSCARSClientException
	 * @throws Exception
	 */
	public void teardownPath(String gri, int pollInterval) throws OSCARSFaultMessage, Exception {

		//create request
		TeardownPathContent request = new TeardownPathContent();
		request.setGlobalReservationId(gri);

		String resvStatus = "";

		fairLock.lock();
		try {
			TeardownPathResponseContent response = client.teardownPath(request);

			//display result
			if (!ResvStatus.STATUS_OK.equals(response.getStatus())) {
				throw new Exception("The teardown request for " + gri + " returned status " + response.getStatus());
			}

			//poll until reservation is down
			while (resvStatus.equals(ResvStatus.STATUS_INTEARDOWN)) {
				//send query
				QueryResContent queryRequest = new QueryResContent();
				queryRequest.setGlobalReservationId(gri);
				QueryResReply queryResponse = client.queryReservation(queryRequest);
				resvStatus = queryResponse.getReservationDetails().getStatus();

				try {
					Thread.sleep(pollInterval * 1000);
				} catch (InterruptedException e) {
					throw new Exception("Sleep interrupted");
				}
			}
		} finally {
			fairLock.unlock();
		}

		if(ResvStatus.STATUS_RESERVED.equals(resvStatus) ||
				ResvStatus.STATUS_FINISHED.equals(resvStatus)) {
			return;
		} else {
			throw new Exception("Teardown of gri " + gri + " failed");
		}
	}

	public void cancelReservation(String gri, int pollInterval) throws OSCARSFaultMessage, Exception {

		//create cancel request
		CancelResContent request = new CancelResContent();
		request.setGlobalReservationId(gri);

		String resvStatus = "";

		//send cancel request
		fairLock.lock();
		try {
			CancelResReply response = client.cancelReservation(request);

			//display result
			if (!ResvStatus.STATUS_OK.equals(response.getStatus())) {
				throw new Exception("The cancel request for " + gri + " returned status " + response.getStatus());
			}

			//poll until reservation is canceled

			while (resvStatus.equals(ResvStatus.STATUS_ACTIVE) ||
					resvStatus.equals(ResvStatus.STATUS_INCANCEL) ||
					(resvStatus.length() == 0)) {

				try {
					Thread.sleep(pollInterval * 1000);
				} catch (InterruptedException e) {
					throw new Exception("Sleep interrupted");
				}

				//send query
				QueryResContent queryRequest = new QueryResContent();
				queryRequest.setGlobalReservationId(gri);
				QueryResReply queryResponse = client.queryReservation(queryRequest);
				resvStatus = queryResponse.getReservationDetails().getStatus();
			}
		} finally {
			fairLock.unlock();
		}

		if (ResvStatus.STATUS_CANCELLED.equals(resvStatus)) {
			return;
		} else {
			throw new Exception("Cancel of gri " + gri + " failed " + resvStatus);
		}
	}

	/**
	 * Query and print
	 * @param activeOnly
	 * @return
	 */
	public String printReservations(boolean activeOnly) {
		try {
			
			List<ResDetails> ret; 
			
			if (activeOnly)
				ret = getActiveReservations();
			else
				ret = getReservations();
			
			return printReservations(ret);
		} catch (OSCARSFaultMessage ofm) {
			return "OSCARS Fault Message: " + ofm.getMessage();
		} catch (Exception e) {
			e.printStackTrace();
			return "Generic exception: " + e.getMessage();
		}
	}
	
	/**
	 * Print results of prior query
	 * @param l
	 * @param activeOnly
	 * @return
	 */
	public String printReservations(List<ResDetails> l) {
		if ((l == null) || (l.size() == 0)) {
			return "No reservations were returnd by " + serverUrl + " for key alias " + keyAlias;
		}
		StringBuilder resp = new StringBuilder();
		for (ResDetails r: l) {
			resp.append(detailsToString(r));
			resp.append("\n");
		}
		return resp.toString();
	}

	/**
	 * Scan the list of reservations to see if a circuit between two given endpoints is present, returning the GRI.
	 * Parameters are expected to be non-null
	 * @param l
	 * @param epA
	 * @param epZ
	 * @param tagA
	 * @param tagZ
	 * @return
	 */
	public String findActiveReservation(List<ResDetails> l, String epA, String epZ, String tagA, String tagZ) {
		if ((l == null) || (l.size() == 0)) {
			return null;
		}
		for(ResDetails r: l) {
			UserRequestConstraintType urc = r.getUserRequestConstraint();
			if (urc == null)
				continue;
			PathInfo pi = urc.getPathInfo();
			if (pi == null)
				continue;
			
			Layer2Info l2i = pi.getLayer2Info();
			
			if (l2i == null)
				continue;
			
			String rEpA = l2i.getSrcEndpoint();
			String rEpZ = l2i.getDestEndpoint();
			VlanTag rTagA = l2i.getSrcVtag();
			VlanTag rTagZ = l2i.getDestVtag();
			
			if ((rTagA == null) || (rTagZ == null))
				continue;
			
			if ((epA != null) && (epA.equals(rEpA))) {
				if ((epZ != null) && (epZ.equals(rEpZ))) {
					if ((tagA != null) && (tagA.equals(rTagA.getValue()))) {
						if ((tagZ != null) && (tagZ.equals(rTagZ.getValue()))) {
							return r.getGlobalReservationId();
						}
					}
				}
			}
		}
		return null;
	}
	
	/**
	 * Query for active and try to locate a matching reservation returning the GRI. If not found or exceptions
	 * encountered, returns null
	 * @param activeOnly
	 * @param epA
	 * @param epZ
	 * @param tagA
	 * @param tagZ
	 * @return
	 * @throws OSCARSFaultMessage
	 * @throws Exception
	 */
	public String findActiveReservation(boolean activeOnly, String epA, String epZ, String tagA, String tagZ) throws OSCARSFaultMessage, Exception {
		try {
			
			List<ResDetails> ret; 
			
			if (activeOnly)
				ret = getActiveReservations();
			else
				ret = getReservations();
			
			return findActiveReservation(ret, epA, epZ, tagA, tagZ);
		} catch (OSCARSFaultMessage ofm) {
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private static String detailsToString(ResDetails d) {
		StringBuilder sb = new StringBuilder();

		sb.append(d.getGlobalReservationId() + "\t");
		sb.append(d.getLogin() + "\t");
		sb.append(d.getStatus() + "\t");
		sb.append(new Date(d.getUserRequestConstraint().getStartTime()*1000L) + "\t");
		sb.append(new Date(d.getUserRequestConstraint().getEndTime()*1000L) + "\t");
		sb.append(d.getUserRequestConstraint().getBandwidth() + "Mbps");

		return sb.toString();
	}

	public static void main(String[] argv) {
		//Driver d = new Driver(OSCARS_URL, OSCARS_CERT_FILE, OSCARS_KEYSTORE_FILE, OSCARS_KEY_ALIAS, PASSWORD, true);

		//System.out.println(d.printReservations(false));
		System.out.println(new Date(1447092034*1000L));
		System.out.println(new Date().getTime());
	}
}
