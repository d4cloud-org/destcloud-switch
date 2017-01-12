/*
 * (C) 2016 Kochi University of Technology and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package net.itrc.ricc.destcloud3.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.RpcRegistration;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.Destcloud3Service;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.FaultActionInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.FaultActionOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.FaultActionOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.GetInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.GetInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.GetInterfaceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.RecoveryInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.RecoveryOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.RecoveryOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.ReloadInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.ReloadOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.ReloadOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.SetInterfaceStateInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.SetInterfaceStateOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.SetInterfaceStateOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.rev151228.get._interface.output.Interface;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Destcloud3Provider implements BindingAwareProvider, Destcloud3Service, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Destcloud3Provider.class);
    
    private DataBroker dataService;
    private RpcRegistration<Destcloud3Service> destcloud3Service;

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("Destcloud3Provider Session Initiated");
        this.dataService = session.getSALService(DataBroker.class);
        
        destcloud3Service = session.addRpcImplementation(Destcloud3Service.class, this);
    }

    @Override
    public void close() throws Exception {
        LOG.info("Destcloud3Provider Closed");
        if (destcloud3Service != null) {
            destcloud3Service.close();
        }
    }

    public Future<RpcResult<GetInterfaceOutput>> getInterface(GetInterfaceInput input) {
        GetInterfaceOutputBuilder builder = new GetInterfaceOutputBuilder();
        RouterManager rm = new RouterManager();
        try {
            List<Interface> ifList = rm.getInterfaces(dataService);
            builder.setInterface(ifList);
        } catch (Exception e) {
            LOG.error("got Exception -> " + e.getMessage());
            e.printStackTrace();
            log(SyslogUtils.LOG_ERR, e.getMessage());
        }
        return RpcResultBuilder.success(builder.build()).buildFuture();
        
    }
    
    public Future<RpcResult<FaultActionOutput>> faultAction(FaultActionInput input) {
        FaultActionOutputBuilder builder = new FaultActionOutputBuilder();
        RouterManager rm = new RouterManager();
        try {
            String retUUID = rm.faultAction(dataService, input.getInterface(), input.getAction());
            if (retUUID == null) {
                builder.setResult(false);
            } else {
                builder.setResult(true);
                builder.setUuid(retUUID);
            }
        } catch (Exception e) {
            LOG.error("got Exception -> " + e.getMessage());
            e.printStackTrace();
            builder.setResult(false);
            builder.setMessage(e.getMessage());
            log(SyslogUtils.LOG_ERR, e.getMessage());
        }
        return RpcResultBuilder.success(builder.build()).buildFuture();
    }
    
    public Future<RpcResult<RecoveryOutput>> recovery(RecoveryInput input) {
        RecoveryOutputBuilder builder = new RecoveryOutputBuilder();
        RouterManager rm = new RouterManager();
        try {
            rm.recovery(dataService, input.getUuid());
            builder.setResult(true);
        } catch (Exception e) {
            builder.setResult(false);
            builder.setMessage(e.getMessage());
            log(SyslogUtils.LOG_ERR, e.getMessage());
        }
        return RpcResultBuilder.success(builder.build()).buildFuture();
    }

    @Override
    public Future<RpcResult<SetInterfaceStateOutput>> setInterfaceState(SetInterfaceStateInput input) {
        SetInterfaceStateOutputBuilder builder = new SetInterfaceStateOutputBuilder();
        RouterManager rm = new RouterManager();
        try {
            rm.setInterfaceState(dataService, input.getInterface(), input.getState());
            builder.setResult(true);
        } catch (Exception e) {
            builder.setResult(false);
            builder.setMessage(e.getMessage());
            log(SyslogUtils.LOG_ERR, e.getMessage());
        }
        return RpcResultBuilder.success(builder.build()).buildFuture();
    }

    @Override
    public Future<RpcResult<ReloadOutput>> reload(ReloadInput input) {
        ReloadOutputBuilder builder = new ReloadOutputBuilder();
        RouterManager rm = new RouterManager();
        try {
            List<String> uuids = new ArrayList<String>();
            rm.reload(dataService, uuids);
            builder.setUuid(uuids);
            builder.setResult(true);
        } catch (Exception e) {
            builder.setResult(false);
            builder.setMessage(e.getMessage());
            log(SyslogUtils.LOG_ERR, e.getMessage());
        }
       
        return RpcResultBuilder.success(builder.build()).buildFuture();
    }
    
    private void log(int priority, String msg) {
        try {
            SyslogUtils s = new SyslogUtils("destcloud3", 0, SyslogUtils.LOG_LOCAL4);
            s.log(priority, msg);
        } catch (Exception e) {
            LOG.error("can not create Syslog object");
        }
    }
}
