

from robot.api.deco import keyword

import requests
from requests.auth import HTTPBasicAuth



class RastaNSORest(object):

    def nso_interact_via_rest(self, dev, uri, method, encoding, payload=None):
        """ internal function to interact with NSO via rest, supporting read/GET and conf/PUT/POST/PATCH/DELETE """

        try:
            username = self.testbed.devices[dev].connections.restconf.username
            password = self.testbed.devices[dev].connections.restconf.password
            ip = self.testbed.devices[dev].connections.restconf.ip
            port = self.testbed.devices[dev].connections.restconf.port
            protocol = self.testbed.devices[dev].connections.restconf.protocol
        except AttributeError:
            print("Error, please define all of the following attributes in the topology yaml file for device {}: username, password, ip, port, protocol".format(dev))
            raise

        if method.lower() != "get":
            h = "Content-type"
        else:
            h = "Accept"

        if encoding.lower() == "xml":
            header = {h: "application/vnd.yang.data+xml"}
        elif encoding.lower() == "json":
            header = {h: "application/vnd.yang.data+json"}
        else:
            print("\nERROR: encoding {} not recognized, must be 'json' or 'xml'".format(encoding))
            raise

        location = protocol + '://' + str(ip) + ':' + str(port) + uri

        if method.lower() == "post":
            print("\nFullstack to send POST request to uri: " + location)
            print("With json body:\n" + payload)
            r = requests.post(location, headers=header, auth=HTTPBasicAuth(username, password), data=payload)
        elif method.lower() == "patch":
            print("\n Fullstack to send PATCH request to uri: " + location)
            print("With json body:\n" + payload)
            r = requests.patch(location, headers=header, auth=HTTPBasicAuth(username, password), data=payload)
        elif method.lower() == "delete":
            print("\n Fullstack to send DELETE request to uri: " + location)
            print("With json body:\n" + payload)
            r = requests.delete(location, headers=header, auth=HTTPBasicAuth(username, password), data=payload)
        elif method.lower() == "get":
            print("Sending GET request to " + location)
            r = requests.get(location, headers=header, auth=HTTPBasicAuth(username, password))
        else:
            print("\nERROR: method {} not recognized, must be one of GET, POST, PATCH or DELETE".format(method))

        print("Request returned with status code {} and text \"{}\"".format(r.status_code, r.text))
        return [r.status_code, r.text]

    @keyword(u'via NSO REST API configure device "${dev}" at URI "${uri}" using "${method}" with "${encoding}" payload "${payload}"')
    def nso_configure_via_rest(self, dev, uri, method, encoding, payload):
        """
        Configures NSO using REST.
        Parmeters are: NSO device (dev), URI (like /api/running/service), method (POST, PATCH, DELETE), encoding (xml or json) and payload.
        It retrieves the NSO's REST address/port and credentials from the unicon testbed (ex: "testbed.yaml"), so it needs
        to be loaded first (using 'load testbed ...')
        It returns the status code and return text as a list.
        """
        (status, response) = self.nso_interact_via_rest(dev, uri, method, encoding, payload)
        assert int(status / 100) == 2, "HTTP Status %d, expected 2xx" % status
        return status

    @keyword(u'via NSO REST API retrieve "${uri}" from "${dev}" as "${encoding}"')
    def nso_retrieve_via_rest(self, uri, dev, encoding):
        """
        Retrieves an URI from NSO using REST.
        Parmeters are: NSO device (dev), URI (like /api/running/service) and encoding (xml or json).
        Note: the method retrieves the NSO's REST address/port and credentials from the unicon testbed (ex: "testbed.yaml"), so it needs
        to be loaded first (using 'load testbed ...')
        It returns the status code and return text as a list.
        """
        return self.nso_interact_via_rest(dev, uri, "GET", encoding)

    @keyword(u'via NSO REST API configure service on device "${dev}" with "${encoding}" payload "${payload}"')
    def nso_configure_service_post(self, dev, encoding, payload):
        """
        Configures a service on NSO using REST, using the POST method. It retrieves the NSO's REST
        address/port and credentials from the unicon "testbed.yaml" and supports both xml and json encoding.
        It calls the URI <proto>://<ip-addr>:<port>/api/running/services.
        The method sets the content-type accordingly.
        It returns the status code and raises an error if the status code is not 2xx.

        Note: a more flexible kewyord is 
        via NSO REST API configure device "${dev}" at URI "${uri}" using "${method}" with "${encoding}" payload "${payload}"
        """

        (status, response) = self.nso_interact_via_rest(dev, "/api/running/services", "POST", encoding, payload)
        assert int(status / 100) == 2, "HTTP Status %d, expected 2xx" % status
        return status

    @keyword(u'via NSO REST API delete service on device "${dev}" with "${encoding}" payload "${payload}"')
    def nso_delete_service(self, dev, encoding, payload):
        """
        Deletes a service on NSO using REST. It retrieves the NSO's REST
        address/port and credentials from the unicon "testbed.yaml" and supports both xml and json encoding.
        It calls the URI <proto>://<ip-addr>:<port>/api/running/services.
        The method sets the content-type accordingly.
        It returns the status code and raises an error if the status code is not 2xx.

        Note: a more flexible kewyord is 
        via NSO REST API configure device "${dev}" at URI "${uri}" using "${method}" with "${encoding}" payload "${payload}"
         """

        (status, response) = self.nso_interact_via_rest(dev, "/api/running/services", "DELETE", encoding, payload)
        assert int(status / 100) == 2, "HTTP Status %d, expected 2xx" % status
        return status
