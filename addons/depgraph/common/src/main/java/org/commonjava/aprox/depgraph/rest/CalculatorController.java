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
package org.commonjava.aprox.depgraph.rest;

import java.io.InputStream;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.commonjava.aprox.AproxWorkflowException;
import org.commonjava.aprox.depgraph.inject.DepgraphSpecific;
import org.commonjava.aprox.depgraph.util.ConfigDTOHelper;
import org.commonjava.aprox.util.ApplicationStatus;
import org.commonjava.maven.cartographer.data.CartoDataException;
import org.commonjava.maven.cartographer.dto.GraphCalculation;
import org.commonjava.maven.cartographer.dto.GraphComposition;
import org.commonjava.maven.cartographer.dto.GraphDescription;
import org.commonjava.maven.cartographer.dto.GraphDifference;
import org.commonjava.maven.cartographer.ops.CalculationOps;
import org.commonjava.web.json.ser.JsonSerializer;

@ApplicationScoped
public class CalculatorController
{

    @Inject
    private CalculationOps ops;

    @Inject
    @DepgraphSpecific
    private JsonSerializer serializer;

    @Inject
    private ConfigDTOHelper configHelper;

    public String difference( final InputStream configStream, final String encoding )
        throws AproxWorkflowException
    {
        final GraphComposition dto = configHelper.readGraphComposition( configStream, encoding );
        return difference( dto );
    }

    public String difference( final String json )
        throws AproxWorkflowException
    {
        final GraphComposition dto = configHelper.readGraphComposition( json );
        return difference( dto );
    }

    private String difference( final GraphComposition dto )
        throws AproxWorkflowException
    {
        try
        {
            final List<GraphDescription> graphs = dto.getGraphs();

            if ( graphs.size() != 2 )
            {
                throw new AproxWorkflowException( ApplicationStatus.BAD_REQUEST,
                                                  "You must specify EXACTLY two graph descriptions (GAV-set with optional filter preset) in order to perform a diff." );
            }
            else
            {
                final GraphDifference<?> difference = ops.difference( graphs.get( 0 ), graphs.get( 1 ) );
                return serializer.toString( difference );
            }
        }
        catch ( final CartoDataException e )
        {
            throw new AproxWorkflowException( "Failed to retrieve graph(s): {}", e, e.getMessage() );
        }
    }

    public String calculate( final InputStream configStream, final String encoding )
        throws AproxWorkflowException
    {
        final GraphComposition dto = configHelper.readGraphComposition( configStream, encoding );
        return calculate( dto );
    }

    public String calculate( final String json )
        throws AproxWorkflowException
    {
        final GraphComposition dto = configHelper.readGraphComposition( json );
        return calculate( dto );
    }

    public String calculate( final GraphComposition dto )
        throws AproxWorkflowException
    {
        try
        {
            final List<GraphDescription> graphs = dto.getGraphs();

            if ( graphs.size() < 2 )
            {
                throw new AproxWorkflowException( ApplicationStatus.BAD_REQUEST,
                                                  "You must specify at least two graph descriptions (GAV-set with optional filter preset) in order to perform a calculation." );
            }
            else if ( dto.getCalculation() == null )
            {
                throw new AproxWorkflowException( ApplicationStatus.BAD_REQUEST, "You must specify a calculation type." );
            }
            else
            {
                final GraphCalculation result = ops.calculate( dto );

                return serializer.toString( result );
            }
        }
        catch ( final CartoDataException e )
        {
            throw new AproxWorkflowException( "Failed to retrieve graph(s): {}", e, e.getMessage() );
        }
    }

}
