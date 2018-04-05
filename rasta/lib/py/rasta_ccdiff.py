
from robot.api.deco import keyword
from robot.libraries.BuiltIn import BuiltIn
from robot.api import logger

import re
import difflib
import os.path

import htmlmin
try:
    from .ciscoconfdiff import CiscoConfDiff
except SystemError:
    from ciscoconfdiff import CiscoConfDiff
#from ciscoconfdiff import CiscoConfDiff

class RastaCiscoConfDiff(object):
    ROBOT_LIBRARY_SCOPE = "TEST SUITE"

    def __init__(self):
        self.config_diff1 = None
        self.config_diff2 = None

    @keyword('use ciscoconfdiff to compare configs "${config1}" and "${config2}"')
    def ciscoconfdiff_diff(self, config1, config2):
        """ compare two IOS or IOS-XR configs using ciscoconfdiff/ciscoconfparse logic.
        You can pass the configs as strings or as filenames.
        """
        return self._diff_configs(config1, config2)

    @keyword('use ciscoconfdiff to compare ios configs "${config1}" and "${config2}"')
    def ciscoconfdiff_diff_ios(self, config1, config2):
        """ compare two IOS or IOS-XR configs using ciscoconfdiff/ciscoconfparse logic.
        You can pass the configs as strings or as filenames.
        """
        return self._diff_configs(config1, config2, 'ios')

    @keyword('use ciscoconfdiff to compare asa configs "${config1}" and "${config2}"')
    def ciscoconfdiff_diff_asa(self, config1, config2):
        """ compare two ASA configs using ciscoconfdiff/ciscoconfparse logic.
        You can pass the configs as strings or as filenames.
        """
        return self._diff_configs(config1, config2, 'asa')

    @keyword('use ciscoconfdiff to compare junos configs "${config1}" and "${config2}"')
    def ciscoconfdiff_diff_junos(self, config1, config2):
        """ compare two JunOS configs using ciscoconfdiff/ciscoconfparse logic.
        You can pass the configs as strings or as filenames.
        """
        return self._diff_configs(config1, config2, 'junos')

    @keyword('use ciscoconfdiff to compare nxos configs "${config1}" and "${config2}"')
    def ciscoconfdiff_diff_nxos(self, config1, config2):
        """ compare two NXOS configs using ciscoconfdiff/ciscoconfparse logic.
        You can pass the configs as strings or as filenames.
        """
        return self._diff_configs(config1, config2, 'nxos')

    def _diff_configs(self, config1, config2, syntax='ios'):
        """ Internal diff function doing most of the actual work """
        SQUASH_MARGIN = 3
        REORDER_WARNING = """<p><strong>WARNING:</strong> Bear in mind that the below lines have been reordered for more
convenience while comparing the two configs, but validity of the result to directly configure a device is not
guaranteed. Please refer to the original line numbers in the first column to see how the lines were reordered.</p>"""
        reorder = False
        squash_lines = True
        align = False

        if os.path.exists(config1):
            fd = open(config1)
            config1 = fd.read()
            fd.close()
        else:
            config1 = "\n".join(config1.strip().splitlines())

        if os.path.exists(config2):
            fd = open(config2)
            config2 = fd.read()
            fd.close()
        else:
            config2 = "\n".join(config2.strip().splitlines())

        logger.info("Creating diff between following configs\n----\n{}\n----\n{}".format(config1, config2), html=False)
        diff = CiscoConfDiff(config1, config2, syntax)
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
            CiscoConfDiff.update_squashable_lines(side_by_side_lines, SQUASH_MARGIN)

        diff_result = diff.print_html(side_by_side_lines, \
                               warning=(REORDER_WARNING if reorder else None))
        diff_result = htmlmin.minify(diff_result, remove_empty_space=True)

        logger.info(diff_result, html=True)

        self.config_diff1 = "\n".join(diff.raw_diff_1.strip().splitlines())
        self.config_diff2 = "\n".join(diff.raw_diff_2.strip().splitlines())

        # TODO: Do we want to potentially return a true/false value if there 
        # was a diff found, instead of the HTML file showing the diffs?
        return "*HTML* %s" % diff_result

    @keyword('compare latest ciscoconfdiff with reference diff in list "${config_diff}"')
    def compare_diff_with_list(self, config_diff):
        """
        Compare the latest diff taken with a reference diff in the list variable ${config_diff}. 

        You can best the variable within a yaml file, for example like below. The first 
        block of lines contains the lines only present in the first file, the 2nd only
        in the 2nd file. Assuming the first file diff'ed was the "before", the 2nd the "after" config,
        the first reference diff would contain the statements we expect to be removed, and the 2nd
        the ones we expect to be added::

            reference_diff:
              - |
                interface GigabitEthernet0/0
                 no ip address
              - |
                ip route 0.0.0.0 0.0.0.0 20.1.1.1
                interface GigabitEthernet0/0
                 media-type rj45
                 duplex     full
                 ip address 20.1.1.2 255.255.255.0


        """

        ref_diff = BuiltIn().get_variables()['@{%s}' % config_diff]
        ref1 = ref_diff[0].strip()
        ref2 = ref_diff[1].strip()

        return self._compare_diffs(ref1, ref2)

    @keyword('compare latest ciscoconfdiff with reference diffs in "${ref1}" and "${ref2}"')
    def compare_diff_with_strings(self, ref1, ref2):
        """
        Compare the latest diff taken with a reference diff in the two strings passed. 

        The first string contains the lines only present in the first file, the 2nd only
        in the 2nd file diff'ed. Assuming the first file diff'ed was the "before", the 2nd the "after" config,
        the first reference diff would contain the statements we expect to be removed, and the 2nd
        the ones we expect to be added. 
        """

        return self._compare_diffs(ref1.strip(), ref2.strip())

    @keyword('compare latest ciscoconfdiff with reference diffs in files "${file1}" and "${file2}"')
    def compare_diff_with_files(self, file1, file2):
        """
        Compare the latest diff taken with a reference diff in the two files passed. 

        The first file contains the lines only present in the first file, the 2nd only
        in the 2nd file. Assuming the first file diff'ed was the "before", the 2nd the "after" config,
        the first reference diff would contain the statements we expect to be removed, and the 2nd
        the ones we expect to be added::

           config-removed.txt:
                interface GigabitEthernet0/0
                 no ip address
           
           config-added.txt:
                ip route 0.0.0.0 0.0.0.0 20.1.1.1
                interface GigabitEthernet0/0
                 media-type rj45
                 duplex     full
                 ip address 20.1.1.2 255.255.255.0

        """

        try:
            fd = open(file1)
            ref1 = fd.read()
            fd.close()
        except (OSError, IOError):
            logger.info("Can't open file {}, assuming empty reference".format(file1))
            ref1 = ''

        try:
            fd = open(file2)
            ref2 = fd.read()
            fd.close()
        except (OSError, IOError):
            logger.info("Can't open file {}, assuming empty reference".format(file2))
            ref2 = ''

        return self._compare_diffs(ref1, ref2)

    def _compare_diffs(self, ref1, ref2):
        """ Internal function to check the actual diff """

        # diff_header = "\n+++ Additional lines\n--- Missing lines\n\n"

        # diff_lines1 = difflib.ndiff(ref1.splitlines(), self.config_diff1.splitlines())
        # diff_text1 = diff_header + "\n".join(diff_lines1)

        # diff_lines2 = difflib.ndiff(ref2.splitlines(), self.config_diff2.splitlines())
        # diff_text2 = diff_header + "\n".join(diff_lines2)

        ref1 = ref1.strip()
        ref2 = ref2.strip()

        error = ""
        if ref1 != self.config_diff1:
            error = "Lines only in config1 do not match reference."
            logger.info("Diff comparing lines only in config1 ({}) with reference diff ({})".format(self.config_diff1, ref1))
            # print("calling difflib.HtmlDiff().make_table({},{}".format(self.config_diff1.splitlines(), ref1.splitlines()))
            logger.info(difflib.HtmlDiff().make_table(self.config_diff1.splitlines(), ref1.splitlines(), 'Actual Diff', 'Reference Diff'), html=True)
        else:
            logger.info("ref diff for lines only in config1 matches")

        if ref2 != self.config_diff2:
            error += "Lines only in config2 do not match reference."
            logger.info("Diff comparing lines only in config2 with reference diff")
            logger.info(difflib.HtmlDiff().make_table(self.config_diff2.splitlines(), ref2.splitlines(), 'Actual Diff', 'Reference Diff'), html=True)
        else:
            logger.info("ref diff for lines only in config2 matches")


        # TODO: Replace with RastaException
        if error != "":
            raise AssertionError(error)



if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('file1', help='Config file 1')
    parser.add_argument('file2', help='Config file 2')
    args = parser.parse_args()

    config1 = open(args.file1).read()
    config2 = open(args.file2).read()

    c = CCDiff()
    diff_text = c._diff_configs(config1, config2)
    c._compare_diffs("interface Loopback0\n", "interface Loopback0\n")


    #diff_lines = difflib.ndiff(config1.splitlines(), config2.splitlines())
    #diff_text = "\n".join(diff_lines)

    #with open("diff.html", 'wb') as f:
    #    f.write(diff_text.encode('utf-8'))
    #f.close()
