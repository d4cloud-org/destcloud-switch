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

public class JuniperManager extends RouterManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(JuniperManager.class);
    
    private String phyIfRegEx;
    private Pattern phyIfPat;
    private String phyIf2RegEx;
    private Pattern phyIf2Pat;
    private String descRegEx;
    private Pattern descPat;
    private String macRegEx;
    private Pattern macPat;
    private String logiRegEx;
    private Pattern logiPat;
    private String flagsRegEx;
    private Pattern flagsPat;
    private String destRegEx;
    private Pattern destPat;
    private String localRegEx;
    private Pattern localPat;
    
    public JuniperManager() {
        phyIfRegEx = "^Physical interface: (\\S+), (\\S+), Physical link is (\\S+)$";
        phyIfPat = Pattern.compile(phyIfRegEx);
        phyIf2RegEx = "^Physical interface: (\\S+)$";
        phyIf2Pat = Pattern.compile(phyIf2RegEx);
        
        descRegEx = "^\\s+Description: (.*)$";
        descPat = Pattern.compile(descRegEx);
        macRegEx = "^\\s+Current address: (\\S+),.*$";
        macPat = Pattern.compile(macRegEx);
        logiRegEx = "^\\s+Logical interface (\\S+) .*$";
        logiPat = Pattern.compile(logiRegEx);
        flagsRegEx = "^\\s+Flags: .*$";
        flagsPat = Pattern.compile(flagsRegEx);
        destRegEx = "^\\s+Destination: (\\S+), Local: (\\S+), Broadcast: (\\S+)$";
        destPat = Pattern.compile(destRegEx);
        localRegEx = "^\\s+Local: (\\S+)$";
        localPat = Pattern.compile(localRegEx);
        
    }

    public List<Interface> getInterfaces(RouterInfo rInfo) {
        List<Interface> ifList = new ArrayList<Interface>();
        List<InterfaceBuilder> ifbList = new ArrayList<InterfaceBuilder>();
        
        SshResult result = SshConfigUtils.runJuniperShowCommand(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                    rInfo.getPassword(), "show interfaces");
        if (!result.isResult()) {
            log(SyslogUtils.LOG_ERR, "SSH utility return -> " + result.getMessage());
            return ifList;
        }
        
        LOG.error("recv str from JUNOS => " + result.getMessage());
        
        // parse it
        String retMsg = result.getMessage().replaceAll("\r\n", "\n");
        String[] msg = retMsg.split("\n");
        
        Integer idx = 0;
        while (idx < msg.length) {
           Matcher m = phyIfPat.matcher(msg[idx]);
           if (m.matches()) {
               idx++;
               idx = getPhysicalInterface(msg, idx, m.group(1), m.group(2), m.group(3), ifbList);
           } else {
               Matcher m2 = phyIf2Pat.matcher(msg[idx]);
               if (m2.matches()) {
                   idx++;
                   idx = getPhysicalInterface(msg, idx, m2.group(1), "enabled", "", ifbList);
               } else {
                   idx++;
               }
           }
        }
        
        for (InterfaceBuilder ifb : ifbList) {
            ifList.add(ifb.build());
        }
        
        return ifList;
    }

    private Integer getPhysicalInterface(String[] msg, Integer idx, String name, String enabled, String link,
            List<InterfaceBuilder> ifbList) {
        InterfaceBuilder ib = new InterfaceBuilder();
        ib.setPhysical(true);
        ib.setName(name);
        ib.setAdminStatus(enabled.equals("Enabled"));
        ib.setOperStatus(false);
        
        while (idx < msg.length) {
            String str = msg[idx];
            if (str.contains("Physical interface")) {
                // reach to next 
                break;
            }
            Matcher m = descPat.matcher(str);
            if (m.matches()) {
                ib.setDescription(m.group(1));
                idx++;
                continue;
            }
            m = macPat.matcher(str);
            if (m.matches()) {
                ib.setMacAddress(m.group(1));
                idx++;
                continue;
            }
            m = logiPat.matcher(str);
            if (m.matches()) {
                // get logical interfaces
                idx = getLogicalInterface(msg, idx, ifbList, name, ib);
                continue;
            }
            idx++;
        }
        
        ifbList.add(ib);
        return idx;
    }

    private Integer getLogicalInterface(String[] msg, Integer idx, List<InterfaceBuilder> ifbList, String name, InterfaceBuilder parentIfb) {
        InterfaceBuilder ifb = new InterfaceBuilder();
        List<Ipv4Prefix> v4List = new ArrayList<Ipv4Prefix>();
        List<Ipv6Prefix> v6List = new ArrayList<Ipv6Prefix>();
    
        Matcher m = logiPat.matcher(msg[idx]);
        if (!m.matches()) {
            log(SyslogUtils.LOG_ERR, "Unknown format for logical interface -> " + msg[idx]);
            idx++;
            return idx;
        }
        
        idx++;
        
        ifb.setName(m.group(1));
        ifb.setParentDevice(name);
        ifb.setAdminStatus(true);
        ifb.setPhysical(false);
        
        // get parameters
        Boolean fFlag = false;
        while (idx < msg.length) {
            String str = msg[idx];
            m = descPat.matcher(str);
            if (m.matches()) {
                ifb.setDescription(m.group(1));
                idx++;
                continue;
            }
            m = destPat.matcher(str);
            if (m.matches()) {
                String prefix = m.group(1);
                String addr = m.group(2);
                Integer pos = prefix.indexOf('/');
                String ifPrefix = addr + "/" + prefix.substring(pos + 1);
               
                Ipv4Prefix pre = new Ipv4Prefix(ifPrefix);
                v4List.add(pre);
                idx++;
                continue;
            }
            m = flagsPat.matcher(str);
            if (!fFlag && m.matches()) {
                if (str.contains("Up")) {
                    ifb.setOperStatus(true);
                } else {
                    ifb.setOperStatus(false);
                }
                String vlanRegEx = "^.* VLAN-Tag \\[ .*\\.(\\d+) \\] .*$";
                Pattern vlanPat = Pattern.compile(vlanRegEx);
                m = vlanPat.matcher(str);
                if (m.matches()) {
                    String tag = m.group(1);
                    ifb.setVlanId(Integer.parseInt(tag));
                }
                fFlag = true;
                idx++;
                continue;
            }
            m = localPat.matcher(str);
            if (m.matches()) {
                String addr = m.group(1);
                Boolean isv6 = addr.contains(":");
                String ifPrefix = "";
                Integer pos = addr.indexOf('/');
                if (isv6) {
                    ifPrefix = addr;
                    if (pos == -1) {
                        ifPrefix = ifPrefix + "/128";
                    }
                    Ipv6Prefix pre = new Ipv6Prefix(ifPrefix);
                    v6List.add(pre);
                } else {
                    ifPrefix = addr;
                    if (pos == -1) {
                        ifPrefix = ifPrefix + "/32";
                    }
                    Ipv4Prefix pre = new Ipv4Prefix(ifPrefix);
                    v4List.add(pre);
                }
                idx++;
                continue;
            }
            m = logiPat.matcher(str);
            if (m.matches()) {
                break;
            }
            m = phyIfPat.matcher(str);
            if (m.matches()) {
                break;
            }
            m = macPat.matcher(str);
            if (m.matches()) {
                ifb.setMacAddress(m.group(1));
            }
            idx++;
        }
        
        if (ifb.getMacAddress() == null || ifb.getMacAddress().isEmpty()) {
            ifb.setMacAddress(parentIfb.getMacAddress());
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
            throw new Exception("operation not supported for device");
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
            throw new Exception("operation not supported for device");
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

    private ActionsBuilder doShaping(RouterInfo rInfo, DataBroker ds, String ifname, Long shaping,
            Direction direction) throws Exception {
        List<String> cmds = new ArrayList<String>();
        
        if (shaping < 32000) {
            throw new Exception ("shaping late too small (minimum 32k)");
        }
        
        Long currentTime = System.currentTimeMillis();
        String suffix = currentTime.toString();
        String aclName = "acl" + suffix;
        String polName = "pol" + suffix;
        String ifcmd = convertVlan(ifname);

        cmds.add("set firewall policer " + polName + " if-exceeding bandwidth-limit " + shaping.toString());
        cmds.add("set firewall policer " + polName + " if-exceeding burst-size-limit 625000");
        cmds.add("set firewall policer " + polName + " then discard");
        cmds.add("set firewall family inet filter " + aclName + " term 1 from source-address 0.0.0.0/0");
        cmds.add("set firewall family inet filter " + aclName + " term 1 from destination-address 0.0.0.0/0");
        cmds.add("set firewall family inet filter " + aclName + " term 1 then policer " + polName);
        cmds.add("set firewall family inet filter " + aclName + " term 2 then accept");
        if (direction.equals(Direction.Inbound)) {
            cmds.add("set interface " + ifcmd + " family inet filter input " + aclName);
        } else if  (direction.equals(Direction.Outbound)) {
            cmds.add("set interface " + ifcmd + " family inet filter output " + aclName);
        } else {
            cmds.add("set interface " + ifcmd + " family inet filter input " + aclName);
            cmds.add("set interface " + ifcmd + " family inet filter output " + aclName);
        }
        
        String uuid = generateUUID();
        
        // do ssh
        SshResult result = SshConfigUtils.runJuniperSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        }
        
        // store Action
        ActionsBuilder acts = new ActionsBuilder();
        acts.setInterface(ifname);
        acts.setUuid(uuid);
        acts.setAclname(suffix);
        ActionBuilder act = new ActionBuilder();
        act.setShaping(shaping);
        act.setDirection(direction);
        
        acts.setAction(act.build());
        
        return acts;
    }

    private ActionsBuilder routeChange(RouterInfo rInfo, DataBroker ds, RouteChange routeChange) throws Exception {
        List<String> cmds = new ArrayList<String>();
        cmds.add("set routing-options static route " + routeChange.getPrefix().getValue() + " next-hop " + 
                routeChange.getNexthop().getValue());
        
        String uuid = generateUUID();
        
        // do ssh
        SshResult result = SshConfigUtils.runJuniperSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
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

    private ActionsBuilder portDown(RouterInfo rInfo, DataBroker ds, String ifname) throws Exception {
        List<String> cmds = new ArrayList<String>();
        String ifcmd = convertVlan(ifname);

        cmds.add("set interface " + ifcmd + " disable");
        
        String uuid = generateUUID();
        
        // do ssh
        SshResult result = SshConfigUtils.runJuniperSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
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
        String ifcmd = convertVlan(ifname);

        cmds.add("set firewall family inet filter " + aclName + " term 1 from source-address 0.0.0.0/0");
        cmds.add("set firewall family inet filter " + aclName + " term 1 from destination-address 0.0.0.0/0");
        cmds.add("set firewall family inet filter " + aclName + " term 1 then discard");
        if (direction.equals(Direction.Inbound)) {
            cmds.add("set interface " + ifcmd + " family inet filter input " + aclName);
        } else if  (direction.equals(Direction.Outbound)) {
            cmds.add("set interface " + ifcmd + " family inet filter output " + aclName);
        } else {
            cmds.add("set interface " + ifcmd + " family inet filter input " + aclName);
            cmds.add("set interface " + ifcmd + " family inet filter output " + aclName);
        }
        
        String uuid = generateUUID();
        
        // do ssh
        SshResult result = SshConfigUtils.runJuniperSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
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
    
    private String convertVlan(String ifname) throws Exception {
        String cmdstr = "";
        Integer pos = ifname.indexOf('.');
        if (pos == -1) {
            cmdstr = ifname;
            return cmdstr;
        }
        cmdstr = ifname.substring(0, pos) + " unit " + ifname.substring(pos + 1); 
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
        if (act.getRouteChange() != null) {
            recoveryRouteChange(ds, rInfo, act.getRouteChange());
            log(SyslogUtils.LOG_INFO, acts.getUuid() + " static route (" + act.getRouteChange().getPrefix().getValue() + " to " + act.getRouteChange().getNexthop().getValue()
                    + ") is removed");
        }
        if (act.getShaping() != null) {
            recoveryShaping(ds, rInfo, acts.getInterface(), acts.getAclname(), act.getDirection());
            log(SyslogUtils.LOG_INFO, acts.getUuid() + " clear shaping on interface " + acts.getInterface());
        }
    }

    private void recoveryShaping(DataBroker ds, RouterInfo rInfo, String ifname, String suffix,
            Direction direction) throws Exception {
        List<String> cmds = new ArrayList<String>();
        String ifcmd = convertVlan(ifname);
        String aclName = "acl" + suffix;
        String polName = "pol" + suffix;
     
        if (direction.equals(Direction.Inbound)) {
            cmds.add("delete interface " + ifcmd + " family inet filter input " + aclName);
        } else if  (direction.equals(Direction.Outbound)) {
            cmds.add("delete interface " + ifcmd + " family inet filter output " + aclName);
        } else {
            cmds.add("delete interface " + ifcmd + " family inet filter input " + aclName);
            cmds.add("delete interface " + ifcmd + " family inet filter output " + aclName);
        }
        cmds.add("delete firewall family inet filter " + aclName);
        cmds.add("delete firewall policer " + polName);
        
        SshResult result = SshConfigUtils.runJuniperSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        }
    }

    private void recoveryRouteChange(DataBroker ds, RouterInfo rInfo, RouteChange routeChange) throws Exception {
        List<String> cmds = new ArrayList<String>();
    
        cmds.add("delete routing-options static route " + routeChange.getPrefix().getValue() + " next-hop " 
                + routeChange.getNexthop().getValue());
        
        SshResult result = SshConfigUtils.runJuniperSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        }  
    }

    private void portUp(DataBroker ds, RouterInfo rInfo, String ifname) throws Exception {
        List<String> cmds = new ArrayList<String>();
        String ifcmd = convertVlan(ifname);
 
        cmds.add("delete interface " + ifcmd + " disable");
        
        SshResult result = SshConfigUtils.runJuniperSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
                rInfo.getPassword(), cmds);
        if (!result.isResult()) {
            throw new Exception(result.getMessage());
        }
    }

    private void recoveryAllLose(DataBroker ds, RouterInfo rInfo, String ifname, String aclName,
            Direction direction) throws Exception {
        List<String> cmds = new ArrayList<String>();
        String ifcmd = convertVlan(ifname);
     
        if (direction.equals(Direction.Inbound)) {
            cmds.add("delete interface " + ifcmd + " family inet filter input " + aclName);
        } else if  (direction.equals(Direction.Outbound)) {
            cmds.add("delete interface " + ifcmd + " family inet filter output " + aclName);
        } else {
            cmds.add("delete interface " + ifcmd + " family inet filter input " + aclName);
            cmds.add("delete interface " + ifcmd + " family inet filter output " + aclName);
        }
        cmds.add("delete firewall family inet filter " + aclName);
        
        SshResult result = SshConfigUtils.runJuniperSetCommandList(rInfo.getIpaddress().getValue(), rInfo.getUsername(), 
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
