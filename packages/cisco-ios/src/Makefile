JAVA_PACKAGE = com.tailf.packages.ned.ios
JDIR = $(shell echo $(JAVA_PACKAGE) | sed 's/\./\//g')
SHELL := /bin/bash
NS  = namespaces

JFLAGS = --java-disable-prefix \
         --exclude-enums \
         --fail-on-warnings \
         --java-package $(JAVA_PACKAGE).$(NS) \
	--emit-java java/src/$(JDIR)/namespaces

ifeq ($(KEEP_FXS),)
all:	filter-yang fxs javac
	pushd ../netsim && $(MAKE) all && popd
	rm -f *.yang
	rm -rf tmp-yang
else
all:	filter-yang fxs javac
	pushd ../netsim && $(MAKE) all && popd
endif

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk
include ned-yang-filter.mk

NCSC_VER := $(shell ${NCSC} --version)
ifneq (,$(findstring 6.2,${NCSC_VER}))
SUPPRESS_WARN := --no-warning TAILF_SUGGEST_COMBO
endif

YANG = $(wildcard tmp-yang/*.yang)
SUBMODULES = $(shell grep -l belongs-to tmp-yang/*.yang)
YANG_MODULES = $(filter-out $(SUBMODULES),$(YANG))

$(FXS): $(YANG)

JAVA_NAMESPACES = \
	$(shell echo $(YANG:yang/%.yang=java/src/$(JDIR)/namespaces/%.java) | \
		perl -pne 's/([a-zA-Z])-([a-zA-Z])/\1\U\2/g' | \
		perl -pne 's/tailfNed//g')

fxs:	ncsc-out/modules/fxs/tailf-ned-cisco-ios.fxs \
	ncsc-out/modules/fxs/tailf-ned-cisco-ios-id.fxs \
	ncsc-out/modules/fxs/tailf-ned-cisco-ios-stats.fxs \
	../load-dir/tailf-ned-cisco-ios-oper.fxs \
	../load-dir/tailf-ned-cisco-ios-meta.fxs


ncsc-out/modules/fxs/tailf-ned-cisco-ios.fxs:	tmp-yang/tailf-ned-cisco-ios.yang
	$(NCSC) --ncs-compile-module $< \
                --ncs-device-dir ncsc-out \
	  	--ncs-skip-statistics \
                --ncs-device-type cli-ned \
		${SUPPRESS_WARN} && \
	  cp $@ ../load-dir
	$(NCSC) $(JFLAGS)/tailfCiscoIos.java $@

ncsc-out/modules/fxs/tailf-ned-cisco-ios-id.fxs:	yang/tailf-ned-cisco-ios-id.yang
	$(NCSC) --ncs-compile-module $< \
                --ncs-device-dir ncsc-out \
	  	--ncs-skip-statistics \
                --ncs-device-type cli-ned && \
	  cp $@ ../load-dir
	$(NCSC) $(JFLAGS)/tailfCiscoIosId.java $@

ncsc-out/modules/fxs/tailf-ned-cisco-ios-stats.fxs:	yang/tailf-ned-cisco-ios-stats.yang
	$(NCSC) --ncs-compile-module $< \
                --ncs-device-dir ncsc-out \
	        --ncs-skip-config \
	        --ncs-skip-template \
                --ncs-device-type cli-ned && \
	  cp $@ ../load-dir
	$(NCSC) $(JFLAGS)/tailfCiscoIosStats.java $@

../load-dir/tailf-ned-cisco-ios-meta.fxs:	yang/tailf-ned-cisco-ios-meta.yang
	$(NCSC) --yangpath yang -c $< -o $@

../load-dir/tailf-ned-cisco-ios-oper.fxs:	yang/tailf-ned-cisco-ios-oper.yang
	$(NCSC) --yangpath yang -c $< -o $@

javac:
	cd java && ant -q all

clean:
	rm -f ned-data-snippet.yang
	rm -rf tmp-yang
	rm -rf ncsc-out/* ../load-dir/* ncsc-out/*
	rm -f ../jar/*.jar
	rm -f java/src/$(JDIR)/$(NS)/*.java
	cd java && ant clean
	cd ../netsim && $(MAKE) clean
