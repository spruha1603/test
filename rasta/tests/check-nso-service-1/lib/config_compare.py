import sys
import os
from .tree_node import Node
from terminaltables import AsciiTable

from robot.api.deco import keyword
from robot.libraries.BuiltIn import BuiltIn


class CONFIG:
    ROBOT_LIBRARY_SCOPE = "TEST SUITE"

    def __init__(self):
        pass

    #compare configuration
    #  [input]
    #     * before_conf  : configuration before case action
    #     * after_conf  : configuration after case action
    #     * expected_add  : expected add configuration
    #     * expected_del  : expected delete configuration
    #     * verification_file  : compare result log file
    #     * endings  : ending strings in configuration (example: !)
    #     * compare_kbn: when 1, get config_add and config_del with heavy mode; when 2, with light mode.

    @keyword(u'compare config "${before_conf}" to "${after_conf}" expect "${expected_add}" to be added and "${expected_del}" removed')
    def verification(self,before_conf, after_conf, expected_add, expected_del, verification_file=None, endings=["!","exit"], compare_kbn=1):

        print("DEBUG: verification('"+before_conf+"' ,'"+after_conf+"', '"+expected_add+"', '"+expected_del+"')")
        actual_add = ""
        actual_del = ""
        result = "NG"
        #
        before_tree = convert_2_tree(before_conf, endings)
        after_tree = convert_2_tree(after_conf, endings)
        expected_add_tree = convert_2_tree(expected_add, endings)
        expected_del_tree = convert_2_tree(expected_del, endings)

        actual_add_tree, actual_del_tree = compare_tree(before_tree, after_tree, compare_kbn)

        add_diff = Node("diff root")
        del_diff = Node("diff root")

        get_diff(expected_add_tree, actual_add_tree, add_diff)
        get_diff(expected_del_tree, actual_del_tree, del_diff)

        if add_diff.is_leaf() and del_diff.is_leaf():
            result = "OK"

        detail = make_detail(expected_add_tree, actual_add_tree, expected_del_tree, actual_del_tree,
                             add_diff, del_diff)

        print_result(verification_file, result, detail)

        if actual_add_tree is not None:
            actual_add = actual_add_tree.to_string()
        if actual_del_tree is not None:
            actual_del = actual_del_tree.to_string()

        assert result == "OK", "Comparison failed, please check log for details"

        return result, actual_add, actual_del


    # compare configuration()
    #  [input]
    #     * actual_cfg  : actual configuration
    #     * expected_cfg  : expected configuration
    #     * endings  : ending strings in configuration (example: !)
    #     * verification_file  : compare result log file
    def verification_simple(self,actual_cfg, expected_cfg, endings, verification_file):
        result = "NG"
        #
        actual_tree = convert_2_tree(actual_cfg, endings)
        expected_tree = convert_2_tree(expected_cfg, endings)

        diff_tree = Node("diff root")
        get_diff(expected_tree, actual_tree, diff_tree)

        if diff_tree.is_leaf():
            result = "OK"

        detail = make_detail_simple(expected_tree, actual_tree, diff_tree)

        print_result(verification_file, result, detail)

        return result


# print conpare result
def print_result(verification_file, result, detail):
    logfile = None
    old_stdout = None
    try:
        if verification_file:
            logfile = open(verification_file, 'a')
            old_stdout = sys.stdout
            sys.stdout = logfile

        table = AsciiTable(detail)
        table.inner_row_border = True
        print('Result: ' + result)
        print(table.table)

    finally:
        if logfile:
            logfile.close()
        if old_stdout:
            sys.stdout = old_stdout


# convert configuration to tree structure
#  [input]
#     * input  : configuration text or file's full path
#     * endings  : ending strings in configuration (example: !)
def convert_2_tree(input, endings):

    # when input is a path of file
    if os.path.exists(input):
        f = open(input, 'r')
        input = f.read()
        f.close()

    root = Node("this is root")
    lines = input.split("\n")
    parents = [[root, -1]]

    indent = -1

    for line in lines:

        if line is None or line.strip() == "":
            continue

        new_indent = get_indent(line)

        if endings.__contains__(line.strip()):
            while len(parents) > 1 and new_indent < parents[-1][1]:
                parents.pop()
            if len(parents) > 1:
                parents[-1][0].ending = line
            indent = new_indent
            continue

        node = Node(line)

        if new_indent > indent:
            parents[-1][0].add_son(node)
            parents.append([node, new_indent])

        if new_indent == indent:
            if len(parents) > 1:
                parents.pop()
            parents[-1][0].add_son(node)
            parents.append([node, new_indent])

        if new_indent < indent:
            while len(parents) > 0 and new_indent <= parents[-1][1]:
                p = parents.pop()
            parents[-1][0].add_son(node)
            parents.append([node, new_indent])

        indent = new_indent

    return root


def compare_tree(t1, t2, kbn):
    add_tree = None
    del_tree = None

    if is_out_of_scope(t1) or is_out_of_scope(t2):
        return add_tree, del_tree

    is_leaf = False
    if t1.is_same(t2):
        add_sons = []
        del_sons = []
        for st1 in t1.sons:
            if t2.has_son(st1):
                st2 = t2.get_son(st1.text)
                son_add, son_del = compare_tree(st1, st2, kbn)
                if son_add is not None:
                    add_sons.append(son_add)
                if son_del is not None:
                    del_sons.append(son_del)
            elif not is_out_of_scope(st1):
                if st1.is_leaf():
                    is_leaf = True
                del_sons.append(st1)

        for st2 in t2.sons:
            if not t1.has_son(st2) and not is_out_of_scope(st2):
                if st2.is_leaf():
                    is_leaf = True
                add_sons.append(st2)

        if len(add_sons) > 0:
            if kbn == 1 and is_leaf and t2.text != "this is root":
                add_tree = t2
                del_tree = t1
            else:
                add_tree = Node(t2.text)
                add_tree.ending = t2.ending
                add_tree.add_sons(add_sons)
        if len(del_sons) > 0:
            if kbn == 1 and is_leaf and t1.text != "this is root":
                add_tree = t2
                del_tree = t1
            else:
                del_tree = Node(t1.text)
                del_tree.ending = t1.ending
                del_tree.add_sons(del_sons)

    else:
        add_tree = t2
        del_tree = t1

    return add_tree, del_tree


def get_diff(t1, t2, diff_node):

    if t1 is None:
        t1 = Node("")

    if t2 is None:
        t2 = Node("")

    if is_out_of_scope(t1) or is_out_of_scope(t2):
        return

    for st1 in t1.sons:

        if t2.has_son_re(st1):
            st2 = t2.get_son_re(st1.text)
            diff_son = Node(" " + st1.text)
            diff_son.ending = " " + st1.ending
            get_diff(st1, st2, diff_son)

            if len(diff_son.sons) != 0:
                diff_node.add_son(diff_son)
        elif not is_out_of_scope(st1):
            diff_node.add_son(st1.clone().add_prefix("-"))

    for st2 in t2.sons:
        if not t1.has_son_re_anti(st2) and not is_out_of_scope(st2):
            diff_node.add_son(st2.clone().add_prefix("+"))


def get_indent(line):
    ret = 0
    i = 0
    while i < len(line):
        if line[i : i + 1] == " ":
            ret += 1
        else:
            break
        i += 1
    return ret


def is_out_of_scope(t):

    if t.text.strip().startswith("!") or t.text.strip().startswith("*") or t.text.strip().startswith("^")\
            or t.text.strip().startswith("Current configuration :") or t.text.strip().startswith(" description"):
        return True
    return False


def make_detail(expected_add_tree, actual_add_tree, expected_del_tree, actual_del_tree,
                         add_diff, del_diff):
    details = [["Type", "Expected", "Actual", "Diff", "Result"]]

    if actual_add_tree is None:
        actual_add_tree = Node("")
    if actual_del_tree is None:
        actual_del_tree = Node("")

    for son in actual_add_tree.sons:
        if expected_add_tree.has_son(son):
            if add_diff.has_son(son):
                details.append(["ADD", expected_add_tree.get_son(son.text).to_string(), son.to_string(),
                                add_diff.get_son(son.text).to_string(), "False"])

    for son in actual_add_tree.sons:
        if not expected_add_tree.has_son(son):
            details.append(["ADD", "", son.to_string(), "", "False"])
    for son in expected_add_tree.sons:
        if not actual_add_tree.has_son(son):
            details.append(["ADD", son.to_string(), "", "", "False"])

    for son in actual_del_tree.sons:
        if expected_del_tree.has_son(son):
            if del_diff.has_son(son):
                details.append(["DELETE", expected_del_tree.get_son(son.text).to_string(), son.to_string(),
                                del_diff.get_son(son.text).to_string(), "False"])

    for son in actual_del_tree.sons:
        if not expected_del_tree.has_son(son):
            details.append(["DELETE", "", son.to_string(), "", "False"])
    for son in expected_del_tree.sons:
        if not actual_del_tree.has_son(son):
            details.append(["DELETE", son.to_string(), "", "", "False"])

    for son in actual_add_tree.sons:
        if expected_add_tree.has_son(son):
            if not add_diff.has_son(son):
                details.append(["ADD", expected_add_tree.get_son(son.text).to_string(), son.to_string(), "", "True"])

    for son in actual_del_tree.sons:
        if expected_del_tree.has_son(son):
            if not del_diff.has_son(son):
                details.append(
                    ["DELETE", expected_del_tree.get_son(son.text).to_string(), son.to_string(), "", "True"])

    return details


def make_detail_simple(expected_tree, actual_tree, diff_tree):
    details = [["Expected", "Actual", "Diff", "Result"]]

    for son in actual_tree.sons:
        if expected_tree.has_son(son):
            if diff_tree.has_son(son):
                details.append([expected_tree.get_son(son.text).to_string(), son.to_string(),
                                diff_tree.get_son(son.text).to_string(), "False"])
    for son in actual_tree.sons:
        if not expected_tree.has_son(son):
            details.append(["", son.to_string(), "", "False"])
    for son in expected_tree.sons:
        if not actual_tree.has_son(son):
            details.append([son.to_string(), "", "", "False"])
    for son in actual_tree.sons:
        if expected_tree.has_son(son):
            if not diff_tree.has_son(son):
                details.append([expected_tree.get_son(son.text).to_string(), son.to_string(), "", "True"])

    return details
