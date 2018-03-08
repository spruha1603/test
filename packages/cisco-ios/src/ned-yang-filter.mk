#################################################################################
#
# MAKEFILE PLUGIN TO BE USED FOR FILTERING OUT YANG ANNOTATIONS UNSUPPORTED BY
# CERTAIN COMPILERS
#
#################################################################################


# Python based cleaner to filter out yang annotations not supported be certain yang compilers
yang_cleaner = python -c 'import re, sys; s=sys.stdin.read(); s=re.sub(zzzREGEX, s); print s.strip()'

# Regex to filter out yang annotations of type tailf:ned-data v3
NED_DATA_YANG_REGEX ="(tailf:ned-data\s*\"\S+\"\s+\{\s*[\r\n]\s*)(\S+\s+\S+;[\n\r]\s*)(\})","//\\1//\\2//\\3"

# Regex to comment/uncomment the tag requires-transaction-states from package-meta-data.xml
PKG_META_DATA_REGEX="(?<!--)(<option>\s*[\r\n]\s*<name>requires-transaction-states</name>\s*[\r\n]\s*</option>)","<!--\\1-->"
PKG_META_DATA_REGEX_R="<!--(<option>\s*[\r\n]\s*<name>requires-transaction-states</name>\s*[\r\n]\s*</option>)-->","\\1"

ned-data-snippet.yang:
	@rm -f $@
	@echo "module ned-data-snippet {" > $@
	@echo " namespace 'http://tail-f.com/ned/ned-data';" >> $@
	@echo " prefix ned-data;" >> $@
	@echo " import tailf-common {" >> $@
	@echo "   prefix tailf;" >> $@
	@echo " }"  >> $@
	@echo " leaf foo {" >> $@
	@echo "   tailf:ned-data "." {" >> $@
	@echo "     tailf:transaction both;" >> $@
	@echo "   }"  >> $@
	@echo "   type uint32;"  >> $@
	@echo " }"  >> $@
	@echo "}"  >> $@

.PHONY: filter-yang

filter-yang: ned-data-snippet.yang tmp-yang $(YANG)

tmp-yang:
	@mkdir tmp-yang

tmp-yang/%.yang: yang/%.yang
	@cp $< $@
	@$(NCSC) --yangpath yang -c ned-data-snippet.yang >/dev/null 2>&1; \
	if [ "$$?" -ne "0" ]; then \
		echo "YANG COMPILER DOES NOT SUPPORT ned-data. Filtering before compile"; \
		for f in `ls tmp-yang/*.yang`; do \
			cat $$f | $(subst zzzREGEX,$(NED_DATA_YANG_REGEX),$(yang_cleaner)) > $$f.tmp && \
			cp $$f.tmp $$f && rm $$f.tmp; \
		done; \
		echo "Enabling commit-queue lock from package-meta-data.xml"; \
		for f in ../package-meta-data.xml; do  \
			cat $$f | $(subst zzzREGEX,$(PKG_META_DATA_REGEX_R),$(yang_cleaner)) > $$f.tmp && \
			cp $$f.tmp $$f && rm $$f.tmp; \
		done; \
	else \
		echo "YANG COMPILER SUPPORTS ned-data. Disabling commit-queue lock from package-meta-data.xml"; \
		for f in ../package-meta-data.xml; do  \
			cat $$f | $(subst zzzREGEX,$(PKG_META_DATA_REGEX),$(yang_cleaner)) > $$f.tmp && \
			cp $$f.tmp $$f && rm $$f.tmp; \
		done \
	fi
