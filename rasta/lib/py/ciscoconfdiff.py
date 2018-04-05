"""
A diff script that is tailored for Cisco configuration files.
    ___ _             ___           __ ___  _  __  __
   / __(_)___ __ ___ / __|___ _ _  / _|   \(_)/ _|/ _|
  | (__| (_-</ _/ _ \ (__/ _ \ ' \|  _| |) | |  _|  _|
   \___|_/__/\__\___/\___\___/_||_|_| |___/|_|_| |_|

It understands the hierarchical structure of a config based on its
indentation and assumes that the order of lines in a given section
does not matter.

Red lines are different or missing in the other config.
Orange lines do have a matching line in the other config but are
necessary to configure red lines (in other words, orange indicates
sections that contain red lines).

Blue icons are warnings about reordered lines: while the order of
line in a given section is most of the time not meaningful, there
are a few exceptions such as old IOS access-lists or IOS-XR
route-policies. You must pay attention to those corner cases.

See http://gitlab.cisco.com/cdessez/ciscoconfdiff to download the
standalone version for this script.
"""

############################################################
############################################################
##    ___ _             ___           __ ___  _  __  __   ##
##   / __(_)___ __ ___ / __|___ _ _  / _|   \(_)/ _|/ _|  ##
##  | (__| (_-</ _/ _ \ (__/ _ \ ' \|  _| |) | |  _|  _|  ##
##   \___|_/__/\__\___/\___\___/_||_|_| |___/|_|_| |_|    ##
##                                                        ##
##  Diff tool to compare Cisco-like configuration files   ##
##      Written by Cedric Dessez (cdessez@cisco.com)      ##
##                                                        ##
############################################################
############################################################

# the following line causes rasta's generate-docs.py to ignore this lib
#rasta generate-docs ignore

#from __future__ import unicode_literals
import logging
import os
import re
from distutils.version import LooseVersion
try:
    import bdblib
    from bdblib.exceptions import BDBTaskError
    MODE = 'bdb'
    BDB_SQUASH_MARGIN = 5
except ImportError:
    MODE = 'standalone'

if MODE == 'standalone':
    import webbrowser
    import sys
    import tempfile
    import argparse
    import json

from copy import deepcopy
import jinja2
import ciscoconfparse
from ciscoconfparse import CiscoConfParse, ConfigLineFactory
import htmlmin
import mimetypes

__author__ = "Cedric Dessez"
__copyright__ = "Copyright 2015, Cisco"
__credits__ = ["Cedric Dessez", "Guillaume Mulocher", "Dave Wapstra", "Patriz Meulendijks"]
__email__ = "cdessez@cisco.com"
__version__ = "1.0.1"

CISCOCONFPARSE_MINIMUM_VERSION = "1.2.39"

LOGGER = logging.getLogger(__name__)

DIFF_MATCHED = 0
DIFF_CHILD_UNMATCHED = 1
DIFF_UNMATCHED = 2
DIFF_IGNORED = -1
DIFF_PADDING = -2

DIFF_TEMPLATE = u"""
{% if add_skeleton %}
<html>
<head>
    <title>CiscoConfigDiff output</title>
    <meta charset="UTF-8">
</head>
<body>
<h1>CiscoConfigDiff output</h1>
    {% if title %}
    <h2>{{ title|escape }}</h2>
    {% endif %}
{% endif %}

<div class="diff_box">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap.min.css"
    integrity="sha384-1q8mTJOASx8j1Au+a5WDVnPi2lkFfwwEAa8hDDdjZlpLegxhjVME1fgjWPGmkzs7" crossorigin="anonymous">
    <style scoped>
div.diff_box{
    font-family: Arial;
}
div.diff_column_header_left{
    outline-color: black;
    background: rgb(255, 255, 255);
    float: left;
    height: auto;
    width: 50%;
    z-index: 1;
}
div.diff_column_header_right{
    outline-color: black;
    background: rgb(255, 255, 255);
    float: right;
    height: auto;
    width: 50%;
    z-index: 2;
}
div.diff_warning{
    background: rgb(255, 255, 255);
    color: rgb(255, 102, 0);
    float: center;
    height: auto;
    width: 90%;
}
div.diff_code{
    float: left;
}
table.diff_output {
    font-family: Courier New,Courier,monospace;
    {% if small_font %}
    font-size: small;
    {% endif %}
    table-layout: fixed;
    width: 100%;
    border: 2px solid black;
    border-collapse: collapse;
}
td.diff_line_comment{
    color: rgb(127,127,127);
}
td.diff_line_padding{
    color: rgb(210,210,210);
    background: rgb(210, 210, 210);
}
td.diff_line_matched{
}
td.diff_line_child_unmatched{
    background: rgb(255, 214, 173);
    color: rgb(255, 102, 0);
}
td.diff_line_unmatched{
    color: red;
    background: rgb(255, 204, 204);
    font-weight: bold;
}
td.diff_number{
    text-align: right;
    vertical-align: text-top;
    border-right: 1px solid black;
    border-left: 2px solid black;
    white-space: nowrap
    width: 8%;
}
.diff_reorder_warning{
    font-size: smaller;
}
td.diff_conf_line{
    width: 42%;
    overflow: hidden;
    text-overflow: ellipsis;
    vertical-align: text-top;
    white-space: nowrap;
}
td.diff_conf_line:hover {
    text-overflow: inherit;
    overflow: visible;
}
td.diff_collapse{
    color: rgb(127, 127, 127);
    border-top: 1px solid black;
    border-bottom: 1px solid black;
}
td.diff_collapse_number{
    border-left: 2px solid black;
}
td.diff_collapse_count{
    width: 42%;
}
.diff_collapse_block{
    color: blue;
    float: left;
}
.clickable{
    color: blue;
    cursor: pointer;
}
    </style>

    <script src="https://code.jquery.com/jquery-1.12.0.min.js"></script>
    <script>
$(document).ready(function(){
    /* action for [+] buttons */
    $("td.diff_collapse_number").find("a").on("click", function(){
        $(this).parent().parent().parent().children().toggle();
    });
    /* action for [-] buttons */
    $("span.diff_collapse_block").find("a").on("click", function(){
        $(this).parent().parent().parent().parent().children().toggle();
    });
    /* update count of collapsed lines */
    $("tr.diff_collapse").each(function(){
        var line_count = $(this).parent().children().length - 1;
        var warning_count = $(this).parent().find(".diff_reorder_warning").length;
        var warning = "";
        if (warning_count){
            warning = ', <strong style="color:rgb(255,102,0);">' + warning_count + ' warnings</strong>';
        }
        var content = "(..." + line_count + " lines" + warning + "...)";
        $(this).find("em").html(content);
    });
});
    </script>

    <div class="diff_header">
        <div class="diff_column_header_left">
            <h3>{{name1}}</h3>
            <h4>Raw diff (parts not in the other config)</h4>
            <p><textarea>{{ to_copy1|escape }}</textarea></p>
        </div>
        <div class="diff_column_header_right">
            <h3>{{name2}}</h3>
            <h4>Raw diff (parts not in the other config)</h4>
            <p><textarea>{{ to_copy2|escape }}</textarea></p>
        </div>
        {% if warning %}
        <div class="diff_warning">
            {{ warning }}
        </div>
        {% endif %}
    </div>
   <div class="diff_code">
   <table class="diff_output">

{% macro line_class(line) -%}
    {% if line.diff_status == DIFF_PADDING %}diff_line_padding{% endif %}
    {% if line.diff_status == DIFF_MATCHED %}diff_line_matched{% endif %}
    {% if line.diff_status == DIFF_CHILD_UNMATCHED %}diff_line_child_unmatched{% endif %}
    {% if line.diff_status == DIFF_UNMATCHED %}diff_line_unmatched{% endif %}
    {% if line.diff_status == DIFF_IGNORED %}diff_line_comment{% endif %}
{%- endmacro %}
{% macro number_display(line, collapse, collapse_prev) -%}
    {% if collapse %}
        {% if not collapse_prev %}
        <span class="diff_collapse_block"><a class="clickable">[-]</a></span>
        {% else %}
        <span class="diff_collapse_block">&nbsp;|</span>
        {% endif %}
    {% endif %}
    {% if line.reordered %}
    <span class="label label-info"><span class="glyphicon glyphicon-random diff_reorder_warning"></span></span>
    {% endif %}
    {% if line.linenum is not none %}{{ line.linenum + 1 }}.{% else %}-{% endif %}
{%- endmacro %}
{% macro config_display(line) -%}
    {{ line.text|replace('\t','    ')|replace(' ','&nbsp;') }}
{%- endmacro %}
{% set reorder_warning = "Warning: this line or section was reordered" %}

   <tbody>
    {% set collapse = false %}
    {% for line1, line2 in lines %}
       {% set collapse_prev = collapse %}
       {% set collapse = line1.squashable and line2.squashable %}
       {% if collapse_prev != collapse %}
            </tbody><tbody>
            {% if collapse %}
            <tr class="diff_collapse" title="Lines collapsed for better readability.">
                <td class="diff_collapse diff_collapse_number">
                    <a class="clickable" title="Expand collapsed lines">[+]</a>
                </td>
                <td class="diff_collapse diff_collapse_count"><em>(...)</em></td>
                <td class="diff_collapse diff_collapse_number">
                    <a class="clickable" title="Expand collapsed lines">[+]</a>
                </td>
                <td class="diff_collapse diff_collapse_count"><em>(...)</em></td>
             </tr>
            {% endif %}
       {% endif %}
        <tr {% if collapse %}style="display:none;"{% endif %}>
            <td class="{{ line_class(line1)|trim }} diff_number"
                {% if line1.reordered %}title="{{ reorder_warning }}"{% endif %}>
                {{ number_display(line1, collapse, collapse_prev) }}
            </td>
            <td class="{{ line_class(line1)|trim }} diff_conf_line">
                {{ config_display(line1) }}
            </td>
            <td class="{{ line_class(line2)|trim }} diff_number"
                {% if line2.reordered %}title="{{ reorder_warning }}"{% endif %}>
                {{ number_display(line2, collapse, collapse_prev) }}
            </td>
            <td class="{{ line_class(line2)|trim }} diff_conf_line">
                {{ config_display(line2) }}
            </td>
        </tr>
    {% endfor %}
   </tbody>
   </table>
   </div>
</div>

{% if add_skeleton %}
</body>
</html>
{% endif %}
"""

REORDER_WARNING = """<p><strong>WARNING:</strong> Bear in mind that the below lines have been reordered for more
convenience while comparing the two configs, but validity of the result to directly configure a device is not
guaranteed. Please refer to the original line numbers in the first column to see how the lines were reordered.</p>"""

class MyIter(object):
    """
    More convenient iterator object (handles exceptions)
    """
    def __init__(self, iterable):
        self.it = iter(iterable)
        self.val = None
        self.overwritten = False
        self.saved_val = None

    def next(self):
        """Returns the next element in the MyIter object if any"""
        if self.overwritten:
            self.val = self.saved_val
            self.saved_val = None
            self.overwritten = False
            return self.val
        try:
            self.val = next(self.it)
        except StopIteration:
            self.val = None
        return self.val

    def overwrite(self, val):
        """Overwrites the current value of the MyIter object"""
        self.saved_val = self.val
        self.overwritten = True
        self.val = val


class TwoStatePointer(object):
    """
    An object to abstract two identical roles
    Works like a switch.
    """
    def __init__(self, obj0, obj1):
        self.obj0 = obj0
        self.obj1 = obj1
        self.point_to_0 = True

    def primary(self):
        """Returns the current primary object depending on the pointer"""
        return self.obj0 if self.point_to_0 else self.obj1

    def secondary(self):
        """Returns the current secondary object depending on the pointer"""
        return self.obj1 if self.point_to_0 else self.obj0

    def swap(self):
        """Changes the pointer from object 0 to object 1 - swaping primary and secondary"""
        self.point_to_0 = not self.point_to_0


class TwoConfigPointer(TwoStatePointer):
    """
    A child class of TwoStatePointer where the two objects have to be
    MyIter objects
    TODO add check
    """
    def __init__(self, it0, it1, tolerance=100):
        super(TwoConfigPointer, self).__init__(it0, it1)
        self.tolerance = tolerance

    def primary_next(self):
        """Changes to the next value in the current primary iterator"""
        self.primary().next()

    def secondary_next(self):
        """Changes to the next value in the current secondary iterator"""
        self.secondary().next()

    def both_next(self):
        """Changes to the next values in both iterators"""
        self.primary().next()
        self.secondary().next()

    def primary_val(self):
        """Returns the current value of the current primary iterator"""
        return self.primary().val

    def secondary_val(self):
        """Returns the current value of the current secondary iterator"""
        return self.secondary().val

    def primary_prepend_distance(self):
        """
        compute the distance between the matching line number of the current line of the primary iterator and the line
        number of the current line of the secondary iterator.
        """
        return self.primary().val.matching_line.linenum - self.secondary().val.linenum

    def secondary_prepend_distance(self):
        """
        compute the distance between the matching line number of the current line of the secondary iterator and the
        line number of the current line of the primaryy iterator.
        """
        return self.secondary().val.matching_line.linenum - self.primary().val.linenum

    def _is_prependable(self, dist):
        """
        check that the distance is strictly positive and if the tolerance value is not 0 that the distance is
        inferior to the tolerance value.
        """
        return (dist > 0 and dist < self.tolerance) if self.tolerance else dist > 0

    def primary_is_prependable(self):
        """Returns a boolean to tell if the primary operator is prependable"""
        return self._is_prependable(self.primary_prepend_distance())

    def secondary_is_prependable(self):
        """Returns a boolean to tell if the secondary iterator is prependable"""
        return self._is_prependable(self.secondary_prepend_distance())


class CiscoConfDiff(object):
    """CiscoConfDiff Object Class"""

    def __init__(self, conf1, conf2, syntax="ios"):
        """
        Constructor
        :param conf1: First configuration
        :param conf2: Second configuration
        """
        self.conf1 = conf1
        self.conf2 = conf2
        self.title = ''
        self.conf1_name = 'Configuration 1'
        self.conf2_name = 'Configuration 2'
        if syntax == 'junos':
            comment = "#!"
        else:
            comment = "!"
        self.parse1 = CiscoConfParse(conf1.split('\n'), syntax=syntax, comment=comment)
        self.parse2 = CiscoConfParse(conf2.split('\n'), syntax=syntax, comment=comment)
        self._detach_comment_lines()
        self._fix_closing_lines()
        self.aligned = False
        self._prepare_parse_objs()
        # for display purposes, creation of a padding line:
        self.padding_line = ConfigLineFactory('!')
        self.padding_line.diff_status = DIFF_PADDING
        self.padding_line.linenum = None
        self.padding_line.squashable = False
        self.padding_line.reordered = False
        # to store raw flat output of the diff, None until processed
        self.raw_diff_1 = None
        self.raw_diff_2 = None


    def _fix_closing_lines(self):
        """
        Patch the wrong behaviour that takes lines like 'end-policy' one level too high in the hierarchy
        """
        for c in (self.parse1, self.parse2):
            prev_line = None
            for o in c.ConfigObjs:
                if not o.is_config_line:
                    o.is_closing_line = False
                    continue
                if re.match(r"^(?:(?:end)|(?:exit))-.+$", o.text.strip()) and prev_line:
                    new_parent = prev_line
                    while new_parent.parent is not new_parent and new_parent.parent is not o.parent:
                        new_parent = new_parent.parent
                    new_parent.children.append(o)
                    if o in o.parent.children:
                        o.parent.children.remove(o)
                    o.parent = new_parent
                    o.is_closing_line = True
                else:
                    o.is_closing_line = False
                prev_line = o


    def _detach_comment_lines(self):
        """
        Detach all comments that CiscoConfParse has attached in the tree (CiscoConfParse does that partially and poorly)
        They will be reattached later if need be
        """
        for c in (self.parse1, self.parse2):
            for o in c.ConfigObjs:
                # if o is a comment and part of the tree
                if not o.is_config_line and o.parent is not o:
                    o.parent.children.remove(o)
                    o.parent = o


    def _reattach_comment_lines(self):
        """
        Reintroduce comments in the tree structure for proper reordering (comments need to be reordered with the block
        they belong to)
        """
        def is_end_of_block(line):
            """ Return True if `line` is the last children of its parent """
            return line is line.parent or line is line.parent.children[-1]

        for c in (self.parse1, self.parse2):
            prev_line = None
            end_of_block = True
            for o in c.ConfigObjs:
                if o.is_config_line:
                    end_of_block = is_end_of_block(o)
                elif not prev_line:
                    pass
                elif not end_of_block:
                    prev_line.children.append(o)
                    o.parent = prev_line
                else:
                    while prev_line.parent is not prev_line and \
                            not (prev_line.indent <= o.indent and prev_line.is_config_line):
                        prev_line = prev_line.parent
                    prev_line.children.append(o)
                    o.parent = prev_line
                prev_line = o


    def _prepare_parse_objs(self):
        """
        Add attributes to the CiscoConfParse structure for future purposes
        """
        for c in (self.parse1, self.parse2):
            c.top_level_lines = []
            for o in c.ConfigObjs:
                o.prepend_empty_lines = 0 # for visual alignment processing
                o.already_displayed = False # for visual alignment processing
                o.matching_line = None
                o.reordered = False # to keep track of lines reordered for display purposes
                o.squashable = False
                if not o.is_config_line or (o.text.strip() and o.text.strip()[0] == '!'):
                    o.diff_status = DIFF_IGNORED
                    continue
                o.diff_status = DIFF_UNMATCHED
                if o.parent is o:
                    c.top_level_lines.append(o)


    @staticmethod
    def _line_key(line):
        return line.text.strip()


    @staticmethod
    def _sort_lines(lines):
        """
        Returns a ordered list of lines and removes comments
        :param conf_obj: a list of lines
        :return: a list
        """
        return sorted([l for l in lines if l.diff_status != DIFF_IGNORED], key=CiscoConfDiff._line_key)


    def diff(self):
        """
        Overall diff function
        :return:
        """
        CiscoConfDiff._rec_diff(self.parse1.top_level_lines, self.parse2.top_level_lines)

        for c in (self.parse1, self.parse2):
            for o in c.ConfigObjs:
                if o.is_closing_line and o.diff_status == DIFF_MATCHED \
                        and o.parent.diff_status == DIFF_CHILD_UNMATCHED:
                    o.diff_status = DIFF_CHILD_UNMATCHED


    @staticmethod
    def _rec_diff(lines1, lines2):
        """
        Recursive diff that compares two lists of children
        """
        # default values for the statuses - status1 for lines1 and status2 for lines2 - DIFF_MATCHED means that the
        # lines are identical
        status1 = DIFF_MATCHED
        status2 = DIFF_MATCHED
        # initialise the iterators
        it1 = MyIter(CiscoConfDiff._sort_lines(lines1))
        it2 = MyIter(CiscoConfDiff._sort_lines(lines2))
        it1.next()
        it2.next()
        key = CiscoConfDiff._line_key

        while it1.val or it2.val:
            if not it1.val:
                status2 = DIFF_CHILD_UNMATCHED
                break
            if not it2.val:
                status1 = DIFF_CHILD_UNMATCHED
                break
            if key(it1.val) == key(it2.val):
                it1.val.matching_line = it2.val
                it2.val.matching_line = it1.val
                it1.val.diff_status, it2.val.diff_status = CiscoConfDiff._rec_diff(it1.val.children, it2.val.children)
                if it1.val.diff_status == DIFF_CHILD_UNMATCHED:
                    status1 = DIFF_CHILD_UNMATCHED
                if it2.val.diff_status == DIFF_CHILD_UNMATCHED:
                    status2 = DIFF_CHILD_UNMATCHED
                it1.next()
                it2.next()
            elif key(it1.val) > key(it2.val):
            # means that the line in conf1 is bigger than line in conf2 (relatively to the sorting algorithm) = hence
            # the line in conf2 is unmatched hence updating the diff_status of the line and returning that the child
            # is unmatched to the parent calling function.
                it2.val.diff_status = DIFF_UNMATCHED
                status2 = DIFF_CHILD_UNMATCHED
                it2.next()
            else: # if key(it1.val) < key(it2.val)
                it1.val.diff_status = DIFF_UNMATCHED
                status1 = DIFF_CHILD_UNMATCHED
                it1.next()
        # possible outputs:
        #     > DIFF_MATCH, DIFF_MATCH
        #     > DIFF MATCH, DIFF_CHILD_UNMATCHED
        #     > DIFF_CHILD_UNMATCHED, DIFF_MATCH
        return status1, status2

    @staticmethod
    def _matched(line):
        return line.diff_status in (DIFF_MATCHED, DIFF_CHILD_UNMATCHED)

    def align_lines(self, tolerance=100):
        """
        Calculate how many empty lines to insert and where to best align the two configs (no reordering)
        :param tolerance: maximum number of empty lines to insert, if set to 0, there is no maximum
        """
        # initialise the parameters
        it1 = MyIter(self.parse1.ConfigObjs)
        it2 = MyIter(self.parse2.ConfigObjs)
        it1.next()
        it2.next()
        p = TwoConfigPointer(it1, it2, tolerance)

        while p.primary_val() and p.secondary_val():
            if not CiscoConfDiff._matched(p.primary_val()):
                if CiscoConfDiff._matched(p.secondary_val()):
                    p.swap()
                    continue
                # secondary was not matched
                p.both_next()
                continue
            # primary was matched
            if p.primary_prepend_distance() == 0: # the 2 lines are each other's match
                p.both_next()
                continue
            # the 2 lines are not each other's match
            if p.primary_is_prependable():
                if CiscoConfDiff._matched(p.secondary_val()) and p.secondary_is_prependable()\
                        and p.primary_prepend_distance() > p.secondary_prepend_distance():
                    p.swap()
                    continue
                p.primary_val().prepend_empty_lines += 1
                p.secondary_next()
            else: # primary not prependable
                if CiscoConfDiff._matched(p.secondary_val()) and p.secondary_is_prependable():
                    p.swap()
                    continue
                p.both_next()
                continue

        self.aligned = True


    def align_reorder_lines(self):
        """
        Tries to best align the two configs for a more convenient visual output by inserting empty lines and
        reordering lines / blocks of lines
        :return: lines1, lines2 => two lists of config lines
        """

        def rec_align(lines1, lines2, out_lines1, out_lines2):
            """Recursive function to align the lines hierarchically"""
            # initialise the parameters
            it1 = MyIter(lines1)
            it2 = MyIter(lines2)
            it1.next()
            it2.next()
            p = TwoConfigPointer(it1, it2)
            out_p = TwoStatePointer(out_lines1, out_lines2)

            def update_result(primary_already_displayed,\
                               secondary_already_displayed, \
                                out_primary_append, out_secondary_append):
                """Support function to update the status of the values display status and append to the current
                output"""
                if p.primary_val():
                    p.primary_val().already_displayed = primary_already_displayed
                if p.secondary_val():
                    p.secondary_val().already_displayed = secondary_already_displayed
                out_p.primary().append(out_primary_append)
                out_p.secondary().append(out_secondary_append)

            while p.primary_val() or p.secondary_val():
                if not p.primary_val():
                    if not p.secondary_val():
                        break
                    p.swap()
                    out_p.swap()
                    continue
                if p.primary_val().already_displayed:
                    p.primary_next()
                    continue
                if not p.secondary_val():
                    update_result(True, False, p.primary_val(), deepcopy(self.padding_line))
                    rec_align(p.primary_val().children, [], out_p.primary(), out_p.secondary())
                    p.both_next()
                    continue
                if p.secondary_val().already_displayed:
                    p.secondary_next()
                    continue

                if not CiscoConfDiff._matched(p.primary_val()):
                    if CiscoConfDiff._matched(p.secondary_val()):
                        update_result(True, False, p.primary_val(), deepcopy(self.padding_line))
                        rec_align(p.primary_val().children, [], out_p.primary(), out_p.secondary())
                        p.primary_next()
                        continue
                    # secondary was not matched either
                    #TODO: optimise
                    children_length_diff = len(p.primary_val().all_children) - len(p.secondary_val().all_children)
                    if children_length_diff < 0:
                        p.swap()
                        out_p.swap()
                        continue
                    update_result(True, True, p.primary_val(), p.secondary_val())
                    out_p.primary().extend(p.primary_val().all_children)
                    out_p.secondary().extend(p.secondary_val().all_children)
                    out_p.secondary().extend([deepcopy(self.padding_line) for _ in range(children_length_diff)])
                    p.both_next()
                    continue

                # primary was matched
                if not CiscoConfDiff._matched(p.secondary_val()):
                    p.swap()
                    out_p.swap()
                    continue
                if p.primary_prepend_distance() == 0: # aligned pair
                    update_result(True, True, p.primary_val(), p.secondary_val())
                    rec_align(p.primary_val().children, p.secondary_val().children, out_p.primary(), out_p.secondary())
                    p.both_next()
                    continue
                # the 2 lines are not each other's match: go get the primary's matching line and reorder it
                if p.primary_val().matching_line and not p.primary_val().matching_line.already_displayed:
                    p.secondary().overwrite(p.primary_val().matching_line)
                    p.secondary_val().reordered = True
                    continue

                # Default output, should not arrive there, data may be corrupted
                LOGGER.error("Data seems to be corrupted, there might be errors with the visual layout")
                update_result(True, True, p.primary_val(), p.secondary_val())
                rec_align(p.primary_val().children, p.secondary_val().children, out_p.primary(), out_p.secondary())
                p.both_next()

        self._reattach_comment_lines()
        output1, output2 = [], []
        top_level_lines1 = [o for o in self.parse1.ConfigObjs if o.parent == o]
        top_level_lines2 = [o for o in self.parse2.ConfigObjs if o.parent == o]
        rec_align(top_level_lines1, top_level_lines2, output1, output2)

        self.aligned = True
        return output1, output2

    @staticmethod
    def _padded_line_list(lines, padding_line):
        """
        Returns a list of lines, with empty lines
        :param lines: a list of config lines
        """

        res = []
        for line in lines:
            for _ in range(line.prepend_empty_lines):
                res.append(deepcopy(padding_line))
            res.append(line)
        return res


    @staticmethod
    def print_diff(parse_obj):
        """
        Print lines of the parse_obj that have a DIFF_UNMATCHED or DIFF_CHILD_UNMATCHED status
        :param parse: a CiscoConfParse obj that has been used by CiscoConfDiff.diff()
        :return: a string
        """
        s = ''
        for o in parse_obj.ConfigObjs:
            if o.diff_status > 0:
                s += '{0}\t{1}\n'.format(o.linenum+1, o.text)
        return s


    def print_cli(self):
        """
        Print a basic ASCII output of the result of the diff
        :return: a string
        """
        s = u"Output of the CiscoConfDiff script for {0} and {1}\n\n".format(self.conf1_name, self.conf2_name)
        s += "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n"
        s += CiscoConfDiff.print_diff(self.parse1)
        s += "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n"
        s += CiscoConfDiff.print_diff(self.parse2)
        return s


    def _assemble_raw_diff(self):
        """
        Creates and saves raw diff (no formatting)
        """
        if not self.raw_diff_1:
            self.raw_diff_1 = '\n'.join([l.text for l in self.parse1.ConfigObjs
                                         if l.diff_status == DIFF_CHILD_UNMATCHED or l.diff_status == DIFF_UNMATCHED])
        if not self.raw_diff_2:
            self.raw_diff_2 = '\n'.join([l.text for l in self.parse2.ConfigObjs
                                         if l.diff_status == DIFF_CHILD_UNMATCHED or l.diff_status == DIFF_UNMATCHED])


    def get_side_by_side_lines(self, lines1=None, lines2=None):
        """
        Returns a list of couples of lines. Each couple represents config line printed side-by-side.
        :param lines1: ordered list of lines to print for conf1, if None, will be built with padded_line_list()
        :param lines2: ordered list of lines to print for conf2, if None, will be built with padded_line_list()
        :return: a list of couples of line objects
        """
        if lines1 is None:
            lines1 = CiscoConfDiff._padded_line_list(self.parse1.ConfigObjs, self.padding_line)
        if lines2 is None:
            lines2 = CiscoConfDiff._padded_line_list(self.parse2.ConfigObjs, self.padding_line)
        nlines = max(len(lines1), len(lines2))
        return  [(lines1[i] if i < len(lines1) else deepcopy(self.padding_line), \
                  lines2[i] if i < len(lines2) else deepcopy(self.padding_line)) \
                  for i in range(nlines)]


    @staticmethod
    def update_squashable_lines(side_by_side_lines, margin=5):
        """
        Update the `squashable` attribute of the side_by_side_lines.
        If the line is matched or ignored, and is more than `margin` lines away from lines that are unmatched, it is
        considered squashable, i.e. elligible to be cut off for display purposes
        :param side_by_side_lines: list of couples of lines to be printed side by side
        :param margin: the number of lines around a region of interest that cannot be squashed
        """
        def is_line_of_interest(line):
            """ Defines whether a line is important for the diff """
            return line[0].diff_status in (DIFF_UNMATCHED, DIFF_CHILD_UNMATCHED) \
                    or line[1].diff_status in (DIFF_UNMATCHED, DIFF_CHILD_UNMATCHED)

        def update_squashable(line, value):
            """ Update field in both lines """
            line[0].squashable = value
            line[1].squashable = value

        def is_squashable(line):
            """ return a boolean to assert if the line is squashable or not"""
            return line[0].squashable and line[1].squashable

        def remove_tiny_squashable_zones(lines, threshold=4):
            """
            Refines squashable zones by removing squashable zones that are too tiny to be worth collapsing.
            For example, it does not improve user-friendliness to collapse only 1 or 2 lines:
            better display them normally.
            :param threshold: minimum size of squashable area (number of lines)
            """
            start_index = None # when in a squashable zone, stores the index of the first line
                               # None otherwise
            for i in range(len(lines)):
                if not start_index and is_squashable(lines[i]):
                    start_index = i
                    continue
                if start_index and not is_squashable(lines[i]):
                    if i - start_index < threshold:
                        for j in range(start_index, i):
                            update_squashable(lines[j], False)
                    start_index = None

        # for each line, stores distance to closest region of interest
        distance_to_roi = [None for _ in range(len(side_by_side_lines))]

        # first traversal forward from the beginning
        cur_distance = margin + 1
        for i in range(len(side_by_side_lines)):
            if is_line_of_interest(side_by_side_lines[i]):
                cur_distance = 0
            else:
                cur_distance += 1
            distance_to_roi[i] = cur_distance
        # second traversal backwards from the end, also updates the `squashable` field
        cur_distance = margin + 1
        for i in range(len(side_by_side_lines)):
            if is_line_of_interest(side_by_side_lines[-i-1]):
                cur_distance = 0
            else:
                cur_distance += 1
            distance_to_roi[-i-1] = min(distance_to_roi[-i-1], cur_distance)
            update_squashable(line=side_by_side_lines[-i-1], value=(distance_to_roi[-i-1] >= margin))

        remove_tiny_squashable_zones(side_by_side_lines)


    def print_html(self, side_by_side_lines=None, print_link2match=False, add_skeleton=False, warning=None):
        """
        Gives a nicely formatted HTML output (version base on a Jinja2 template)
        :param side_by_side_lines: list of couples of lines to be printed side by side.
                                   If None, will be built with padded_line_list() and get_side_by_side_lines()
        :param print_link2match: boolean, whether to generate a hyperlink to the matching line
        :param add_skeleton: if True, add an HTML skeleton around the diff box (standalone HTML page)
        :param warning: if you want to print a warning message (HTML accepted)
        :return: the HTML code
        """
        self._assemble_raw_diff()
        template = jinja2.Template(DIFF_TEMPLATE)
        if not side_by_side_lines:
            side_by_side_lines = self.get_side_by_side_lines()
        return template.render(
            title=self.title,
            name1=self.conf1_name,
            name2=self.conf2_name,
            to_copy1=self.raw_diff_1,
            to_copy2=self.raw_diff_2,
            lines=side_by_side_lines,
            print_link2match=print_link2match,
            add_skeleton=add_skeleton,
            warning=warning,
            DIFF_PADDING=DIFF_PADDING, DIFF_MATCHED=DIFF_MATCHED, DIFF_CHILD_UNMATCHED=DIFF_CHILD_UNMATCHED,
            DIFF_UNMATCHED=DIFF_UNMATCHED, DIFF_IGNORED=DIFF_IGNORED
        )


    def to_external_output_format(self, side_by_side_lines=None):
        """
        Flattens and prunes the data to a Python structures that contains only primitive types, dictionaries and lists.
        Allows to easily export the data to JSON or XML.
        :param side_by_side_lines: list of couples of lines to be printed side by side.
                                   If None, will be built with padded_line_list() and get_side_by_side_lines()
        :return: a Python dict
        """
        keyword_matching = {
            DIFF_MATCHED: 'matched',
            DIFF_CHILD_UNMATCHED: 'child_unmatched',
            DIFF_UNMATCHED: 'unmatched',
            DIFF_IGNORED: 'comment',
            DIFF_PADDING: 'padding'
        }
        def flatten_line_object(line):
            """Given a config line object, create a dict that contains minimal information"""
            return {
                "text": line.text, \
                "lineNumber": line.linenum, \
                "matchingLineNumber": line.matching_line.linenum \
                                            if hasattr(line, "matching_line") and line.matching_line else None, \
                "diffStatus": keyword_matching[line.diff_status], \
                "reordered": line.reordered \
            }

        self._assemble_raw_diff()
        if not side_by_side_lines:
            side_by_side_lines = self.get_side_by_side_lines()

        return {
            "title": self.title,
            "name1": self.conf1_name,
            "name2": self.conf2_name,
            "rawDiff1": self.raw_diff_1,
            "rawDiff2": self.raw_diff_2,
            "sideBySideLines": [
                {"line1": flatten_line_object(line1), "line2": flatten_line_object(line2), \
                 "collapse": line1.squashable and line2.squashable}
                for line1, line2 in side_by_side_lines
            ]
        }

class InvalidFileException(Exception):
    """ Custom Exception to raise when a file is not valid as an input for the program"""

    def __init__(self, message, errors=None):
        super(InvalidFileException, self).__init__(message)
        self.errors = errors

class InvalidEncodingException(InvalidFileException):
    """ Custom Exception to raise when a file does not have a Valid Encoding for the program"""

    def __init__(self, message, errors=None):
        super(InvalidEncodingException, self).__init__(message)
        self.errors = errors


def read_and_decode_file(filepath):
    """
    Get an input file, check the extension and attempt to decode it.
    Indeed Cisco configuration are supposed to be utf-8, but other types are tried anyway in a best effort fashion.
    :param filepath: path to the file
    :return: None if no encoding manages to decode the file without error.
    """
    # Check extension to rule out all file that are not text files
    # If the extension says that it is not a text file, it will be refused
    # If it cannot be guessed (extension unknown or no extension), we proceed further with trying to open it
    mimetype = mimetypes.guess_type(filepath)[0]
    if mimetype and not mimetype.startswith('text/'):
        raise InvalidFileException('Invalid type of file for {}'.format(filepath))

    # Try different encodings
    valid_encodings = [
        "utf_8",
        # following encodings are tried in a best effort fashion
        # their order has been blindly copied blindly from another script
        # TODO: optimise order and maybe do proper charset detection (chardet package?)
        "euc_kr",
        "euc_jp",
        "iso8859_2",
        "latin_1",
        "cp1251",
        "greek8",
        "shift_jis",
        "cp1252",
        "iso_ir_138",
        "cp1256",
        "iso8859_15",
        "iso8859_9",
        "cp1250",
        "cp1254",
        "big5",
        "ascii",
        "utf_16",
        "utf_32",
    ]
    with open(filepath) as fd:
        text = fd.read()
        for enc in valid_encodings:
            try:
                res = text.decode(enc)
                LOGGER.debug(u"Successfully decoded file {} with encoding {}".format(filepath, enc))
                return res
            except UnicodeDecodeError as e:
                LOGGER.debug(u"read_and_decode_file: {} {}".format(str(e), filepath))
                continue
        LOGGER.error(u"read_and_decode_file: NO ENCODING FOUND for {}".format(filepath))
        # Raising the UnicodeDecodeError again with generic parameters to be caught by main/BDB task
        raise InvalidEncodingException('No valid encoding found to decode {}'.format(filepath))


class MinimumVersionException(Exception):
    """Custom Exception to raise when minimum version requirements are not reached for imported modules"""

    def __init__(self, message, errors=None):
        super(MinimumVersionException, self).__init__(message)
        self.errors = errors


def check_ciscoconfparse_version():
    """
    Check the version of the imported CiscoConfParse module and raise a MinimumVersionException
    if the minimum is not met.
    """
    ccp_version = ciscoconfparse.version.__version__
    if LooseVersion(ccp_version) < LooseVersion(CISCOCONFPARSE_MINIMUM_VERSION):
        raise MinimumVersionException("CiscoConfParse minimum version is {0} - you are using {1}"\
                .format(CISCOCONFPARSE_MINIMUM_VERSION, ccp_version))
    else:
        LOGGER.debug("CiscoConfParse minimum version is {} - you are running {}"
                    .format(CISCOCONFPARSE_MINIMUM_VERSION, ccp_version))


if MODE == 'standalone':
# in case the module is not executed from BDB but as a standalone script

    def set_log_level(log_level):
        """ set the log level for LOGGER"""
        numeric_level = getattr(logging, log_level.upper(), None)
        if not isinstance(numeric_level, int):
            raise ValueError('Invalid log level: {}'.format(log_level))
        LOGGER.setLevel(numeric_level)

    # pylint: disable= E1101
    def main(**kwargs):
        """Entry point in standalone mode"""
        logging.basicConfig(stream=sys.stderr, level=logging.INFO, format="[%(levelname)8s]:  %(message)s")

        try:
            check_ciscoconfparse_version()
        except MinimumVersionException as e:
            LOGGER.critical(e.message)
            exit(1)

        if kwargs:
            args = kwargs
        else:
            parser = argparse.ArgumentParser(prog='diff')
            parser.add_argument('config1')
            parser.add_argument('config2')
            parser.add_argument('-w', '--web', action='store_true', help='Open your default web browser '
                                                                         'to display the result of the diff. '
                                                                         'If not set the output is in the terminal')
            parser.add_argument('-j', '--json-file', required=False, metavar='PATH', default=None,
                                help="If specified, the output will also be saved in the JSON format at the specified "
                                     "path.")
            parser.add_argument('-a', '--align', action='store_true', help='For the web output, try to align the files '
                                                                           '(not always possible, best effort '
                                                                           'approach)')
            parser.add_argument('-r', '--reorder', action='store_true',
                                help='For the web output, align and reorder the lines for more convenient comparison'
                                     '(original line number will be displayed anyways)')
            parser.add_argument('--align-tolerance', required=False, metavar='NUMBER', type=int, default=100,
                                help="Max number of empty lines accepted while trying to align "
                                     "the two files. If 0, there is no maximum. Default value: 100")
            parser.add_argument('-s', '--squash', action='store_true',
                                help='For the reordered and aligned outputs, determines whether large matching regions'
                                     ' can be squashed from the display for more readability')
            parser.add_argument('-m', '--squash-margin', required=False, metavar='MARGIN', type=int, default=5,
                                help="When --squash is given, determines number of matching lines to display around "
                                     "regions of interest. Default value: 5.")
            parser.add_argument('--syntax', required=False, metavar='SYNTAX', type=str, default='ios',
                                help="set config syntax, one of ios, asa, nxos, junos. default is ios")
            parser.add_argument('--log-level', required=False, metavar='LOG-LEVEL', type=str, default='INFO',
                                choices=['DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL'],
                                help="When --log-level is given, determines the level of logs displayed in the console "
                                     "Default value: WARNING")
            args = parser.parse_args()

        set_log_level(args.log_level)

        safe_conf1_name = args.config1.decode('utf-8')
        safe_conf2_name = args.config2.decode('utf-8')

        try:
            decoded_content_1 = read_and_decode_file(safe_conf1_name)
        except InvalidFileException as e:
            LOGGER.error(e.message)
            exit(1)
        try:
            decoded_content_2 = read_and_decode_file(safe_conf2_name)
        except InvalidFileException as e:
            LOGGER.error(e.message)
            exit(1)

        diff = CiscoConfDiff(decoded_content_1, decoded_content_2, args.syntax)
        diff.conf1_name = os.path.basename(safe_conf1_name)
        diff.conf2_name = os.path.basename(safe_conf2_name)

        diff.diff()

        if not args.web and not args.json_file:  # output in the standard output
            print(diff.print_cli())
            return

        lines1, lines2 = None, None
        if args.reorder:
            lines1, lines2 = diff.align_reorder_lines()
        elif args.align:
            diff.align_lines(args.align_tolerance)
        side_by_side_lines = diff.get_side_by_side_lines(lines1, lines2)

        if args.squash:
            CiscoConfDiff.update_squashable_lines(side_by_side_lines, args.squash_margin)

        if args.json_file:
            data = diff.to_external_output_format(side_by_side_lines)
            json_data = json.dumps(data)
            with open(args.json_file, 'w') as jf:
                jf.write(json_data)

        if args.web:
            html = diff.print_html(side_by_side_lines, \
                                   print_link2match=not args.reorder, \
                                   add_skeleton=True, \
                                   warning=(REORDER_WARNING if args.reorder else None))
            html = htmlmin.minify(html, remove_empty_space=True)
            temp_f = tempfile.NamedTemporaryFile(delete=False, suffix='.html')
            temp_f.write(html.encode('utf8'))
            temp_f.close()
            url = "file:///{0}".format(temp_f.name)
            webbrowser.open_new_tab(url)
    #pylint: enable= E1101

    if __name__ == '__main__':
        main()


if MODE == 'bdb':
# if the module is executed in BDB

    def task(env, file1=None, file2=None, config1=None, config2=None, align=True, reorder=True, squash_lines=True, \
             out_type='html'):
        """
        BDB task function: entry point of the application when run in BDB.
        """
        LOGGER.setLevel(logging.INFO)

        def error(msg):
            """Print an error message to BDB output"""
            out = bdblib.TaskResult()
            out.append(bdblib.HTML("<p><font color=\"red\">{}</font></p>".format(msg)))
            return out

        try:
            check_ciscoconfparse_version()
        except MinimumVersionException as e:
            return error(e.message)

        if file1:
            file1 = file1.encode('utf-8')
            if not os.path.exists(file1):
                return error("Parameter 'file1' is not a valid path")
            try:
                config1 = read_and_decode_file(file1.decode('utf-8'))
            except InvalidFileException as e:
                raise BDBTaskError(e)
        if file2:
            file2 = file2.encode('utf-8')
            if not os.path.exists(file2):
                return error("Parameter 'file2' is not a valid path")
            try:
                config2 = read_and_decode_file(file2.decode('utf-8'))
            except InvalidFileException as e:
                raise BDBTaskError(e)

        if not config1 or not config2:
            return error("Input configuration(s) missing (2 configs required)")
        diff = CiscoConfDiff(config1, config2)
        diff.conf1_name = "First config"
        diff.conf2_name = "Second config"
        diff.diff()
        lines1, lines2 = None, None
        if reorder:
            lines1, lines2 = diff.align_reorder_lines()
        elif align:
            diff.align_lines(0)
        side_by_side_lines = diff.get_side_by_side_lines(lines1, lines2)

        if squash_lines:
            CiscoConfDiff.update_squashable_lines(side_by_side_lines, BDB_SQUASH_MARGIN)

        out = bdblib.TaskResult()
        if out_type in ('html', 'all'):
            html = diff.print_html(side_by_side_lines, \
                                   warning=(REORDER_WARNING if reorder else None))
            html = htmlmin.minify(html, remove_empty_space=True)
            out.append(bdblib.HTML(html))
        if out_type in ('json', 'all'):
            json_output = diff.to_external_output_format(side_by_side_lines)
            out.append(json_output, u'json', render=False)
        return out
