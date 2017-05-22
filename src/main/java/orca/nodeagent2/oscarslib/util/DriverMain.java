package orca.nodeagent2.oscarslib.util;

import java.util.Calendar;

import net.es.oscars.common.soap.gen.OSCARSFaultMessage;
import orca.nodeagent2.oscarslib.driver.Driver;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Main program for OSCARS 06 ORCA driver
 * @author ibaldin
 *
 */
public class DriverMain {

	private static final String CANCEL_CMD = "cancel";
	private static final String CREATE_CMD = "create";
	private static final String LIST_CMD = "list";
	private static final String POLL_INTERVAL = "poll";
	private static final String KEYSTOREPASS = "keystorepass";
	private static final String KEY_ALIAS = "alias";
	private static final String KEYSTORE = "keystore";
	private static final String TRUSTSTORE = "truststore";
	private static final String IDC_URL = "url";
	private static final String INT_Z = "intZ";
	private static final String INT_A = "intA";
	private static final String BWUNITS = "bwunits";
	private static final String GRI = "gri";

	@SuppressWarnings("static-access")
	public static void main(String[] argv) {
		// create the command line parser
		CommandLineParser parser = new GnuParser();

		// create the Options
		Options options = new Options();
		
		options.addOption(OptionBuilder.
				withArgName(IDC_URL )
                .hasArg().isRequired()
                .withDescription( "url of OSCARS IDC")
                .create(IDC_URL));
		
		options.addOption(OptionBuilder.
				withArgName("jks file").
				hasArg().isRequired().
				withDescription("keystore with user cert").
				create(KEYSTORE));
		
		options.addOption(OptionBuilder.
				withArgName("jks file").
				hasArg().isRequired().
				withDescription("truststore with IDC server certs").
				create(TRUSTSTORE));
		
		options.addOption(OptionBuilder.
				withArgName("key alias").
				hasArg().isRequired().
				withDescription("alias of the key in keystore to use").
				create(KEY_ALIAS));
		
		options.addOption(OptionBuilder.
				withArgName("password string").
				hasArg().isRequired().
				withDescription("password for the key alias and the keystore").
				create(KEYSTOREPASS));
		
		options.addOption(OptionBuilder.
				withArgName("password string").
				hasArg().
				withDescription("password for the truststore file (optional - only if different from keystore password)").
				create("truststorepass"));
		
		options.addOption(OptionBuilder.
				withArgName("interface urn").
				hasArg().
				withDescription("URN of the interface A of the path").
				create(INT_A));
		
		options.addOption(OptionBuilder.
				withArgName("interface urn").
				hasArg().
				withDescription("URN of the interface Z of the path").
				create(INT_Z));
		
		options.addOption(OptionBuilder.
				withArgName("vlan tag").
				hasArg().
				withDescription("VLAN tag of the interface A of the path").
				create("tagA"));
		
		options.addOption(OptionBuilder.
				withArgName("vlan tag").
				hasArg().
				withDescription("VLAN tag of the interface Z of the path").
				create("tagZ"));
		
		options.addOption(OptionBuilder.
				withArgName("bandwidth (default Mbps)").
				hasArg().
				withDescription("requested bandwidth of the path. zero means best-effort.").
				create("bw"));
		
		options.addOption(OptionBuilder.
				withArgName("bandwidth units=<bps|kbps|mbps|gbps>").
				hasArg().
				withDescription("requested bandwidth units").
				create(BWUNITS));
		
		options.addOption(OptionBuilder.
				withArgName("gri").
				hasArg().
				withDescription("GRI of the reservation of interest").
				create("gri"));
		
		options.addOption(OptionBuilder.
				withArgName("command=<list|create|cancel>").
				hasArg().isRequired().
				withDescription("command to issue").
				create("command"));
		
		options.addOption(OptionBuilder.
				withArgName("duration in seconds").
				hasArg().
				withDescription("duration of the reservation in seconds").
				create("duration"));
		
		options.addOption(OptionBuilder.
				withArgName("reservation description").
				hasArg().
				withDescription("description of the reservation").
				create("description"));
		
		options.addOption(OptionBuilder.
				withArgName("polling interval for create and cancel").
				hasArg().
				withDescription("polling interval in seconds").
				create(POLL_INTERVAL));
		
		options.addOption("d", false, "provide debugging information");
		options.addOption("c", false, "use compact concatenated circuit description on create and cancel: GRI|intA|tagA|intZ|tagZ");
		
		try {
			// parse the command line arguments
			CommandLine line = parser.parse( options, argv );
			
			String command = line.getOptionValue("command");

			boolean debugOn = false;
			if (line.hasOption("d")) 
				debugOn = true;
			
			boolean compact = false;
			if (line.hasOption("c"))
				compact = true;
			
			if (command.equals(LIST_CMD)) {
				// check command line parameters for list - none outside default
				Driver d = new Driver(
						line.getOptionValue(IDC_URL), 
						line.getOptionValue(TRUSTSTORE), 
						line.getOptionValue(KEYSTORE), 
						line.getOptionValue(KEY_ALIAS), 
						line.getOptionValue(KEYSTOREPASS), false);

				System.out.println(d.printReservations(false));
			} else if (command.equals("listactive")) {
				// check command line parameters for list - none outside default
				Driver d = new Driver(
						line.getOptionValue(IDC_URL), 
						line.getOptionValue(TRUSTSTORE), 
						line.getOptionValue(KEYSTORE), 
						line.getOptionValue(KEY_ALIAS), 
						line.getOptionValue(KEYSTOREPASS), false);

				System.out.println(d.printReservations(true));
			} else if (command.equals(CREATE_CMD)) {
				// check command line parameters for create
				
				if (!line.hasOption(INT_A) || 
						!line.hasOption(INT_Z) ||
						!line.hasOption("tagA") ||
						!line.hasOption("tagZ") ||
						!line.hasOption("bw") || 
						!line.hasOption("duration") ||
						!line.hasOption("description"))
					throw new ParseException("intA, intZ, tagA, tagZ, description, duration and bw must be specified");
				
				int duration = Integer.parseInt(line.getOptionValue("duration"));
				long bw = Long.parseLong(line.getOptionValue("bw"));
				int tagA = Integer.parseInt(line.getOptionValue("tagA"));
				int tagZ = Integer.parseInt(line.getOptionValue("tagZ"));
				int poll = 10;
				
				if (line.hasOption(POLL_INTERVAL)) {
					poll = Integer.parseInt(line.getOptionValue(POLL_INTERVAL));
					if (poll < 0)
						throw new ParseException("Polling interval must be positive in seconds.");
				}
				
				if ((tagA <=0) || (tagA > 4096) || (tagZ <= 0) || (tagZ > 4096))
					throw new ParseException("VLAN tags must be in 1-4096 range");
				
				// deal with bandwidth units
				if (line.hasOption(BWUNITS)) {
					if (line.getOptionValue(BWUNITS).equalsIgnoreCase("mbps")) 
						;
					else if (line.getOptionValue(BWUNITS).equalsIgnoreCase("kbps"))
						bw /= 1000;
					else if (line.getOptionValue(BWUNITS).equalsIgnoreCase("gbps"))
						bw *= 1000;
					else if (line.getOptionValue(BWUNITS).equalsIgnoreCase("bps"))
						bw /= 1000000;
					else
						throw new ParseException("Only bps, kbps, mbps and gbps are allowed as units");
				}
				Calendar start = Calendar.getInstance();
				Calendar end = Calendar.getInstance();
				end.add(Calendar.SECOND, duration);
				
				if (debugOn)
					System.out.println("Creating reservation from " + line.getOptionValue(INT_A) + "/" + tagA + " to " + 
							line.getOptionValue(INT_A) + "/" + tagZ + " bw=" + bw + " for " + duration);
				
				Driver d = new Driver(
						line.getOptionValue(IDC_URL), 
						line.getOptionValue(TRUSTSTORE), 
						line.getOptionValue(KEYSTORE), 
						line.getOptionValue(KEY_ALIAS), 
						line.getOptionValue(KEYSTOREPASS), false);
				
				String gri = d.createReservationPoll(line.getOptionValue("description"), 
						start.getTime(), end.getTime(), 
						(int)bw, 
						line.getOptionValue(INT_A), tagA, 
						line.getOptionValue(INT_Z), tagZ, 
						poll);
				
				if (compact) 
					System.out.println(gri + "|" + line.getOptionValue(INT_A) + "|" + tagA + "|" + line.getOptionValue(INT_Z) + "|" + tagZ);
				else
					System.out.println("SUCCESS: gri=" + gri);
			} else if (command.equals(CANCEL_CMD)) {
				if (!line.hasOption(GRI)) 
					throw new ParseException("GRI must be specified");

				String gri = line.getOptionValue(GRI);
				
				if (line.hasOption("c")) {
					// get the gri
					String[] splits = gri.split("\\|");
					gri = splits[0].trim();
				}
				
				int poll = 10;
				
				if (line.hasOption(POLL_INTERVAL)) {
					poll = Integer.parseInt(line.getOptionValue(POLL_INTERVAL));
					if (poll < 0)
						throw new ParseException("Polling interval must be positive in seconds.");
				}
				
				Driver d = new Driver(
						line.getOptionValue(IDC_URL), 
						line.getOptionValue(TRUSTSTORE), 
						line.getOptionValue(KEYSTORE), 
						line.getOptionValue(KEY_ALIAS), 
						line.getOptionValue(KEYSTOREPASS), false);
				
				if (debugOn)
					System.out.println("Canceling reservation " + gri);
				
				d.cancelReservation(gri, poll);
				
				System.out.println("SUCCESS");
			}
		}
		catch( ParseException exp ) {
			System.err.println("ERROR: Incorrect command line: " + exp.getMessage() );
		}
		catch (OSCARSFaultMessage ofe) {
			System.err.println("ERROR: OSCARS Fault: " + ofe.getMessage());
		}
		catch (Exception e) {
			System.err.println("ERROR: Generic exception: " + e.getMessage());
		}
	}
}
