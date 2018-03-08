package com.tailf.packages.ned.ios;

import java.util.List;
import java.util.ArrayList;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.tailf.ned.NedWorker;
import com.tailf.ned.NedException;
import com.tailf.ned.NedExpectResult;

import com.tailf.conf.ConfPath;
import com.tailf.ned.CliSession;

/**
 * Utility class for restoring interfaces addresses on IOS device
 *
 * @author lbang
 * @version 20170302
 */

@SuppressWarnings("deprecation")
public class NedDataModify {

    /*
     * Local data
     */
    private NedWorker worker;
    boolean trace;
    private String device_id;
    private boolean showVerbose;
    private CliSession session;

    /**
     * Constructor
     */
    NedDataModify(CliSession session, NedWorker worker, boolean trace, String device_id, boolean showVerbose) {
        this.session     = session;
        this.worker      = worker;
        this.trace       = trace;
        this.device_id   = device_id;
        this.showVerbose = showVerbose;
    }

    /*
     * Write info in NED trace
     *
     * @param info - log string
     */
    private void traceInfo(String info) {
        if (trace) {
            worker.trace("-- " + info + "\n", "out", device_id);
        }
    }

    /*
     * Write info in NED trace if verbose output
     *
     * @param info - log string
     */
    private void traceVerbose(String info) {
        if (showVerbose && trace) {
            worker.trace("-- " + info + "\n", "out", device_id);
        }
    }

    /*
     * isTopExit
     */
    private boolean isTopExit(String line) {
        if (line.startsWith("exit"))
            return true;
        if (line.startsWith("!") && line.trim().equals("!"))
            return true;
        return false;
    }

    /*
     * getIfAddresses
     */
    private ArrayList<String> getIfAddresses(String toptag, String data)
        throws NedException {
        String tags[] = toptag.split(" +");
        String ifline = tags[0] + " " + tags[1];

        // Check if interface is being deleted in same transaction
        if (data.contains("\nno " + ifline)) {
            return null; // interface is being deleted, no need to re-inject ip address(es)
        }

        // Send line and wait for echo and result
        String result;
        try {
            String line = "show run " + ifline + " | i address";
            session.print(line + "\n");
            session.expect(new String[] { Pattern.quote(line) }, worker);
            result = session.expect("\\A[^\\# ]+#[ ]?$", worker);
        } catch (Exception e) {
            throw new NedException("NedDataModify(getIfAddresses) - ERROR : failed to show "+toptag+" : " + e.getMessage());
        }

        if (result.contains("Invalid input detected"))
            return null; // interface not existing yet

        // Add ip and ipv6 addresses
        ArrayList<String> addresses = new ArrayList<String>();
        String lines[] = result.split("\n");
        for (int n = 0; n < lines.length; n++) {
            if (lines[n].trim().isEmpty())
                continue;
            if (lines[n].startsWith(" ip address "))
                addresses.add(lines[n].trim());
            if (lines[n].startsWith(" ipv6 address "))
                addresses.add(lines[n].trim());
        }

        return addresses;
    }


    private boolean isIpv4Primary(String addr) {
        if (addr.contains(" secondary"))
            return false;
        if (addr.startsWith("ip address "))
            return true;
        return false;
    }

    /*
     * vrfForwardingRestore
     *
     * @param data - config data from applyConfig, before commit
     * @return config data modified after modifying sets
     */
    public String vrfForwardingRestore(String data)
        throws NedException {
        int j;

        String lines[] = data.split("\n");
        StringBuilder newdata = new StringBuilder();
        String toptag = "";

        for (int n = 0; n < lines.length; n++) {
            int exit = -1;
            String trimmed = lines[n].trim();
            if (trimmed.isEmpty())
                continue;
            if (isTopExit(lines[n])) {
                toptag = "";
            } else if (Character.isLetter(lines[n].charAt(0))) {
                toptag = lines[n].trim();
            }

            // VRF modified - re-inject current (non deleted) addresses
            ArrayList<String> addresses;
            if (toptag.startsWith("interface ")
                && trimmed.matches("^(no )?(ip )?vrf forwarding \\S+$")
                && (addresses = getIfAddresses(toptag, data)) != null) {

                //traceVerbose("ADDRESSES "+toptag+":");
                //for (j = 0; j < addresses.size(); j++) traceVerbose(" ["+j+"]="+addresses.get(j));

                // Check interface for address changes - update old addresses in array list
                for (j = n; j < lines.length; j++) {
                    if (isTopExit(lines[j]))
                        break;
                    String line = lines[j].trim();
                    if (line.startsWith("no ip address") || line.startsWith("no ipv6 address")) {
                        addresses.remove(line.substring(3));
                    }
                    else if (line.startsWith("ip address") || line.startsWith("ipv6 address")) {
                        // Make sure there is only one primary address
                        if (isIpv4Primary(line)) {
                            for (int a = 0; a < addresses.size(); a++) {
                                String addr = addresses.get(a);
                                if (isIpv4Primary(addr)) {
                                    addresses.remove(addr);
                                    break;
                                }
                            }
                        }
                        // Put in list in order to sort secondary addresses
                        // addresses.add(line);
                        // lines[j] = "";
                    }
                }

                // Add the VRF line
                newdata.append(lines[n]+"\n");

                // Re-inject addresses after the vrf line (loop twice to put secondary last)
                for (j = 0; j < addresses.size(); j++) {
                    String address = addresses.get(j);
                    if (!address.contains(" secondary")) {
                        traceInfo("PATCH: "+toptag+" vrf modified, restoring '"+address+"'");
                        newdata.append(" " + address + "\n");
                    }
                }
                for (j = 0; j < addresses.size(); j++) {
                    String address = addresses.get(j);
                    if (address.contains(" secondary")) {
                        traceInfo("PATCH: "+toptag+" vrf modified, restoring '"+address+"'");
                        newdata.append(" " + address + "\n");
                    }
                }
            }

            // Add line (may be empty due to stripped deleted address)
            else if (!lines[n].trim().isEmpty()) {
                newdata.append(lines[n]+"\n");
            }
        }

        data = newdata.toString();
        return "\n" + data + "\n";
    }
}
