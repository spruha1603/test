package com.tailf.packages.ned.ios;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.EnumSet;

import com.tailf.ned.NedWorker;
import com.tailf.ned.NedException;

import com.tailf.conf.ConfPath;

import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiFlag;

import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;
import com.tailf.navu.NavuLeaf;


/**
 * Utility class for modifying config data based on YANG model meta data provided by NCS.
 * WARNING: old code does not handle multiple meta-data tags [TODO]
 *
 * @author lbang
 * @version 20161007
 */

// META SYNTAX:
// ================================
// metas[0]    = ! meta-data
// metas[1]    = path
// metas[2]    = annotation name
// metas[3..N] = meta value(s)
//
// Supported new line annotations:
//    max-values
//    max-values-mode
//    max-values-copy-meta
//    replace-list
//    replace-mls-qos-srr-queue
//    string-add-quotes
//    patch-interface-speed
//    inject-interface-config-X
//    trim-delete-when-empty
//
// Supported old line annotations:
//    add-keyword
//    string-remove-quotes
//    range-list-syntax
//    range-list-syntax-mode
//    diff-interface-move-X
//    shutdown-container-before-change

@SuppressWarnings("deprecation")
public class MetaDataModify {

    /*
     * Local data
     */
    private NedWorker worker;
    private String prefix;
    private String device_id;
    private String model;
    private boolean isNetsim;
    private boolean trace;
    private boolean showVerbose;
    private boolean autoIpCommunityListRepopulate;

    /**
     * Constructor
     */
    MetaDataModify(NedWorker worker, String prefix, String device_id, String model,
                   boolean trace, boolean showVerbose,
                   boolean autoIpCommunityListRepopulate) {
        this.worker      = worker;
        this.prefix      = prefix;
        this.device_id   = device_id;
        this.model       = model;
        this.trace       = trace;
        this.showVerbose = showVerbose;
        this.autoIpCommunityListRepopulate = autoIpCommunityListRepopulate;

        this.isNetsim = model.equals("NETSIM");
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

    private int getCmd(String lines[], int i) {
        int cmd;
        for (cmd = i; cmd < lines.length; cmd++) {
            String line = lines[cmd].trim();
            if (line.isEmpty())
                continue;
            if (line.startsWith("! meta-data :: /ncs:devices/device{"))
                continue;
            return cmd;
        }
        return -1;
    }

    private String[] insertCmdAfter(String lines[], int i, int cmd, String insert) {
        for (int n = i; n < cmd; n++) {
            lines[n] = lines[n+1];
        }
        lines[cmd] = insert;
        return lines;
    }

    /*
     * Trim cmd and all meta-data tags that goes with it
     */
    private String[] trimCmd(String lines[], int i, int cmd) {
        for (int n = i; n <= cmd; n++)
            lines[n] = "";
        return lines;
    }

    /*
     * Trim all identical tags (including this one)
     */
    private String[] trimMetaTags(String lines[], int i, String meta) {
        for (int n = i; n < lines.length; n++)
            if (lines[n].trim().equals(meta)) {
                traceVerbose("meta-data :: trimmed tag["+n+"]: " + meta);
                lines[n] = "";
            }
        return lines;
    }

    /*
     * Trim all duplicate interface MetaTags except the last or first one.
     */
    private String[] trimDuplicateInterfaceMetaTags(String lines[], int i, String meta, boolean keepfirst) {
        int n, last = -1;
        int count = -1;
        for (n = i; n < lines.length; n++) {
            if (lines[n].trim().equals("exit"))
                break;
            if (lines[n].trim().equals(meta)) {
                lines[n] = "";
                last = n;
                count++;
            }
        }
        if (keepfirst)
            lines[i] = meta;
        else
            lines[last] = meta;
        if (count > 0)
            traceVerbose("meta-data :: trimmed "+count+" tag(s): " + meta);
        return lines;
    }

    private String[] trimMetaTagsAndCmd(String lines[], int i, String meta) {
        int j;
        for (int n = i; n < lines.length - 1; n++) {
            if (lines[n].trim().equals(meta)) {
                lines[n] = ""; // Trim this meta-data tag
                for (j = n + 1; j < lines.length; j++) {
                    if (lines[j].trim().startsWith("! meta-data :: /ncs:devices/device{")) {
                        // Trim other meta-data tag
                        lines[j] = "";
                        continue;
                    }
                    // Trim this command
                    lines[j] = "";
                    break;
                }
            }
        }
        return lines;
    }

    /*
     * Remove last duplicate "no switchport" caused by "show-no" (NCS BUG)
     */
    private String[] ncsPatchInterface(String lines[], int lastif, int i) {
        int no_switchport = -1;
        for (int j = i + 1; j < lines.length; j++) {
            if (lines[j].trim().equals("exit"))
                break;
            if (lines[j].trim().equals("no switchport")) {
                if (no_switchport == -1)
                    no_switchport = j;
                else {
                    // Trim cmd and it's meta tag
                    traceInfo("NCSPATCH: removing duplicate 'no switchport' (NCS bug)");
                    lines[j] = "";
                    for (int n = j - 1; n > lastif; n--) {
                        if (lines[n].trim().startsWith("! meta-data :: /ncs:devices/device{") == false)
                            break;
                        lines[n] = "";
                    }
                }
            }
        }
        return lines;
    }

    private String duplicateToX(String lprefix, String values, String postfix, int x, String sep) {
        String val[] = values.split(sep+"+");
        if (val.length <= x)
            return lprefix + " " + values + postfix + "\n";
        return duplicateToX2(lprefix, val, postfix, x, sep);
    }

    private String duplicateToX2(String lprefix, String[] val, String postfix, int x, String sep) {
        String buf = "";
        for (int n = 0; n < val.length; n = n + x) {
            String line = "";
            for (int j = n; (j < n + x) && (j < val.length); j++) {
                if (j != n)
                    line += sep;
                line += val[j];
            }
            buf = buf + lprefix + " " + line + postfix + "\n";
        }
        return buf;
    }

    private String makeLine(String[] values, int start, int end) {
        String line = "";
        for (int i = start; i < end; i++)
            line = line + values[i] + " ";
        return line.trim();
    }

    private int NindexOf(String text, String str, int num) {
        int n, i = 0;
        for (n = 0; n < num - 1; n++) {
            i = text.indexOf(str, i);
            if (i < 0)
                return -1;
            i++;
        }
        return text.indexOf(str, i);
    }

    private boolean isTopExit(String line) {
        if (line.equals("!"))
            return true;
        if (line.equals("exit"))
            return true;
        return false;
    }

    private int getInterfaceStart(String lines[], int i) {
        for (; i >= 0; i--)
            if (lines[i].trim().startsWith("interface "))
                return i;
        return -1;
    }

    private int getInterfaceExit(String lines[], int i) {
        for (; i < lines.length; i++)
            if (lines[i].trim().equals("exit"))
                return i;
        return -1;
    }

    /*
     * getInterfaceConfig
     */
    private int getInterfaceConfig(String lines[], int lastif, String entry) {
        if (lastif < 0)
            return -1;
        for (int i = lastif; i < lines.length; i++) {
            if (lines[i].trim().equals("exit"))
                return -1;
            if (lines[i].startsWith(entry))
                return i;
        }
        return -1;
    }

    /*
     * setMaapiFlags
     */
    private void setMaapiFlags(int toTh, Maapi mm) {
        try {
            EnumSet<MaapiFlag> enums = EnumSet.of(MaapiFlag.NO_DEFAULTS);
            mm.setFlags(toTh, enums);
        } catch (Exception ignore) {
            traceVerbose("setMaapiFlags() :: ignored exception : "+ignore.getMessage());
        }
    }

    /*
     * clrMaapiFlags
     */
    private void clrMaapiFlags(int toTh, Maapi mm) {
        try {
            EnumSet<MaapiFlag> enums = EnumSet.noneOf(MaapiFlag.class);
            mm.setFlags(toTh, enums);
        } catch (Exception ignore) {
            traceVerbose("clrMaapiFlags() :: ignored exception : "+ignore.getMessage());
        }
    }

    /*
     * maapiExists
     */
    private boolean maapiExists(Maapi mm, int toTh, String path)
        throws NedException {

        try {
            if (mm.exists(toTh, path)) {
                traceVerbose("maapiExists("+path+") = true");
                return true;
            }
        } catch (Exception e) {
            throw new NedException("maapiExists("+path+") ERROR : " + e.getMessage());
        }

        traceVerbose("maapiExists("+path+") = false");
        return false;
    }


    /*
     * navuGetString
     */
    private String navuGetString(NavuContext toContext, String path) {
        String value;
        try {
            ConfPath cp = new ConfPath(path);
            NavuLeaf leaf = (NavuLeaf)new NavuContainer(toContext).getNavuNode(cp);
            if (leaf == null || !leaf.exists())
                return null;
            value = leaf.valueAsString();
        } catch (Exception ignore) {
            return null;
        }
        traceVerbose("navuGetString("+path+") = " + value);
        return value;
    }

    /*
     * Modify config data based on meta-data given by NCS.
     *
     * @param data - config data from applyConfig, before commit
     * @return Config data modified after parsing !meta-data tags
     */
    public String modifyData(String data, NavuContext toContext, int toTh, Maapi mm)
        throws NedException {

        int i, j, n;
        int cmd, next;

        //
        // MODIFY LINE NEW
        // Note: Can add new lines and can handle multiple meta-data tags per cmd
        //
        String lines[] = data.split("\n");
        StringBuilder newdata = new StringBuilder();
        int lastif = -1;
        for (i = 0; i < lines.length; i++) {
            if (lines[i].trim().isEmpty())
                continue;
            if (lines[i].trim().startsWith("interface ")) {
                lastif = i;
                lines = ncsPatchInterface(lines, lastif, i);
            }
            if (lines[i].trim().startsWith("! meta-data :: /ncs:devices/device{") == false) {
                newdata.append(lines[i] + "\n");  // Normal config line -> add
                continue;
            }
            // Find command index (reason: can be multiple meta-data tags per command)
            cmd = getCmd(lines, i + 1);
            if (cmd == -1) {
                continue;
            }
            String otherMetas = "";
            for (j = i + 1; j < cmd; j++)
                otherMetas += lines[j] + "\n";
            //traceVerbose("OTHER-METAS='"+otherMetas+"'");
            String trimmed = lines[cmd].trim();
            String pxSpace = lines[cmd].substring(0, lines[cmd].length() - trimmed.length());

            // Extract meta-data and meta-value(s), store in metas[] where:
            // metas[1] = meta path
            // metas[2] = meta tag name
            // metas[3] = first meta-value (each value separated by ' :: '
            String meta = lines[i].trim();
            String metas[] = meta.split(" :: ");
            String metaPath = metas[1];
            String metaTag = metas[2];

            //for (j = 0; j < metas.length; j++) traceVerbose("METAS["+j+"]='"+metas[j]+"'");
            //traceVerbose("LINE='"+line+"'");

            // max-values
            // max-values-copy-meta
            // max-values-mode
            // ===============
            // Split config lines with multiple values into multiple lines with a maximum
            // number of values per line.
            // metas[3] = offset in values[] for first value
            // metas[4] = maximum number of values per line
            // metas[5] = value separator [OPTIONAL]
            // Example:
            // tailf:meta-data "max-values" {
            //  tailf:meta-value "4 :: 8";
            // }
            if (metaTag.startsWith("max-values")) {
                // Do not split modes with separators if contents in submode
                if (metaTag.equals("max-values-mode")
                    && cmd + 1 < lines.length
                    && !isTopExit(lines[cmd+1])) {
                    continue;
                }
                String sep = " ";
                if (metas.length > 5)
                    sep = metas[5];
                int offset = Integer.parseInt(metas[3]);
                if (trimmed.startsWith("no "))
                    offset++;
                int start = NindexOf(trimmed, " ", offset);
                if (start > 0) {
                    int maxValues = Integer.parseInt(metas[4]);
                    String val[] = trimmed.substring(start+1).trim().split(sep+"+");
                    if (val.length > maxValues) {
                        String lprefix = pxSpace + trimmed.substring(0, start).trim();
                        if (metaTag.indexOf("-copy-meta") > 0) {
                            lprefix = otherMetas + lprefix;
                            //for (j = i + 1; j < cmd; j++)
                        }
                        traceInfo("meta-data :: max-values :: PATCH: splitting "+val.length
                                  +" values into max "+maxValues+"'s, separator='"+sep+"'");
                        newdata.append(duplicateToX2(lprefix, val, "", maxValues, sep));
                        lines = trimCmd(lines, i, cmd);
                    }
                }
            }


            // replace-list[-withkey][-withdesc]
            // ===================
            // Use with lists where the entire list is deleted if one entry is deleted
            // metas[1] = path
            // metas[2] = "replace-list[-opt]"
            // metas[3] = entry prefix
            // metas[4] = sublist list name
            // metas[5] = sublist list key | sublist leaf [with replace-list-withkey]
            // metas[6] = device regexp [OPTIONAL]
            // Example:
            // tailf:meta-data "replace-list" {
            //  tailf:meta-value "ip community-list standard :: entry :: expr :: C3550";
            // }
            else if (metaTag.startsWith("replace-list")) {
                if (autoIpCommunityListRepopulate && metas[3].startsWith("ip community-list ")) {
                    // Honor old ned-setting regardless of device model
                }
                else if (metas.length > 6 && !model.matches(".*"+metas[6]+".*")) {
                    traceVerbose("meta-data :: "+metaTag+" :: ignored, different model: "+model);
                    lines = trimMetaTags(lines, i, meta);
                    continue;
                }
                String name = metaPath.substring(metaPath.lastIndexOf("{")+1).replace("}", "");
                boolean hasDeleteLine = false;
                for (j = cmd; j < lines.length; j++)
                    if (lines[j].trim().startsWith("no " + metas[3] + " " + name + " ")) {
                        hasDeleteLine = true;
                        break;
                    }
                if (hasDeleteLine == false) {
                    // No delete of individual entries -> trim all identical meta-data tags on this entry
                    lines = trimMetaTags(lines, i, meta);
                    continue;
                }

                // Delete list and trim all tags & commands operating on this list entry
                traceInfo("meta-data :: "+metaTag+" :: PATCH: " + metas[3] + " " + name);
                lines = trimMetaTagsAndCmd(lines, i, meta);
                newdata.append("no " +  metas[3] + " " + name + "\n");

                // If non-empty list put back all existing entries
                try {
                    NavuContainer root;
                    try {
                        ConfPath cp = new ConfPath(metaPath);
                        root = (NavuContainer)new NavuContainer(toContext).getNavuNode(cp);
                    } catch (Exception ignore) {
                        continue;
                    }
                    if (root == null || !root.exists())
                        continue;
                    // -withdesc
                    if (metaTag.contains("-withdesc")) {
                        String val = root.leaf(prefix, "description").valueAsString().trim();
                        newdata.append(metas[3] + " " + name + " description " + val + "\n");
                    }
                    NavuList list = root.list(prefix, metas[4]);
                    if (list == null || list.isEmpty())
                        continue;
                    for (NavuContainer entry : list.elements()) {
                        String val = "";
                        if (metaTag.contains("-withkey")) {
                            val = " " + metas[4] + " " + entry.leaf(prefix, metas[4]).valueAsString().trim();
                        }
                        val += " " + entry.leaf(prefix, metas[5]).valueAsString().trim();
                        newdata.append(metas[3] + " " + name + val + "\n");
                    }
                } catch (Exception e) {
                    throw new NedException("modifyData(replace-list) ERROR : "+e.getMessage());
                }
            }

            // replace-mls-qos-srr-queue
            // =========================
            // A cat3750 can't delete single entries in the mls qos srr-queue
            // As a consequence the whole list must be removed first.
            // And then all entries always added back.
            else if (metaTag.equals("replace-mls-qos-srr-queue")) {
                String name = metaPath.substring(metaPath.lastIndexOf("{")+1).replace("}", "");
                boolean hasDeleteLine = false;
                for (j = cmd; j < lines.length; j++)
                    if (lines[j].trim().startsWith("no mls qos srr-queue " + name + " ")) {
                        hasDeleteLine = true;
                        break;
                    }
                if (hasDeleteLine == false) {
                    // Did not find a single delete of entry -> add this line -> but split to max 8 values
                    int start = NindexOf(trimmed, " ", 9);
                    if (start > 0) {
                        String lprefix = trimmed.substring(0, start).trim();
                        newdata.append(duplicateToX(lprefix, trimmed.substring(start+1).trim(), "", 8, " "));
                        lines = trimCmd(lines, i, cmd);
                    }
                    continue;
                }

                // Delete list and trim all tags & commands operating on this list entry
                traceInfo("meta-data :: replace-list :: PATCH: mls qos srr-queue " + name);
                lines = trimMetaTagsAndCmd(lines, i, meta);
                newdata.append("no mls qos srr-queue " + name + "\n");

                // If non-empty list put back all existing entries
                try {
                    NavuContainer root;
                    try {
                        ConfPath cp = new ConfPath(metaPath);
                        root = (NavuContainer)new NavuContainer(toContext).getNavuNode(cp);
                    } catch (Exception ignore) {
                        continue;
                    }
                    if (root == null || !root.exists())
                        continue;
                    NavuList list = root.list(prefix, "queue-threshold-list");
                    if (list == null || list.isEmpty())
                        continue;
                    for (NavuContainer entry : list.elements()) {
                        String key1 = entry.leaf(prefix, "queue").valueAsString().trim();
                        String key2 = entry.leaf(prefix, "threshold").valueAsString().trim();
                        String key  = "queue " + key1 + " threshold " + key2;
                        NavuLeaf valuesLeaf = entry.leaf(prefix, "values");
                        if (valuesLeaf == null || valuesLeaf.valueAsString() == null) {
                            traceInfo("meta-data :: WARNING :: null values in: mls qos srr-queue " + name + " " + key);
                            continue;
                        }
                        String values = valuesLeaf.valueAsString().trim();
                        newdata.append(duplicateToX("mls qos srr-queue " + name + " " + key, values, "", 8, " "));
                    }
                } catch (Exception e) {
                    throw new NedException("modifyData(replace-mls-qos-srr-queue) ERROR : "+e.getMessage());
                }
            }

            // string-add-quotes
            // =========================
            // Add a " before and after specified string
            // metas[3] = regexp, where <STRING> is the string to look at.
            // example:
            // tailf:meta-data "string-add-quotes" {
            //  tailf:meta-value "syslog msg <STRING>";
            // }
            else if (metaTag.equals("string-add-quotes")) {
                lines[i] = ""; // Strip meta-data comment
                String regexp = metas[3].replace("<STRING>", "(.*)");
                String replacement = metas[3].replace("<STRING>", "\\\"$1\\\"");
                String newline = lines[cmd].replaceFirst(regexp, replacement);
                if (lines[cmd].equals(newline) == false) {
                    lines[cmd] = newline;
                    traceInfo("meta-data :: string-add-quotes :: PATCH: "+
                              "added quotes around: '"+lines[cmd]+"'");
                }
            }


            // patch-interface-speed
            // =========================
            // ME-3600X no speed -> speed auto
            else if (metaTag.equals("patch-interface-speed")) {
                lines[i] = ""; // Strip meta-data comment
                if (model.startsWith("ME-3600X") && lines[cmd].trim().equals("no speed")) {
                    traceInfo("meta-data :: patch-interface-speed PATCH: ME-3600X: no speed -> speed auto");
                    lines[cmd] = " speed auto";
                }
            }

            // inject-interface-config
            // =========================
            // Inject config from TO transaction after or before this.
            // metas[3] = relative path
            // metas[4] = leaf name line
            // metas[5] = after|before
            // metas[6] = create|delete|any
            // metas[7] = value to ignore in set [OPTIONAL]
            // Example:
            // tailf:meta-data "inject-interface-config" {
            //  tailf:meta-value "speed :: speed :: after create";
            // }
            else if (metaTag.startsWith("inject-interface-config")) {
                boolean before = metas[5].indexOf("before") >= 0;
                lines = trimDuplicateInterfaceMetaTags(lines, i, meta, before);
                if (lines[i].isEmpty())
                    continue;
                lines[i] = ""; // Strip meta-data comment
                if (metas[6].equals("create")) {
                    if (lines[cmd].trim().startsWith("no "))
                        continue;
                } else if (metas[6].equals("delete")) {
                    if (!lines[cmd].trim().startsWith("no "))
                        continue;
                }
                if (getInterfaceConfig(lines, lastif, " " + metas[4] + " ") >= 0)
                    continue; // If config already in transaction, do not re-inject
                try {
                    NavuLeaf leaf;
                    setMaapiFlags(toTh, mm);
                    try {
                        String path = metas[1].substring(0,metas[1].lastIndexOf("}")+1) + "/" + metas[3];
                        ConfPath cp = new ConfPath(path);
                        leaf = (NavuLeaf)new NavuContainer(toContext).getNavuNode(cp);
                    } catch (Exception ignore) {
                        traceVerbose("modifyData(inject-interface-config) ignored exception : "+ignore.getMessage());
                        clrMaapiFlags(toTh, mm);
                        continue;
                    }
                    if (leaf == null || !leaf.exists()) {
                        clrMaapiFlags(toTh, mm);
                        continue;
                    }
                    String val = leaf.valueAsString();
                    if (val == null || val.isEmpty()) {
                        clrMaapiFlags(toTh, mm);
                        continue; // leaf not set but default value causes it to return 'null'
                    }
                    if (metas.length > 7 && metas[7].equals(val)) {
                        clrMaapiFlags(toTh, mm);
                        continue; // ignore setting this (e.g. default) value
                    }
                    String insert = " " + metas[4] + " " + val;
                    traceInfo("meta-data :: "+metas[2]+" :: PATCH: injecting: '"
                              + insert + "' " + metas[5] + " '" + lines[cmd] + "'");
                    if (before) {
                        newdata.append(insert + "\n");
                    } else {
                        lines = insertCmdAfter(lines, i, cmd, insert);
                        i--;
                    }
                    clrMaapiFlags(toTh, mm);
                } catch (Exception e) {
                    throw new NedException("modifyData(inject-interface-config) ERROR : "+e.getMessage());
                }
            }

            // trim-delete-when-empty
            // ===================
            // Strip all sub-leaves when deleting or device will keep the entry
            // metas[3] = strip all after this regexp match
            // Example:
            // tailf:meta-data "trim-delete-when-empty" {
            //  tailf:meta-value " preempt";
            // }
            // tailf:ned-data "." { tailf:transaction to; }
            else if (metaTag.equals("trim-delete-when-empty")) {
                lines = trimMetaTags(lines, i + 1, meta);
                if (trimmed.startsWith("no ")
                    && !maapiExists(mm, toTh, metaPath)) {
                    Pattern pattern = Pattern.compile(metas[3]);
                    Matcher matcher = pattern.matcher(lines[cmd]);
                    if (matcher.find()) {
                        String transformed = lines[cmd].substring(0, matcher.end(1));
                        traceInfo("meta-data :: trim-delete-when-empty :: PATCH: deleting '"+transformed+"'");
                        lines[cmd] = transformed;
                    }
                }
            }

            // metaTag not handled by this loop -> copy it over
            else {
                newdata.append(lines[i] + "\n");
            }
        }
        data = newdata.toString();


        //
        // MODIFY LINE OLD (old style - can't add lines)
        //
        lines = data.split("\n");
        lastif = -1;
        for (i = 0; i < lines.length - 1; i++) {
            if (lines[i].isEmpty())
                continue;
            if (lines[i].startsWith("interface "))
                lastif = i;
            String meta = lines[i].trim();
            String line = lines[i+1];
            if (meta.startsWith("! meta-data :: /ncs:devices/device{") == false)
                continue;

            String metas[] = meta.split(" :: ");
            // traceVerbose("Looking at [i="+i+"]: \n"+meta);
            //for (j = 0; j < metas.length; j++) traceVerbose("LINE METAS["+j+"]='"+metas[j]+"'");

            // Strip duplicate meta-data tags
            if (meta.equals(line)) {
                lines[i] = ""; // Trim duplicate meta-data comment
                traceVerbose("meta-data :: trimmed duplicate tag :: "+meta);
                continue;
            }

            // Warn for multiple meta-data tags
            if (line.startsWith("! meta-data :: /ncs:devices/device{")) {
                traceInfo("meta-data :: WARNING :: double tag #1 :: "+ meta);
                traceInfo("meta-data :: WARNING :: double tag #2 :: "+ line);
            }


            // add-keyword
            // ===========
            // Add 'insert' keyword if 'search' not in command line
            // metas[3] = add keyword
            // metas[4] = positive regexp
            // metas[5] = negative regexp
            // metas[6] = last word [OPTIONAL]
            // Example:
            // add 'log disable' if extended|webtype and not log set
            // tailf:meta-data "add-keyword" {
            //   tailf:meta-value "log disable :: access-list \\S+ \"(extended|webtype) .* :: .* log ::  inactive";
            // }
            else if (metas[2].startsWith("add-keyword")) {
                lines[i] = ""; // Strip meta-data comment
                if (!line.matches("^"+metas[4].trim()+"$"))
                    continue;
                if (line.matches("^"+metas[5].trim()+"$"))
                    continue;
                if (metas.length > 6 && line.endsWith(metas[6])) {
                    lines[i+1] = line.substring(0,line.length()-metas[6].length()) + " " + metas[3] + metas[6];
                } else {
                    lines[i+1] = line + " " + metas[3];
                }
                traceInfo("meta-data :: add-keyword :: PATCH: new line '"+lines[i+1]+"'");
            }

            // string-remove-quotes
            // ====================
            // metas[3] = regexp, where <STRING> is the string to look at.
            // example:
            // tailf:meta-data "string-remove-quotes" {
            //  tailf:meta-value "route-policy <STRING>";
            // }
            else if (metas[2].startsWith("string-remove-quotes")) {
                lines[i] = ""; // Strip meta-data comment
                String regexp = metas[3].replace("<STRING>", "\\\"(.*)\\\"");
                String replacement = metas[3].replace("<STRING>", "$1");
                String newline = lines[i+1].replaceFirst(regexp, replacement);
                if (lines[i+1].equals(newline) == false) {
                    lines[i+1] = newline;
                    traceInfo("meta-data :: string-remove-quotes :: PATCH: "+
                              "removed quotes on: '"+lines[i+1]+"'");
                }
            }

            // range-list-syntax
            // range-list-syntax-mode
            // ======================
            // Compact individual entries to range syntax.
            // Also supports empty mode and list delete.
            // metas[3] = entry to look for, contains <ID> and optional $i tags
            // Example:
            // tailf:meta-data "range-list-syntax" {
            //  tailf:meta-value "spanning-tree vlan <ID> $3 $4";
            // }
            else if (metas[2].startsWith("range-list-syntax")) {
                String values[] = line.trim().split(" +");
                int delete = values[0].equals("no") ? 1 : 0; // let first line device if create/delete
                boolean modeSearch = metas[2].equals("range-list-syntax-mode") && delete == 0;

                // Create line regexp and simple first match (to minimize regexp searches)
                String regexp = metas[3];
                if (delete == 1)
                    regexp = "no " + regexp;
                String first = regexp.substring(0,regexp.indexOf(" <ID>"));
                regexp = regexp.replace("<ID>", "(\\d+)");
                if (regexp.indexOf(" $") > 0) {
                    // Replace $i with value from line
                    String tokens[] = regexp.trim().split(" +");
                    for (int x = 0; x < tokens.length; x++) {
                        if (tokens[x].startsWith("$")) {
                            int index = (int)(tokens[x].charAt(1) - '0');
                            if (index + delete < values.length)
                                regexp = regexp.replace(tokens[x],values[index+delete]);
                        }
                    }
                    if (regexp.indexOf(" $") > 0) {
                        traceInfo(metas[2] + " :: ignoring '"+line+"'");
                        continue; // unresolved values, ignore non-matching line
                    }
                }

                // Find all matching entries, including this one (to extract first low/high value)
                int low = -1, high = -1;
                traceVerbose(metas[2] + " :: searching : mode="+modeSearch+" delete="+delete+" first='"+first+"' regexp='"+regexp+"'");
                for (j = i; j < lines.length - 1; j++) {
                    if (lines[j].indexOf(metas[2]) < 0)
                        continue; // non-matching meta
                    if (lines[j+1].trim().startsWith(first) == false)
                        continue; // create/delete mismatch
                    if (modeSearch && delete == 0) {
                        if (j + 2 >= lines.length)
                            break;
                        if (!(lines[j+2].trim().equals("!") || lines[j+2].trim().equals("exit")))
                            break; // entry contains submode config, can't compress this entry
                    }
                    Pattern pattern = Pattern.compile("^\\s*"+regexp+"$");
                    Matcher matcher = pattern.matcher(lines[j+1]);
                    if (!matcher.find())
                        continue; // non-matching line (mismatching $i values)
                    int index = Integer.parseInt(matcher.group(1));
                    if (low == -1) {
                        // first entry (the command line which will be modified if range found)
                        high = index;
                        low  = index;
                    } else if (index - 1 == high) {
                        // interval increased by 1
                        high++;
                        lines[j+1] = ""; // Strip command and optional mode exit
                        if (modeSearch) lines[j+2] = "";
                    } else if (index + 1 == low) {
                        // interval decreased by 1
                        low--;
                        lines[j+1] = ""; // Strip command and optional mode exit
                        if (modeSearch) lines[j+2] = "";
                    } else {
                        break; // non linear range, end compression [NOTE: possibly a continue, to find scattered entries?]
                    }
                    lines[j] = ""; // First or expanded range match -> strip meta-data comment
                }

                // Compress single entries to span, minimum 2 entries
                if (high - low > 0) {
                    traceInfo("meta-data :: range-list-syntax :: PATCH: compressed '"+lines[i+1]+"' to range="+low+"-"+high);
                    lines[i+1] = regexp.replace("(\\d+)", low + "-" + high);
                } else {
                    lines[i] = ""; // Strip meta-data comment
                }
            }

            // diff-interface-move
            // ===================
            //    A :: after|before :: B
            // Move line A before or after line B within interface boundaries
            // metas[3] = line A to move (regexp)
            // metas[4] = after|before
            // metas[5] = line B to stay
            // metas[6] = device regexp [OPTIONAL] (if no match, toggle move direction)
            // example:
            // tailf:meta-data "diff-interface-move-1" {
            //  tailf:meta-value "no ip route-cache :: before :: switchport";
            // }
            else if (metas[2].startsWith("diff-interface-move")) {
                boolean before = metas[4].equals("before");
                if (metas.length > 6 && !model.matches(".*"+metas[6]+".*")) {
                    traceVerbose("meta-data :: "+metas[2]+" :: model != "+metas[6]+" -> toggled move direction");
                    before = !before;
                }
                lines = trimDuplicateInterfaceMetaTags(lines, i, meta, before); // if before, keep first occurrence
                if (lines[i].isEmpty())
                    continue;
                lines[i] = ""; // Strip meta-data comment
                for (;;) {
                    int move = -1, stay = -1, exit = -1;

                    // First find stay and exit (note: stay may have moved since last loop)
                    for (j = lastif; j < lines.length; j++) {
                        String command = lines[j].trim();
                        if (command.matches("^"+metas[5].trim()+"$")) {
                            stay = j;
                        } else if (lines[j].equals("exit")) {
                            exit = j;
                            break;
                        }
                    }
                    if (stay == -1 || exit == -1) {
                        break;
                    }

                    // Then find best move (depends on after or before)
                    if (before) {
                        for (j = stay + 1; j < exit; j++) {
                            String command = lines[j].trim();
                            if (command.matches("^"+metas[3].trim()+"$")) {
                                move = j;
                                break;
                            }
                        }
                    } else {
                        for (j = lastif + 1; j < stay; j++) {
                            String command = lines[j].trim();
                            if (command.matches("^"+metas[3].trim()+"$")) {
                                move = j;
                                break;
                            }
                        }
                    }
                    if (move == -1) {
                        break;
                    }

                    // Move the 'move' entry by shifting lines
                    if (before == true && move > stay) {
                        traceInfo("meta-data :: "+metas[2]+" :: PATCH: "+
                                  "moved '"+lines[move]+"' before '"+lines[stay]+"'");
                        String moveLine = lines[move];
                        for (j = move; j > i; j--) {
                            lines[j] = lines[j-1];
                        }
                        lines[i] = moveLine;
                    }
                    else if (before == false && move < stay) {
                        traceInfo("meta-data :: "+metas[2]+" :: PATCH: "+
                                  "moved '"+lines[move]+"' after '"+lines[stay]+"'");
                        String moveLine = lines[move];
                        for (j = move; j < stay; j++) {
                            lines[j] = lines[j+1];
                        }
                        lines[stay] = moveLine;
                        i--; // must subtract i in order to look at next meta tag
                    }
                }
            }

            // shutdown-container-before-change
            // ===================
            // Inject shutdown and no shutdown around all changes inside container
            // metas[3] = ID (used to find exit)
            // Example:
            // tailf:cli-run-template-enter "pm-agent\n ! meta-data :: $(.ipath)
            //                               :: shutdown-container-before-change :: pm-agent\n";
            // tailf:cli-exit-command "! exit-meta-data-pm-agent";
            else if (metas[2].startsWith("shutdown-container-before-change")) {
                String exitTag = "! exit-meta-data-"+metas[3];

                // Empty container/list (no modified commands in it)
                if (lines[i+1].trim().equals(exitTag)) {
                    lines[i] = "";   // Strip meta-data comment
                    lines[i+1] = ""; // Strip meta-data-exit comment
                    continue;
                }

                // Deleted entry, do not insert shutdown
                if (lines[i-1].trim().startsWith("no ")) {
                    lines[i] = "";   // Strip meta-data comment
                    continue;
                }

                // Entry with at least one modified sub-entry
                traceInfo("meta-data :: "+metas[2]+" :: PATCH: injecting shutdown in " + lines[i-1]);
                lines[i] = lines[i].replace(meta, "shutdown");

                // Clean exit (trim extra shutdown and insert no shutdown)
                for (j = i + 1; j < lines.length; j++) {
                    if (lines[j].trim().equals(exitTag)) {
                        if (lines[j-1].trim().equals("shutdown") || lines[j-1].trim().equals("no shutdown"))
                            lines[j-1] = ""; // strip native [no ]shutdown
                        if (maapiExists(mm, toTh, metas[1]) && !maapiExists(mm, toTh, metas[1]+"/shutdown"))
                            lines[j] = lines[j].replace(exitTag, "no shutdown");
                        else
                            lines[j] = ""; // shutdown already injected first
                        break;
                    }
                }
            }
        }

        // Make single string again
        StringBuilder moddata = new StringBuilder();
        for (i = 0; i < lines.length; i++) {
            if (lines[i] != null && !lines[i].isEmpty()) {
                moddata.append(lines[i]+"\n");
            }
        }
        data = "\n" + moddata.toString() + "\n";
        //traceVerbose("\nSHOW_AFTER_META:\n"+data);
        return data;
    }
}
