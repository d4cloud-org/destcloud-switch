package net.itrc.ricc.destcloud3.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.SetInterfaceStateInput.State;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.get._interface.output.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.types.rev151228.fault.action.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.types.rev151228.fault.action.action.RouteChange;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.node.rev151228.FaultActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.node.rev151228.RouterInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.node.rev151228.RouterInfo.RouterType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.node.rev151228.fault.actions.Actions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.node.rev151228.fault.actions.ActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.node.rev151228.fault.actions.ActionsKey;

public class RouterManager {

    private static final Logger LOG = LoggerFactory.getLogger(RouterManager.class);
    

    protected String generateUUID() {
        UUID u1 = UUID.randomUUID();
        return u1.toString();
    }
    
    protected void log(int priority, String msg) {
        try {
            SyslogUtils s = new SyslogUtils("destcloud3", 0, SyslogUtils.LOG_LOCAL4);
            s.log(priority, msg);
        } catch (Exception e) {
            LOG.error("can not creat syslog object");
        }
    }
    
    public List<Interface> getInterfaces(DataBroker ds) {
        List<Interface> ifList = new ArrayList<Interface>();
     
        RouterInfo rInfo = getRouterInfo(ds);
        if (rInfo == null) {
            return ifList;
        }
        
        // do SSH
        if (rInfo.getRouterType().equals(RouterInfo.RouterType.Juniper)) {
            JuniperManager jm = new JuniperManager();
            ifList = jm.getInterfaces(rInfo);
        } else if (rInfo.getRouterType().equals(RouterInfo.RouterType.Vyos)){
            VyosManager vm = new VyosManager();
            ifList = vm.getInterfaces(rInfo);
        } else {
            LOG.error("no router-type defined");
            return ifList;
        }
        
        // store it to data store
        
        return ifList;
    }

    private RouterInfo getRouterInfo(DataBroker ds) {
        InstanceIdentifier<RouterInfo> iid = InstanceIdentifier.create(RouterInfo.class);
        Optional<RouterInfo> optData = null;
        ReadOnlyTransaction readTx = ds.newReadOnlyTransaction();
        
        try {
           optData = readTx.read(LogicalDatastoreType.CONFIGURATION, iid).checkedGet();
        } catch (Exception e) {
            LOG.error("Failed to get router-info");
            return null;
        }
        
        if (optData == null || !optData.isPresent()) {
            LOG.error("No router-info on datastore");
            return null;
        }
        return optData.get();
    }

    public String faultAction(DataBroker ds, String ifname, Action action) throws Exception {
        RouterInfo rInfo = getRouterInfo(ds);
        if (rInfo == null) {
            throw new Exception("No router found on datastore");
        }
        
        // sanity check
        Integer acts = 0;
        if (action.isPortDown() != null && action.isPortDown()) {
            LOG.debug("Action is port-down");
            acts++;
        }
        if (action.isAllLose() != null && action.isAllLose()) {
            LOG.debug("Action is all-lose");
            if (action.getDirection() == null) {
                throw new Exception("all-lose need direction");
            }
            acts++;
        }
        if (action.getPacketLoss() != null) {
            LOG.debug("Action is packet-loss");
            if (action.getDirection() == null) {
                throw new Exception("packet-loss need direction");
            }
            acts++;
        }
        if (action.getRouteChange() != null) {
            RouteChange rc = action.getRouteChange();
            if (rc.getPrefix() == null || rc.getNexthop() == null || rc.getPrefix().getValue().isEmpty() ||
                    rc.getNexthop().getValue().isEmpty()) {
                throw new Exception("route-change missing parameter");
            }
            LOG.debug("Action is route-change");
            acts++;
        }
        if (action.getShaping() != null) {
            LOG.debug("Action is shaping");
            if (action.getDirection() == null) {
                throw new Exception("shaping need direction");
            }
            acts++;
        }
        if (action.getDelay() != null) {
            LOG.debug("Action is delay");
            if (action.getDirection() == null) {
                throw new Exception("delay need direction");
            }
            acts++;
        }
        
        if (acts != 1) {
            throw new Exception("Invalid paramater");
        }
  
        Actions retAction = null;
        if (rInfo.getRouterType().equals(RouterInfo.RouterType.Juniper)) {
            JuniperManager jm = new JuniperManager();
            retAction = jm.faultAction(rInfo, ds, ifname, action);
        } else if (rInfo.getRouterType().equals(RouterInfo.RouterType.Vyos)){
            VyosManager vm = new VyosManager(); 
            retAction = vm.faultAction(rInfo, ds, ifname, action);
        } else {
            throw new Exception("no router-type defined");
        }
        
        if (retAction == null) {
            throw new Exception("failure-action got an error");
        }
       
        InstanceIdentifier<Actions> iid = InstanceIdentifier.builder(FaultActions.class).
                child(Actions.class, new ActionsKey(retAction.getUuid())).build();
        WriteTransaction writeTx = ds.newWriteOnlyTransaction();
        writeTx.merge(LogicalDatastoreType.CONFIGURATION, iid, retAction, true);
        try {
            writeTx.submit();
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("can not write datastore");
        }
        
        
        return retAction.getUuid();
    }

    public void recovery(DataBroker ds, String uuid) throws Exception {
        RouterInfo rInfo = getRouterInfo(ds);
        if (rInfo == null) {
            throw new Exception("No router found on datastore");
        }
        
        // Find FaultAction
        ReadOnlyTransaction readTx = ds.newReadOnlyTransaction();
        InstanceIdentifier<Actions> iid = InstanceIdentifier.builder(FaultActions.class).
                child(Actions.class, new ActionsKey(uuid)).build();
        Optional<Actions> optData = null;
        try {
            optData = readTx.read(LogicalDatastoreType.CONFIGURATION, iid).checkedGet();
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("UUID not found on datastore");
        }
        if (optData == null || !optData.isPresent()) {
            throw new Exception("UUID not found on datastore");
        }
        
        Actions acts = optData.get();
        if (rInfo.getRouterType().equals(RouterType.Juniper)) {
            JuniperManager jm = new JuniperManager();
            jm.recovery(ds, rInfo, acts);
        } else if (rInfo.getRouterType().equals(RouterType.Vyos)) {
            VyosManager vm = new VyosManager();
            vm.recovery(ds, rInfo, acts);
        } else {
            throw new Exception("no router-type defined");
        }
        
        // change state
        ActionsBuilder nActs = new ActionsBuilder(acts);
        nActs.setRecovered(true);
        
        WriteTransaction writeTx = ds.newWriteOnlyTransaction();
        InstanceIdentifier<Actions> iid2 = InstanceIdentifier.builder(FaultActions.class).
                child(Actions.class, new ActionsKey(nActs.getUuid())).build();
        Actions n = nActs.build();
        writeTx.merge(LogicalDatastoreType.CONFIGURATION, iid2, n, true);
        try {
            writeTx.submit();
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("can not update datastore");
        }
        
    }

    public void setInterfaceState(DataBroker ds, String ifname, State state) throws Exception {
        RouterInfo rInfo = getRouterInfo(ds);
        if (rInfo == null) {
            throw new Exception("No router found on datastore");
        }
        
        if (rInfo.getRouterType().equals(RouterType.Juniper)) {
            JuniperManager jm = new JuniperManager();
            jm.setInterfaceState(ds, rInfo, ifname, state);
        } else if (rInfo.getRouterType().equals(RouterType.Vyos)) {
            VyosManager vm = new VyosManager();
            vm.setInterfaceState(ds, rInfo, ifname, state);
        } else {
            throw new Exception("no router-type defined");
        }
        
    }

    public void reload(DataBroker ds, List<String> uuids) throws Exception {
        ReadOnlyTransaction readTx = ds.newReadOnlyTransaction();
        InstanceIdentifier<FaultActions> iid = InstanceIdentifier.create(FaultActions.class);
        
        Optional<FaultActions> optData = null;
        try {
            optData = readTx.read(LogicalDatastoreType.CONFIGURATION, iid).checkedGet();
        } catch (Exception e) {
            throw new Exception("can not get fault-actions from datastore");
        }
        
        if (optData == null || !optData.isPresent()) {
            return;
        }
        
        List<Actions> actList = new ArrayList<Actions>();
        FaultActions fActs = optData.get();
        if (fActs == null || fActs.getActions() == null) {
            return;
        }
        for (Actions acts : fActs.getActions()) {
            if (!acts.isRecovered()) {
                actList.add(acts);
            }
        }
        
        // sort
        Collections.sort(actList, new ActionsComparator());
        
        // recover each fault
        for (Actions a : actList) {
            this.recovery(ds, a.getUuid());
            uuids.add(a.getUuid());
        }
    }
}
