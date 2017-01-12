package net.itrc.ricc.destcloud3.impl;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.node.rev151228.RouterInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.node.rev151228.fault.actions.Actions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.node.rev151228.fault.actions.ActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.SetInterfaceStateInput.State;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.get._interface.output.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.get._interface.output.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.types.rev151228.DirectionRef.Direction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.types.rev151228.fault.action.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.types.rev151228.fault.action.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.types.rev151228.fault.action.action.RouteChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VyosManager extends RouterManager {

    private static final Logger LOG = LoggerFactory.getLogger(VyosManager.class);
    
    private String ifRegEx;
    private Pattern ifPat;
    private String macRegEx;
    private Pattern macPat;
    private String inetRegEx;
    private Pattern inetPat;
    private String v6RegEx;
    private Pattern v6Pat;
    private String descRegEx;
    private Pattern descPat;
    
    public VyosManager() {
        ifRegEx = "^\\s*(\\S+):\\s+<(\\S+)> .*$";
        ifPat = Pattern.compile(ifRegEx);
        macRegEx = "^\\s+link/\\S+\\s+(\\S+) .*$";
        macPat = Pattern.compile(macRegEx);
        inetRegEx = "^\\s+inet\\s+(\\S+) .*$";
        inetPat = Pattern.compile(inetRegEx);
        v6RegEx = "^\\s+inet6\\s+(\\S+) .*$";
        v6Pat = Pattern.compile(v6RegEx);
        descRegEx = "^\\s+Description: (.*)$";
        descPat = Pattern.compile(descRegEx);
    }
    
    public List<Interface> getInterfaces(RouterInfo rInfo) {
        List<Interface> ifList = new ArrayList<Interface>();
        List<InterfaceBuilder> ifbList = new ArrayList<InterfaceBuilder>();
        
        SshResult result = SshConfigUtils.runVyattaShowCommand(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), "show interfaces detail");
        if (!result.isResult()) {
            log(SyslogUtils.LOG_ERR, "SSH utility return -> " + result.getMessage());
            return ifList;
        }
        
        
        LOG.error("recv str from VYOS => " + result.getMessage());
        String retStr = result.getMessage().replaceAll("\r\n", "\n");
        String[] msg = retStr.split("\n");
        Integer idx = 0;
        
        while (idx < msg.length) {
            String str = msg[idx];
            Matcher m = ifPat.matcher(str);
            if (m.matches()) {
                idx = getInterfaceInfo(idx, msg, m.group(1), m.group(2), ifbList);
                continue;
            }
            idx++;
        }
        
        for (InterfaceBuilder ifb : ifbList) {
            ifList.add(ifb.build());
        }
        
        return ifList;
    }

    private Integer getInterfaceInfo(Integer idx, String[] msg, String name, String flags,
            List<InterfaceBuilder> ifbList) {
        InterfaceBuilder ifb = new InterfaceBuilder();
        List<Ipv4Prefix> v4List = new ArrayList<Ipv4Prefix>();
        List<Ipv6Prefix> v6List = new ArrayList<Ipv6Prefix>();
        
        Integer pos = name.indexOf('@');
        if (pos != -1) {
            String ifName = name.substring(0, pos);
            ifb.setName(ifName);
            ifb.setParentDevice(name.substring(pos + 1));
            ifb.setPhysical(false);
            pos = ifName.indexOf('.');
            ifb.setVlanId(Integer.parseInt(ifName.substring(pos + 1)));
        } else {
            ifb.setPhysical(true);
            ifb.setName(name);
        }
        
        ifb.setAdminStatus(true);
        ifb.setOperStatus(flags.contains("LOWER_UP"));
       
        // parse line
        idx++;
        
        while (idx < msg.length) {
            String str = msg[idx];
            Matcher m = ifPat.matcher(str);
            if (m.matches()) {
                break;
            }
            m = macPat.matcher(str);
            if (m.matches()) {
                ifb.setMacAddress(m.group(1));
                idx++;
                continue;
            }
            m = descPat.matcher(str);
            if (m.matches()) {
                ifb.setDescription(m.group(1));
                idx++;
                continue;
            }
            m = inetPat.matcher(str);
            if (m.matches()) {
                Ipv4Prefix pre = new Ipv4Prefix(m.group(1));
                v4List.add(pre);
                idx++;
                continue;
            }
            m = v6Pat.matcher(str);
            if (m.matches()) {
                Ipv6Prefix pre = new Ipv6Prefix(m.group(1));
                v6List.add(pre);
                idx++;
                continue;
            }
            idx++;
        }
        
        ifb.setIpv4Addresses(v4List);
        ifb.setIpv6Addresses(v6List);
        
        ifbList.add(ifb);
        
        return idx;
    }

    public Actions faultAction(RouterInfo rInfo, DataBroker ds, String ifname, Action action) throws Exception {
        ActionsBuilder result = null;
        if (action.isPortDown() != null && action.isPortDown()) {
            result = portDown(rInfo, ds, ifname);
            if (result != null) {
                log(SyslogUtils.LOG_INFO, result.getUuid() + " interface " + ifname + " goes down");
            }
        }
        if (action.isAllLose() != null && action.isAllLose()) {
            result = allLose(rInfo, ds, ifname, action.getDirection());
            if (result != null) {
                log(SyslogUtils.LOG_INFO, result.getUuid() + " set all-lose on interface " + ifname);
            }
        }
        if (action.getPacketLoss() != null) {
            result = packetLoss(rInfo, ds, ifname, action.getPacketLoss(), action.getDirection());
            if (result != null) {
                log(SyslogUtils.LOG_INFO, result.getUuid() + " set packet-loss (" + action.getPacketLoss().toString() + "%) on interface " + ifname);
            }
        }
        if (action.getRouteChange() != null) {
            result = routeChange(rInfo, ds, action.getRouteChange());
            if (result != null) {
                log(SyslogUtils.LOG_INFO, result.getUuid() + " set static route for " + action.getRouteChange().getPrefix().getValue() + " to " +
                        action.getRouteChange().getNexthop().getValue());
            }
        }
        if (action.getShaping() != null) {
            result = doShaping(rInfo, ds, ifname, action.getShaping(), action.getDirection());
            if (result != null) {
                log(SyslogUtils.LOG_INFO, result.getUuid() + " set shaping " + action.getShaping().toString() + "bps on interface " + ifname);
            }
        }
        if (action.getDelay() != null) {
            result = setDelay(rInfo, ds, ifname, action.getDelay(), action.getDirection());
            if (result != null) {
                log(SyslogUtils.LOG_INFO, result.getUuid() + " set delay " + action.getDelay().toString() + "msec on interface " + ifname);
            }
        }
        
        if (result != null) {
            Date d = new Date();
            SimpleDateFormat sf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            result.setDate(sf.format(d));
            Long l = System.currentTimeMillis();
            BigInteger bi = new BigInteger(l.toString());
            result.setTimestamp(bi);
            result.setRecovered(false);
            Actions acts = result.build();
            return acts;
        }
        
        return null;
    }

    private ActionsBuilder setDelay(RouterInfo rInfo, DataBroker ds, String ifname, Integer delay,
            Direction direction) throws Exception {
        List<String> cmds = new ArrayList<String>();
        
        Long currentTime = System.currentTimeMillis();
        String aclName = "acl" + currentTime.toString();
        String ifcmd = convertVif(ifname);
        
        cmds.add("set traffic-policy network-emulator " + aclName + " network-delay " + delay.toString());
        if (direction.equals(Direction.Outbound)) {
            cmds.add("set interface " + ifcmd + " traffic-policy out " + aclName);
        } else {
            throw new Exception("network-delay only available for outbound-traffic");
        }
        
        String uuid = generateUUID();
        
        // do ssh
        SshResult result = SshConfigUtils.runVyattaSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        }
        
        // store Action
        ActionsBuilder acts = new ActionsBuilder();
        acts.setInterface(ifname);
        acts.setUuid(uuid);
        acts.setAclname(aclName);
        ActionBuilder act = new ActionBuilder();
        act.setDelay(delay);
        act.setDirection(direction);
        
        acts.setAction(act.build());
        
        return acts;
    }

    private ActionsBuilder doShaping(RouterInfo rInfo, DataBroker ds, String ifname, Long shaping,
            Direction direction) throws Exception {
        List<String> cmds = new ArrayList<String>();
        
        Long currentTime = System.currentTimeMillis();
        String aclName = "acl" + currentTime.toString();
        String ifcmd = convertVif(ifname);
        
        cmds.add("set traffic-policy network-emulator " + aclName + " bandwidth " + shaping.toString() + "bit");
        if (direction.equals(Direction.Outbound)) {
            cmds.add("set interface " + ifcmd + " traffic-policy out " + aclName);
        } else {
            throw new Exception("shaping only available for outbound-traffic");
        }
        
        String uuid = generateUUID();
        
        // do ssh
        SshResult result = SshConfigUtils.runVyattaSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        }
        
        // store Action
        ActionsBuilder acts = new ActionsBuilder();
        acts.setInterface(ifname);
        acts.setUuid(uuid);
        acts.setAclname(aclName);
        ActionBuilder act = new ActionBuilder();
        act.setShaping(shaping);
        act.setDirection(direction);
        
        acts.setAction(act.build());
        
        return acts;
    }

    private ActionsBuilder routeChange(RouterInfo rInfo, DataBroker ds, RouteChange routeChange) throws Exception {
        List<String> cmds = new ArrayList<String>();
        cmds.add("set protocols static route " + routeChange.getPrefix().getValue() + " next-hop " + 
                routeChange.getNexthop().getValue());
        
        String uuid = generateUUID();
        
        // do ssh
        SshResult result = SshConfigUtils.runVyattaSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        }
        
        // store Action
        ActionsBuilder acts = new ActionsBuilder();
        acts.setUuid(uuid);
        ActionBuilder act = new ActionBuilder();
        act.setRouteChange(routeChange);
        
        acts.setAction(act.build());
        
        return acts;
    }

    private ActionsBuilder packetLoss(RouterInfo rInfo, DataBroker ds, String ifname, Short packetLoss, Direction direction) throws Exception {
        List<String> cmds = new ArrayList<String>();
        
        Long currentTime = System.currentTimeMillis();
        String aclName = "acl" + currentTime.toString();
        String ifcmd = convertVif(ifname);
        
        cmds.add("set traffic-policy network-emulator " + aclName + " packet-loss " + packetLoss.toString() + "%");
        if (direction.equals(Direction.Outbound)) {
            cmds.add("set interface " + ifcmd + " traffic-policy out " + aclName);
        } else {
            throw new Exception("packet-loss only available for outbound-traffic");
        }
        
        String uuid = generateUUID();
        
        // do ssh
        SshResult result = SshConfigUtils.runVyattaSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        }
        
        // store Action
        ActionsBuilder acts = new ActionsBuilder();
        acts.setInterface(ifname);
        acts.setUuid(uuid);
        acts.setAclname(aclName);
        ActionBuilder act = new ActionBuilder();
        act.setPacketLoss(packetLoss);
        act.setDirection(direction);
        
        acts.setAction(act.build());
        
        return acts;
    }

    private ActionsBuilder portDown(RouterInfo rInfo, DataBroker ds, String ifname) throws Exception {
        List<String> cmds = new ArrayList<String>();
        String ifcmd = convertVif(ifname);
        
        cmds.add("set interface " + ifcmd + " disable");
        
        String uuid = generateUUID();
        
        // do ssh
        SshResult result = SshConfigUtils.runVyattaSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        }
        
        // store Action
        ActionsBuilder acts = new ActionsBuilder();
        acts.setInterface(ifname);
        acts.setUuid(uuid);
        ActionBuilder act = new ActionBuilder();
        act.setPortDown(true);
        
        acts.setAction(act.build());
        
        return acts;      
    }

    private ActionsBuilder allLose(RouterInfo rInfo, DataBroker ds, String ifname, Direction direction) throws Exception {
        List<String> cmds = new ArrayList<String>();
        
        Long currentTime = System.currentTimeMillis();
        String aclName = "acl" + currentTime.toString();
        String ifcmd = convertVif(ifname);
        
        cmds.add("set firewall name " + aclName + " rule 1 action drop");
        cmds.add("set firewall name " + aclName + " rule 1 source address 0.0.0.0/0");
        cmds.add("set firewall name " + aclName + " rule 1 destination address 0.0.0.0/0");
        if (direction.equals(Direction.Inbound)) {
            cmds.add("set interface " + ifcmd + " firewall in name " + aclName);
        } else if  (direction.equals(Direction.Outbound)) {
            cmds.add("set interface " + ifcmd + " firewall out name " + aclName);
        } else {
            cmds.add("set interface " + ifcmd + " firewall in name " + aclName);
            cmds.add("set interface " + ifcmd + " firewall out name " + aclName);
        }
        
        String uuid = generateUUID();
        
        // do ssh
        SshResult result = SshConfigUtils.runVyattaSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        }
        
        // store Action
        ActionsBuilder acts = new ActionsBuilder();
        acts.setInterface(ifname);
        acts.setUuid(uuid);
        acts.setAclname(aclName);
        ActionBuilder act = new ActionBuilder();
        act.setAllLose(true);
        act.setDirection(direction);
        
        acts.setAction(act.build());
        
        return acts;
    }

    private String convertVif(String ifname) throws Exception {
        String cmdstr = "";
        Integer pos = ifname.indexOf('.');
        if (ifname.contains("eth")) {
            cmdstr = "ethernet ";
        } else {
            throw new Exception("interface type not supported");
        }
        if (pos == -1) {
            cmdstr = cmdstr + ifname;
            return cmdstr;
        }
        cmdstr = cmdstr + ifname.substring(0, pos) + " vif " + ifname.substring(pos + 1); 
        return cmdstr;
    }

    public void recovery(DataBroker ds, RouterInfo rInfo, Actions acts) throws Exception {
        Action act = acts.getAction();
        if (act.isPortDown() != null && act.isPortDown()) {
            portUp(ds, rInfo, acts.getInterface());
            log(SyslogUtils.LOG_INFO, acts.getUuid() + " interface " + acts.getInterface() + " is up");
        }
        if (act.isAllLose() != null && act.isAllLose()) {
            recoveryAllLose(ds, rInfo, acts.getInterface(), acts.getAclname(), act.getDirection());
            log(SyslogUtils.LOG_INFO, acts.getUuid() + " clear all-lose on interface " + acts.getInterface());
        }
        if (act.getPacketLoss() != null) {
            recoveryPacketLoss(ds, rInfo, acts.getInterface(), acts.getAclname(), act.getDirection());
            log(SyslogUtils.LOG_INFO, acts.getUuid() + " clear packet-loss on interface " + acts.getInterface());
        }
        if (act.getRouteChange() != null) {
            recoveryRouteChange(ds, rInfo, act.getRouteChange());
            log(SyslogUtils.LOG_INFO, acts.getUuid() + " static route (" + act.getRouteChange().getPrefix().getValue() + " to " + act.getRouteChange().getNexthop().getValue()
                    + ") is removed");
        }
        if (act.getShaping() != null) {
            recoveryShaping(ds, rInfo, acts.getInterface(), acts.getAclname(), act.getDirection());
            log(SyslogUtils.LOG_INFO, acts.getUuid() + " clear shaping on interface " + acts.getInterface());
        }
        if (act.getDelay() != null) {
            recoveryDelay(ds, rInfo, acts.getInterface(), acts.getAclname(), act.getDirection());
            log(SyslogUtils.LOG_INFO, acts.getUuid() + " clear delay on interface " + acts.getInterface());
        }
        
    }

    private void recoveryDelay(DataBroker ds, RouterInfo rInfo, String ifname, String aclName,
            Direction direction) throws Exception {
        List<String> cmds = new ArrayList<String>();
        String ifcmd = convertVif(ifname);
     
        if (direction.equals(Direction.Outbound)) {
            cmds.add("delete interface " + ifcmd + " traffic-policy out " + aclName);
        } 
        cmds.add("delete traffic-policy network-emulator " + aclName);
        
        SshResult result = SshConfigUtils.runVyattaSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        } 
    }

    private void recoveryShaping(DataBroker ds, RouterInfo rInfo, String ifname, String aclName,
            Direction direction) throws Exception {
        List<String> cmds = new ArrayList<String>();
        String ifcmd = convertVif(ifname);
     
        if (direction.equals(Direction.Outbound)) {
            cmds.add("delete interface " + ifcmd + " traffic-policy out " + aclName);
        } 
        cmds.add("delete traffic-policy network-emulator " + aclName);
        
        SshResult result = SshConfigUtils.runVyattaSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        }
    }

    private void recoveryRouteChange(DataBroker ds, RouterInfo rInfo, RouteChange routeChange) throws Exception {
        List<String> cmds = new ArrayList<String>();
        
        cmds.add("delete protocols static route " + routeChange.getPrefix().getValue());
        
        SshResult result = SshConfigUtils.runVyattaSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        }
    }

    private void recoveryPacketLoss(DataBroker ds, RouterInfo rInfo, String ifname, String aclName,
            Direction direction) throws Exception {
        List<String> cmds = new ArrayList<String>();
        String ifcmd = convertVif(ifname);
     
        if (direction.equals(Direction.Outbound)) {
            cmds.add("delete interface " + ifcmd + " traffic-policy out " + aclName);
        }  
        cmds.add("delete traffic-policy network-emulator " + aclName);
        
        SshResult result = SshConfigUtils.runVyattaSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        }
    }

    private void portUp(DataBroker ds, RouterInfo rInfo, String ifname) throws Exception {
        // TODO Auto-generated method stub
        List<String> cmds = new ArrayList<String>();
        String ifcmd = convertVif(ifname);     
        
        cmds.add("delete interface " + ifcmd + " disable");
        SshResult result = SshConfigUtils.runVyattaSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        }
    }

    private void recoveryAllLose(DataBroker ds, RouterInfo rInfo, String ifname, String aclName, Direction direction) throws Exception {
        List<String> cmds = new ArrayList<String>();
        String ifcmd = convertVif(ifname);
     
        if (direction.equals(Direction.Inbound)) {
            cmds.add("delete interface " + ifcmd + " firewall in name " + aclName);
        } else if  (direction.equals(Direction.Outbound)) {
            cmds.add("delete interface " + ifcmd + " firewall out name " + aclName);
        } else {
            cmds.add("delete interface " + ifcmd + " firewall in name " + aclName);
            cmds.add("delete interface " + ifcmd + " firewall out name " + aclName);
        }
        
        // 1st commit
        SshResult result = SshConfigUtils.runVyattaSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        }
        
        // 2nd commit
        cmds.clear();
        cmds.add("delete firewall name " + aclName);
        result = SshConfigUtils.runVyattaSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        }
        
    }

    public void setInterfaceState(DataBroker ds, RouterInfo rInfo, String ifname, State state) throws Exception {
       // 1st get current status
        List<Interface> ifList = this.getInterfaces(rInfo);
        
        for (Interface ifInfo : ifList) {
           if (ifInfo.getName().equals(ifname)) {
               // 2nd compare status
               if (ifInfo.isOperStatus()) {
                   if (state.equals(State.Down)) {
                       // need to port down
                       this.portDown(rInfo, ds, ifname);
                       log(SyslogUtils.LOG_INFO, "interface " + ifname + " goes down to create sub-topology");
                   }
                   return;
               }
               if (!ifInfo.isOperStatus()) { 
                   if (state.equals(State.Up)) {
                       // need to port up
                       this.portUp(ds, rInfo, ifname);
                       log(SyslogUtils.LOG_INFO, "interface " + ifname + " goes up to create sub-topology");
                   }
                   return;
               }
           }
        }
        
        throw new Exception("interface " + ifname + " not found on target router");
        
    }
}
