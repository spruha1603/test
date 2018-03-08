package com.tailf.packages.ned.ios;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.log4j.Logger;

import java.lang.Character;
import java.lang.reflect.Method;

import com.tailf.cdb.Cdb;
import com.tailf.cdb.CdbDBType;
import com.tailf.cdb.CdbSession;

import com.tailf.conf.Conf;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamStart;
import com.tailf.conf.ConfXMLParamStop;
import com.tailf.conf.ConfXMLParamValue;

import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiCrypto;
import com.tailf.maapi.MaapiException;
import com.tailf.maapi.MaapiSchemas.CSNode;

import com.tailf.ncs.ResourceManager;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ncs.ns.Ncs;

import com.tailf.ned.NedCapability;
import com.tailf.ned.NedCliBase;
import com.tailf.ned.NedCliBaseTemplate;
import com.tailf.ned.NedCmd;
import com.tailf.ned.NedException;
import com.tailf.ned.NedExpectResult;
import com.tailf.ned.NedMux;
import com.tailf.ned.NedTTL;
import com.tailf.ned.NedTracer;
import com.tailf.ned.NedWorker;
import com.tailf.ned.NedWorker.TransactionIdMode;
import com.tailf.ned.SSHSessionException;
import com.tailf.ned.TelnetSession;

import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;


/**
 * This class implements NED interface for cisco ios devices
 *
 */
@SuppressWarnings("deprecation")
public class IOSNedCli extends NedCliBaseTemplate {
    public static Logger LOGGER  = Logger.getLogger(IOSNedCli.class);

    // Connects to the NcsServer and sets mm
    @Resource(type=ResourceType.MAAPI, scope=Scope.INSTANCE)
    public  Maapi mm;
    private int thr = -1; // read transaction handle

    @Resource(type=ResourceType.CDB, scope=Scope.INSTANCE)
    public Cdb cdb;
    public CdbSession cdbOper;

    private MetaDataModify metaData;
    private NedDataModify nedData;
    private NedSecrets secrets;
    private String lastGetConfig = null;
    private NedDefaults defaults;
    private MaapiCrypto mCrypto = null;

    private String date_string = "2017-03-17";
    private String version_string = "5.0.14";
    private String iosversion = "unknown";
    private String iosmodel   = "unknown";
    private String iospolice = "cirmode";
    private String licenseLevel = null;
    private String licenseType = null;
    private final static String privexec_prompt, prompt;
    private final static Pattern[] plw, ec, ec2, config_prompt;
    private enum Echo { WAIT, DONTWAIT, SKIPCMD };
    private Echo waitForEcho = Echo.WAIT;
    private boolean inConfig = false;
    private String trimMode = "trim";  // explicit, report-all
    private int num_reordered;
    private String lastOKLine = "";
    private String warningsBuf = "";
    private boolean showRaw = false;
    private String syncFile = null;

    // cached-show
    private ArrayList<String[]> cachedShowInventory = new ArrayList<String[]>();

    // have show command:
    private boolean haveShowBoot = true;
    private boolean haveShowVtpStatus = true;
    private boolean haveShowVlan = true;
    private boolean haveShowVlanSwitch = true;
    private boolean haveShowSnmpUser = true;

    // NED-SETTINGS
    private ArrayList<String> dynamicWarning = new ArrayList<String>();
    private ArrayList<String[]> autoPrompts = new ArrayList<String[]>();
    private ArrayList<String[]> interfaceConfig = new ArrayList<String[]>();
    private ArrayList<String[]> globalConfig = new ArrayList<String[]>();
    private ArrayList<String[]> globalCommand = new ArrayList<String[]>();
    private boolean showVerbose;
    private String writeMemory;
    private String writeMemoryMode;
    private String policeMode;
    private String remoteConnection;
    private int promptTimeout;
    private int deviceOutputDelay;
    private String transIdMethod;
    private String showRunningConfig;
    private boolean useIpMrouteCacheDistributed;
    private boolean newIpACL;
    private boolean includeCachedShowVersion;
    private boolean autoVrfForwardingRestore;
    private boolean autoIpCommunityListRepopulate;
    private boolean autoInterfaceSwitchportStatus;

    static {
        // start of input, > 0 non-# and ' ', one #, >= 0 ' ', eol
        privexec_prompt = "\\A[^\\# ]+#[ ]?$";

        prompt = "\\A\\S*#";

        // print_line_wait() pattern
        plw = new Pattern[] {
            Pattern.compile("\\A.*\\(cfg\\)#"),
            Pattern.compile("\\A.*\\(config\\)#"),
            Pattern.compile("\\A.*\\(.*\\)#"),
            Pattern.compile("\\A\\S*#"),
            Pattern.compile("\\?[ ]?\\(yes/\\[no\\]\\)"),  // ? (yes/[no])
            Pattern.compile("\\?[ ]?\\[yes/no\\]"),        // ? [yes/no]
            Pattern.compile("\\?[ ]?\\[yes\\]"),           // ? [yes]
            Pattern.compile("\\?[ ]?\\[no\\]"),            // ? [no]
            Pattern.compile("\\?[ ]?\\[confirm\\]")        // ? [confirm]
        };

        config_prompt = new Pattern[] {
            Pattern.compile("\\A\\S*\\(config\\)#"),
            Pattern.compile("\\A.*\\(.*\\)#")
        };

        // config t
        ec = new Pattern[] {
            Pattern.compile("Do you want to kill that session and continue"),
            Pattern.compile("\\A\\S*\\(config\\)#"),
            Pattern.compile("\\A.*\\(.*\\)#"),
            Pattern.compile("Aborted.*\n"),
            Pattern.compile("Error.*\n"),
            Pattern.compile("syntax error.*\n"),
            Pattern.compile("error:.*\n")
        };

        ec2 = new Pattern[] {
            Pattern.compile("\\A.*\\(cfg\\)#"),
            Pattern.compile("\\A.*\\(config\\)#"),
            Pattern.compile("\\A.*\\(.*\\)#"),
            Pattern.compile("Aborted.*\n"),
            Pattern.compile("Error.*\n"),
            Pattern.compile("syntax error.*\n"),
            Pattern.compile("error:.*\n")
        };
    }


    /*
     * CiscoIOS NED Constructors
     */

    public IOSNedCli() {
        super();
        try {
            ResourceManager.registerResources(this);
        } catch (Exception e) {
            LOGGER.error("Error injecting Resources", e);
        }
    }

    public IOSNedCli(String device_id,
               InetAddress ip,
               int port,
               String proto,  // ssh or telnet
               String ruser,
               String pass,
               String secpass,
               boolean trace,
               int connectTimeout, // msec
               int readTimeout,    // msec
               int writeTimeout,   // msec
               NedMux mux,
               NedWorker worker) {

        super(device_id, ip, port, proto, ruser, pass, secpass,
              trace, connectTimeout, readTimeout, writeTimeout, mux,
              worker);

        NedTracer tracer;
        if (trace)
            tracer = worker;
        else
            tracer = null;

        // LOG NCS version, NED version, date and timeouts
        logInfo(worker, "NCS VERSION: "+String.format("%x", Conf.LIBVSN));
        logInfo(worker, "NED VERSION: cisco-ios "+version_string+" "+date_string);
        logInfo(worker, "connect-timeout "+connectTimeout+" read-timeout "+readTimeout+" write-timeout "+writeTimeout);

        //
        // Init NCS resources and open maapi read session
        //
        try {
            ResourceManager.registerResources(this);
            cdbOper = cdb.startSession(CdbDBType.CDB_OPERATIONAL);
        } catch (Exception e) {
            logError(worker, "Error injecting Resources", e);
        }

        try {
            mm.setUserSession(1);
            thr = mm.startTrans(Conf.DB_RUNNING, Conf.MODE_READ);
        } catch (Exception e) {
            logError(worker, "Error initializing CDB read session :: ", e);
        }

        //
        // Read ned-settings
        //
        try {
            readNedSettings(worker);
        }
        catch (Exception e) {
            logError(worker, "failed to read cisco-ios ned-settings", e);
            worker.error(NedWorker.CONNECT_CONNECTION_REFUSED, e.getMessage());
            return;
        }

        //
        // Connect to device (or jump host)
        //
        try {
            worker.setTimeout(connectTimeout);
            if (proto.equals("ssh")) {
                setupSSH(worker);
                traceInfo(worker, "SSH logged in");
                // Note: logged in (but not in enable mode)
            }
            else {
                setupTelnet2(worker);
                traceInfo(worker, "TELNET logged in");
                // Note: logged in (in enable mode)
            }
        }
        catch (Exception e) {
            logError(worker, "connect failed",  e);
            try {
                worker.connectError(NedWorker.CONNECT_CONNECTION_REFUSED, e.getMessage());
            } catch (Exception ignore) {
                logError(null, "connect response failed", ignore);
            }
            return;
        }

        //
        // Login/enable the actual device
        //
        try {
            // SSH/TELNET/SERIAL
            if (remoteConnection.equals("ssh")
                || remoteConnection.equals("telnet")
                || remoteConnection.equals("serial")) {
                // We are already logged in on the jump host.
                // Next step is to telnet/SSH/press enter and then login on the real device
                proxyConnect(worker);
            } else {
                // Enter enable mode (on real device or exec master)
                if (proto.equals("ssh")) {
                    loginDevice(worker, 0, ruser, pass, secpass);
                }

                // Optional EXEC proxy connect (must be in enable mode)
                if (remoteConnection.equals("exec")) {
                    proxyExecConnect(worker);
                }
            }
        } catch (Exception e) {
            logError(worker, "failed to login on proxy", e);
            worker.error(NedWorker.CONNECT_CONNECTION_REFUSED, e.getMessage());
            return;
        }

        //
        // Logged in, set terminal settings and check device type
        //
        try {
            // Set terminal settings
            session.print("terminal length 0\n");
            session.expect("terminal length 0", worker);
            session.expect(privexec_prompt, worker);

            session.print("terminal width 0\n");
            session.expect("terminal width 0", worker);
            session.expect(privexec_prompt, worker);

            // Issue show version to check device/os type
            traceInfo(worker, "Requesting version string");
            session.print("show version\n");
            session.expect("show version", worker);
            String version = session.expect(privexec_prompt, worker);
            version = filterNonPrintable(version);

            // Scan version string
            try {
                traceInfo(worker, "Inspecting version string");

                if (version.indexOf("Cisco IOS Software") >= 0
                    || version.indexOf("Cisco Internetwork Operating") >= 0) {
                    // Found IOS device
                    String iosname = "ios";
                    int b, e;

                    traceVerbose(worker, "Found IOS device");

                    NedCapability capas[] = new NedCapability[2];
                    NedCapability statscapas[] = new NedCapability[1];

                    // NETSIM
                    version = version.replaceAll("\\r", "");
                    if (version.indexOf("NETSIM") >= 0) {
                        iosmodel = "NETSIM";
                        iosversion = "cisco-ios-" + version_string;

                        // Show CONFD & NED version used by NETSIM in ned trace
                        session.print("show confd-state version\n");
                        session.expect("show confd-state version", worker);
                        session.expect(privexec_prompt, worker);

                        session.print("show confd-state loaded-data-models "+
                                      "data-model tailf-ned-cisco-ios\n");
                        session.expect("show confd-state loaded-data-models "+
                                       "data-model tailf-ned-cisco-ios", worker);
                        session.expect(privexec_prompt, worker);

                        // Disable show commands for device only:
                        traceInfo(worker, "Disabling all device show checks");
                        haveShowBoot = haveShowVtpStatus = haveShowVlan = haveShowVlanSwitch = haveShowSnmpUser = false;
                    }

                    // REAL DEVICE
                    else {

                        // Set iospolice and iosname
                        if (!policeMode.equals("auto")) {
                            iospolice = policeMode; // Police format configured in ned-settings
                        } else if (version.indexOf("ME340x Software") >= 0) {
                            // iospolice = cirmode
                        } else if (version.indexOf("C3550") >= 0) {
                            iospolice = "numflat";
                        } else if (version.indexOf("C3750") >= 0) {
                            iospolice = "cirflat";
                        } else if (version.indexOf("Catalyst 4500 L3") >= 0) {
                            iospolice = "cirmode-bpsflat";
                        } else if (version.indexOf("C6504") >= 0) {
                            iospolice = "cirflat";
                        } else if (version.indexOf("Catalyst") >= 0) {
                            iospolice = "bpsflat";
                        } else if (version.indexOf("vios-") >= 0) {
                            iosname = "ViOS";
                        } else if (version.indexOf("vios_l2") >= 0) {
                            iosname = "ViOS";
                            iospolice = "cirflat";
                        } else if (version.indexOf("10000 Software") >= 0) {
                            iospolice = "numflat";
                        }

                        // Cache show version License Type & Level
                        licenseType = findLine(version, "License Type:");
                        if (licenseType != null)
                            licenseType = licenseType.substring(14);
                        licenseLevel = findLine(version, "License Level:");
                        if (licenseLevel != null) {
                            licenseLevel = licenseLevel.substring(15).trim();
                            if ((b = licenseLevel.indexOf("Type:")) > 0) {
                                licenseType = licenseLevel.substring(b+6).trim();
                                licenseLevel = licenseLevel.substring(0,b).trim();
                            }
                        }
                        if (licenseType != null && licenseType.indexOf(" ") > 0)
                            licenseType = "\"" + licenseType + "\"";

                        // cached-show inventory (name and serial numbers)
                        cacheShowInventory(worker);
                    }

                    //
                    // Get iosname
                    //
                    if (version.indexOf("Cisco IOS XE Software") >= 0
                        || version.indexOf("IOS-XE Software") >= 0) {
                        iosname = "ios-xe";
                    }

                    //
                    // Get iosmodel
                    //
                    Pattern pattern = Pattern.compile("\n[Cc]isco (\\S+) .*(?:processor |revision )");
                    Matcher matcher = pattern.matcher(version);
                    if (matcher.find()) {
                        iosmodel = matcher.group(1);
                    }

                    //
                    // Get iosversion (pick IOS version before XE version)
                    //
                    pattern = Pattern.compile("Cisco.*IOS Software.*Version ([0-9]+[A-Za-z0-9\\.():-]+[0-9a-zA-Z)]+)");
                    matcher = pattern.matcher(version);
                    if (matcher.find()) {
                        iosversion = matcher.group(1);
                    } else {
                        // cat3550 and cat6500 version extraction do not trigger on the above regexp
                        pattern = Pattern.compile("(?:Cisco)?.*IOS.*Software.*Version ([0-9]+[A-Za-z0-9\\.():-]+[0-9a-zA-Z)]+)");
                        matcher = pattern.matcher(version);
                        if (matcher.find())
                            iosversion = matcher.group(1);
                    }

                    logInfo(worker, "DEVICE:"
                            +" name="    + iosname
                            +" model="   + iosmodel
                            +" version=" + iosversion
                            +" police="  + iospolice);

                    capas[0] = new NedCapability(
                            "",
                            "urn:ios",
                            "tailf-ned-cisco-ios",
                            "",
                            date_string,
                            "");

                    capas[1] = new NedCapability(
                            "urn:ietf:params:netconf:capability:" +
                            "with-defaults:1.0?basic-mode=" + trimMode,
                            "urn:ietf:params:netconf:capability:" +
                            "with-defaults:1.0",
                            "",
                            "",
                            "",
                            "");

                    statscapas[0] = new NedCapability(
                            "",
                            "urn:ios-stats",
                            "tailf-ned-cisco-ios-stats",
                            "",
                            date_string,
                            "");

                    setConnectionData(capas,
                                      statscapas,
                                      true,
                                      TransactionIdMode.UNIQUE_STRING);

                    /*
                     * On NSO 4.0 and later, do register device model and
                     * os version.
                     */
                    if (Conf.LIBVSN >= 0x6000000) {
                        ConfXMLParam[] platformData =
                            new ConfXMLParam[] {
                            new ConfXMLParamStart("ncs", "platform"),
                            new ConfXMLParamValue("ncs", "name", new ConfBuf(iosname)),
                            new ConfXMLParamValue("ncs", "version", new ConfBuf(iosversion)),
                            new ConfXMLParamValue("ncs", "model", new ConfBuf(iosmodel)),
                            new ConfXMLParamStop("ncs", "platform")
                        };

                        Method method = this.getClass().getMethod("setPlatformData", new Class[]{ConfXMLParam[].class});
                        method.invoke(this, new Object[]{platformData});
                    }

                    // Create utility classes used by IOS NED
                    try {
                        metaData = new MetaDataModify(worker, "ios", device_id, iosmodel,
                                                      trace, showVerbose,
                                                      autoIpCommunityListRepopulate);
                        nedData = new NedDataModify(session, worker, trace, device_id, showVerbose);
                        secrets = new NedSecrets(worker, cdbOper, device_id, trace, showVerbose);
                        defaults = new NedDefaults(session, worker, cdbOper, device_id, iosmodel, trace, showVerbose);
                    }
                    catch (Exception e2) {
                        worker.error(NedCmd.CONNECT_CLI, e2.getMessage());
                    }
                } else {
                    worker.error(NedCmd.CONNECT_CLI, "unknown device");
                }
            } catch (Exception e) {
                throw new NedException("Failed to read device version string");
            }
        }
        catch (SSHSessionException e) {
            worker.error(NedCmd.CONNECT_CLI, e.getMessage());
        }
        catch (IOException e) {
            worker.error(NedCmd.CONNECT_CLI, e.getMessage());
        }
        catch (Exception e) {
            worker.error(NedCmd.CONNECT_CLI, e.getMessage());
        }
    }

    private void cacheShowInventory(NedWorker worker)
       throws Exception {
        String res = print_line_exec(worker, "show inventory");
        String lines[] = res.split("NAME: ");
        for (int i = 0; i < lines.length; i++) {
            Pattern pattern = Pattern.compile("(\\\".*?\\\"), .*,\\s+SN: (.*)", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(lines[i]);
            if (matcher.find()) {
                String[] entry = new String[2];
                entry[0] = matcher.group(1);
                entry[1] = matcher.group(2).trim();
                traceInfo(worker, "Adding cached-show inventory: NAME="+entry[0]+" SN="+entry[1]);
                cachedShowInventory.add(entry);
            }
        }
    }

    private String getNedSetting(NedWorker worker, String path)
       throws Exception {
        String val = null;

        // Global
        String p = "/ncs:devices/ncs:global-settings/ncs:ned-settings/"+path;
        try {
            if (mm.exists(thr, p))
                val = ConfValue.getStringByValue(p, mm.getElem(thr, p));
        } catch (MaapiException ignore) {
        }

        // Profile
        p = "/ncs:devices/ncs:profiles/profile{cisco-ios}/ncs:ned-settings/"+path;
        try {
            if (mm.exists(thr, p))
                val = ConfValue.getStringByValue(p, mm.getElem(thr, p));
        } catch (MaapiException ignore) {
        }

        // Device
        p = "/ncs:devices/device{"+device_id+"}/ned-settings/"+path;
        if (mm.exists(thr, p)) {
            val = ConfValue.getStringByValue(p, mm.getElem(thr, p));
        }

        return val;
    }

    private String getNedSettingString(NedWorker worker, String path, String defaultValue)
       throws Exception {
        String value = defaultValue;
        String setting = getNedSetting(worker, path);
        String defbuf = "*";
        if (setting != null) {
            value = setting;
            defbuf = "";
        }
        logInfo(worker, path + " = " + value + defbuf);
        return value;
    }

    private boolean getNedSettingBoolean(NedWorker worker, String path, boolean defaultValue)
       throws Exception {
        boolean value = defaultValue;
        String setting = getNedSetting(worker, path);
        String defbuf = "*";
        if (setting != null) {
            value = setting.equals("true") ? true : false;
            defbuf = "";
        }
        logInfo(worker, path + " = " + value + defbuf);
        return value;
    }

    private int getNedSettingInt(NedWorker worker, String path, int defaultValue)
       throws Exception {
        int value = defaultValue;
        String setting = getNedSetting(worker, path);
        String defbuf = "*";
        if (setting != null) {
            value =  Integer.parseInt(setting);
            defbuf = "";
        }
        logInfo(worker, path + " = " + value + defbuf);
        return value;
    }

    /**
     * Simple utility to extract the relevant ned-settings from CDB
     * @param worker
     * @param th
     */
    private void readNedSettings(NedWorker worker)
       throws Exception {
        String val;

        logInfo(worker, "NED-SETTINGS: (* = default value)");

        // cisco-ios-auto interface-switchport-status
        autoInterfaceSwitchportStatus = getNedSettingBoolean(worker, "cisco-ios-auto/interface-switchport-status", false);

        NavuContext context = new NavuContext(mm, thr);

        // Base roots
        NavuContainer deviceSettings= new NavuContainer(context)
            .container(Ncs.hash)
            .container(Ncs._devices_)
            .list(Ncs._device_)
            .elem(new ConfKey(new ConfBuf(device_id)));

        NavuContainer globalSettings = new NavuContainer(context)
            .container(Ncs.hash)
            .container(Ncs._devices_)
            .container("ncs", "global-settings");

        NavuContainer profileSettings = new NavuContainer(context)
            .container(Ncs.hash)
            .container(Ncs._devices_)
            .container("ncs", "profiles")
            .list(Ncs._profile_)
            .elem(new ConfKey(new ConfBuf("cisco-ios")));

        NavuContainer[] settings = {globalSettings,
                                    profileSettings,
                                    deviceSettings };

        /*
         * Get config warnings
         */

        for (NavuContainer s : settings ) {
            if (s == null)
                continue;
            NavuList newWarnings = s.container("ncs", "ned-settings")
                .list("cisco-ios-meta", "cisco-ios-config-warning");
            for (NavuContainer entry : newWarnings.elements()) {
                logInfo(worker, "cisco-ios-config-warning \""
                        +entry.leaf("warning").valueAsString()+"\"");
                dynamicWarning.add(entry.leaf("warning").valueAsString());
            }
        }

        /*
         * Get auto-prompts
         */

        for (NavuContainer s : settings ) {
            if (s == null)
                continue;
            NavuList prompts = s.container("ncs", "ned-settings")
                .list("cisco-ios-meta", "cisco-ios-auto-prompts");
            for (NavuContainer entry : prompts.elements()) {
                String[] newEntry = new String[3];
                newEntry[0] = entry.leaf("id").valueAsString();
                newEntry[1] = entry.leaf("question").valueAsString();
                newEntry[2] = entry.leaf("answer").valueAsString();
                logInfo(worker, "cisco-ios-auto-prompts "+newEntry[0]
                        + " q \"" +newEntry[1]+"\""
                        + " a \"" +newEntry[2]+"\"");
                autoPrompts.add(newEntry);
            }
        }

        /*
         * Get global inject config
         */

        for (NavuContainer s : settings ) {
            if (s == null)
                continue;
            NavuList newConfig = s.container("ncs", "ned-settings")
                .list("cisco-ios-meta", "cisco-ios-inject-config");
            for (NavuContainer entry : newConfig.elements()) {
                String[] newEntry = new String[3];
                newEntry[0] = entry.leaf("config").valueAsString();
                newEntry[1] = entry.leaf("id").valueAsString();
                newEntry[2] = entry.leaf("regexp").valueAsString();
                String buf = "cisco-ios-inject-config "+newEntry[1];
                if (newEntry[2] != null)
                    buf += " regexp \""+newEntry[2]+"\"";
                buf += " c \"" +newEntry[0]+"\"";
                logInfo(worker, buf);
                globalConfig.add(newEntry);
            }
        }

        /*
         * Get interface inject config
         */

        // Add a static global default 'no switchport' setting
        // Used for devices/interfaces which do not support switchport
        // or for devices which hide 'no switchport' when disabled, eg:
        // WS-C6504-E or CISCO7606-S or CISCO2901/K9
        if (autoInterfaceSwitchportStatus == false) {
            String[] staticEntry = new String[3];
            staticEntry[0] = "Ethernet|Port-channel";
            staticEntry[1] = "no switchport";
            staticEntry[2] = "globstat-sp";
            logInfo(worker, "cisco-ios-inject-interface-config "+staticEntry[2]
                    +" i \""+staticEntry[0]+"\""
                    +" c \""+staticEntry[1]+"\"");
            interfaceConfig.add(staticEntry);
        }

        // Add the user defined interface inject config
        for (NavuContainer s : settings ) {
            if (s == null)
                continue;
            NavuList newIfConfig = s.container("ncs", "ned-settings")
                .list("cisco-ios-meta", "cisco-ios-inject-interface-config");
            for (NavuContainer entry : newIfConfig.elements()) {
                String[] newEntry  = new String[3];
                newEntry[0] = entry.leaf("interface").valueAsString();
                newEntry[1] = entry.leaf("config").valueAsString();
                newEntry[2] = entry.leaf("id").valueAsString();
                logInfo(worker, "cisco-ios-inject-interface-config "+newEntry[2]
                        +" i \""+newEntry[0]+"\""
                        +" c \""+newEntry[1]+"\"");
                interfaceConfig.add(newEntry);
            }
        }

        /*
         * Get global inject command(s)
         */

        for (NavuContainer s : settings ) {
            if (s == null)
                continue;
            NavuList newConfig = s.container("ncs", "ned-settings")
                .list("cisco-ios-meta", "cisco-ios-inject-command");
            for (NavuContainer entry : newConfig.elements()) {
                String[] newEntry  = new String[4];
                newEntry[0] = entry.leaf("config-line").valueAsString();
                newEntry[1] = entry.leaf("id").valueAsString();
                newEntry[2] = entry.leaf("command").valueAsString();
                newEntry[3] = entry.leaf("where").valueAsString();
                logInfo(worker, "cisco-ios-inject-command "+newEntry[1]
                        +" conf \""+newEntry[0]+"\""
                        +" comm \""+newEntry[2]+"\""
                        +" "+newEntry[3]);
                globalCommand.add(newEntry);
            }
        }

        // cisco-ios-auto vrf-forwarding-restore
        autoVrfForwardingRestore = getNedSettingBoolean(worker, "cisco-ios-auto/vrf-forwarding-restore", true);

        // cisco-ios-auto ip-community-list-repopulate
        autoIpCommunityListRepopulate = getNedSettingBoolean(worker, "cisco-ios-auto/ip-community-list-repopulate", false);

        // cisco-ios-write-memory-method
        writeMemory = getNedSettingString(worker, "cisco-ios-write-memory-method", "write memory");

        // cisco-ios-write-memory-setting
        writeMemoryMode = getNedSettingString(worker, "cisco-ios-write-memory-setting", "on-commit");

        // cisco-ios-transaction-id-method
        transIdMethod = getNedSettingString(worker, "cisco-ios-transaction-id-method", "config-hash");

        // cisco-ios-show-running-method
        showRunningConfig = getNedSettingString(worker, "cisco-ios-show-running-method", "show running-config");

        // cisco-ios-api new-ip-access-list
        newIpACL = getNedSettingBoolean(worker, "cisco-ios-api/new-ip-access-list", false);

        // cisco-ios-cached-show-enable version
        includeCachedShowVersion = getNedSettingBoolean(worker, "cisco-ios-cached-show-enable/version", true);

        // cisco-ios-log-verbose
        showVerbose = getNedSettingBoolean(worker, "cisco-ios-log-verbose", false);

        // cisco-ios-use-ip-mroute-cache-distributed
        // Get if 'ip mroute-cache distributed' should be used instead of 'ip mroute-cache'
        // Cat3560/3750 allows only 'ip mroute-cache distributed'
        // cat4506 allows only 'ip mroute-cache'
        // both supports 'no ip mroute-cache'
        // both just shows 'ip mroute-cache' when show running-config is executed.
        // to avoid compare-config diff and rollback issue, use this ned-setting
        useIpMrouteCacheDistributed = getNedSettingBoolean(worker, "cisco-ios-use-ip-mroute-cache-distributed", false);

        // cisco-ios-police-format
        policeMode = getNedSettingString(worker, "cisco-ios-police-format", "auto");

        // cisco-ios-proxy-settings remote-connection
        remoteConnection = getNedSettingString(worker, "cisco-ios-proxy-settings/remote-connection", "none");

        // cisco-ios connection-settings prompt-timeout
        promptTimeout = getNedSettingInt(worker, "cisco-ios/connection-settings/prompt-timeout", connectTimeout);

        // cisco-ios connection-settings device-output-delay
        deviceOutputDelay = getNedSettingInt(worker, "cisco-ios/connection-settings/device-output-delay", 0);

        traceInfo(worker, "");
    }

    private void proxyConnect(NedWorker worker)
        throws Exception, NedException, IOException, SSHSessionException, ApplyException {
        NedExpectResult res;
        String remoteName = null;
        String remotePassword = null;

        logInfo(worker, "Connecting using proxy method : "+remoteConnection);

        // Get base proxy container
        String devpath = "/ncs:devices/device{"
            + device_id
            + "}/ned-settings/cisco-ios-proxy-settings";

        // Get remote username
        String p = devpath + "/remote-name";
        if (mm.exists(thr, p)) {
            remoteName = ConfValue.getStringByValue(p, mm.getElem(thr, p));
        }

        // Get remote password
        p = devpath + "/remote-password";
        if (mm.exists(thr, p)) {
            remotePassword = ConfValue.getStringByValue(p, mm.getElem(thr, p));
        }

        if (remoteConnection.equals("ssh") || remoteConnection.equals("telnet")) {
            // ssh or telnet

            // SSH/TELNET jump host have its own prompt (regexp)
            p = devpath + "/proxy-prompt";
            String proxyPrompt = ConfValue.getStringByValue(p, mm.getElem(thr, p));
            traceInfo(worker, "Waiting for proxy prompt '" + proxyPrompt + "'");
            session.expect(proxyPrompt, worker);

            // Get remote-address and remote-port
            p = devpath + "/remote-address";
            String remoteAddress = ConfValue.getStringByValue(p, mm.getElem(thr, p));
            p = devpath + "/remote-port";
            String remotePort = ConfValue.getStringByValue(p, mm.getElem(thr, p));

            // Connect using ssh or telnet
            if (remoteConnection.equals("ssh")) {
                session.println("ssh -p "+remotePort+" "+remoteName+"@"+remoteAddress);
            } else {
                session.println("telnet "+remoteAddress+" "+remotePort);
            }
        } else {
            sendNewLine(worker, "Sending newline (serial proxy)");
        }

        // Second login (on the real device)
        loginDevice(worker, 1, remoteName, remotePassword, secpass);

        logInfo(worker, "PROXY connected");
    }

    private void proxyExecConnect(NedWorker worker)
        throws Exception, NedException, IOException, ApplyException {
        NedExpectResult res;
        String remoteSecondaryPassword = null;

        // Get base proxy container
        String devpath = "/ncs:devices/device{"
            + device_id
            + "}/ned-settings/cisco-ios-proxy-settings";
        if (!mm.exists(thr, devpath))
            throw new NedException("EXEC PROXY connect failed, no config settings");

        // Get proxy config
        ConfValue v;
        String p = devpath + "/remote-connection";
        v = mm.getElem(thr, p);
        remoteConnection = ConfValue.getStringByValue(p, v);

        p = devpath + "/remote-command";
        v = mm.getElem(thr, p);
        String remoteCommand = ConfValue.getStringByValue(p, v);

        p = devpath + "/remote-prompt";
        v = mm.getElem(thr, p);
        String remotePrompt = ConfValue.getStringByValue(p, v);

        p = devpath + "/remote-name";
        v = mm.getElem(thr, p);
        String remoteName = ConfValue.getStringByValue(p, v);

        p = devpath + "/remote-password";
        v = mm.getElem(thr, p);
        String remotePassword = ConfValue.getStringByValue(p, v);

        // Optional remote-secondary-password
        p = devpath + "/remote-secondary-password";
        v = mm.safeGetElem(thr, p);
        if (v != null)
            remoteSecondaryPassword = ConfValue.getStringByValue(p, v);

        logInfo(worker, "PROXY connecting using "+remoteConnection);

        // Send connect string, wait for prompt
        session.println(remoteCommand);
        // CHECK FOR: Trying 10.67.16.59, 2002 ...
        res = session.expect(new String[] {
                "Connection refused by remote host",
                remotePrompt
            }, worker);
        if (res.getHit() < 1)
            throw new NedException("PROXY connect failed, connection refused");

        logInfo(worker, "PROXY connected");

        // Send newline, wait for username prompt
        sendNewLine(worker, "Sending newline (exec proxy)");

        // Login on exec device using remoteXXX credentials
        loginDevice(worker, 1, remoteName, remotePassword, remoteSecondaryPassword);
    }

    private void logError(NedWorker worker, String text, Exception e) {
        LOGGER.error(device_id + " " + text, e);
        if (trace && worker != null)
            worker.trace("-- " + text + ": " + e.getMessage() + "\n", "out", device_id);
    }

    private void logInfo(NedWorker worker, String info) {
        LOGGER.info(device_id + " " + info);
        if (trace && worker != null)
            worker.trace("-- " + info + "\n", "out", device_id);
    }

    private void logDebug(NedWorker worker, String info) {
        LOGGER.debug(device_id + " " + info);
        if (trace && worker != null)
            worker.trace("-- " + info + "\n", "out", device_id);
    }

    private void logVerbose(NedWorker worker, String info) {
        if (showVerbose) {
            LOGGER.debug(device_id + " " + info);
            if (trace && worker != null)
                worker.trace("-- " + info + "\n", "out", device_id);
        }
    }

    private void traceInfo(NedWorker worker, String info) {
        if (trace)
            worker.trace("-- " + info + "\n", "out", device_id);
    }

    private void traceVerbose(NedWorker worker, String info) {
        if (showVerbose && trace) {
            worker.trace("-- " + info + "\n", "out", device_id);
        }
    }

    @Override
    public void trace(NedWorker worker, String msg, String direction) {
        if (trace) {
            worker.trace("-- "+msg+" --\n", direction, device_id);
        }
    }

    @Override
    public void reconnect(NedWorker worker) {
        // all capas and transmode already set in constructor
        // nothing needs to be done
    }

    // Which Yang modules are covered by the class
    @Override
    public String [] modules() {
        return new String[] { "tailf-ned-cisco-ios" };
    }

    // Which identity is implemented by the class
    @Override
    public String identity() {
        return "ios-id:cisco-ios";
    }

    private void moveToTopConfig(NedWorker worker)
        throws IOException, SSHSessionException {
        NedExpectResult res;

        traceVerbose(worker, "moveToTopConfig()");

        while (true) {
            session.print("exit\n");
            res = session.expect(config_prompt);
            if (res.getHit() == 0)
                return;
        }
    }

    private boolean isDevice() {
        return !iosmodel.equals("NETSIM");
    }

    private boolean isNetsim() {
        return iosmodel.equals("NETSIM");
    }

    private boolean hasPolice(String police) {
        if (iospolice.indexOf(police) >= 0)
            return true;
        else
            return false;
    }

    private boolean isCliRetry(NedWorker worker, String reply, String line) {
        int n;

        if (reply.trim().isEmpty())
            return false;

        //traceVerbose(worker, "isCliRetry?  reply='"+reply.trim()+"'");

        // Ignore retry on these patterns:
        String[] ignoreRetry = {
            "%(\\S+): (informational|error): \\S+ is in use on",
            "please remove .* from .* first",
            "first remove .* from the above" // no crypto ipsec transform-set
        };
        for (n = 0; n < ignoreRetry.length; n++) {
            if (findString(ignoreRetry[n], reply.toLowerCase()) >= 0)
                return false;
        }

        // Retry on these patterns:
        String[] isRetry = {
            "is in use",
            "is still in use and cannot be removed",
            "wait for it to complete",
            "wait for the current operation to complete",
            "is currently being deconfigured",
            "is currently deactivating",
            "is being deleted, please try later"
        };
        for (n = 0; n < isRetry.length; n++) {
            if (findString(isRetry[n], reply) >= 0)
                return true;
        }

        // Do not retry
        return false;
    }

    private boolean isCliPatch(NedWorker worker, int cmd, String reply, String line, String meta)
        throws NedException, IOException, SSHSessionException, ApplyException {
        String match;

        // Changing track type
        if (line.startsWith("track ")
            && (match = getMatch(reply, "Cannot change tracked object (\\d+) - delete old config first")) != null) {
            traceInfo(worker, "inserting track delete in order to change track type");
            print_line_wait(worker, cmd, "no track "+match, 0, meta);
            return true;
        }

        return false;
    }

    private boolean isCliError2(NedWorker worker, int cmd, String replyall, String reply, String line, String meta) {
        int n;

        reply = reply.trim();
        if (reply.isEmpty())
            return false;

        traceVerbose(worker, "isCliError?  reply='"+reply+"'");
        traceVerbose(worker, "          replyall='"+replyall.trim()+"'");

        // Warnings, regular expressions. NOTE: Lowercase!
        String[] staticWarning = {
            // general
            "warning[:,] \\S+.*",
            "warning:",
            ".?note:",
            "info:",
            "aaa: warning",
            "added successfully",
            ".*success",
            "enter text message",
            "enter macro commands one per line",
            "this commmand is deprecated",
            "this cli will be deprecated soon",
            "command accepted but obsolete, unreleased or unsupported",
            "redundant .* statement",
            "elapsed time was \\d+ seconds",
            "configuring anyway",
            //this command is an unreleased and unsupported feature

            // remove || delete
            "hqm_tablemap_inform: class_remove error",
            "all rsa keys will be removed",
            "all router certs issued using these keys will also be removed",
            "not all config may be removed and may reappear after",
            "removed .* policy from .* interface",
            "this will remove previously",
            "removing ssp group",
            "remote  deleted",
            "tunnel interface was deleted",
            "mac address.*has been deleted from the bridge table",
            "non-fr-specific configuration, if not yet explicitly deconfigured",
            "can't delete last 5 vty lines",
            "bridge-domain \\d+ cannot be deleted because it is not empty",  // no bridgde-domain *
            //"must be removed from child classes",
            //dcs.ioswarningexpressionsremovecfg=not all config may be removed
            //please remove bandwidth from the child policy

            // change
            "changes to .* will not take effect until the next",
            "security level for .* changed to",
            "connection name is changed",
            "changes to the running .* have been stored",
            "you are about to \\S+grade",

            // VRF
            "removed due to \\S+abling vrf",
            "removed due to vrf change",
            "the static routes in vrf .*with outgoing interface .*will be",
            "ip.* addresses from all interfaces in vrf .*have been removed",
            "number of vrfs \\S+graded",
            "vrf .*exists but is not enabled",
            "a new tunnel id may be used if the default mdt is reconfigured for this vrf",
            "for vrf .* scheduled for deletion",
            "vrf \\S+ not configured, invalid vrf name",
            //.*unknown vrf specified
            //.?vrf .*does not exist

            ".* set use own .* address for the nexthop not supported", // MPLS-OUT

            // vlan
            "vlan.* does not exist.* creating vlan",
            "please refer to documentation on configuring ieee 802.1q vlans",
            "vlan mapping is also changed",
            "ip address .* will be removed from .* due to removal of vlan",
            ".*vlan .* does not exist, creating vlan.*",
            "vlan mapping is also changed",
            "vlan  mod/ports",
            "applying vlan changes may take few minutes",
            "access vlan does not exist",

            // interface
            "if .*interface does.* support baby giant frames",
            "no cef interface information",
            "unrecognized virtual interface .* treat it as loopback stub",
            "ipv4 and ipv6 addresses from all",
            "ip\\S+ addresses from all interfaces",
            "pim configuration for interface",
            "interface .* hsrp [a-f0-9:]* removed due to vrf change",
            "(\\S+): informational: \\S+ is in use on",
            "is reverting to router mode configuration, and remains disabled",
            "ospf will not operate on this interface until ip is configured on it",
            "command will have no effect with this interface",
            "portfast has been configured on ",  // spanning-tree portfast
            "creating a port-channel interface port-channel", // interface * / channel-group 3 mode active

            // router
            "peer-group \\S+ is not present, but will go ahead and delete",
            "setting the encapsulation of ipv(4|6) to \\S+. encapsulation cannot be different", // router lisp / service * / encapsulation
            "all bgp sessions must be reset to take the new", // bgp graceful-restart restart-time
            "only classful networks will be redistributed", // router ospf * / redistribute static

            // tunnel
            "tunnel mpls traffic-eng fast-reroute",
            "attach member tunnel to a master",

            // SSH & certificate
            "enter the certificate",
            "certificate accepted",
            "certificate request sent",
            "ssh:publickey disabled.overriding rfc",
            "ssh:no auth method configured.incoming connection will be dropped",
            "please create rsa keys to enable ssh",
            "generating \\d+ bit rsa keys",   // ip http secure-server
            "the certificate has been deleted", // no certificate self-signed X

            // crypto
            "ikev2 \\S+ must have",
            "crypto-6-isakmp_on_off: isakmp is",
            "be sure to ask the ca administrator to revoke your certificates",
            "overriding already existing source with priority",
            "updated group cp to ", // crypto gkm group * / server address ipv4
            "this will remove all existing \\S+ on this map", // crypto map ** ipsec-isakmp / reverse-route static
            "removing \\S+ will delete all routes and clear current ipsec", // crypto map ** ipsec-isakmp / no reverse-route static
            "ikev2 proposal must either have a set of an encryption algorithm", // crypto ikev2 proposal *
            //crypto ezvpn does not exist
            //signature rsa keys not found in configuration
            //profile already contains this keyring

            // nat
            "global .* will be port address translated",
            "outside interface address added",
            "pool nat-pool mask .* too small",

            // policy-map & class-map
            "no specific protocol configured in class (.*) for inspection",
            // policy map .* not configured
            //class-map .* being used
            //service policy .* not attached

            // routing
            "reload or use .* command, for this to take effect",
            // ip routing table .* does not exist. create first
            //no matching route to delete

            // cos
            ".*propagating cos-map configuration to.*",
            ".*propagating queue-limit configuration to.*",
            "cos mutation map",
            "(cos-map|queue-limit) configured on all .* ports on slot .*",

            // cable
            "minislot size set to", // no us-channel
            "the minislot size is now changed to", // no cable service class
            "response of applying upstream controller-profile", // no cable service class
            "fiber node \\d+ is valid",

            // misc
            "warning\\S+ auto discovery already ", // service-insertion * / node-discovery enable
            "enabling mls qos globally",
            "name length exceeded the recommended length of .* characters",
            "a profile is deemed incomplete until it has .* statements",
            "address aliases with",
            "explicit path name",
            "global ethernet mtu is set to",
            "restarting .* service",
            "the .* command will also show the fingerprint",
            "encapsulation dot1q",
            "icmp redirect",
            "\\S+abling learning on",
            "\\S+abling failover",
            "the threshold option has been accepted",
            "added .*to the bridge table",
            "arp inspection \\S+abled on",
            "configurations are no longer synchronized",
            "pix-[.]-",
            "secured .* cleared from",
            "current activity time is .* seconds",
            "rm entries aging is turned o",
            "zoning is currently not configured for interface",
            "propagating wred configuration to",
            "conform burst size \\S+creased to",
            "selected country",
            "is not a legal lat node name",
            "changing vtp domain name from",
            "setting device to vtp .*",
            "profile cannot enable more than one transport method", // call-home / profile * / destination transport-method http
            "call-home profile need to have at least one transport-method", // call-home / profile * / no destination transport-method http
            "removal of cisco tac profile is not allowed. it can be disabled by issuing", // call-home / no profile *
            "activating virtual-service .* this might take a few minutes", //  virtual-service * / activate
            "virtual service .* install has not completed",
            "the email address configured in .* will be used as", // call-home / contact-email-addr
            "wait for .* license request to succeed", // platform hardware throughput level
            "logging of %snmp-3-authfail is (dis|en)abled", // logging snmp-authfail
            "translating \\S+"  // ntp server

            // UNCERTAIN:
            //already found same .* statement in this profile
            //cns config partial agent is running already
            //configuration buffer full, can't add command
            //pvc is already defined
            //translation not found
            //unable to disable parser cache
            //unknown vpn
            //dcs.ioswarningexpressionsexitcfgmode=
            //dcs.pixwarningexpressions=access rules download complete
            //bandwidth can't be more than parent's
            //failed to create
            //cannot overwrite an already existing static entry
        };

        if (meta != null
            && line.startsWith("no ")
            && reply.indexOf("Invalid input detected at") >= 0
            && meta.indexOf("suppress-delete-error-invalid") > 0) {
            traceVerbose(worker, "suppressed delete invalid error on: " + line);
            return false;
        }

        // Special cases ugly patches
        if (line.indexOf("no ip address ") >= 0
            && reply.indexOf("Invalid address") >= 0) {
            // Happens when IP addresses already deleted on interface
            return false;
        }
        if (line.equals("no duplex") || line.equals("no speed")) {
            // Ignore these errors because harmless and happen:
            // E.g. when 'no media-type' is deleted before duplex or speed
            // E.g. when 'no speed' is sent after negotiation auto
            return false;
        }
        if (line.equals("no switchport")) {
            // Can't do no switchport on some devices:
            trace(worker, "Ignoring non-required command", "out");
            return false;
        }
        if (line.equals("switchport")) {
            // Some devices (e.g. 891) do not use switchport on single line.
            // Some devices do not support switchport on Port-channel if.
            trace(worker, "Ignoring non-required command", "out");
            return false;
        }
        if (findString("no ip dns view( vrf \\S+)? default", line) >= 0) {
            trace(worker, "Ignoring non-required command", "out");
            return false;
        }
        if (line.startsWith("no interface ")
            && replyall.indexOf("Sub-interfaces are not allowed on switchports") >= 0) {
            trace(worker, "Ignoring useless warning", "out");
            return false;
        }
        if (line.startsWith("no interface LISP")
            && reply.indexOf("Invalid input detected at") >= 0) {
            // Delete of router lisp deletes LISP interfaces (which in turn can't be deleted first)
            trace(worker, "Ignoring delete of missing LISP interface", "out");
            return false;
        }

        if (reply.indexOf("Invalid input detected at") >= 0) {
            // Ignore Invalid input error on non-existing injected config
            for (n = interfaceConfig.size()-1; n >= 0; n--) {
                String entry[] = interfaceConfig.get(n);
                if (findString(line, entry[1]) >= 0) {
                    trace(worker, "Ignoring non-supported injected interface config", "out");
                    return false;
                }
            }
            for (n = globalConfig.size()-1; n >= 0; n--) {
                String entry[] = globalConfig.get(n);
                if (findString(line, entry[0]) >= 0) {
                    trace(worker, "Ignoring non-supported injected global config", "out");
                    return false;
                }
            }
        }

        // Error override messages
        String[] staticError = {
            "Error Message",
            "HARDWARE_NOT_SUPPORTED",
            " Incomplete command.",
            "password/key will be truncated to 8 characters",
            "Warning: Current config does not permit HSRP version 1"
        };
        for (n = 0; n < staticError.length; n++) {
            if (findString(staticError[n], reply) >= 0) {
                trace(worker, "triggered static error: '"+reply+"'", "out");
                return true;
            }
        }

        // Ignore static warnings
        for (n = 0; n < staticWarning.length; n++) {
            if (findString(staticWarning[n], reply.toLowerCase()) >= 0) {
                trace(worker, "ignoring static warning: '"+reply+"'", "out");
                warningsBuf += "> "+line+"\n"+reply+"\n";
                return false;
            }
        }

        // Ignore dynamic warnings
        for (n = 0; n < dynamicWarning.size(); n++) {
            if (findString(dynamicWarning.get(n), reply) >= 0) {
                trace(worker, "ignoring dynamic warning: '"+reply+"'", "out");
                warningsBuf += "> "+line+"\n"+reply+"\n" ;
                return false;
            }
        }

        // Ignore all errors when rollbacking due to abort (i.e. a previous error)
        if (cmd == NedCmd.ABORT_CLI) {
            trace(worker, "ignoring ABORT error: '"+reply+"'", "out");
            return false;
        }

        // Fail on all else
        return true;
    }

    private boolean isCliError(NedWorker worker, int cmd, String reply, String line, String meta) {
        String replyall = reply;

        // Trim and check if empty reply
        reply = reply.replaceAll("\\r", "").trim();
        if (reply.isEmpty() || reply.length() <= 1)
            return false;

        // Strip echo of the failing command 'line'
        if (reply.indexOf("Invalid input") >= 0) {
            reply = reply.replace(line, "");
        }

        // Check all warnings, may be multiple
        reply = "\n" + reply;
        String warnings[] = reply.split("\n% ");
        for (int i = 0; i < warnings.length; i++) {
            String warning = warnings[i].trim();
            if (warning.isEmpty() || warning.length() <= 1)
                continue;
            if (isCliError2(worker, cmd, replyall, warning, line, meta) == true)
                return true;
        }
        return false;
    }

    /**
     * print_line_exec
     */
    private String print_line_exec(NedWorker worker, String line)
        throws Exception {
        String prompt = "\\A[^\\# ]+#[ ]?$";

        // Send command and wait for echo
        session.print(line + "\n");
        session.expect(new String[] { Pattern.quote(line) }, worker);

        // Return command output
        return session.expect(prompt, worker);
    }

    //
    // print_line_wait_oper
    //
    private void print_line_wait_oper(NedWorker worker, int cmd,
                                      String line, int retrying)
        throws NedException, IOException, SSHSessionException, ApplyException {
        NedExpectResult res = null;
        boolean loop = true;

        // Send line and wait for echo
        session.print(line+"\n");
        session.expect(new String[] { Pattern.quote(line) }, worker);

        // Wait for prompt
        while (loop) {
            traceVerbose(worker, "Waiting for oper prompt");
            res = session.expect(new String[] {
                    "Overwrite the previous NVRAM configuration\\?\\[confirm\\]",
                    "Warning: Saving this config to nvram may corrupt any network",
                    "Destination filename \\[\\S+\\][\\?]?\\s*$",
                    privexec_prompt}, worker);
            String failtxt = res.getText();
            switch (res.getHit()) {
            case 0:
                // Overwrite the previous NVRAM configuration
                traceVerbose(worker, "Sending 'y'");
                session.print("y");
                break;
            case 1:
                // Warning: Saving this config to nvram may corrupt any network
                // management or security files stored at the end of nvram.
                // Continue? [no]: no
                // % Configuration buffer full, can't add command: access-list 99
                // %Aborting Save. Compress the config,
                // Save it to flash or Free up space on device[OK]
                // Confirm question with "n", wait for prompt again then fail
                traceVerbose(worker, "Sending 'n'");
                session.print("n");
                res = session.expect(new String[] {".*#"}, worker);
                throw new ExtendedApplyException(line, failtxt, true, false);
            case 2:
                // Destination filename
                traceInfo(worker, "Sending newline (destination filename)");
                session.print("\r\n");
                break;
            default:
                loop = false;
                break;
            }
        }

        //
        // Check device reply
        //
        String lines[] = res.getText().split("\n|\r");
        for (int i = 0 ; i < lines.length ; i++) {
            // Check for retry
            if (lines[i].indexOf("Device or resource busy") >= 0) {
                if (retrying > 66) {
                    // Give up after 65+ seconds
                    throw new ExtendedApplyException(line,lines[i],true,false);
                }
                else {
                    if (retrying == 0)
                        worker.setTimeout(10*66*1000);
                    sleep(worker, 1 * 1000, true); // sleep a second
                    print_line_wait_oper(worker,cmd,line,retrying+1);
                    return;
                }
            }

            // Check for error
            if (lines[i].toLowerCase().indexOf("error") >= 0 ||
                lines[i].toLowerCase().indexOf("failed") >= 0) {
                throw new ExtendedApplyException(line,lines[i],true,false);
            }
        }
    }

    private String decryptPassword(NedWorker worker, String line) {
        Pattern pattern = Pattern.compile("(\\s\\$[48]\\$[^\\s]*)"); // " $4$<key> || $8<key>
        Matcher match   = pattern.matcher(line);
        while (match.find()) {
            if (mCrypto == null) {
                try {
                    traceVerbose(worker, "Creating MaapiCrypto mCrypto");
                    mCrypto = new MaapiCrypto(mm);
                } catch (MaapiException e) {
                    traceInfo(worker, "new MaapiCrypto exception ERROR: "+ e.getMessage());
                }
            }
            try {
                String password  = line.substring(match.start() + 1, match.end());
                String decrypted = mCrypto.decrypt(password);
                traceVerbose(worker, "DECRYPTED MAAPI password: "+password);
                line = line.substring(0, match.start()+1)
                    + decrypted
                    + line.substring(match.end(), line.length());
            } catch (MaapiException e) {
                traceInfo(worker, "mCrypto.decrypt() exception ERROR: "+ e.getMessage());
            }
            match = pattern.matcher(line);
        }
        return line;
    }

    private boolean print_line_wait(NedWorker worker, int cmd, String line,
                                    int retrying, String meta)
        throws NedException, IOException, SSHSessionException, ApplyException {
        String orgLine = line;
        NedExpectResult res;
        boolean isAtTop;
        String reply;
        boolean decrypted = false;

        // dirty patch to fix error that happens in timeout
        if (line.equals("config t")) {
            traceVerbose(worker, "ignored malplaced 'config t'");
            return true;
        }

        // Modify tailfned police for testing
        if (line.startsWith("tailfned police ")) {
            iospolice = line.substring(16);
            logInfo(worker, "SET tailfned police to: "+iospolice);
        }

        // Ignore setting/deleting tailfned 'config'
        if (isNetsim() == false
            && (line.startsWith("tailfned ") || line.startsWith("no tailfned "))) {
            traceInfo(worker, "ignored non-config: " + line);
            return true;
        }

        // Ignore setting/deleting cached-show 'config'
        if (line.trim().indexOf("cached-show ") >= 0) {
            traceInfo(worker, "ignored non-config: " + line);
            return true;
        }

        // password - may be maapi encrypted, decrypt to cleartext
        if (meta != null && (meta.indexOf(" :: secret-password") > 0)
            || meta.indexOf(" :: support-encrypted-password") > 0) {
            String decryptedLine = decryptPassword(worker, line);
            if (!decryptedLine.equals(line)) {
                decrypted = true;
                if (trace) {
                    worker.trace("*" + orgLine + "\n\n", "out", device_id);
                    if (!showVerbose)
                        session.setTracer(null);
                }
                line = decryptedLine;
            }
        }

        // Send line (insert CTRL-V before all '?')
        session.print(stringInsertCtrlV(line) + "\n");

        // Optional delay, used e.g. to not overload link/device
        if (deviceOutputDelay > 0) {
            sleep(worker, deviceOutputDelay, false);
        }

        // Send commad and wait for optional echo
        if (waitForEcho == Echo.WAIT) {
            try {
                res = session.expect(new String[] { Pattern.quote(line) }, worker);
            } catch (SSHSessionException e) {
                throw new NedException(e.getMessage()+" sending '"+line+"' [previous sent cmd = '"+lastOKLine+"']");
            }
            //traceVerbose(worker, "got echo: '"+res.getMatch()+"'");
        } else {
            //traceVerbose(worker, "ignored echo of: '"+line+"'");
        }

        // Enable tracing if disabled due to sending decrypted clear text passwords
        if (decrypted) {
            if (trace) {
                session.setTracer(worker);
                worker.trace("*" + orgLine + "\n", "out", device_id);  // simulated echo
            }
            line = orgLine;
        }

        // Wait for prompt
        res = session.expect(plw, worker);
        //traceVerbose(worker, "prompt matched("+res.getHit()+"): text='"+res.getText() + "'");

        // Check for a blocking confirmation prompt
        if (waitForEcho == Echo.WAIT && res.getHit() >= 4) {
            traceVerbose(worker, "PROMPTED: " + res.getText());

            // First try sending a 'y' only, wait 1 sec for prompt
            session.print("y");
            session.expect(new String[] { "y" }, worker);
            try {
                res = session.expect(plw, false, 1000, worker);
            } catch (Exception e) {
                // Timeout -> send 'es\n' for a full 'yes' + enter
                session.print("es\n");
                session.expect(new String[] { "es" }, worker);
                res = session.expect(plw, worker);
            }
        }

        // Get reply text (note: after confirm-questions for new text)
        reply = res.getText();

        // Check prompt
        if (res.getHit() == 0 || res.getHit() == 1) {
            // Top container - (cfg) || (config)
            isAtTop = true;
        } else if (res.getHit() == 2) {
            // Config mode
            isAtTop = false;
        } else if (res.getHit() == 3) {
            // non config mode - #
            inConfig = false;
            logInfo(worker, "command '"+line+"' caused exit from config mode");
            throw new ExtendedApplyException(line, "exited from config mode",
                                             true, false);
        } else {
            exitPrompting(worker);
            throw new ExtendedApplyException(line,
                                             "print_line_wait internal error",
                                             true, inConfig);
        }

        // Skip first line in error checks since that is the echo of the cmd
        if (waitForEcho == Echo.SKIPCMD) {
            int nl = reply.indexOf("\n");
            if (nl > 0) {
                reply = reply.substring(nl);
            }
        }

        // Look for retries
        if (isCliRetry(worker, reply, line)) {
            // Wait a while and retry
            if (retrying > 66) {
                // Already tried enough, give up
                throw new ExtendedApplyException(line, reply, isAtTop, true);
            }
            else {
                if (retrying == 0)
                    worker.setTimeout(10*66*1000);
                sleep(worker, 1 * 1000, true); // sleep a second
                traceVerbose(worker, "Retry #" + (retrying+1));
                return print_line_wait(worker, cmd, line, retrying+1, meta);
            }
        }

        // Special line treatment
        if (isCliPatch(worker, cmd, reply, line, meta)) {
            return print_line_wait(worker, cmd, line, retrying, meta);
        }

        // Look for errors
        if (isCliError(worker, cmd, reply, line, meta)) {
            throw new ExtendedApplyException(line, reply, isAtTop, true);
        }

        // Retry succeeded, reset writeTimeout
        if (retrying > 0) {
            logInfo(worker, "Retry success after " + retrying + " retries");
            logInfo(worker, "setTimeout(writeTimeout = "+writeTimeout+")");
            worker.setTimeout(writeTimeout);
        }

        // Sleep threee seconds for clear command to take effect (RT20042)
        if (line.equals("do clear crypto ikev2 sa fast")) {
            worker.setTimeout(10*60*1000);
            sleep(worker, 3 * 1000, true); // Sleep 3 seconds
        }

        lastOKLine = line;

        return isAtTop;
    }

    private boolean enterConfig(NedWorker worker, int cmd)
        throws NedException, IOException, SSHSessionException {
        NedExpectResult res = null;

        session.print("config t\n");
        res = session.expect(ec, worker);

        if (res.getHit() > 2) {
            // Aborted | Error | syntax error | error
            traceVerbose(worker, "error entering config mode, abort or error");
            worker.error(cmd, res.getText());
            return false;
        }

        else if (res.getHit() == 0) {
            // Do you want to kill that session and continue
            session.print("yes\n");
            res = session.expect(ec2, worker);
            if (res.getHit() > 2) {
                // Aborted | Error | syntax error | error
                traceVerbose(worker, "error entering config mode, failed to kill session");
                worker.error(cmd, res.getText());
                return false;
            }
        }

        inConfig = true;
        return true;
    }

    private void exitConfig(NedWorker worker) throws IOException, SSHSessionException {
        NedExpectResult res;

        traceVerbose(worker, "exitConfig()");

        while (true) {
            session.print("exit\n");
            res = session.expect(new String[]
                {"\\A\\S*\\(config\\)#",
                 "\\A\\S*\\(cfg\\)#",
                 "\\A.*\\(.*\\)#",
                 "\\A\\S*\\(cfg.*\\)#",
                 prompt});
            if (res.getHit() == 4) {
                inConfig = false;
                return;
            }
        }
    }

    private void sendBackspaces(NedWorker worker, String cmd)
        throws Exception {
        if (cmd.length() <= 1)
            return;
        String buf = "";
        for (int i = 0; i < cmd.length() - 1; i++)
            buf += "\u0008"; // back space
        traceVerbose(worker, "Sending " + (cmd.length()-1) + " backspace(s)");
        session.print(buf);
    }

    private void exitPrompting(NedWorker worker) throws IOException, SSHSessionException {
        NedExpectResult res;

        Pattern[] cmdPrompt = new Pattern[] {
            // Prompt patterns:
            Pattern.compile(privexec_prompt),
            Pattern.compile("\\A.*\\(.*\\)#"),
            Pattern.compile("\\A\\S*#"),
            // Question patterns:
            Pattern.compile(":\\s*$"),
            Pattern.compile("\\]\\s*$")
        };

        while (true) {
            traceVerbose(worker, "Sending CTRL-C");
            session.print("\u0003");
            traceVerbose(worker, "Waiting for non-question");
            res = session.expect(cmdPrompt, true, readTimeout, worker);
            if (res.getHit() <= 2) {
                traceVerbose(worker, "Got prompt ("+res.getHit()+")");
                return;
            }
        }
    }

    private String[] fillGroups(Matcher matcher) {
        String[] groups = new String[matcher.groupCount()+1];
        for (int i = 0; i < matcher.groupCount()+1; i++) {
            groups[i] = matcher.group(i);
        }
        return groups;
    }

    private String modifyInsertCommand(NedWorker worker, String insert, String where, String[] groups) {
        int i, offset = 0;

        // Replace $i with group value from match.
        // Note: hard coded to only support up to $9
        for (i = insert.indexOf("$"); i >= 0; i = insert.indexOf("$", i+offset)) {
            int num = (int)(insert.charAt(i+1) - '0');
            insert = insert.substring(0,i) + groups[num] + insert.substring(i+2);
            offset = offset + groups[num].length() - 2;
        }

        if (isNetsim())
            insert = "!" + insert;

        traceInfo(worker, "INJECT: injecting command '"+insert.trim()+"' "+where+" "+groups[0]);

        return insert;
    }

    private void printIfData(NedWorker worker, String round, String lines[], int lastif)
        throws NedException {
        if (showVerbose == false)
            return;
        traceVerbose(worker, "printIfData("+round+")");
        for (int i = lastif; i < lines.length; i++) {
            traceVerbose(worker, lines[i]);
            if (isTopExit(lines[i]))
                break;
        }
    }

    private String[] reorderDataBlock(NedWorker worker, String rules[], String lines[], int start, int end) {
        int j;

        //
        // Syntax of reorder string array - rules
        //
        // rules[rule] = tag[0] :: tag[1] :: tag[2] :: [optional tag[3]]
        // tag[0] = line A to move (regexp)
        // tag[1] = after|before
        // tag[2] = line B to stay (regexp)
        //

        // Keep looping until we no longer reorder any config
        for (boolean inorder = false; inorder == false; ) {
            inorder = true;

            // Loop through the rules
            for (int rule = 0; rule < rules.length; rule++) {
                String tag[] = rules[rule].split(" :: ");
                boolean before = tag[1].startsWith("before");

                // Loop through and reorder all entries in this rule
                for (;;) {

                    // First find stay (note: stay may have moved since last loop)
                    int stay = -1;
                    for (j = start; j < end; j++) {
                        if (lines[j].trim().matches("^"+tag[2].trim()+"$")) {
                            stay = j;
                            if (before)
                                break; // if moving something before, break at first match
                        }
                    }
                    if (stay == -1)
                        break;

                    // Then find best move (depends on after or before)
                    int move = -1;
                    if (before) {
                        for (j = stay + 1; j < end; j++) {
                            if (lines[j].trim().matches("^"+tag[0].trim()+"$")) {
                                move = j;
                                break;
                            }
                        }
                    } else {
                        for (j = start + 1; j < stay; j++) {
                            if (lines[j].trim().matches("^"+tag[0].trim()+"$")) {
                                move = j;
                                break;
                            }
                        }
                    }
                    if (move == -1)
                        break;

                    // Move the 'move' entry by shifting lines
                    traceInfo(worker, "DIFFPATCH :: reorder rule #"+rule+" : "+
                              "moved '"+lines[move]+"' "+tag[1]+" '"+lines[stay]+"'");
                    String moveLine = lines[move];
                    if (before) {
                        for (j = move; j > stay; j--)
                            lines[j] = lines[j-1];
                    } else {
                        for (j = move; j < stay; j++)
                            lines[j] = lines[j+1];
                    }
                    lines[stay] = moveLine;
                    inorder = false;
                    num_reordered++;
                }
            }
        }
        return lines;
    }

    private String reorderData(NedWorker worker, String data) {
        int n;

        num_reordered = 0;

        //
        // Pass 1 - reorder sub-mode (block) config
        //
        String toptag = "";
        String lines[] = data.split("\n");
        for (n = 0; n < lines.length - 1; n++) {
            String trimmed = lines[n].trim();
            int start = n;
            int end = -1;
            if (trimmed.isEmpty())
                continue;
            if (isTopExit(lines[n])) {
                toptag = "";
            } else if (Character.isLetter(lines[n].charAt(0))) {
                toptag = lines[n].trim();
            }

            //
            // interface
            //
            // Note: Rule is whitespace insensitive in endings due to line.trim()
            String[] interfaceRules = {
                "no switchport \\S.* :: before :: no switchport",
                "no switchport trunk allowed vlan :: after :: no switchport trunk allowed vlan \\S.*",

                "no service instance .* :: before :: no switchport.*",
                "no ip dhcp snooping .* :: before :: no switchport.*",
                "(no )?ip route-cache :: before :: switchport",
                "(no )?ip route-cache :: after :: no switchport",
                "ip address \\S.* :: after :: no switchport",

                "no ip address.* :: before :: no encapsulation dot1Q \\d+", // RT25447
                "ip address \\S.* :: after :: (ip )?vrf \\S.*",
                "no ip address \\S.* :: before :: no (ip )?vrf \\S.*",

                "(no )?mdix auto :: before :: (media-type\\s+sfp|no media-type\\s+rj45)",
                "duplex\\s+auto :: before :: speed\\s+auto"
            };
            if (toptag.startsWith("interface ")) {
                // Find interface exit (and forward outer loop)
                for (n = n + 1; n < lines.length; n++) {
                    if (isTopExit(lines[n])) {
                        end = n;
                        break;
                    }
                }
                if (end == -1) {
                    logVerbose(worker, "reorderData :: internal error : missing interface exit");
                    continue;
                }

                // Reorder interface config
                lines = reorderDataBlock(worker, interfaceRules, lines, start, end);
            }

            //
            // reorder delete lists
            //
            String[] deleteListRules = {
                "ip prefix-list ",
                "ipv6 prefix-list "
            };
            for (int r = 0; r < deleteListRules.length; r++) {
                String name = deleteListRules[r];
                if (toptag.startsWith(name)) {
                    for (n = n + 1; n < lines.length; n++) {
                        if (lines[n].startsWith(name) || lines[n].startsWith("no "+name)) {
                            end = n;
                        } else {
                            break;
                        }
                    }
                    if (end == -1)
                        continue;
                    traceVerbose(worker, "reordering '"+name+"' start="+start+" end="+end);
                    String[] rule = new String[1];
                    rule[0] = "no "+name+".* :: before :: "+name+".*";
                    lines = reorderDataBlock(worker, rule, lines, start, end + 1);
                    break;
                }
            }
        }

        //
        // Pass 2 - Put delete lines inside modes before create lines
        //
        for (n = 0; n < lines.length; n++) {
            if (newIpACL && lines[n].startsWith("ip access-list ")) {
                List<String> delete = new ArrayList<String>();
                List<String> create = new ArrayList<String>();
                for (int m = ++n; m < lines.length; m++) {
                    String trimmed = lines[m].trim();
                    if (isTopExit(trimmed) || lines[m].charAt(0) != ' ')
                        break;
                    if (trimmed.startsWith("no "))
                        delete.add(lines[m]);
                    else
                        create.add(lines[m]);
                }
                for (String line : delete)
                    lines[n++] = line;
                for (String line : create)
                    lines[n++] = line;
            }
        }


        //
        // Pass 3 - reorder top mode config (e.g. delete of routes, change of bgp num)
        //
        StringBuilder middle = new StringBuilder();
        StringBuilder first = new StringBuilder();
        for (n = 0; n < lines.length; n++) {
            if (lines[n].startsWith("no ip route ")) {
                first.append(lines[n]+"\n");
            } else {
                middle.append(lines[n]+"\n");
            }
        }
        data = first.toString() + middle.toString();

        //
        // Reordering complete -> log results
        //
        if (num_reordered > 0) {
            traceInfo(worker, "DIFFPATCH: reordered "+num_reordered+" line(s)");
            //traceVerbose(worker, "APPLY_AFTER_DIFFPATCH:\n"+data);
        }

        return data;
    }

    private String modifyLineByLine(NedWorker worker, String data)
        throws NedException {
        String match;

        String lines[] = data.split("\n");
        data = null; // to provoke crash if used below

        StringBuilder buffer = new StringBuilder();
        String toptag = "";
        String meta = "";
        for (int n = 0; n < lines.length; n++) {
            String transformed = null;
            String trimmed = lines[n].trim();
            String cmd = trimmed.startsWith("no ") ? trimmed.substring(3) : trimmed;
            if (trimmed.isEmpty())
                continue;
            if (trimmed.startsWith("! meta-data :: ")) {
                meta = meta + lines[n] + "\n";
                buffer.append(lines[n]+"\n");
                continue;
            }

            // Update toptag
            if (Character.isLetter(lines[n].charAt(0))) {
                toptag = trimmed;
            }

            //
            // meta-data "support-encrypted-password"
            //
            if (meta.contains("}/config/key/config-key/password-encrypt :: support-encrypted-password")) {
                traceInfo(worker, "PATCH: injecting config-key password-encrypt delete");
                buffer.append("no key config-key password-encrypt\n");
            }

            //
            // Transform lines[n] -> XXX
            //
            if (transformed != null && !transformed.equals(lines[n])) {
                if (transformed.isEmpty())
                    traceVerbose(worker, "transformed to dev: stripped '"+trimmed+"'");
                else
                    traceVerbose(worker, "transformed to dev: '"+trimmed+"' -> '"+transformed.trim()+"'");
                lines[n] = transformed;
            }

            // Append to buffer
            if (lines[n] != null && !lines[n].isEmpty()) {
                buffer.append(lines[n]+"\n");
            }
            meta = "";
        }
        data = buffer.toString();
        return data;
    }

    private String[] modifyData(NedWorker worker, String data, String function)
        throws NedException {
        String lines[];
        String line, nextline;
        int i, j, n;
        boolean log;
        NavuContext toContext;
        int toTh;

        //
        // Scan meta-data and modify data
        //

        // Attach to CDB, create NAVU Context and disable default values when reading
        logVerbose(worker, function + " TRANSFORMING - meta-data");
        try {
            toTh = worker.getToTransactionId();
            mm.attach(toTh, 0);
            toContext = new NavuContext(mm, toTh);
        } catch (Exception e) {
            throw new NedException("modifyData() - ERROR : failed to create NAVU (maapi NO_DEFAULTS)", e);
        }

        // Scan meta-data and modify data
        data = metaData.modifyData(data, toContext, toTh, mm);

        // Detach getToTransactionId NAVU context
        try {
            mm.detach(toTh);
        } catch (Exception e) {
            throw new NedException("modifyData() - ERROR : failed to detach NAVU", e);
        }


        //
        // nedData
        //
        if (autoVrfForwardingRestore) {
            logVerbose(worker, function + " TRANSFORMING - restoring interface addresses");
            data = nedData.vrfForwardingRestore(data);
        }


        //
        // Reorder data
        //
        logVerbose(worker, function + " TRANSFORMING - reordering config");
        data = reorderData(worker, data);


        //
        // policy-map - bandwidth&priority percent subtractions first
        //
        logVerbose(worker, function + " TRANSFORMING - policy-map bandwidth");
        for (i = data.indexOf("\npolicy-map ");
             i >= 0;
             i = data.indexOf("\npolicy-map ", i+12)) {
            if ((n = data.indexOf("\n!", i+1)) < 0)
                continue;
            // Copy the entire policy-map into polmap
            String polmap = data.substring(i,n+2);
            if (polmap.indexOf("no bandwidth percent ") < 0
                && polmap.indexOf("no priority percent ") < 0)
                continue; // if not deleting, then this patch not needed
            if (!hasString("\n\\s+(bandwidth|priority) percent \\S+", polmap))
                continue; // if not adding any entries, no reason to delete first
            // Strip all lines except the 'no bandwidth/priority percent'
            lines = polmap.split("\n");
            StringBuilder newlines = new StringBuilder();
            for (n = 0; n < lines.length; n++) {
                if (lines[n].indexOf("!") >= 0
                    || lines[n].indexOf("policy-map ") >= 0
                    || lines[n].indexOf("class ") >= 0
                    || lines[n].indexOf("no bandwidth percent ") >= 0
                    || lines[n].indexOf("no priority percent ") >= 0) {
                    newlines.append(lines[n]+"\n");
                }
            }
            // Add the new stripped duplicate policy-map before the original
            polmap = newlines.toString();
            traceInfo(worker, "PATCH: subtracting all bandwidth percent first, inserting:\n"+polmap);
            data = data.substring(0,i) + polmap + data.substring(i);
            // Skip this policy-map when looking for the next
            i = i + polmap.length();
        }


        //
        // Dequote certificates
        //
        if (isDevice()) {
            logVerbose(worker, function + " TRANSFORMING - certificates");
            for (i = data.indexOf("\n certificate ");
                 i >= 0;
                 i = data.indexOf("\n certificate ", i + 12)) {
                int start = data.indexOf("\"", i+1);
                if (start < 0)
                    continue;
                int end = data.indexOf("\"", start+1);
                if (end < 0)
                    break;
                traceVerbose(worker, "dequoted: " + data.substring(i, start).trim());
                traceVerbose(worker, "Sending certificate -> disabling wait for echo");
                waitForEcho = Echo.DONTWAIT;
                String cert = data.substring(start, end+1);
                cert = stringDequote(worker, cert);
                data = data.substring(0,start-1) + cert + "\n" + data.substring(end+1);
            }
        }


        //
        // switchport trunk allowed vlan
        //
        if (isDevice()) {
            // Note: Don't use 'add' keyword the first time
            logVerbose(worker, function + " TRANSFORMING - switchport trunk allowed");
            data = data.replaceAll("\n(\\s*)switchport trunk allowed vlan (\\d+)",
                                 "\n$1switchport trunk allowed vlan add $2");
            data = data.replaceAll("\n(\\s*)switchport trunk allowed vlan"+
                                   "\n(\\s*)switchport trunk allowed vlan add",
                                   "\n$1switchport trunk allowed vlan");
            data = data.replaceAll("\n(\\s*)switchport trunk allowed vlan\n", "\n");
        }


        //
        // Insert global command(s)
        //
        logVerbose(worker, function + " TRANSFORMING - injecting commands");
        for (n = 0; n < globalCommand.size(); n++) {
            String entry[]  = globalCommand.get(n);
            Pattern pattern = Pattern.compile(entry[0]);
            Matcher matcher = pattern.matcher(data);
            int offset = 0;
            String groups[] = null;
            String insert;
            if (entry[3].equals("before-first")) {
                if (matcher.find()) {
                    insert = modifyInsertCommand(worker, entry[2] + "\n", entry[3], fillGroups(matcher));
                    data = data.substring(0, matcher.start(0))
                        + insert
                        + data.substring(matcher.start(0));
                }
            } else if (entry[3].equals("before-each")) {
                while (matcher.find()) {
                    insert = modifyInsertCommand(worker, entry[2] + "\n", entry[3], fillGroups(matcher));
                    data = data.substring(0, matcher.start(0) + offset)
                        + insert
                        + data.substring(matcher.start(0) + offset);
                    offset = offset + insert.length();
                }
            } else if (entry[3].equals("after-last")) {
                int end = -1;
                while (matcher.find()) {
                    end = matcher.end(0);
                    groups = fillGroups(matcher);
                }
                if (end != -1) {
                    insert = modifyInsertCommand(worker, entry[2] + "\n", entry[3], groups);
                    data = data.substring(0, end + 1)
                        + insert + "\n"
                        + data.substring(end + 1);
                }
            } else if (entry[3].equals("after-each")) {
                while (matcher.find()) {
                    insert = modifyInsertCommand(worker, entry[2] + "\n", entry[3], fillGroups(matcher)) + "\n";
                    data = data.substring(0, matcher.end(0) + 1 + offset)
                        + insert
                        + data.substring(matcher.end(0) + 1 + offset);
                    offset = offset + insert.length();
                }
            }
        }


        //
        // LINE-BY-LINE TRANSFORMATIONS
        //
        if (isDevice()) {
            logVerbose(worker, function + " TRANSFORMING - line-by-line");
            data = modifyLineByLine(worker, data);
        }

        traceVerbose(worker, "\nSHOW_AFTER_LINE_BY_LINE:\n"+data);

        //
        // ROUND 1 - reorder and/or modify single lines
        //
        traceVerbose(worker, function + " TRANSFORMING - round 1");
        lines = data.split("\n");
        for (i = 0; i < lines.length; i++) {
            line = lines[i];

            // switch 'max-metric router-lsa' and 'no max-metric router-lsa'
            // in 'router ospf *' due to unidentified bug in ncs-3.4.4 or older.
            if (line.trim().startsWith("max-metric router-lsa ")) {
                nextline = lines[i+1];
                if (nextline.trim().startsWith("no max-metric router-lsa ")) {
                    traceInfo(worker, "NCSPATCH: reordering 'max-metric router-lsa' due to NCS bug");
                    lines[i]   = nextline;
                    lines[i+1] = line;
                    i = i + 1;
                    continue;
                }
            }

            // Reorder 'redistribute X <Y> .. [Z]' and 'no redistribute X'
            String match;
            if (i > 1 && (match = getMatch(line, "no redistribute (.*)")) != null
                && lines[i-1].trim().startsWith("redistribute "+match+" ")) {
                traceInfo(worker, "NCSPATCH: reordering '"+lines[i]+"' and '"+lines[i-1]+"' due to NCS bug");
                lines[i]   = lines[i-1];
                lines[i-1] = line;
                continue;
            }

            // Move router * / distribute-list create last, to after all no
            if (line.trim().startsWith("distribute-list ")) {
                log = true;
                for (; i < lines.length - 1; i++) {
                    nextline = lines[i+1];
                    if (nextline.indexOf("distribute-list") < 0)
                        break;
                    if (log) {
                        traceInfo(worker, "NCSPATCH: moving 'distribute-list' creation last'");
                        log = false;
                    }
                    lines[i]   = nextline;
                    lines[i+1] = line;
                }
            }
        }

        //
        // ROUND 2 - strip create/delete of bridge-group contents when delete entire group
        //
        // Note: sub-interface Dot11Radio config on a 880
        traceVerbose(worker, function + " TRANSFORMING - round 2");
        for (i = 0; i < lines.length; i++) {
            line = lines[i];
            if (line.matches("^\\s*no bridge-group \\d+\\s*$")) {
                log = true;
                String id = line.substring(line.indexOf("no bridge-group ") + 16);
                for (; i < lines.length - 1; i++) {
                    if (lines[i+1].trim().equals("exit"))
                        break;
                    if (lines[i+1].indexOf("bridge-group " + id) >= 0) {
                        lines[i+1] = "!" + lines[i+1];
                        if (log) {
                            traceVerbose(worker, "PATCH: stripping unnecessary bridge-group command(s)");
                            log = false;
                        }
                    }
                }
            }
        }

        //
        // ROUND 3 - Reverse order of 'no line vty .*' [RT 24125]
        //
        // When deleting a vty the device deletes all higher ones dynamically.
        // Hence, we must delete vty's with highest numbers first.
        traceVerbose(worker, function + " TRANSFORMING - round 3");
        Map <Integer,String> lineVty = new LinkedHashMap<Integer,String>();
        for (i = 0; i < lines.length; i++) {
            line = lines[i];
            if (line.matches("^\\s*no line vty \\d+ \\d+\\s*$")) {
               lineVty.put(i, line);
            }
        }
        if (lineVty.size() > 1) {
            traceVerbose(worker, "PATCH: reordering delete of vty lines");
            int minIndex = (int)Collections.min(lineVty.keySet());
            int maxIndex = (int)Collections.max(lineVty.keySet());
            for (int x = maxIndex, y = minIndex; x >= minIndex && y <= maxIndex; x--, y++) {
                lines[x] = lineVty.get(y);
            }
        }

        return lines;
    }


    // NOTE: prepareDry calls modifyLine without trim() where as applyConfig
    //       has called trim() prior to calling the function.
    private String modifyLine(NedWorker worker, String line, String meta)
        throws NedException {
        int i;
        String match;
        String[] group;
        String trimmed = line.trim();

        //traceVerbose(worker, "modifyLine() line="+line+" meta="+meta);

        if (isNetsim()) {

            // description patch for netsim, quote text and escape "
            if ((i = line.indexOf("description ")) >= 0) {
                String desc = line.substring(i+12).trim(); // Strip initial white spaces, added by NCS
                if (desc.charAt(0) != '"') {
                    desc = desc.replaceAll("\\\"", "\\\\\\\""); // Convert " to \"
                    line = line.substring(0,i+12) + "\"" + desc + "\""; // Quote string, add ""
                }
            }

            // strip switchport trunk allowed 'add' trick on netsim
            else if (line.matches("^\\s*(no )?switchport trunk allowed vlan\\s*$")) {
                return null;
            }

            return line;
        }

        // REAL DEVICES BELOW:

        // suppress command that can not be removed on the device.
        if (meta != null && meta.indexOf(" :: suppress-no-command") > 0
                 && line.trim().startsWith("no ")) {
            line = "!suppressed: "+line;
        }

        // description string
        else if ((i = line.indexOf(" description ")) >= 0) {
            // NOTE: Not acctually used because of cli-preformatted !?
            line = line.substring(0,i+13) + stringDequote(worker, line.substring(i+13));
        }

        // logging discriminator * [mnemonics drops|includes *] msg-body drops|includes *
        else if (line.trim().startsWith("logging discriminator ")) {
            if (line.indexOf("mnemonics") >= 0 && line.indexOf("msg-body") >= 0)
                line = line.replaceFirst("mnemonics (\\S+) \"(.*)\" msg-body (\\S+) \"(.*)\"",
                                         "mnemonics $1 $2 msg-body $3 $4");
            else if (line.indexOf("mnemonics") >= 0)
                line = line.replaceFirst("mnemonics (\\S+) \"(.*)\"", "mnemonics $1 $2");
            else
                line = line.replaceFirst("msg-body (\\S+) \"(.*)\"", "msg-body $1 $2");
        }

        // Passwords need to be dequoted using passwordDequote before sent to device
        else if (meta != null &&
                 (meta.contains(":: secret-password") || meta.contains(":: support-encrypted-password"))
                 && (match = getMatch(line, " (\\\".*\\\")")) != null) {
            line = line.replace(match, passwordDequote(worker, match));
        }

        // interface * / cable rf-channels channel-list x-y z bandwidth-percent
        // interface * / cable rf-channels controller ? channel-list x-y z bandwidth-percent
        else if (line.indexOf("cable rf-channels ") >= 0
                 && line.indexOf(" channel-list ") > 0) {
            line = line.replaceAll(",", " ");
        }

        // cable profile service-group * / mac-domain * / downstream sg-channel
        else if (line.matches("^\\s*downstream sg-channel .* profile \\S+$")) {
            line = line.replaceAll(",", " ");
        }

        // no cable service class * name <name>
        else if (trimmed.startsWith("no cable service class ")
                 && (match = getMatch(trimmed, "^no cable service class (\\d+) name \\S+$")) != null) {
            line = "no cable service class "+match;
        }

        // banner motd|exec|login|prompt-timeout|etc.
        else if (line.matches("^\\s*banner .*$")) {
            i = line.indexOf("banner ");
            i = line.indexOf(" ",i+7);
            String banner = stringDequote(worker, line.substring(i+1).trim());
            banner = banner.replaceAll("\\r", "");  // device adds \r itself
            line = line.substring(0,i+1) + "^\n" + banner + "^";
            traceVerbose(worker, "Sending banner -> disabling wait for echo");
            waitForEcho = Echo.DONTWAIT;
        }

        // menu <name> title ^C <title text> \n^C
        else if (line.matches("^\\s*menu \\S+ title .*$")) {
            i = line.indexOf("title ");
            String title = stringDequote(worker, line.substring(i+6).trim());
            title = title.replaceAll("\\r", "");  // device adds \r itself
            line = line.substring(0,i+6) + "^" + title + "^";
            traceVerbose(worker, "Sending menu -> disabling wait for echo");
            waitForEcho = Echo.DONTWAIT;
        }

        // macro name <name> "command1\r\ncommand2\r\ncommandN\r\n"
        else if (line.matches("^\\s*macro name .*$")) {
            i = line.indexOf("macro name ");
            i = line.indexOf(" ",i+11);
            String commands = stringDequote(worker, line.substring(i+1).trim());
            commands = commands.replaceAll("\\r", "");  // device adds \r itself
            line = line.substring(0,i+1) + "\n" + commands + "@";
            traceVerbose(worker, "Sending macro -> disabling wait for echo");
            waitForEcho = Echo.DONTWAIT;
        }

        // event manager applet * / action * regexp
        else if (line.matches("^\\s*action \\d+ regexp .*$")) {
            i = line.indexOf(" regexp \"");
            if (i > 0) {
                String regexp = stringDequote(worker, line.substring(i+8));
                line = line.substring(0,i+8) + regexp;
            }
        }

        // alias <mode> <name> *
        else if (trimmed.startsWith("alias ") &&
                 (group =  getMatches(worker, line, "(alias \\S+ \\S+ )\\\"(.*)\\\"")) != null) {
            line = group[0] + passwordDequote(worker, group[1]);
        }

        // snmp-server location|contact *
        else if (trimmed.startsWith("snmp-server ") &&
                 (group =  getMatches(worker, line, "(snmp-server (?:location|contact) )\\\"(.*)\\\"")) != null) {
            line = group[0] + passwordDequote(worker, group[1]);
        }

        // ip address (without arguments)
        else if (line.matches("^\\s*ip address\\s*$")) {
            line = "!" + line;
        }

        // no disable passive-interface
        else if (line.indexOf("no disable passive-interface ") >= 0) {
            line = line.replaceAll("no disable passive-interface ",
                                   "passive-interface ");
        }

        // disable passive-interface
        else if (line.indexOf("disable passive-interface ") >= 0) {
            line = line.replaceAll("disable passive-interface ",
                                   "no passive-interface ");
        }

        // no-list - generic trick for no-lists
        else if (line.indexOf("no-list ") >= 0) {
            line = line.replace("no-list ", "");
            if (line.matches("^\\s*no .*$"))
                line = line.replace("no ", "");
            else
                line = "no " + line;
        }

        // ip forward-protocol udp
        else if (line.indexOf("ip forward-protocol udp ") >= 0
                 && line.indexOf(" disabled") > 0) {
            line = line.replace(" disabled", "");
            if (line.indexOf("no ip") >= 0)
                line = line.replace("no ip", "ip");
            else
                line = "no " + line;
        }

        // network-clock-participate
        else if (line.indexOf("network-clock-participate wic-disabled ") >= 0) {
            line = line.replaceAll("network-clock-participate wic-disabled ",
                                   "no network-clock-participate wic ");
        }

        // police
        else if (hasPolice("bpsflat") && line.indexOf("police ") >= 0) {
            // Catalyst device style: policy-map / class / police
            line = line.replaceAll("police (\\d+) bps (\\d+) byte", "police $1 $2");
        }

        // no ip ssh server|client algorithm mac .*
        else if (line.matches("^no ip ssh (server|client) algorithm mac .*$")) {
            line = line.substring(0,line.indexOf("mac")+3);
            line = line.replace("no", "default");
        }


        // no ip ssh server|client algorithm encryption .*
        else if (line.matches("^no ip ssh (server|client) algorithm encryption .*$")) {
            line = line.substring(0,line.indexOf("encryption")+10);
            line = line.replace("no", "default");
        }

        // ip mroute-cache
        else if (line.matches("^\\s*ip mroute-cache$") && this.useIpMrouteCacheDistributed) {
            line = line+" distributed";
        }

        // no switchport trunk allowed vlan -> switchport trunk allowed vlan remove
        else if (line.indexOf("no switchport trunk allowed vlan ") >= 0) {
            line = line.replaceAll("(\\s*)no switchport trunk allowed vlan (\\d+)",
                                   "$1switchport trunk allowed vlan remove $2");
        }

        // monitor session * filter vlan *
        else if (line.indexOf("monitor session") >= 0 && line.indexOf(" filter vlan ") > 0) {
            line = line.replace(","," , ").replace("-"," - ");
        }

        // controller SONET * / sts-1 "x - y" mode sts-3c
        else if (trimmed.startsWith("sts-1 ")) {
            line = line.replace("\"", "");
        }

        return line;
    }

    private void sendPassword(NedWorker worker, String text, String password)
        throws Exception {
        if (showVerbose) {
            traceInfo(worker, text + password);
            session.print(password+"\n");
        } else {
            traceInfo(worker, text + "*HIDDEN PASSWORD*");
            if (trace)
                session.setTracer(null);
            session.print(password+"\n");
            if (trace)
                session.setTracer(worker);
        }
    }

    private void sendNewLine(NedWorker worker, String logtext)
        throws Exception {
        traceInfo(worker, logtext);
        session.print("\r\n");
    }

    public void loginDevice(NedWorker worker, int phase, String username, String password, String enablePassword)
        throws Exception {
        NedExpectResult res;
        int maxFails = 2;  // To bypass banners confusing login process
        boolean enableMode = false;
        int timeout = promptTimeout;

        while (true) {

            // Wait for terminal output from device
            try {
                traceInfo(worker, "Waiting for input from device");
                res = session.expect(new String[] {
                        "\\A.*[Bb]ad passwords",
                        "\\A.*[Pp]ermission denied",
                        "\\A.*[Rr]equest [Dd]enied",
                        "\\A.*[Aa]uthentication failed",
                        "\\A.*[Ll]ogin invalid",
                        "\\A.*[Aa]ccess denied",

                        "\\A.*[Ee]rror in authentication",
                        "\\A.*[Bb]ad secrets",
                        "\\A.*[Cc]ommand authorization failed",

                        "\\A.*User Access Verification",
                        "\\A.*[Pp]assword OK",
                        "\\A.*[Ll]ogin:",
                        "\\A.*[Uu]sername:",
                        "\\A.*[Pp]assword:",
                        "\\A.*Press RETURN to get started",
                        "\\A\\S.*>",
                        privexec_prompt},
                    false, timeout, worker);
            } catch (Throwable e) {
                if (timeout >= connectTimeout)
                    throw new NedException("Timeout, no response from device");
                sendNewLine(worker, "Sending newline (timeout "+timeout+")");
                timeout += promptTimeout;
                continue;
            }

            // Parse reply and act accordingly
            timeout = promptTimeout;
            switch (res.getHit()) {
            case 0: // Bad passwords
            case 1: // Permission denied
            case 2: // Request Denied
            case 3: // Authentication failed
            case 4: // Login invalid
            case 5: // Access denied
                traceInfo(worker, "Authentication failed, attempts left = " + maxFails);
                if (maxFails-- == 0)
                    throw new NedException("Authentication failed");
                break;

            case 6: // Error in authentication (note: enable fails instantly)
            case 7: // Bad secrets
            case 8: // Command authorization failed
                throw new NedException("Authentication failed");

            case 9: // User Access Verification
                if (phase == 0 && remoteConnection.equals("serial")) {
                    traceInfo(worker, "Received 'User Access Verification' -> login using proxy credentials");
                    return; // serial proxy, login later using the proxy credentials
                }
                break;

            case 10: // Password OK
                traceInfo(worker, "Received Password OK");
                break;

            case 11: // Login:
            case 12: // Username:
                enableMode = false;
                if (username == null)
                    throw new NedException("(remote) username not set");
                traceInfo(worker, "Sending username");
                session.print(username+"\n");
                traceInfo(worker, "Waiting for echo of username");
                session.expect(username, worker);
                break;

            case 13: // Password:
                if (enableMode) {
                    if (enablePassword == null || enablePassword.isEmpty())
                        throw new NedException("secondary password not set");
                    sendPassword(worker, "Sending enable password: ", enablePassword);
                }
                else {
                    if (password == null)
                        throw new NedException("(remote) password not set");
                    sendPassword(worker, "Sending password: ", password);
                    if (!remoteConnection.equals("none")) {
                        sendNewLine(worker, "Sending newline (after passport)");
                    }
                }
                break;

            case 14: // Press RETURN to get started.
                traceInfo(worker, "Sending newline (Press RETURN)");
                session.print("\r\n");
                break;

            case 15: // Non-privileged mode prompt '>'
                enableMode = true;
                maxFails = 2;
                traceInfo(worker, "Sending enable");
                session.print("enable\n");
                session.expect("enable", worker);
                break;

            case 16: // Privileged mode prompt '#'
                return; // Success, logged in
            }
        }
    }

    private void sleep(NedWorker worker, long milliseconds, boolean log) {
        if (log)
            traceVerbose(worker, "Sleeping " + milliseconds + " milliseconds");
        try {
            Thread.sleep(milliseconds);
            if (log)
                traceVerbose(worker, "Woke up from sleep");
        } catch (InterruptedException e) {
            System.err.println("sleep interrupted");
        }
    }

    public void setupTelnet2(NedWorker worker) throws Exception {
        TelnetSession tsession;
        NedExpectResult res;

        traceInfo(worker, "TELNET connecting to host: "+ip.getHostAddress()+":"+port);

        if (trace)
            tsession = new TelnetSession(worker, ruser, readTimeout, worker, this);
        else
            tsession = new TelnetSession(worker, ruser, readTimeout, null, this);
        session = tsession;

        if (!remoteConnection.equals("none")) {
            traceInfo(worker, "Sending newline (initial)");
            session.print("\r\n");
        }

        // Login and enter enable mode
        loginDevice(worker, 0, ruser, pass, secpass);
    }

    //
    // interfaceGetLine
    //
    private int interfaceGetLine(String lines[], String line, int i) {
        for (int n = i; n < lines.length; n++) {
            if (isTopExit(lines[n]))
                break;
            if (lines[n].trim().matches(line))
                return n;
        }
        return -1;
    }

    //
    // applyConfig() - apply one line at a time
    //
    @Override
    public void applyConfig(NedWorker worker, int cmd, String data)
        throws NedException, IOException, SSHSessionException, ApplyException {
        String lines[];
        String nextline, line, meta = "";
        int i;
        boolean isAtTop=true;
        long time;
        long lastTime = System.currentTimeMillis();
        warningsBuf = "";

        // Clear cached config
        lastGetConfig = null;

        // Modify data and split into lines
        logInfo(worker, "APPLY-CONFIG TRANSFORMING");
        lines = modifyData(worker, data, "APPLY-CONFIG");

        // Enter config mode
        if (!enterConfig(worker, cmd)) {
            throw new NedException("applyConfig() :: Failed to enter config mode");
        }

        // Send all lines to the device, one at a time
        logInfo(worker, "APPLY-CONFIG SENDING");
        try {
            String toptag = "";
            for (i = 0 ; i < lines.length ; i++) {
                time = System.currentTimeMillis();
                if ((time - lastTime) > (0.8 * writeTimeout)) {
                    lastTime = time;
                    worker.setTimeout(writeTimeout);
                }
                waitForEcho = Echo.WAIT;

                if (isTopExit(lines[i])) {
                    toptag = "";
                } else if (!lines[i].trim().isEmpty()
                           && Character.isLetter(lines[i].charAt(0))) {
                    toptag = lines[i].trim();
                }

                lines[i] = lines[i].trim();
                if (lines[i].startsWith("! meta-data :: ")) {
                    meta = meta + lines[i] + "\n";
                    continue;
                }
                line = modifyLine(worker, lines[i], meta);
                if (line == null || line.isEmpty()) {
                    meta = "";
                    continue;
                }

                // DIRTY patch for interface / speed & duplex ordering
                if (toptag.startsWith("interface ")) {
                    if (line.startsWith("duplex ")) {
                        int speed = interfaceGetLine(lines, "speed\\s+\\S+", i+1);
                        if (speed > 0) {
                            try {
                                // Send duplex command
                                isAtTop = print_line_wait(worker, cmd, line, 0, meta);
                                meta = "";
                                continue;
                            } catch (ApplyException failed) {
                                // duplex command failed, inject the speed command and retry
                                traceInfo(worker, "injecting speed setting to solve speed/duplex ordering issue");
                                isAtTop = print_line_wait(worker, cmd, lines[speed], 0, meta);
                                lines[speed] = "";
                            }
                        }
                    }
                    else if (line.startsWith("speed ")) {
                        int duplex = interfaceGetLine(lines, "duplex\\s+\\S+", i+1);
                        if (duplex > 0) {
                            try {
                                // Send speed command
                                isAtTop = print_line_wait(worker, cmd, line, 0, meta);
                                meta = "";
                                continue;
                            } catch (ApplyException failed) {
                                // speed command failed, inject the duplex command and retry
                                traceInfo(worker, "injecting duplex setting to solve speed/duplex ordering issue");
                                isAtTop = print_line_wait(worker, cmd, lines[duplex], 0, meta);
                                lines[duplex] = "";
                            }
                        }
                    }
                }

                // Send line to device
                isAtTop = print_line_wait(worker, cmd, line, 0, meta);
                meta = "";
            } // for(;;)
        }
        catch (ApplyException e) {
            if (!e.isAtTop)
                moveToTopConfig(worker);
            if (e.inConfigMode)
                exitConfig(worker);
            throw e;
        }

        // Exit config mode
        if (!isAtTop)
            moveToTopConfig(worker);
        exitConfig(worker);

        // All commands accepted by device, prepare caching of secrets and defaults
        try {
            if (secrets.prepare(worker, lines)) {
                traceVerbose(worker, "SECRETS - new secrets, caching encrypted entries");
                lastGetConfig = getConfig(worker, false);
            }
        } catch (Exception e) {
            logError(worker, "secrets.prepare() ERROR", e);
            throw new NedException("applyConfig() :: internal error SECRETS post-processing");
        }
        try {
            defaults.cache(worker, lines);
        } catch (Exception e) {
            logError(worker, "defaults.cache() ERROR", e);
            throw new NedException("applyConfig() :: internal error DEFAULTS post-processing");
        }

        logInfo(worker, "DONE APPLY-CONFIG");
    }

    private class ExtendedApplyException extends ApplyException {
        public ExtendedApplyException(String line, String msg,
                                      boolean isAtTop,
                                      boolean inConfigMode) {
            super(line+": "+msg, isAtTop, inConfigMode);
         }
    }

    @Override
    public void revert(NedWorker worker, String data)
        throws Exception {
        if (trace)
            session.setTracer(worker);

        this.applyConfig(worker, NedCmd.REVERT_CLI, data);

        if (this.writeMemoryMode.equals("on-commit")) {
            print_line_wait_oper(worker, NedCmd.REVERT_CLI, writeMemory, 0);
        }

        worker.revertResponse();
    }

    @Override
    public void commit(NedWorker worker, int timeout)
        throws Exception {
        if (trace)
            session.setTracer(worker);

        if (this.writeMemoryMode.equals("on-commit")) {
            print_line_wait_oper(worker, NedCmd.COMMIT, writeMemory, 0);
        }

        worker.commitResponse();
    }

    @Override
    public void prepareDry(NedWorker worker, String data)
        throws Exception {
        String lines[];
        String line, meta = "";
        StringBuilder newdata = new StringBuilder();
        int i;

        logInfo(worker, "PREPARE-DRY");

        // ShowRaw used in debugging, to see cli commands before modification
        if (showRaw) {
            logInfo(worker, "prepareDry() : showing raw (unmodified) outformat");
            worker.prepareDryResponse(data);
            showRaw = false;
            logInfo(worker, "DONE PREPARE-DRY");
            return;
        }

        // Modify data buffer
        lines = modifyData(worker, data, "PREPARE-DRY");

        // Modify line per line
        for (i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith("! meta-data :: ")) {
                meta = meta + lines[i].trim() + "\n";
                if (showVerbose)
                    newdata.append(lines[i]+"\n");
                continue;
            }
            line = modifyLine(worker, lines[i], meta);
            meta = "";
            if (line == null)
                continue;
            newdata.append(line+"\n");
        }

        logInfo(worker, "DONE PREPARE-DRY");
        worker.prepareDryResponse(newdata.toString());
    }

    @Override
    public void
    persist(NedWorker worker) throws Exception {
        if (trace)
            session.setTracer(worker);

        if (this.writeMemoryMode.equals("on-persist")) {
            print_line_wait_oper(worker, NedCmd.PERSIST, writeMemory, 0);
        }

        worker.persistResponse();
    }

    private void cleanup()
        throws NedException, IOException {

        // Finish read transaction
        if (thr != -1) {
            try {
                mm.finishTrans(thr);
                thr = -1;
            } catch (Exception ignore) {
                logError(null, "cleanup() - finishTrans() :: ", ignore);
            }
        }

        // Unregister resources
        try {
            cdbOper.endSession();
            ResourceManager.unregisterResources(this);
        } catch (Exception ignore) {
            logError(null, "cleanup() - unRegistering Resources :: ", ignore);
        }
    }

    @Override
    public void close(NedWorker worker)
        throws NedException, IOException {
        close();
    }

    @Override
    public void close() {
        logDebug(null, "close");
        try {
            cleanup();
        } catch (Exception ignore) {
            logError(null, "cleanup() - ERROR :: ", ignore);
        }

        super.close();
    }

    private String sortConfig2(NedWorker worker, String res, String line) {

        // Find first line
        int first = res.indexOf("\n" + line);
        if (first < 0)
            return res; // no need to sort

        // Find last line
        int last = res.lastIndexOf("\n" + line);
        int nl = res.indexOf("\n", last + 1);
        if (nl < 0) {
            traceInfo(worker, "sortConfig2(): ERROR: missing new line for: "+line);
            return res;
        }

        // First = last, e.g. only one line -> nothing to sort
        if (first == last)
            return res;

        // Sort lines using String array, Arrays and StringBuilder
        String sort = res.substring(first + 1, nl);
        String lines[] = sort.split("\n");
        Arrays.sort(lines);
        StringBuilder sortbuf = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isEmpty()) {
                sortbuf.append(lines[i]+"\n");
            }
        }
        sort = sortbuf.toString();

        return res.substring(0, first + 1) + sort + res.substring(nl + 1);
    }

    private String sortConfig(NedWorker worker, String res) {
        // Sort some entries since csr1000v reorders them after reload
        res = sortConfig2(worker, res, "ip route vrf ");
        res = sortConfig2(worker, res, "ip nat translation max-entries vrf ");
        return res;
    }

    @Override
    public void getTransId(NedWorker worker)
        throws Exception {
        String res = null;
        int i;

        if (trace)
            session.setTracer(worker);

        logInfo(worker, "GET-TRANS-ID");

        // If new cleartext secret set, scan running-config and cache encrypted secret(s)
        if (lastGetConfig != null) {
            logInfo(worker, "Using last config from SECRETS for checksum calculation");
            res = lastGetConfig;
            lastGetConfig = null;
        }

        // NETSIM, optionally use confd-state transaction id
        if (transIdMethod.equals("confd-state-trans-id") && isNetsim()) {
            session.println("show confd-state internal cdb datastore running transaction-id");
            session.expect("show confd-state internal cdb datastore running transaction-id");
            res = session.expect(privexec_prompt, worker);
            if (res.indexOf("error") >= 0)
                throw new NedException("Failed to run get confd running transaction-id");
            res = res.substring(res.indexOf(" ")+1).trim();
            logInfo(worker, "DONE GET-TRANS-ID ("+res+")");
            worker.getTransIdResponse(res);
            return;
        }

        // Use 'Last configuration change' string from running-config
        else if (transIdMethod.equals("last-config-change") && !isNetsim()) {
            String line = "show running-config | include Last configuration change";
            session.print(line+"\n");
            session.expect(new String[] { Pattern.quote(line) }, worker);
            res = session.expect(privexec_prompt, worker);
            if (res.indexOf("Last configuration change") < 0)
                throw new NedException("Failed to get running-config 'Last configuration change' string");
            res = res + res + res + res;
        }

        // Use 'show configuration id' command
        else if (transIdMethod.equals("config-id") && !isNetsim()) {
            session.println("show configuration id");
            session.expect("show configuration id");
            res = session.expect(privexec_prompt, worker);
            if (res.indexOf("Invalid input") >= 0)
                throw new NedException("Failed to use 'show configuration id' for transaction id");
            res = res + res + res + res;
        }

        // Use 'show configuration history' command
        else if (transIdMethod.equals("config-history") && !isNetsim()) {
            session.println("show configuration history");
            session.expect("show configuration history");
            res = session.expect(privexec_prompt, worker);
            if (res.indexOf("Invalid input") >= 0)
                throw new NedException("Failed to use 'show configuration history' for transaction id");
            res = res + res;
        }

        // Use running-config for string data
        else if (res == null) {
            res = getConfig(worker, false);
        }

        // Sort config since some IOS revices reorder entries after reboot
        res = res.trim();
        res = sortConfig(worker, res);

        //traceVerbose(worker, "TRANS_AFTER=\n'"+res+"'\n");

        // Calculate checksum of running-config
        byte[] bytes         = res.getBytes("UTF-8");
        MessageDigest md     = MessageDigest.getInstance("MD5");
        byte[] thedigest     = md.digest(bytes);
        BigInteger md5Number = new BigInteger(1, thedigest);
        String md5String     = md5Number.toString(16);

        logInfo(worker, "DONE GET-TRANS-ID ("+md5String+")");

        worker.getTransIdResponse(md5String);
    }

    private String stringQuote(String aText) {
        StringBuilder result = new StringBuilder();
        StringCharacterIterator iterator =
            new StringCharacterIterator(aText);
        char character =  iterator.current();
        result.append("\"");
        while (character != CharacterIterator.DONE ){
            if (character == '"')
                result.append("\\\"");
            else if (character == '\\')
                result.append("\\\\");
            else if (character == '\b')
                result.append("\\b");
            else if (character == '\n')
                result.append("\\n");
            else if (character == '\r')
                result.append("\\r");
            else if (character == (char) 11) // \v
                result.append("\\v");
            else if (character == '\f')
                result.append("'\f");
            else if (character == '\t')
                result.append("\\t");
            else if (character == (char) 27) // \e
                result.append("\\e");
            else
                // The char is not a special one, add it to the result as is
                result.append(character);
            character = iterator.next();
        }
        result.append("\"");
        return result.toString();
    }

    private String stringDequote(NedWorker worker, String aText) {
        if (aText.indexOf("\"") != 0) {
            traceVerbose(worker, "stringDequote(ignored) : " + aText);
            return aText;
        }

        //traceVerbose(worker, "stringDequote(parse) : " + aText);

        aText = aText.substring(1,aText.length()-1);

        StringBuilder result = new StringBuilder();
        StringCharacterIterator iterator =
            new StringCharacterIterator(aText);
        char c1 = iterator.current();

        while (c1 != CharacterIterator.DONE) {
            if (c1 == '\\') {
                char c2 = iterator.next();
                if (c2 == CharacterIterator.DONE )
                    result.append(c1);
                else if (c2 == 'b')
                    result.append('\b');
                else if (c2 == 'n')
                    result.append('\n');
                else if (c2 == 'r')
                    result.append('\r');
                else if (c2 == 'v')
                    result.append((char) 11); // \v
                else if (c2 == 'f')
                    result.append('\f');
                else if (c2 == 't')
                    result.append('\t');
                else if (c2 == 'e')
                    result.append((char) 27); // \e
                else {
                    result.append(c2);
                }
            }
            else {
                // The char is not a special one, add it to the result as is
                result.append(c1);
            }
            c1 = iterator.next();
        }
        //traceVerbose(worker, "stringDequote(parsed) : " + result.toString());
        return result.toString();
    }

    private String stringInsertCtrlV(String line) {
        if (line.indexOf("?") < 0)
            return line;
        return line.replace("?", (char)(0x16)+"?");
    }

    private String passwordQuote(String aText) {
        StringBuilder result = new StringBuilder();
        StringCharacterIterator iterator =
            new StringCharacterIterator(aText);
        char character =  iterator.current();
        while (character != CharacterIterator.DONE ){
            if (character == '"')
                result.append("\\\"");
            else if (character == '\\')
                result.append("\\\\");
            else {
                // The char is not a special one, add it to the result as is
                result.append(character);
            }
            character = iterator.next();
        }
        return result.toString();
    }

    private String passwordDequote(NedWorker worker, String aText) {
        if (aText.indexOf("\"") != 0) {
            traceVerbose(worker, "passwordDequote(ignored) : " + aText);
            return aText;
        }

        traceVerbose(worker, "passwordDequote(parse) : " + aText);

        aText = aText.substring(1,aText.length()-1); // strip ""

        StringBuilder result = new StringBuilder();
        StringCharacterIterator iterator =
            new StringCharacterIterator(aText);
        char c1 = iterator.current();

        while (c1 != CharacterIterator.DONE) {
            if (c1 == '\\') {
                char c2 = iterator.next();
                if (c2 == CharacterIterator.DONE )
                    result.append(c1);
                else if (c2 == '\\')
                    result.append('\\');
                else if (c2 == '\"')
                    result.append('\"');
                else {
                    result.append(c1);
                    result.append(c2);
                }
            }
            else {
                result.append(c1);
            }
            c1 = iterator.next();
        }
        traceVerbose(worker, "passwordDequote(parsed) : " + result.toString());
        //String data = result.toString();
        //for (int i = 0; i < data.length(); i++)
        //traceVerbose(worker, "PD-"+i+"= "+ data.charAt(i));

        return result.toString();
    }

    private static int indexOf(Pattern pattern, String s, int start) {
        Matcher matcher = pattern.matcher(s);
        return matcher.find(start) ? matcher.start() : -1;
    }

    private static int findString(String search, String text) {
        return indexOf(Pattern.compile(search), text, 0);
    }

    private static boolean hasString(String search, String text) {
        if (indexOf(Pattern.compile(search), text, 0) >= 0)
            return true;
        return false;
    }

    private static String findLine(String buf, String search) {
        int i = buf.indexOf(search);
        if (i >= 0) {
            int nl = buf.indexOf("\n", i+1);
            if (nl >= 0)
                return buf.substring(i,nl);
            else
                return buf.substring(i);
        }
        return null;
    }

    private String getMatch(String text, String regexp) {
        Pattern pattern = Pattern.compile(regexp);
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find())
            return null;
        return matcher.group(1);
    }

    private String[] getMatches(NedWorker worker, String text, String regexp) {
        String[] matches;
        Pattern pattern = Pattern.compile(regexp);
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find())
            return null;
        matches = new String[matcher.groupCount()];
        for (int i = 0; i < matcher.groupCount(); i++) {
            matches[i] = matcher.group(i+1);
            traceVerbose(worker, "MATCH-"+i+"="+matches[i]);
        }
        return matches;
    }

    private static String getString(String buf, int offset) {
        int nl;
        nl = buf.indexOf("\n", offset);
        if (nl < 0)
            return buf;
        return buf.substring(offset, nl).trim();
    }

    private String stripLineAll(NedWorker worker, String res, String search) {
        StringBuilder buffer = new StringBuilder();
        String lines[] = res.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith(search)) {
                traceVerbose(worker, "transformed: stripped '"+lines[i]+"'");
                continue;
            }
            buffer.append(lines[i]+"\n");
        }
        return buffer.toString();
    }

    private String getConfigLoggingType(NedWorker worker, String showbuf, String type)
        throws Exception {
        String name = type + "-logging";
        String line = findLine(showbuf, "<"+name);
        if (line == null)
            return "";
        if (line.trim().startsWith("<"+name+">disabled<"))
            return "";
        Pattern pattern = Pattern.compile("<"+name+" level=\"(\\S+?)\" ");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            if (type.equals("buffer"))
                type = "buffered";
            String logging = "logging " + type + " " + matcher.group(1);
            traceVerbose(worker, "inserting '"+logging+"' from show logging xml");
            return logging + "\n";
        }
        return "";
    }

    private String getConfigLogging(NedWorker worker)
        throws Exception {

        String cmd = "show logging xml"; // NOTE: Not supported on older IOS versions [12.2(33)]
        session.print(cmd+"\n");
        session.expect(new String[] { Pattern.quote(cmd) }, worker);
        String showbuf = session.expect(privexec_prompt, worker);
        if (showbuf.indexOf("Invalid input") >= 0) {
            traceInfo(worker, "WARNING: unable to determine logging status due to too old IOS");
            return "";
        }

        String res = "";
        res += getConfigLoggingType(worker, showbuf, "console");
        res += getConfigLoggingType(worker, showbuf, "monitor");
        res += getConfigLoggingType(worker, showbuf, "buffer");
        return "\n" + res;
    }


    private String getConfigVersion(NedWorker worker)
        throws Exception {
        String cmd = "show version | include password-recovery";
        session.print(cmd + "\n");
        session.expect(new String[] { Pattern.quote(cmd) }, worker);
        String line = session.expect(privexec_prompt, worker);
        if (line.indexOf("password-recovery") < 0)
            return "";
        if (line.indexOf("enabled") > 0)
            return "\nservice password-recovery\n";
        if (line.indexOf("disabled") > 0)
            return "\nno service password-recovery\n";
        return "";
    }

    // note: dirty fix to show vlan's in config for devices which do not
    //       do that for you, e.g. c2900.
    private String getConfigVlan(NedWorker worker)
        throws Exception {
        String res;
        String vtpStatus;
        String result = "\n";
        int i;
        boolean VTPClient = false;

        if (haveShowVtpStatus == false)
            return "";

        session.print("show vtp status\n");
        session.expect("show vtp status", worker);
        vtpStatus = session.expect(privexec_prompt, worker);
        if (vtpStatus.indexOf("Invalid input") >= 0) {
            traceInfo(worker, "Disabling 'show vtp status' check");
            haveShowVtpStatus = false;
            return "";
        }

        //
        // Extract VTP "config" from 'show vtp status'
        //

        // vtp mode
        if ((res = findLine(vtpStatus, "VTP Operating Mode")) != null) {
            String mode =  res.replaceAll("VTP Operating Mode\\s+:\\s+(\\S+)", "$1").trim().toLowerCase();
            result += "vtp mode " + mode + "\n";
            if (mode.equals("client"))
                VTPClient = true;
        }

        // vtp domain
        if ((res = findLine(vtpStatus, "VTP Domain Name")) != null) {
            if ((i = res.indexOf(":")) > 0) {
                String value = res.substring(i+1).trim();
                if (!value.isEmpty())
                    result += "vtp domain " + value + "\n";
            }
        }

        // vtp version
        if ((res = findLine(vtpStatus, "VTP version running")) != null) { // 2960 & 7600
            if ((i = res.indexOf(":")) > 0) {
                String value = res.substring(i+1).trim();
                if (!value.isEmpty())
                    result += "vtp version " + value + "\n";
            }
        } else if ((res = findLine(vtpStatus, "VTP Version")) != null) { // 4500
            if ((i = res.indexOf(":")) > 0) {
                String value = res.substring(i+1).trim();
                if (!value.isEmpty())
                    result += "vtp version " + value + "\n";
            }
        }

        // vtp pruning
        if ((res = findLine(vtpStatus, "VTP Pruning Mode")) != null) {
            String value = res.replaceAll("VTP Pruning Mode\\s+:\\s+(\\S+)", "$1").trim();
            if (value.equals("Enabled"))
                result += "vtp pruning\n";
        }

        //
        // Add vlan entries:
        //

        // If VTP Client, do not add vlan's to config.
        if (VTPClient) {
            traceInfo(worker, "Found VTP Client, do not list vlan(s) using show vlan");
            return result;
        }

        // First try 'show vlan'
        if (haveShowVlan) {
            session.print("show vlan\n");
            session.expect("show vlan", worker);
            res = session.expect(privexec_prompt, worker);
            if (res.indexOf("\n----") < 0) {
                traceInfo(worker, "Disabling 'show vlan' check");
                haveShowVlan = false;
            }
        }

        // Then try 'show vlan-switch'
        if (haveShowVlanSwitch && !haveShowVlan) {
            session.print("show vlan-switch\n");
            session.expect("show vlan-switch", worker);
            res = session.expect(privexec_prompt, worker);
            if (res.indexOf("Invalid input") >= 0) {
                traceInfo(worker, "Disabling 'show vlan-switch' check");
                haveShowVlanSwitch = false;
                return "";
            }
        }

        // Strip all text before first entry
        if ((i = res.indexOf("\n----")) < 0)
            return "";
        if ((i = res.indexOf("\n", i+1)) < 0)
            return "";
        res = res.substring(i+1);

        // Parse lines, create:
        // vlan #
        //  name <name>
        //todo: mtu <mtu>
        String vlans[] = res.split("\r\n");
        for (i = 0; i < vlans.length; i++) {
            if (vlans[i] == null || vlans[i].equals(""))
                break;
            // Skip multi line entries. Each new starts with a digit
            if (!Character.isDigit(vlans[i].trim().charAt(0))) {
                continue;
            }
            String tokens[] = vlans[i].split(" +");
            if (tokens.length < 3)
                break;
            if (tokens[2].indexOf("act/unsup") == 0)
                continue;
            result += "vlan " + tokens[0] + "\n";
            if (!(tokens[1].indexOf("VLAN") == 0
                  && tokens[1].indexOf(tokens[0]) > 0))
                result += " name "+tokens[1]+"\n";
            result += "!\n";
        }

        return result;
    }

    private String getConfigSnmpUser(NedWorker worker)
        throws Exception {
        String res;
        String result = "\n";
        int b, e;
        ConfValue val;
        ConfPath path;

        if (!haveShowSnmpUser)
            return "";

        try {
            session.print("show snmp user\n");
            session.expect("show snmp user", worker);
            res = session.expect(privexec_prompt, worker);
            if (res.indexOf("Invalid input") >= 0) {
                traceInfo(worker, "Disabling 'show snmp user' check");
                haveShowSnmpUser = false;
                return "";
            }
            if (res.indexOf("SNMP agent not enabled") >= 0)
                return "";
            b = res.indexOf("\nUser name: ");
            if (b < 0)
                return "";

            try {
                mm.getMyUserSession();
            } catch (Exception ex) {
                mm.setUserSession(1);
            }
            int th = mm.startTrans(Conf.DB_RUNNING, Conf.MODE_READ);

            while (b >= 0) {
                String name = getString(res, b+12);

                e = res.indexOf("\nAuthentication Protocol: ", b);
                if (e < 0)
                    break;
                String auth = getString(res, e+26).toLowerCase();

                e = res.indexOf("\nPrivacy Protocol: ", b);
                if (e < 0)
                    break;
                String priv = getString(res, e+19).toLowerCase().trim();
                if (priv.indexOf("aes") == 0)
                    priv = "aes " + priv.substring(3);

                int end = res.indexOf("\nGroup-name: ", b);
                if (end < 0)
                    break;
                String group = getString(res, end+13);

                // Get access list info
                String acl = "";
                e = res.indexOf("IPv6 access-list: ", b);
                if (e > 0 && e < end) {
                    acl = "ipv6 " + getString(res, e+18);
                } else {
                    e = res.indexOf("access-list: ", b);
                    if (e > 0 && e < end) {
                        acl = getString(res, e+13);
                    }
                }

                // Begin making entry
                result = result + "\nsnmp-server user "+name+" "+group+" v3";

                // Add optional 'auth' params
                if (!auth.equals("none")) {
                    String authPw = "NOT-SET-IN-NCS";
                    path = new ConfPath(
                     "/ncs:devices/device{%s}/config/ios:snmp-server/" +
                     "user{%s}/auth-password",
                     device_id, name);
                    if ((val = mm.safeGetElem(th, path)) != null) {
                        authPw = val.toString();
                    }
                    result = result + " auth " + auth + " " +authPw;
                }

                // Add optional 'priv' params
                if (!priv.equals("none")) {
                    String privPw = "NOT-SET-IN-NCS";
                    path = new ConfPath(
                       "/ncs:devices/device{%s}/config/ios:snmp-server/" +
                       "user{%s}/priv-password",
                       device_id, name);
                    if ((val = mm.safeGetElem(th, path)) != null) {
                        privPw = val.toString();
                    }
                    result = result + " priv " + priv + " " + privPw;
                }

                // Add optional 'access' params
                if (!acl.isEmpty())
                    result = result + " access " + acl;

                // Get next entry
                b = res.indexOf("\nUser name: ", b+12);
            }

            mm.finishTrans(th);
        } catch (Exception ex) {
            throw new NedException("", ex);
        }

        return result;
    }

    private boolean isTopExit(String line) {
        if (line.startsWith("exit"))
            return true;
        if (line.startsWith("!") && line.trim().equals("!"))
            return true;
        return false;
    }

    private String linesToString(String lines[]) {
        StringBuilder string = new StringBuilder();
        for (int n = 0; n < lines.length; n++) {
            if (lines[n].isEmpty())
                continue;
            string.append(lines[n]+"\n");
        }
        return "\n" + string.toString() + "\n";
    }

    private String[] getTransformDescriptions(NedWorker worker, String lines[]) {
        int i, n;
        String toptag = "";
        for (n = 0; n < lines.length; n++) {
            if (lines[n].isEmpty())
                continue;
            if (isTopExit(lines[n])) {
                toptag = "";
            } else if (Character.isLetter(lines[n].charAt(0))) {
                toptag = lines[n].trim();
            }
            i = lines[n].indexOf(" description ");
            if (i < 0)
                continue;
            // special case for: ip msdp description <hostname> <description>
            int offset = 13;
            if (toptag.startsWith("ip msdp description ")) {
                int space = lines[n].indexOf(" ", i + offset);
                if (space > 0) {
                    offset = space - i + 1;
                }
            }
            // Quote description string
            String desc = stringQuote(lines[n].substring(i+offset).trim());
            // Ignore quoting service-insertion descriptions
            if (toptag.startsWith("service-insertion ")) {
                traceVerbose(worker, "IGNORED QUOTED DESC="+desc);
                continue;
            }
            //traceVerbose(worker, "QUOTED DESC="+desc);
            lines[n] = lines[n].substring(0,i+offset) + desc;
        }
        return lines;
    }

    private boolean getAppendLines(StringBuilder buffer, String tokens[], int start, int end, int x) {
        if (tokens.length - start <= x) {
            return false;
        }
        int n;
        int length = tokens.length - end;

        String prefix = tokens[0];
        for (n = 1; n < start; n++)
            prefix += " " + tokens[n];

        String postfix = "";
        for (n = length; n < tokens.length; n++)
            postfix += " " + tokens[n];

        for (n = start; n < length; n = n + x) {
            String values = "";
            for (int j = n; (j < n + x) && (j < length); j++)
                values += " " + tokens[j];
            buffer.append(prefix + values + postfix + "\n");
        }
        return true;
    }

    private String insertKeyConfigKeyPassword(NedWorker worker, String res)
        throws Exception {

        try {
            mm.getMyUserSession();
        } catch (Exception ex) {
            mm.setUserSession(1);
        }
        int th = mm.startTrans(Conf.DB_RUNNING, Conf.MODE_READ);

        try {
            ConfPath path = new ConfPath("/ncs:devices/device{%s}/config/ios:key/config-key/password-encrypt", device_id);
            ConfValue val = mm.safeGetElem(th, path);
            if (val != null) {
                String password = val.toString();
                res = "key config-key password-encrypt " + password + "\n" + res;
            }
        } catch (Exception ignore) {
            // ignore not found
        }

        mm.finishTrans(th);

        return res;
    }

    private String getConfig(NedWorker worker, boolean convert)
        throws Exception {
        int i, n, d, nl = -1;
        String res, match;

        if (convert && syncFile != null) {
            logVerbose(worker, "SHOW READING - show running from file " + syncFile);
            res = print_line_exec(worker, "file show " + syncFile);
            if (res.indexOf("Error: failed to open file") >= 0)
                throw new NedException("failed to sync from file " + syncFile);
        } else {
            logVerbose(worker, "SHOW READING - " + showRunningConfig);
            res = print_line_exec(worker, showRunningConfig);
            if (res.indexOf("Invalid input detected") >= 0)
                throw new NedException("failed to show config using '"+showRunningConfig+"'");
        }
        worker.setTimeout(readTimeout);

        // Strip everything before and including the following comments:
        i = res.indexOf("Current configuration");
        if (i >= 0 && (d = res.indexOf("\n", i)) > 0)
            res = res.substring(d+1);

        i = res.indexOf("Last configuration change");
        if (i >= 0 && (d = res.indexOf("\n", i)) > 0)
            res = res.substring(d+1);

        i = res.indexOf("No configuration change since last restart");
        if (i >= 0 && (d = res.indexOf("\n", i)) > 0)
            res = res.substring(d+1);

        i = res.indexOf("No entries found.");
        if (i >= 0 && (d = res.indexOf("\n", i)) > 0)
            res = res.substring(d+1);

        i = res.lastIndexOf("NVRAM config last updated"); // multiple entries
        if (i >= 0 && (d = res.indexOf("\n", i)) > 0)
            res = res.substring(d+1);

        // Strip all text after and including the last 'end'
        i = res.lastIndexOf("\nend");
        if (i >= 0)
            res = res.substring(0,i);

        //
        // REAL DEVICE ONLY
        //
        if (!isNetsim()) {

            // Insert config shown in "show version"
            logVerbose(worker, "SHOW READING - show version");
            res = res + getConfigVersion(worker);

            // Insert 'logging console X' shown in "show logging"
            logVerbose(worker, "SHOW READING - show logging");
            res = getConfigLogging(worker) + res;

            // Insert missing VLAN config from show vlan into running-config
            logVerbose(worker, "SHOW READING - show vlan");
            res = res + getConfigVlan(worker);

            // Insert missing 'snmp-server ... v3 ...' config from show snmp user
            logVerbose(worker, "SHOW READING - show snmp user");
            res = res + getConfigSnmpUser(worker);

            // Insert missing 'show boot' config from show boot
            if (haveShowBoot) {
                logVerbose(worker, "SHOW READING - show boot");
                session.print("show boot\n");
                session.expect("show boot", worker);
                String boot = session.expect(privexec_prompt, worker);
                if ((boot = findLine(boot, "BOOT path-list:")) != null) {
                    boot = boot.substring(15).trim();
                    if (!boot.isEmpty())
                        res = res + "boot system " + boot;
                } else {
                    traceInfo(worker, "Disabling 'show boot' check");
                    haveShowBoot = false;
                }
            }
        }

        // After reading all device config, trim to avoid checksum diff
        res = res.trim();


        //
        // TRANSFORMING CONFIG
        //
        logInfo(worker, "SHOW TRANSFORMING CONFIG");

        // Inject global config at top of running-config
        if (convert) {
            logVerbose(worker, "SHOW TRANSFORMING - injecting global config");
            for (n = globalConfig.size()-1; n >= 0; n--) {
                String entry[] = globalConfig.get(n);
                if (entry[2] == null) {
                    traceVerbose(worker, "INJECT: inserting["+entry[1]+"] '"
                                 +entry[0] + "' first in running-config ");
                    res = entry[0] + "\n" + res;
                } else {
                    // regexp -> insert after-each match in global config
                    Pattern pattern = Pattern.compile(entry[2], Pattern.DOTALL);
                    Matcher matcher = pattern.matcher(res);
                    int offset = 0;
                    while (matcher.find()) {
                        String insert = modifyInsertCommand(worker, "\n" + entry[0], "after-each", fillGroups(matcher));
                        res = res.substring(0, matcher.end(0) + offset)
                            + insert
                            + res.substring(matcher.end(0) + offset);
                        offset = offset + insert.length();
                    }
                }
            }
        }

        // Top-trick xxyyzztop must always be set to avoid sync diff
        res = "xxyyzztop 0\n" + res;


        //
        // NETSIM - leave early
        //
        if (isNetsim() && syncFile == null) {
            String lines[] = res.split("\n");
            lines = getTransformDescriptions(worker, lines);
            return linesToString(lines);
        }


        //
        // REAL DEVICES BELOW
        //

        // Add tailfned "config" if missing
        if (newIpACL && (i = res.indexOf("tailfned api new-ip-access-list")) < 0) {
            res = "\ntailfned api new-ip-access-list\n" + res;
        }
        if ((i = res.indexOf("tailfned police ")) < 0) {
            res = "\ntailfned police "+iospolice+"\n" + res;
        }

        // Add info to cached-show
        res = res + "\n";
        if (includeCachedShowVersion) {
            res = res + "cached-show version version " + iosversion + "\n";
            res = res + "cached-show version model " + iosmodel + "\n";
            if (licenseLevel != null)
                res = res + "cached-show version license level " + licenseLevel + "\n";
            if (licenseType != null)
                res = res + "cached-show version license type " + licenseType + "\n";
            for (i = 0; i < cachedShowInventory.size(); i++) {
                String entry[] = cachedShowInventory.get(i);
                res += "cached-show inventory name " + entry[0];
                if (!entry[1].trim().isEmpty())
                    res += " sn " + entry[1];
                res += "\n";
            }
        }

        // Strip clock-period, device may change it, i.e. not config
        res = stripLineAll(worker, res, "ntp clock-period");
        // Strip console log messages
        res = stripLineAll(worker, res, "%");

        // Update secrets - replace encrypted secrets with cleartext if not changed
        logVerbose(worker, "SHOW TRANSFORMING - updating secrets");
        res = secrets.update(worker, res, convert);

        //
        // getTransId does not need to convert, it is just a checksum
        //
        if (convert == false) {
            traceVerbose(worker, "skipping further conversion of show-running-config, checksum only");
            return res;
        }

        //
        // LINE BY LINE TRANSFORMATIONS
        //
        String lines[] = res.split("\n");
        res = null; // to provoke crash if used below

        // Quote ' description ' strings (required for both hw & netsim)
        logVerbose(worker, "SHOW TRANSFORMING - quoting descriptions");
        lines = getTransformDescriptions(worker, lines);

        // MAIN LINE-BY-LINE LOOP
        logVerbose(worker, "SHOW TRANSFORMING - line-by-line patches");
        String toptag = "";
        for (n = 0; n < lines.length; n++) {
            String transformed = null;
            String trimmed = lines[n].trim();
            if (trimmed.isEmpty())
                continue;

            // Update toptag
            if (isTopExit(lines[n])) {
                toptag = "";
            } else if (Character.isLetter(lines[n].charAt(0))) {
                toptag = trimmed;
            }

            //
            /// errdisable
            //
            if (toptag.startsWith("errdisable")) {
                transformed = lines[n].replace("channel-misconfig \\(STP\\)", "channel-misconfig");
            }

            //
            /// interface
            //
            else if (toptag.startsWith("interface ")) {

                // interface * / ntp broadcast key <key> destination <address>
                // Move destination address (list key) first.
                if (lines[n].indexOf(" ntp broadcast ") >= 0 && lines[n].indexOf(" destination ") > 0) {
                    transformed = lines[n].replaceFirst("ntp broadcast (.*?) (destination \\S+)",
                                                        "ntp broadcast $2 $1");
                }
            }

            //
            /// controller SONET *
            //
            else if (toptag.startsWith("controller SONET ")) {
                // controller SONET * / sts-1 x - y mode sts-3c
                transformed = lines[n].replaceFirst("sts-1 (.*?) mode ", "sts-1 \"$1\" mode ");
            }

            //
            /// ip explicit-path
            //
            if (toptag.startsWith("ip explicit-path")) {
                // insert missing 'index <value>' (not shown in running-config)
                int index = 1;
                for (i = n + 1; i < lines.length; i++, index++) {
                    if (lines[i].startsWith(" index ")) {
                        String indexbuf = getMatch(lines[i], " index (\\d+) ");
                        if (indexbuf != null)
                            index = Integer.parseInt(indexbuf);
                    }
                    else if (lines[i].startsWith(" next-address ")
                             || lines[i].startsWith(" exclude-address ")) {
                        String entry = " index " + index + lines[i];
                        traceVerbose(worker, "transformed: '"+lines[i].trim()+"' -> '"+entry.trim());
                        lines[i] = entry;
                    }
                    else {
                        break;
                    }
                }
            }

            //
            /// logging discriminator
            //
            else if (toptag.startsWith("logging discriminator")) {
                // logging discriminator * [mnemonics drops|includes *] msg-body drops|includes *
                if (trimmed.indexOf("mnemonics") >= 0 && trimmed.indexOf("msg-body") >= 0) {
                    transformed = lines[n].replaceFirst("mnemonics (\\S+) (.*) msg-body (\\S+) (.*)",
                                                        "mnemonics $1 \"$2\" msg-body $3 \"$4\"");
                } else if (trimmed.indexOf("mnemonics") >= 0) {
                    transformed = lines[n].replaceFirst("mnemonics (\\S+) (.*)", "mnemonics $1 \"$2\"");
                } else {
                    transformed = lines[n].replaceFirst("msg-body (\\S+) (.*)", "msg-body $1 \"$2\"");
                }
            }

            //
            /// policy-map
            //
            else if (toptag.startsWith("policy-map ")) {

                // policy-map * / class * / random-detect drops 'precedence-based' name
                if (trimmed.startsWith("random-detect aggregate")) {
                    transformed = lines[n].replace("random-detect aggregate",
                                                   "random-detect precedence-based aggregate");
                }
                else if (trimmed.equals("random-detect")) {
                    transformed = lines[n].replace("random-detect",
                                                   "random-detect precedence-based");
                }

                // 'policy-map * / class * / police' string replacement
                else if (trimmed.startsWith("police ")) {
                    if (trimmed.startsWith("police cir ")
                        || trimmed.startsWith("police rate ")
                        || trimmed.startsWith("police aggregate ")) {
                        // Ignore these police lines, no transform needed
                        continue;
                    }
                    else if (trimmed.matches("police (\\d+) bps (\\d+) byte.*")) {
                        // Ignore "bpsflat " bps&byte (Catalyst) entries
                        continue;
                    }
                    if (hasPolice("cirmode") || hasPolice("cirflat")) {
                        // Insert missing [cir|bc|be]
                        transformed = lines[n].replaceAll("police (\\d+) (\\d+) (\\d+)",
                                                          "police cir $1 bc $2 be $3");
                        transformed = transformed.replaceAll("police (\\d+) (\\d+)",
                                                             "police cir $1 bc $2");
                        transformed = transformed.replaceAll("police (\\d+)",
                                                             "police cir $1");
                    }
                }
            }

            //
            /// spanning-tree mst configuration
            //
            else if (toptag.startsWith("spanning-tree mst configuration")) {

                // Fix spanning-tree mst configuration / instance * vlan <val>, <val2>
                if (findString(" instance [0-9]+ vlan ", lines[n]) >= 0) {
                    transformed = lines[n].replace(", ", ",");
                }
            }


            //
            /// monitor session
            //
            else if (toptag.startsWith("monitor session ")) {

                // leaf-list fix for 'monitor session * filter vlan *'
                if (trimmed.indexOf(" filter vlan ") > 0) {
                    transformed = lines[n].replace(" , ",",").replace(" - ","-");
                }
            }


            //
            // l2tp-class
            //
            else if (toptag.startsWith("l2tp-class ")) {
                if (trimmed.equals("password encryption aes"))
                    transformed = "";
            }


            //
            // crypto keyring
            //
            else if (toptag.startsWith("crypto keyring ")) {
                if (trimmed.indexOf("! Keyring unusable for nonexistent vrf") > 0)
                    transformed = lines[n].replace("! Keyring unusable for nonexistent vrf", "");
            }

            //
            // router bgp * / neighbor * password *
            //
            else if (toptag.startsWith("router bgp ")
                     && (match = getMatch(trimmed, "neighbor \\S+ password(?: [0-7])? (.*)")) != null) {
                transformed = lines[n].replace(match, passwordQuote(match));
            }

            //
            // track * ipv6 route
            //
            else if (toptag.startsWith("track ") && trimmed.contains(" ipv6 route :: ")) {
                transformed = lines[n].replace(" :: ", " ::/0 ");
            }

            //
            // transform single lines
            //
            else if (trimmed.startsWith("ip domain-name")) {
                transformed = lines[n].replace("ip domain-name", "ip domain name");
            } else if (trimmed.startsWith("ip domain-list")) {
                transformed = lines[n].replace("ip domain-list", "ip domain list");
            } else if (trimmed.startsWith("no ip domain-lookup")) {
                transformed = lines[n].replace("no ip domain-lookup", "no ip domain lookup");
            } else if (trimmed.equals("line con 0")) {
                transformed = "line console 0";
            } else if (trimmed.startsWith("aaa authorization ")) {
                transformed = lines[n].replaceAll("aaa authorization (.*)local if-authenticated",
                                                  "aaa authorization $1if-authenticated local");
            }

            //
            // transform no-list lists/leaves
            //
            if (trimmed.startsWith("no ip forward-protocol udp ")) {
                transformed = lines[n].replaceAll("no ip forward-protocol udp (\\S+)",
                                                  "ip forward-protocol udp $1 disabled");
            } else if (trimmed.startsWith("no cable cm-status enable ")) {
                transformed = lines[n].replace("no cable cm-status enable ",
                                               "cable cm-status enable no-list ");
            } else if (trimmed.startsWith("no passive-interface ")) {
                transformed = lines[n].replace("no passive-interface ",
                                               "disable passive-interface ");
            } else if (trimmed.startsWith("no network-clock-participate wic ")) {
                transformed = lines[n].replace("no network-clock-participate wic ",
                                               "network-clock-participate wic-disabled ");
            } else if (trimmed.startsWith("no wrr-queue random-detect ")) {
                transformed = lines[n].replace("no wrr-queue random-detect ",
                                               "no-list wrr-queue random-detect ");
            } else if (trimmed.startsWith("no spanning-tree vlan ")) {
                transformed = lines[n].replace("no spanning-tree vlan ",
                                               "spanning-tree vlan no-list ");
            } else if (trimmed.startsWith("no mac-address-table learning vlan ")) {
                transformed = lines[n].replace("no mac-address-table learning vlan ",
                                               "mac-address-table learning vlan no-list ");
            } else if (trimmed.startsWith("no ip igmp snooping vlan ")) {
                transformed = lines[n].replace("no ip igmp snooping vlan ",
                                               "ip igmp snooping vlan no-list ");
            } else if (trimmed.startsWith("no ip next-hop-self eigrp ")) {
                transformed = lines[n].replace("no ip next-hop-self eigrp ",
                                               "ip next-hop-self eigrp no-list ");
            } else if (trimmed.startsWith("no ip split-horizon eigrp ")) {
                transformed = lines[n].replace("no ip split-horizon eigrp ",
                                               "ip split-horizon eigrp no-list ");
            }

            //
            // strip single lines
            //
            else if (trimmed.equals("boot-start-marker") || trimmed.equals("boot-end-marker")) {
                transformed = "";
            } else if (trimmed.startsWith("radius-server source-ports ")) {
                transformed = "";
            } else if (trimmed.startsWith("license udi")) {
                transformed = ""; // not config
            } else if (trimmed.startsWith("! Incomplete")) {
                transformed = ""; // comments
            } else if (toptag.equals("ip msdp cache-sa-state")) {
                transformed = ""; // config? (can't be disabled)
            }

            //
            // Convert space to comma for range-list-syntax leaf-list's
            //
            if (transformed == null) {
                String[][] spaceToComma = {
                    // Fix cable rf-channels channel-list x-y z bandwidth-percent
                    // Fix cable rf-channels controller ? channel-list x-y z bandwidth-percent
                    { " channel-list ", " bandwidth-percent" },
                    { " downstream sg-channel ", " profile" }
                };
                for (int j = 0; j < spaceToComma.length; j++) {
                    int start = lines[n].indexOf(spaceToComma[j][0]);
                    if (start < 0)
                        continue;
                    start += spaceToComma[j][0].length();
                    int end = lines[n].indexOf(spaceToComma[j][1], i);
                    if (end < 0)
                        continue;
                    String groupList = lines[n].substring(start,end).trim().replace(" ", ",");
                    transformed = lines[n].substring(0,start) + groupList + lines[n].substring(end);
                }
            }

            //
            // Transform lines[n] -> XXX
            //
            if (transformed != null && !transformed.equals(lines[n])) {
                if (transformed.isEmpty())
                    traceVerbose(worker, "transformed: stripped '"+trimmed+"'");
                else
                    traceVerbose(worker, "transformed: '"+trimmed+"' -> '"+transformed.trim()+"'");
                lines[n] = transformed;
            }

        } // for (line-by-line)

        // Convert back lines to string
        res = linesToString(lines);


        //
        // APPEND TRANSFORMATIONS (may add, delete or reorder lines)
        //
        logVerbose(worker, "SHOW TRANSFORMING - appending config");
        lines = res.split("\n");
        StringBuilder buffer = new StringBuilder();
        for (n = 0; n < lines.length; n++) {
            String trimmed = lines[n].trim();
            boolean split = false;
            if (trimmed.isEmpty())
                continue;

            // Update toptag
            if (isTopExit(lines[n])) {
                toptag = "";
            } else if (Character.isLetter(lines[n].charAt(0))) {
                toptag = trimmed;
            }

            // autoInterfaceSwitchportStatus = true
            if (autoInterfaceSwitchportStatus && lines[n].startsWith("interface ")) {
                buffer.append(lines[n]+"\n");
                if (trimmed.indexOf("Ethernet") > 0 || trimmed.indexOf("Port-channel") > 0) {
                    res = print_line_exec(worker,"show " + trimmed + " switchport | i Switchport");
                    if (res.indexOf("Switchport: Enabled") >= 0) {
                        buffer.append(" switchport\n");
                    } else if (res.indexOf("Switchport: Disabled") >= 0) {
                        buffer.append(" no switchport\n");
                    }
                }
                continue;
            }

            //
            // ip name-server [vrf <vrf>] <address 1> .. [address N]
            //
            if (trimmed.startsWith("ip name-server ")) {
                String tokens[] = trimmed.split(" +");
                if (tokens[2].equals("vrf"))
                    split = getAppendLines(buffer, tokens, 4, 0, 1);
                else
                    split = getAppendLines(buffer, tokens, 2, 0, 1);
            }

            //
            // table-map
            //
            else if (toptag.startsWith("table-map ")
                     && trimmed.startsWith("map from ")) {
                String tokens[] = trimmed.split(" +");
                split = getAppendLines(buffer, tokens, 2, 2, 1);
            }

            //
            // Log or add if not split
            //
            if (split) {
                traceVerbose(worker, "transformed: split(1) '"+trimmed+"'");
            } else {
                buffer.append(lines[n]+"\n");
            }
        }
        res = buffer.toString();


        //
        // SINGLE BUFFER TRANSFORMATIONS:
        //

        //
        // Quote strings
        //
        logVerbose(worker, "SHOW TRANSFORMING - quoting strings");
        String[] quoteStrings = {
            "crypto isakmp key (\\S+) (?:address|hostname|address ipv6) \\S+",
            "alias \\S+ \\S+ (.*)",
            /* event manager applet * / action * regexp */ "action \\d+ regexp (.*)",
            "snmp-server (?:contact|location) (.*)"
        };
        for (n = 0; n < quoteStrings.length; n++) {
            Pattern pattern = Pattern.compile(quoteStrings[n]);
            Matcher matcher = pattern.matcher(res);
            int offset = 0;
            while (matcher.find()) {
                String quoted = stringQuote(matcher.group(1));
                traceVerbose(worker, "transformed: quoted string '"+matcher.group(0)+"' -> '"+quoted+"'");
                res = res.substring(0, matcher.start(1) + offset)
                    + quoted
                    + res.substring(matcher.start(1) + matcher.group(1).length() + offset);
                offset = offset + quoted.length() - matcher.group(1).length();
            }
        }

        //
        // Quote texts
        //
        logVerbose(worker, "SHOW TRANSFORMING - quoting texts");
        String[] quoteTexts = {
            // menu <name> title ^C
            // <title text>
            // ^C
            "\n(menu \\S+ title) (\\^C)(.*?)(\\^C)"
        };
        for (n = 0; n < quoteTexts.length; n++) {
            Pattern pattern = Pattern.compile(quoteTexts[n], Pattern.DOTALL);
            Matcher matcher = pattern.matcher(res);
            int offset = 0;
            while (matcher.find()) {
                String quoted = stringQuote(matcher.group(3));
                traceVerbose(worker, "transformed: quoted text '"+matcher.group(1)+"'");
                res = res.substring(0, matcher.start(2) + offset)
                    + quoted
                    + res.substring(matcher.end(4) + offset);
                int reduce = matcher.group(2).length() + matcher.group(4).length();
                offset = offset + quoted.length() - matcher.group(3).length() - reduce;
            }
        }

        //
        // Quote passwords
        //
        logVerbose(worker, "SHOW TRANSFORMING - quoting passwords");
        String[] quotePasswords = {
            "crypto isakmp key 6 (\\S+) (?:address|hostname|address ipv6) \\S+",
            /* router lisp */                  "authentication-key 6 (\\S+)",
            /* router lisp */                  "ipv4 etr map-server \\S+ key 6 (\\S+)",
            /* crypto ikev2 keyring / peer */  "pre-shared-key local 6 (\\S+)",
            /* crypto ikev2 keyring / peer */  "pre-shared-key remote 6 (\\S+)",
            /* crypto ikev2 keyring / peer */  "pre-shared-key 6 (\\S+)",
            /* crypto ikev2 profile */         "aaa authorization group (?:psk|eap) list \\S+ password 6 (\\S+)",
            /* crypto ikev2 profile */         "authentication (?:local|remote) pre-share key 6 (\\S+)",
            /* crypto isakmp client configuration group */ "\n\\s+key 6 (\\S+)",
            /* crypto keyring */               "pre-shared-key address \\S+(?: \\S+)? key 6 (\\S+)"
        };
        for (n = 0; n < quotePasswords.length; n++) {
            Pattern pattern = Pattern.compile(quotePasswords[n]);
            Matcher matcher = pattern.matcher(res);
            int offset = 0;
            while (matcher.find()) {
                String quoted = passwordQuote(matcher.group(1));
                traceVerbose(worker, "tranformed: '"+matcher.group(0)+"' -> '"+quoted+"'");
                res = res.substring(0, matcher.start(1) + offset)
                    + quoted
                    + res.substring(matcher.start(1) + matcher.group(1).length() + offset);
                offset = offset + quoted.length() - matcher.group(1).length();
            }
        }

        //
        // Quote certificates (contents)
        //
        logVerbose(worker, "SHOW TRANSFORMING - quoting certificates");
        for (i = res.indexOf("\n certificate ");
             i >= 0;
             i = res.indexOf("\n certificate ", i+14)) {
            int start = res.indexOf("\n", i+1);
            if (start < 0)
                continue;
            int end = res.indexOf("quit", start);
            if (end < 0)
                break;
            traceVerbose(worker, "transformed: quoted cert '"+res.substring(i,start).trim()+"'");
            String cert = "  " + res.substring(start+1, end).trim() + "\r\n"; // strip trailing \t
            res = res.substring(0,start+1) + stringQuote(cert) + "\n" + res.substring(end);
        }

        //
        // Quote banners (to single string)
        //
        logVerbose(worker, "SHOW TRANSFORMING - quoting banners");
        for (i = res.indexOf("\nbanner ");
             i >= 0;
             i = res.indexOf("\nbanner ", i+8)) {
            // banner <type> <delim>\n<MESSAGE><delim>
            n = res.indexOf(" ", i+8);
            if (n < 0)
                continue;
            nl = res.indexOf("\n", n);
            if (nl < 0)
                continue;
            traceVerbose(worker, "transformed: quoted banner '"+res.substring(i,nl).trim()+"'");
            String delim = res.substring(n+1, n+3);
            int delim2 = res.indexOf(delim, nl+1);
            if (delim2 < 0)
                continue;
            int nl2 = res.indexOf("\n", delim2);
            if (nl2 < 0)
                continue;
            String banner = res.substring(nl+1, delim2);
            //banner = banner.replace("\\r", "");
            banner = stringQuote(banner);
            res = res.substring(0,n+1) + banner + res.substring(nl2);
        }

        //
        // Quote macros
        //
        logVerbose(worker, "SHOW TRANSFORMING - quoting macros");
        for (i = res.indexOf("\nmacro name ");
             i >= 0;
             i = res.indexOf("\nmacro name ", i+12)) {
            // macro name <name>
            // xxx
            // yyy
            // @
            nl = res.indexOf("\n", i+12);
            if (nl < 0)
                continue;
            int me = res.indexOf("\n@", nl);
            if (me < 0)
                continue;
            traceVerbose(worker, "transformed: quoted macro '"+res.substring(i+1,nl).trim()+"'");
            String commands = res.substring(nl+1, me+1);
            commands = stringQuote(commands);
            res = res.substring(0,nl) + " " + commands + res.substring(me + 2);
        }

        //
        // Make a single vlan entry without add-keyword of all entries.
        //
        logVerbose(worker, "SHOW TRANSFORMING - interface * / switchport");
        res = res.replace("\n switchport trunk allowed vlan add ", "\\,");

        //
        // aaa accounting
        //
        logVerbose(worker, "SHOW TRANSFORMING - aaa accounting");
        for (i = res.indexOf("\naaa accounting ");
             i >= 0;
             i = res.indexOf("\naaa accounting ",i+16)) {
            int start = i;
            int end = res.indexOf("\n!", i+1);
            if (end < 0)
                continue;
            String aaa = res.substring(start,end);
            if (aaa.indexOf("action-type ") < 0)
                continue;
            aaa = aaa.replace("action-type ","");
            aaa = aaa.replace("\n", "");
            aaa = aaa.replace("\r", "");
            traceVerbose(worker, "transformed: compacted AAA='"+aaa+"'");
            res = res.substring(0,start+1) + aaa + res.substring(end);
        }

        //
        // Insert inject interface config first in matching interface
        //
        logVerbose(worker, "SHOW TRANSFORMING - injecting interface config");
        for (i = res.indexOf("\ninterface ");
             i >= 0;
             i = res.indexOf("\ninterface ",i+11)) {
            int end = res.indexOf(" ", i+11);
            if (end < 0)
                continue;
            nl = res.indexOf("\n", i+1);
            if (nl < 0)
                continue;
            if (end > nl)
                end = nl;
            String ifname = res.substring(i+11, end);
            for (n = interfaceConfig.size()-1; n >= 0; n--) {
                String entry[] = interfaceConfig.get(n);
                if (findString(entry[0], ifname) >= 0) {
                    traceVerbose(worker, "INJECT: inserting["+entry[2]+"]: '"+entry[1]
                               + "' first in interface "+ifname);
                    res = res.substring(0, nl+1) + " " + entry[1] + res.substring(nl);
                }
            }
        }

        //
        // DEFAULTS - inject hidden defaults values set by NSO
        //
        logVerbose(worker, "SHOW TRANSFORMING - injecting default values");
        res = defaults.inject(worker, res);

        //
        // Insert 'key config-key password-encrypt' config from CDB
        //
        res = insertKeyConfigKeyPassword(worker, res);

        //
        // Force top mode before each interface config
        //
        res = res.replace("\ninterface ", "\nxxyyzztop 0\ninterface ");


        //
        // DONE
        //
        if (syncFile != null) {
            traceVerbose(worker, "\nSHOW_AFTER_FILE:\n"+res);
            syncFile = null;
        } else {
            traceVerbose(worker, "\nSHOW_AFTER:\n"+res);
        }

        // Respond with updated show buffer
        return res;
    }


    @Override
    public void show(NedWorker worker, String toptag)
        throws Exception {
        if (trace)
            session.setTracer(worker);
        lastGetConfig = null;
        if (toptag.equals("interface")) {
            logInfo(worker, "SHOW READING CONFIG");
            String res = getConfig(worker, true);
            logInfo(worker, "DONE SHOW");
            worker.showCliResponse(res);
        } else {
            // only respond to first toptag since the IOS
            // cannot show different parts of the config.
            worker.showCliResponse("");
        }
    }


    @Override
    public boolean isConnection(String device_id,
                                InetAddress ip,
                                int port,
                                String proto,  // ssh or telnet
                                String ruser,
                                String pass,
                                String secpass,
                                String keydir,
                                boolean trace,
                                int connectTimeout, // msec
                                int readTimeout,
                                int writeTimeout) {
        return ((this.device_id.equals(device_id)) &&
                (this.ip.equals(ip)) &&
                (this.port == port) &&
                (this.proto.equals(proto)) &&
                (this.ruser.equals(ruser)) &&
                (this.pass.equals(pass)) &&
                (this.secpass.equals(secpass)) &&
                (this.trace == trace) &&
                (this.connectTimeout == connectTimeout) &&
                (this.readTimeout == readTimeout) &&
                (this.writeTimeout == writeTimeout));
    }

    private String filterNonPrintable(String text)
    {
        return text.replaceAll("[\u0001-\u0008\u000b\u000c\u000e-\u001f]", "");
    }

    private String commandWash(String cmd) {
        byte[] bytes = cmd.getBytes();
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < cmd.length(); ++i) {
            if (bytes[i] == 9)
                continue;
            if (bytes[i] == -61)
                continue;
            result.append(cmd.charAt(i));
        }
        return result.toString();
    }

    @Override
    public void command(NedWorker worker, String cmdName, ConfXMLParam[] p)
        throws Exception {
        String cmd  = cmdName;
        String reply = "";
        Pattern[] cmdPrompt;
        NedExpectResult res;
        boolean configMode = false;
        boolean wasInConfig = inConfig;
        boolean rebooting = false;
        String promptv[] = null;
        int promptc = 0;
        int i;

        if (trace)
            session.setTracer(worker);

        // Add arguments
        for (i = 0; i < p.length; ++i) {
            ConfObject val = p[i].getValue();
            if (val != null)
                cmd = cmd + " " + val.toString();
        }
        traceVerbose(worker, "command(" + cmd + ") [inConfig="+inConfig+"]");
        cmd = commandWash(cmd);

        // Extract answer(s) to prompting questions
        Pattern pattern = Pattern.compile("(.*)\\|\\s*prompts\\s+(.*)");
        Matcher matcher = pattern.matcher(cmd);
        if (matcher.find()) {
            cmd = matcher.group(1).trim();
            traceVerbose(worker, "command = '"+cmd+"'");
            promptv = matcher.group(2).trim().split(" +");
            for (i = 0; i < promptv.length; i++)
                traceVerbose(worker, "promptv["+i+"] = '"+promptv[i]+"'");
        }

        // Config mode exec command
        if (cmdName.startsWith("default") || cmd.startsWith("exec ")) {
            configMode = true;
            if (isNetsim()) {
                worker.error(NedCmd.CMD, "'"+cmd+"' not supported on NETSIM, "+
                             "use a real device");
                return;
            }
            if (cmd.startsWith("exec "))
                cmd = cmd.substring(5);
            if (!wasInConfig)
                enterConfig(worker, NedCmd.CMD);
        } else if (cmd.startsWith("any ")) {
            cmd = cmd.substring(4);
        }

        // patch for service node bug, quoting command
        if (cmd.charAt(cmd.length() - 1) == '"') {
            traceInfo(worker, "NCSPATCH: removing quotes inserted by bug in NCS");
            cmd = cmd.substring(0, cmd.length() -1 );
            cmd = cmd.replaceFirst("\"", "");
        }

        // show warnings [internal command]
        if (cmd.equals("show warnings")) {
            reply = "\nWarnings/output since last commit: \n"+ warningsBuf;
        }

        // show outformat raw [internal command]
        else if (cmd.equals("show outformat raw")) {
            reply = "\nNext dry-run will show raw (unmodified) format.\n";
            traceInfo(worker, reply);
            showRaw = true;
        }

        // show outformat raw [internal command]
        else if (cmd.equals("secrets resync")) {
            secrets.enableReSync();
            getConfig(worker, true);
            reply = "\nRe-synced all cached secrets.\n";
            traceInfo(worker, reply);
        }

        // sync-from-file <FILE>
        else if (isNetsim() && cmd.startsWith("sync-from-file ")) {
            syncFile = cmd.trim().substring(15).trim();
            reply = "\nNext sync-from will use file = " + syncFile + "\n";
            traceInfo(worker, reply);
        }

        // DEVICE command
        else {
            cmdPrompt = new Pattern[] {
                // Prompt patterns:
                Pattern.compile("^\\S+\\(\\S+\\)#"), // config prompt
                Pattern.compile(privexec_prompt),
                Pattern.compile("^\\S+#"),
                // Ignore patterns:
                Pattern.compile("\\[OK\\]"),
                Pattern.compile("\\[Done\\]"),
                Pattern.compile("timeout is \\d+ seconds:"),  // ping
                Pattern.compile("Key data:"), // crypto key export rsa
                // Question patterns:
                Pattern.compile(":\\s*$"),
                Pattern.compile("\\][\\?]?\\s*$")
            };

            // Send command or question (ending with ?) to device
            boolean help = cmd.charAt(cmd.length() - 1) == '?';
            if (help) {
                traceVerbose(worker, "Sending"+(configMode ? " config" : "" )+" help: " + cmd);
                session.print(cmd);
            }
            else {
                traceVerbose(worker, "Sending"+(configMode ? " config" : "" )+" command: " + cmd);
                session.print(cmd+"\n");
            }

            // Wait for command echo from device
            String echo = cmd;
            traceVerbose(worker, "Waiting for command echo '"+echo+"'");
            session.expect(new String[] { Pattern.quote(echo) }, worker);

            // Wait for prompt, answer prompting questions with | prompts info
            worker.setTimeout(readTimeout);
            while (true) {
                traceVerbose(worker, "Waiting for command prompt (read-timeout "+readTimeout+")");
                res = session.expect(cmdPrompt, true, readTimeout, worker);
                String output = res.getText();
                String answer = null;
                reply += output;
                if (res.getHit() <= 2) {
                    traceVerbose(worker, "Got prompt["+res.getHit()+"] '"+output+"'");
                    if (help) {
                        sendBackspaces(worker, cmd);
                    }
                    if (promptv != null && promptc < promptv.length) {
                        reply += "\n(unused prompts:";
                        for (i = promptc; i < promptv.length; i++)
                            reply += " "+promptv[i];
                        reply += ")";
                    }
                    break;
                } else if (res.getHit() <= 6
                           || help
                           || cmd.startsWith("show ")) {
                    traceVerbose(worker, "Ignoring output '"+output+"'");
                    continue;
                }

                traceVerbose(worker, "Got question '"+output+"'");

                // Get answer from command line, i.e. '| prompts <val>'
                if (promptv != null && promptc < promptv.length) {
                    answer = promptv[promptc++];
                }

                // Look for answer in auto-prompts ned-settings
                else {
                    for (int n = autoPrompts.size()-1; n >= 0; n--) {
                        String entry[] = autoPrompts.get(n);
                        if (findString(entry[1], output) >= 0) {
                            traceInfo(worker, "Matched auto-prompt["+entry[0]+"]");
                            answer = entry[2];
                            reply += "(auto-prompt "+answer+") -> ";
                            break;
                        }
                    }
                }

                // Send answer to device. Check if rebooting
                if (answer != null) {
                    traceInfo(worker, "Sending: "+answer);
                    if (answer.equals("ENTER"))
                        session.print("\n");
                    else if (answer.length() == 1)
                        session.print(answer);
                    else
                        session.print(answer+"\n");
                    if (cmd.startsWith("reload")
                        && output.indexOf("Proceed with reload") >= 0
                        && answer.charAt(0) != 'n') {
                        rebooting = true;
                        break;
                    }
                    continue;
                }

                // Missing answer to a question prompt:
                reply = "\nMissing answer to a device question:\n+++" + reply;
                reply +="\n+++\nSet auto-prompts ned-setting or add '| prompts <answer>', e.g.:\n";
                if (configMode)
                    reply += "exec \"crypto key zeroize rsa MYKEY | prompts yes\"";
                else
                    reply += "devices device <devname> live-status exec any \"reload | prompts yes\"";
                reply += "\nNote: Single letter is sent without LF. Use 'ENTER' for LF only.";
                exitPrompting(worker);
                if (configMode && !wasInConfig)
                    exitConfig(worker);
                worker.error(NedCmd.CMD, reply);
                return;
            }
        }

        // Report device output 'reply'
        if (configMode) {
            if (!wasInConfig)
                exitConfig(worker);
            worker.commandResponse(new ConfXMLParam[] {
                    new ConfXMLParamValue("ios", "result",
                                          new ConfBuf(reply))});
        } else {
            worker.commandResponse(new ConfXMLParam[] {
                    new ConfXMLParamValue("ios-stats", "result",
                                          new ConfBuf(reply))});
        }

        // Rebooting
        if (rebooting) {
            logInfo(worker, "Rebooting device...");
            worker.setTimeout(10*60*1000);
            sleep(worker, 30 * 1000, true); // Sleep 30 seconds
        }
    }


    private String ConfObjectToIfName(ConfObject kp) {
        String name = kp.toString();
        name = name.replaceAll("\\{", "");
        name = name.replaceAll("\\}", "");
        name = name.replaceAll(" ", "");
        return name;
    }

    @Override
    public void showStats(NedWorker worker, int th, ConfPath path)
        throws Exception {

        mm.attach(th, -1, 1);

        System.err.println("showStats() "+path);

        Maapi m = mm;

        ConfObject[] kp = path.getKP();
        ConfKey x = (ConfKey) kp[1];
        ConfObject[] kos = x.elements();

        String root =
            "/ncs:devices/device{"+device_id+"}"
            +"/live-status/ios-stats:interfaces"+x;

        // Send show single interface command to device
        session.println("show interfaces "+ConfObjectToIfName(kp[1])+
                        " | include line|address");
        String res = session.expect("\\A.*#", worker);

        // Parse single interface
        String[] lines = res.split("\r|\n");
        for(int i=0 ; i < lines.length ; i++) {
            if (lines[i].indexOf("Invalid input detected") > 0)
                throw new NedException("showStats(): Invalid input");
            if (lines[i].indexOf("Hardware is") >= 0) {
                String[] tokens = lines[i].split(" +");
                for(int k=0 ; k < tokens.length-3 ; k++) {
                    if (tokens[k].equals("address") &&
                        tokens[k+1].equals("is")) {
                        m.setElem(th, tokens[k+2], root+"/mac-address");
                    }
                }
            }
            else if (lines[i].indexOf("Internet address is") >= 0) {
                String[] tokens = lines[i].split(" +");
                m.setElem(th, tokens[4], root+"/ip-address");
            }
        }

        worker.showStatsResponse(new NedTTL[] {
                new NedTTL(new ConfPath(root+"/ip-address"), 3),
                new NedTTL(new ConfPath(root+"/mac-address"), 3)
            });

        mm.detach(th);
    }

    @Override
    public void showStatsList(NedWorker worker, int th, ConfPath path)
        throws Exception {

        System.err.println("showStatsList() "+path);

        ArrayList<NedTTL> ttls = new ArrayList<NedTTL>();

        mm.attach(th, -1, 1);

        String root =
            "/ncs:devices/device{"+device_id+"}"
            +"/live-status/ios-stats:interfaces";

        mm.delete(th, root);

        session.println("show interfaces | include line|address");
        String res = session.expect("\\A.*#", worker);

        String[] lines = res.split("\r|\n");
        String currentInterfaceType = null;
        String currentInterfaceName = null;
        for(int i=0 ; i < lines.length ; i++) {
            if (lines[i].indexOf("line protocol") >= 0) {
                String[] tokens = lines[i].split(" +");
                Pattern pattern = Pattern.compile("\\d");
                Matcher matcher = pattern.matcher(tokens[0]);
                if (matcher.find()) {
                    currentInterfaceType =
                        tokens[0].substring(0,matcher.start());
                    currentInterfaceName =
                        tokens[0].substring(matcher.start());
                    mm.create(th, root+"{"+currentInterfaceType+
                              " "+currentInterfaceName+"}");
                }
            }
            if (currentInterfaceType != null &&
                lines[i].indexOf("Hardware is") >= 0) {
                String[] tokens = lines[i].split(" +");
                for(int x=0 ; x < tokens.length-3 ; x++) {
                    if (tokens[x].equals("address") &&
                        tokens[x+1].equals("is")) {
                        String epath =
                            root+"{"+currentInterfaceType+
                            " "+currentInterfaceName+"}"+"/mac-address";
                        mm.setElem(th, tokens[x+2], epath);
                        ttls.add(new NedTTL(new ConfPath(epath), 3));
                    }
                }
            }
            else if (currentInterfaceType != null &&
                     lines[i].indexOf("Internet address is") >= 0) {
                String[] tokens = lines[i].split(" +");
                String epath =
                    root+"{"+currentInterfaceType+" "+
                    currentInterfaceName+"}"+"/ip-address";
                mm.setElem(th, tokens[4], epath);
                ttls.add(new NedTTL(new ConfPath(epath), 3));
            }
        }

        worker.showStatsListResponse(60,
                                     ttls.toArray(new NedTTL[ttls.size()]));

        mm.detach(th);
    }

    @Override
    public NedCliBase newConnection(String device_id,
                                InetAddress ip,
                                int port,
                                String proto,  // ssh or telnet
                                String ruser,
                                String pass,
                                String secpass,
                                String publicKeyDir,
                                boolean trace,
                                int connectTimeout, // msec
                                int readTimeout,    // msec
                                int writeTimeout,   // msecs
                                NedMux mux,
                                NedWorker worker) {
        return new IOSNedCli(device_id,
                               ip, port, proto, ruser, pass, secpass, trace,
                               connectTimeout, readTimeout, writeTimeout,
                               mux, worker);
    }

}
