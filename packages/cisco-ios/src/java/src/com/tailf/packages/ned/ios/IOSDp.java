package com.tailf.packages.ned.ios;

import java.net.Socket;
import com.tailf.maapi.Maapi;
import com.tailf.ncs.NcsMain;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.ResourceManager;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfValue;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.DpTrans;
import com.tailf.dp.DpUserInfo;
import com.tailf.dp.annotations.DataCallback;
import com.tailf.dp.annotations.TransCallback;
import com.tailf.dp.proto.DataCBType;
import com.tailf.dp.proto.TransCBType;

public class IOSDp {

    @Resource(type=ResourceType.MAAPI, scope=Scope.INSTANCE)
    public Maapi   mm;

    private boolean isNetconf(DpTrans trans)
        throws DpCallbackException {

        DpUserInfo uinfo = trans.getUserInfo();
        if ("netconf".equals(uinfo.getContext()))
            return true;

        return false;
    }

    // interfaceSwitchportCreate
    @DataCallback(callPoint="interface-switchport-hook",
            callType=DataCBType.CREATE)
        public int interfaceSwitchportCreate(DpTrans trans, ConfObject[] keyPath)
            throws DpCallbackException {
        try {
            if (isNetconf(trans))
                return Conf.REPLY_OK;

            int    tid     = trans.getTransaction();
            String path    = new ConfPath(keyPath).toString();
            String ifpath  = path.replace("switchport", "");
            String toppath = path.substring(0, path.indexOf("interface"));

            //System.out.println("interfaceSwitchportCreate() path="+path+" newval="+newval);

            // Delete primary and secondary IP address(es)
            mm.safeDelete(tid, ifpath+"ip/address");

            // Check device version, act on device type
            if (mm.exists(tid, toppath+"cached-show/version/model")) {
                ConfValue val = mm.safeGetElem(tid, toppath+"cached-show/version/model");
                if (val.toString().indexOf("C650") >= 0) {
                    // Don't delete 'no ip address' since can be set with switchport on 650x
                    return Conf.REPLY_OK;
                }
            }

            // Clear 'no ip address' to avoid diff due to NCS bug with default values in choice
            mm.safeDelete(tid, ifpath+"ip/no-address/address");
            return Conf.REPLY_OK;
        }
        catch (Exception e) {
            throw new DpCallbackException("", e);
        }
    }


    // IOSDpInit
    @TransCallback(callType=TransCBType.INIT)
    public void IOSDpInit(DpTrans trans) throws DpCallbackException {

        //System.out.println("IOSDpInit()");

        try {
            if (mm == null) {
                // Need a Maapi socket so that we can attach
                Socket s = new Socket("127.0.0.1", NcsMain.getInstance().
                                      getNcsPort());
                mm = new Maapi(s);
            }
            mm.attach(trans.getTransaction(),0,
                      trans.getUserInfo().getUserId());
            return;
        }
        catch (Exception e) {
            throw new DpCallbackException("Failed to attach", e);
        }
    }


    // IOSDpFinish
    @TransCallback(callType=TransCBType.FINISH)
    public void IOSDpFinish(DpTrans trans) throws DpCallbackException {

        //System.out.println("IOSDpFinish()");

        try {
            mm.detach(trans.getTransaction());
        }
        catch (Exception e) {
            ;
        }
    }

}
