PACKAGES = check-dc-vlan-service

all: packages

packages: FORCE
	for i in $(PACKAGES); do \
		echo $${i}; \
		$(MAKE) -C packages/$${i}/src all || exit 1; \
	done

clean: 
	for i in $(PACKAGES); do \
		$(MAKE) -C packages/$${i}/src clean || exit 1; \
	 done

FORCE:
