## RASTA Examples

1. **hello-world**

    Get your feet wet with the hello world robot script.

2. **netsim-setup**

    Creates a simple NSO/netsim environment, demonstrates the use of pyATS/unicon library to interact with the devices on CLI basis.

3. **check-nso-service-1**

    Leverages the netsim setup and creates a l3vpn service on NSO and shows two different methods to verify the resulting service configuration

4. **setup-teardown-rollback**

    We utilize Robot's Test Setup/Test Teardown to implement a rollback to a previously collected revision so each test would rollback to the previous version automatically

5. **nso-ui**

    Example robot script driving web browser using robot keywords against the NSO Web UI.


To execute the examples, please first set the PYTHONPATH environment variables using

```
export RASTAHOME=<the directory where you cloned the repo in>
source $RASTAHOME/env.sh
```
