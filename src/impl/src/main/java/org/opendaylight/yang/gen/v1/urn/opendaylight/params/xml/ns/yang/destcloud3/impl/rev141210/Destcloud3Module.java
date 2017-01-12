/*
 * (C) 2016 Kochi University of Technology and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.impl.rev141210;

import net.itrc.ricc.destcloud3.impl.Destcloud3Provider;

public class Destcloud3Module extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.impl.rev141210.AbstractDestcloud3Module {
    public Destcloud3Module(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public Destcloud3Module(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.destcloud3.impl.rev141210.Destcloud3Module oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        Destcloud3Provider provider = new Destcloud3Provider();
        getBrokerDependency().registerProvider(provider);
        return provider;
    }

}
