package orca.nodeagent2.oscarslib.driver;

public class  ResvStatus {
    public final static String STATUS_ACCEPTED = "ACCEPTED";       // createReservation is authorized, gri is assigned
    public final static String STATUS_INPATHCALCULATION = "INPATHCALCULATION";   //start local path calculation
    public final static String STATUS_PATHCALCULATED  = "PATHCALCULATED"; // whole path calculation done
    public final static String STATUS_INCOMMIT = "INCOMMIT";       // in commit phase for calculated path
    public final static String STATUS_COMMITTED = "COMMITTED";     // whole path resources committed
    public final static String STATUS_RESERVED = "RESERVED";       // all domains have committed resources
    public final static String STATUS_INSETUP = "INSETUP";         // circuit setup has been started
    public final static String STATUS_ACTIVE = "ACTIVE";           // entire circuit has been setup
    public final static String STATUS_INTEARDOWN = "INTEARDOWN";   // circuit teardown has been started
    public final static String STATUS_FINISHED = "FINISHED";       // reservation endtime reached with no errors, circuit has been torndown
    public final static String STATUS_CANCELLED = "CANCELLED";     // complete reservation has been canceled, no circuit
    public final static String STATUS_FAILED = "FAILED";           // reservation failed at some point, no circuit
    public final static String STATUS_INMODIFY = "INMODIFY";       // reservation is being modified
    public final static String STATUS_INCANCEL = "INCANCEL";       // reservation is being canceled
    public final static String STATUS_OK = "Ok";
}
