Example to configure an NSO service using Rest and performing some basic checks on the resulting configuration.

``` 
$ robot l3vpn-service.robot

```

The test environment is based on the netsim setup brought up using the example in ../netsim-setup, please refer to the README.md there

We would invite you to play around with the ip addresses included as part of the service setup (in the xml payload in config.yaml) to see how Rasta catches diffs in the configs. 

So after the test works fine, change 20.1.1.2 to a different value (like 30.1.1.2) in the below setting to see Rasta catch the errors and highlight the diffs in the log.html:

```
createPayload: |
      <sw-init-l3vpn xmlns="http://com/example/swinitl3vpn">
        <name>VRFNAME</name>
        <endpoint>
          <pe>pe0</pe>
          <pe-interface>0/0/0/0</pe-interface>
          <ce>ce0</ce>
          <ce-interface>0/0</ce-interface>
          <pe-address>20.1.1.1</pe-address>
          <ce-address>20.1.1.2</ce-address>
        </endpoint>
      </sw-init-l3vpn>

```


---

*Note:* There is still a problem in the unicon library which will cause an error like the one below:

```
Setup L3VPN service                                                   | FAIL |
ConnectionError: failed to connect to ce0
```

you can see the error message in the **log.html** file.

To workaround, please connect to the devices using your local ssh client at least once manually to add the remote's key to the local known_hosts files. You can derive the port numbers of the netsim devices through ncs, but as the two ce devices are created first in the ../netsim-setup test, they should run at 10022 and 10023.. so just do

```
ssh -l admin 127.0.0.1 -p 10022
ssh -l admin 127.0.0.1 -p 10023
```
and answer "yes" to add the key to the known_hosts (you can interrupt with ^c, or log in with pw "admin".
After this, the test should run fine.
