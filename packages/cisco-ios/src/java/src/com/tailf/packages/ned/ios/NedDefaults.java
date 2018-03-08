/**
 * Utility class for injecting hidden default values if explicitly set in NSO.
 * Uses the meta-data tag "default-value".
 * Example:
 *   tailf:meta-data "default-value" {
 *    tailf:meta-value "$1 $2<NL> <DEFAULT><NL>exit<NL>"
 *        + " :: wrr-queue cos-map 1 1 0 :: wrr-queue cos-map 1 1 1"
 *        + " :: wrr-queue cos-map 1 2 2 :: wrr-queue cos-map 1 2 3"
 *        + " :: wrr-queue cos-map 3 1 6";
 *   }
 *
 * @author lbang
 * @version 20161207
 */

package com.tailf.packages.ned.ios;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.tailf.ned.NedWorker;
import com.tailf.ned.NedException;

import java.io.IOException;

import com.tailf.conf.ConfException;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfKey;

import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiSchemas.CSNode;

import com.tailf.cdb.Cdb;
import com.tailf.cdb.CdbDBType;
import com.tailf.cdb.CdbSession;

import com.tailf.ncs.ns.Ncs;

import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;

import com.tailf.ned.CliSession;

import org.apache.log4j.Logger;

//
// NedDefaults
//
@SuppressWarnings("deprecation")
public class NedDefaults {

    /*
     * Local data
     */
    private NedWorker worker;
    private CliSession session;
    private CdbSession cdbOper;
    private String device_id;
    private String model;
    private boolean trace;
    private boolean showVerbose;

    private String SP = "^";
    public static Logger LOGGER  = Logger.getLogger(IOSNedCli.class);

    String[][] defaultMaps = {
        {
            "WRR-QUEUE-COSMAP-2",
            "wrr-queue cos-map 1 1 0 :: wrr-queue cos-map 1 1 1 :: wrr-queue cos-map 1 2 2 :: wrr-queue cos-map 1 2 3"
        },
        {
            "WRR-QUEUE-COSMAP-3",
            "wrr-queue cos-map 1 1 0 :: wrr-queue cos-map 1 2 1 :: wrr-queue cos-map 3 1 6"
        }
    };

    /*
     * Constructor
     */
    NedDefaults(CliSession session, NedWorker worker, CdbSession cdbOper,
                String device_id, String model,
                boolean trace, boolean showVerbose)
        throws NedException {

        this.worker      = worker;
        this.session     = session;
        this.cdbOper     = cdbOper;
        this.device_id   = device_id;
        this.model       = model;
        this.trace       = trace;
        this.showVerbose = showVerbose;
    }

    private void traceVerbose(NedWorker worker, String info) {
        if (showVerbose && trace) {
            worker.trace("-- " + info + "\n", "out", device_id);
        }
    }

    private void traceInfo(NedWorker worker, String info) {
        if (trace)
            worker.trace("-- " + info + "\n", "out", device_id);
    }

    private void logInfo (NedWorker worker, String info) {
        LOGGER.info(device_id + " " + info);
        if (trace)
            worker.trace("-- " + info + "\n", "out", device_id);
    }

    private void logError(NedWorker worker, String text, Exception e) {
        LOGGER.error(device_id + " " + text, e);
        if (trace && worker != null)
            worker.trace("-- " + text + ": " + e.getMessage() + "\n", "out", device_id);
    }

    //
    // operSetElem
    //
    private void operSetElem(NedWorker worker, String value, String path) {
        String root = path.substring(0,path.lastIndexOf("/"));
        String elem = path.substring(path.lastIndexOf("/"));
        try {
            ConfPath cp = new ConfPath("/ncs:devices/ncs:device{"+device_id+"}"
                                       +"/ncs:ned-settings/ios-op:cisco-ios-oper/defaults{"+root+"}");
            if (!cdbOper.exists(cp)) {
                cdbOper.create(cp);
            }
            cdbOper.setElem(new ConfBuf(value), cp.append(elem));
            //traceVerbose(worker, "          " + path + " = " + value);
        } catch (Exception e) {
            logError(worker, "DEFAULTS - ERROR : failed to set "+path, e);
        }
    }

    //
    // operDeleteList
    //
    private void operDeleteList(NedWorker worker, String path) {
        try {
            ConfPath cp = new ConfPath("/ncs:devices/ncs:device{"+device_id+"}"
                                       +"/ncs:ned-settings/ios-op:cisco-ios-oper/defaults{"+path+"}");
            if (cdbOper.exists(cp)) {
                cdbOper.delete(cp);
            }
        } catch (Exception e) {
            logError(worker, "DEFAULTS - ERROR : failed to delete "+path, e);
        }
    }


    //
    // isMetaDataDefault
    //
    private boolean isMetaDataDefault(String line) {
        if (!line.trim().startsWith("! meta-data :: "))
            return false;
        if (line.indexOf(" :: default-value") < 0)
            return false;
        return true;
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

    private String getWrrQueueCosMapDefaultMap(String tokens[], String line) {
        String defaultMap = "WRR-QUEUE-COSMAP-2";

        // Interface name
        String ifname = tokens[0] + " " + tokens[1];
        ifname = ifname.replace("{", "").replace("}", "").replace(SP, "/");

        // Show queuing to determine what type of default map
        try {
            // WS-C6504-E
            String res = print_line_exec(worker, "show queueing "+ifname+" | i WRR");
            // CISCOIOS7604
            if (res.indexOf("Invalid input detected at") > 0)
                res = print_line_exec(worker, "show mls qos queuing "+ifname+" | i WRR");
            if (res.indexOf("Invalid input detected at") > 0) {
                logInfo(worker, "DEFAULTS - cache() ERROR :: failed to show queuing for interface");
            } else if (res.indexOf("[queue 3]") > 0) {
                defaultMap = "WRR-QUEUE-COSMAP-3";
            }
        } catch (Exception e) {
            logError(worker, "DEFAULTS - cache() ERROR :: show queuing exception", e);
        }

        return defaultMap;
    }


    //
    // cacheLine
    //
    private void cacheLine(NedWorker worker, String line, String meta)
        throws NedException {
        int i, j;
        // metas[0] = ! meta-data
        // metas[1] = path
        // metas[2] = default-value
        // metas[3] = inject syntax
        // metas[4] = default line(s)
        String metas[] = meta.split(" :: ");

        // Get root path for default-value container
        String root = metas[1].substring(0, metas[1].lastIndexOf("/"));
        root = root.replaceFirst("(.*)}/config/(.*)", "$2");

        // Replace '/' within keys with SP to avoid being counted as token
        int depth = 0;
        for (i = 0; i < root.length(); i++) {
            if (root.charAt(i) == '{')
                depth++;
            else if (root.charAt(i) == '}')
                depth--;
            else if  (root.charAt(i) == '/' && depth > 0) {
                root = root.substring(0,i)+SP+root.substring(i+1);
            }
        }
        String tokens[] = root.split("/");

        // Delete or create command
        boolean delete = false;
        if (line.startsWith("no ")) {
            traceVerbose(worker, "DEFAULTS - Un-caching default : " + root);
            line = line.substring(3);
            delete = true;
        } else {
            traceVerbose(worker, "DEFAULTS - Caching default : " + root);
        }

        //
        // Check if command matches one of the defaults [honor model regexp]
        //
        String defaultLine = null;
        if (metas[4].startsWith("MAP=")) {

            // Using pre-configured default map
            String defaultMap = metas[4].substring(4);

            // Dynamic maps
            if (metas[4].startsWith("MAP=WRR-QUEUE-COSMAP")) {
                defaultMap = getWrrQueueCosMapDefaultMap(tokens, line);
            }

            // Look for default value in matching defaultMap
            traceVerbose(worker, "DEFAULTS - Using default map = " + defaultMap);
            for (int map = 0; map < defaultMaps.length && defaultLine == null; map++) {
                if (defaultMaps[map][0].equals(defaultMap)) {
                    String[] defaults = defaultMaps[map][1].split(" :: ");
                    for (i = 0; i < defaults.length; i++) {
                        if (defaults[i].equals(line)) {
                            defaultLine = line;
                            break;
                        }
                    }
                }
            }
        } else {
            String modelRegexp = "";
            for (i = 4; i < metas.length; i++) {
                if ((j = metas[i].indexOf(" MODEL=")) > 0) {
                    defaultLine = metas[i].substring(0, j);
                    modelRegexp = metas[i].substring(j + 7);
                    if (!model.matches(".*"+modelRegexp+".*"))
                        continue;
                } else {
                    defaultLine = metas[i];
                }
                if (defaultLine.equals(line))
                    break;
            }
            if (i == metas.length) {
                defaultLine = null;
            }
            //traceVerbose(worker, "   modelRegexp = "+modelRegexp);
        }
        if (defaultLine == null)
            return;

        //
        // Command matches a default
        //
        //traceVerbose(worker, "          root = "+root);
        //traceVerbose(worker, "       default = "+defaultLine);

        //
        // Create inject config snippet using the template from metas[3]
        //
        String inject = metas[3];
        inject = inject.replace("<NL>", "\n");
        int offset = 0;
        for (i = inject.indexOf("$"); i >= 0; i = inject.indexOf("$", i+offset)) {
            int num = (int)(inject.charAt(i+1) - '1');
            inject = inject.substring(0,i) + tokens[num] + inject.substring(i+2);
            offset = offset + tokens[num].length() - 2;
        }
        inject = inject.replace("<DEFAULT>", line);
        inject = inject.replace("{", " ");
        inject = inject.replace("}", " ");
        inject = inject.replace(SP, "/");
        //traceVerbose(worker, "        inject = '"+inject+"'");

        //
        // Create root path to store the inject config in oper cache
        //
        root = root.replace(" ", SP); // multiple keys, can't have blank in keys
        root = root.replace("{", "(").replace("}", ")");
        root = root + line.replace(" ", "");

        //
        // Add/update or delete defaults cache
        //
        if (delete) {
            traceInfo(worker, "DEFAULTS - Deleting : " + root);
            operDeleteList(worker, root);
        } else {
            traceInfo(worker, "DEFAULTS - Adding : "+root);
            operSetElem(worker, inject, root+"/inject");
        }
    }


    //
    // cache
    //
    public void cache(NedWorker worker, String lines[])
        throws NedException {
        int i, c;

        for (i = 0 ; i < lines.length - 1; i++) {
            if (!isMetaDataDefault(lines[i]))
                continue; // not a meta-data default
            for (c = i + 1; c < lines.length; c++)
                if (!lines[c].trim().startsWith("! meta-data :: "))
                    break; // found command line
            cacheLine(worker, lines[c], lines[i].trim());
        }
    }


    //
    // inject
    //
    public String inject(NedWorker worker, String res) {

        NavuContext context = null;
        NavuContainer container = null;
        try {
            context = new NavuContext(cdbOper);
            container = new NavuContainer(context);
            NavuList defaultsList = container
                .container(Ncs.hash)
                .container(Ncs._devices_)
                .list(Ncs._device_)
                .elem(new ConfKey(new ConfBuf(device_id)))
                .container("ncs", "ned-settings")
                .container("ios-op", "cisco-ios-oper")
                .list("defaults");
            for (NavuContainer entry : defaultsList.elements()) {
                String inject = entry.leaf("inject").valueAsString();
                traceInfo(worker, "DEFAULTS - injecting:\n" + inject);
                res = inject + res;
            }
        }
        catch (Exception e) {
            logError(worker, "DEFAULTS - inject() ERROR", e);
        }
        finally {
            if (container != null) {
                container.stopCdbSession();
                container = null;
            }
            context = null;
        }

        return res;
    }
}
