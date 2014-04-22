/*******************************************************************************
 * Copyright (c) 2014 Red Hat, Inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.commonjava.aprox.sec.rest.admin;

import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import javax.ws.rs.core.Response;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.commonjava.aprox.model.StoreType;
import org.commonjava.aprox.rest.admin.DeployPointAdminResource;
import org.commonjava.badgr.model.Permission;

@Decorator
@RequiresAuthentication
public abstract class DeployPointAdminResourceSecurity
    implements DeployPointAdminResource
{

    @Delegate
    @Inject
    @Default
    private DeployPointAdminResource delegate;

    @Override
    public Response create()
    {
        SecurityUtils.getSubject()
                     .isPermitted( Permission.name( StoreType.deploy_point.name(), Permission.ADMIN ) );

        return delegate.create();
    }

    @Override
    public Response store( final String name )
    {
        SecurityUtils.getSubject()
                     .isPermitted( Permission.name( StoreType.deploy_point.name(), Permission.ADMIN ) );

        return delegate.store( name );
    }

    @Override
    public Response getAll()
    {
        SecurityUtils.getSubject()
                     .isPermitted( Permission.name( StoreType.deploy_point.name(), Permission.ADMIN ) );

        return delegate.getAll();
    }

    @Override
    public Response get( final String name )
    {
        SecurityUtils.getSubject()
                     .isPermitted( Permission.name( StoreType.deploy_point.name(), Permission.ADMIN ) );

        return delegate.get( name );
    }

    @Override
    public Response delete( final String name )
    {
        SecurityUtils.getSubject()
                     .isPermitted( Permission.name( StoreType.deploy_point.name(), Permission.ADMIN ) );

        return delegate.delete( name );
    }

}
