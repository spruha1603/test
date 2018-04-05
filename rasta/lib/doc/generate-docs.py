#!/usr/bin/env python
#
# Generate html documentation for python and robot libs
#
# Usage:
#   cd $RASTA_ROOT/lib/doc
#   python generate-docs.py
#
#


import os
import re
import sys
from robot.libdoc import LibDoc

libdir = '..'
subdirs = ['py', 'robot']
outdir = os.getcwd()
# append rasta libs to pythonpath to allow the script being called
# in a non-interactive way
for p in subdirs:
    sys.path.append(outdir + os.path.sep + libdir + os.path.sep + p)
index = {}


def create_docs(dir):
    for file in os.listdir(dir):
        if file == '.' or file == '..':
            # seems os.list
            continue
        elif os.path.isdir(dir + os.path.sep + file):
            create_docs(dir + os.path.sep + file)
            continue
        elif re.match(r'.*\.(robot|py)$', file) is None:
            continue

        # Python libs
        if re.match(r'.*\.py', file) is not None:
            outfile = os.path.splitext(file)[0]
            #
            # robot.libdoc requires a class name as resource (as in the Robot "Library" kewyord argument)
            # so we are looking for "class ..." statements in the python lib file
            fd = open(dir + os.path.sep + file)
            classes = []
            skip = False
            for line in fd.readlines():
                if line.lower().startswith('#rasta generate-docs ignore'):
                    print("found %s line and stop processing this file" % line)
                    skip = True
                    break
                m = re.match(r'class\s+([^\(\s]+)', line)
                if m is not None:
                    classes.append(m.group(1))
            fd.close()
            if len(classes) > 0:
                for c in classes:
                    resource = outfile + '.' + c
                    output = outdir + os.path.sep + resource + '.html'
                    print("Calling libdoc({},{})".format(resource, output))
                    LibDoc().main([resource, output], docformat='reST')
                    # Update index
                    update_index(resource, os.path.basename(output))
            elif not skip:
                print("File {} does not contain a class defintion. skipping".format(dir + os.path.sep + file))

        else:
            # Robot
            outfile = outdir + os.path.sep + dir.replace(os.path.sep, ".") + '.' + os.path.splitext(file)[0] + '.html'
            resource = dir + os.path.sep + file
            # print("Calling libdoc({},{})".format(resource,outfile))
            LibDoc().main([resource, outfile], docformat='reST')
            update_index(resource, os.path.basename(outfile))


def update_index(resource, file):
    # the libdoc "list" method only prints the keywords on stdout, so we
    # redirect stdout to a file and retrieve the output to update the index
    tmpname = ".tmpindex-" + str(os.getpid())
    fd = open(tmpname, "w")
    old_stdout = sys.stdout
    sys.stdout = fd
    LibDoc().main([resource, "list"])
    sys.stdout.flush()
    fd.close()
    sys.stdout = old_stdout

    fd = open(tmpname, "r")
    for kw in fd.readlines():
        kw = kw.rstrip()
        if kw in index.keys():
            index[kw] += "," + file
        else:
            index[kw] = file
    fd.close()
    os.unlink(tmpname)


os.chdir(libdir)
for dir in subdirs:
    create_docs(dir)

print("Creating " + outdir + os.path.sep + "keyword-index.html")
fd = open(outdir + os.path.sep + "keyword-index.html", "w")
fd.write("<html><head><title>RASTA keyword index</title></head>\n")
fd.write("<body><h2>RASTA keyword index</h2>\n")
fd.write("<table border=0>\n")
for key, files in sorted(index.items()):
    fd.write("<tr><td>" + key + "</td><td>")
    for link in files.split(","):
        fd.write("<a href={}>{}</a>&nbsp;&nbsp;&nbsp;".format(link, link))
    fd.write("</td></tr>\n")
fd.write("<table><br><br>created by generate-docs.py</body></html>")
fd.close()
