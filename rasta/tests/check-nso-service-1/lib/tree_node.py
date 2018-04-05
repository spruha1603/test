import re


def trim(s):
    return re.sub('^[+|-]', '', re.sub('\s{2,}', ' ', s)).strip()


def rtrim(s):
    m = re.match('(^\s*)(.*[\s\S]*)', s)
    return m.group(1) + re.sub('\s{2,}', ' ', m.group(2)).strip()


def match(t1, t2):
    result = False
    try:
        if re.match(t1 + '$', t2):
            result = True
    except:
        pass
    if t1 == t2:
        result = True
    return result


class Node:

    def __init__(self, text):
        self.text = rtrim(text)
        self.sons = []
        self.ending = ""

    def add_son(self, node):
        if not self.has_son(node):
            self.sons.append(node)

    def add_sons(self, nodes):
        for node in nodes:
            if not self.has_son(node):
                self.sons.extend(nodes)

    def has_son(self, node):
        t1 = trim(node.text)
        for s in self.sons:
            t2 = trim(s.text)
            if t1 == t2:
                return True
        return False

    def get_son(self, text):
        t1 = trim(text)
        for s in self.sons:
            t2 = trim(s.text)
            if t1 == t2:
                return s
        return None

    def has_son_re(self, node):
        t1 = trim(node.text)
        for s in self.sons:
            t2 = trim(s.text)
            if match(t1, t2):
                return True
        return False

    def get_son_re(self, text):
        t1 = trim(text)
        for s in self.sons:
            t2 = trim(s.text)
            if match(t1, t2):
                return s
        return None

    def has_son_re_anti(self, node):
        t1 = trim(node.text)
        for s in self.sons:
            t2 = trim(s.text)
            if match(t2, t1):
                return True
        return False

    def get_son_re_anti(self, text):
        t1 = trim(text)
        for s in self.sons:
            t2 = trim(s.text)
            if match(t2, t1):
                return s
        return None

    def is_same(self, node):
        t1 = trim(self.text)
        t2 = trim(node.text)
        if t1 == t2:
            return True
        return False

    def is_leaf(self):
        if self.ending.strip() == "" and len(self.sons) == 0:
            return True
        return False

    def to_string(self):
        lines = []
        if self.text != "this is root":
            lines.append(self.text)
        for son in self.sons:
            lines.append(son.to_string())
        if self.ending.strip() != "":
            lines.append(self.ending)

        return "\n".join(lines)

    def clone(self):
        node = Node(self.text)
        for son in self.sons:
            node.add_son(son.clone())
        return node

    def add_prefix(self, prefix):
        self.text = prefix + self.text
        for son in self.sons:
            son.add_prefix(prefix)
        return self
