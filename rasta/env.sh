#!/bin/bash

if [ x$RASTAHOME = x ] ; then
    if [ -f ./env.sh -a -d ./lib/py -a ./lib/robot -a -d ./examples ] ; then
        export RASTAHOME="`pwd`"
    else
        echo "Please set RASTAHOME environment variable to the directory where the RASTA repo was cloned,"
        echo "or source this file in this directory"
    fi
fi

if [ "$RASTAHOME" -a ! -d "$RASTAHOME" ] ; then
    echo "\$RASTAHOME doesn't point to a valid directory: $RASTAHOME"
    RASTAHOME=
fi

[ "$RASTAHOME" ]  && export PYTHONPATH=$RASTAHOME/lib/py:$RASTAHOME/lib/robot:.

