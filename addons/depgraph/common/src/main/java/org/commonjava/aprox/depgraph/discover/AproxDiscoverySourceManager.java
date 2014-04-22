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
package org.commonjava.aprox.depgraph.discover;

import static org.commonjava.aprox.depgraph.util.AproxDepgraphUtils.APROX_URI_PREFIX;
import static org.commonjava.aprox.depgraph.util.AproxDepgraphUtils.toDiscoveryURI;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;

import org.commonjava.aprox.data.ProxyDataException;
import org.commonjava.aprox.data.StoreDataManager;
import org.commonjava.aprox.inject.Production;
import org.commonjava.aprox.model.ArtifactStore;
import org.commonjava.aprox.model.StoreKey;
import org.commonjava.aprox.model.StoreType;
import org.commonjava.aprox.util.LocationUtils;
import org.commonjava.maven.atlas.graph.workspace.GraphWorkspace;
import org.commonjava.maven.cartographer.data.CartoDataException;
import org.commonjava.maven.cartographer.discover.DiscoverySourceManager;
import org.commonjava.maven.galley.model.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Production
@Default
public class AproxDiscoverySourceManager
    implements DiscoverySourceManager
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    private StoreDataManager stores;

    protected AproxDiscoverySourceManager()
    {
    }

    public AproxDiscoverySourceManager( final StoreDataManager stores )
    {
        this.stores = stores;
    }

    @Override
    public URI createSourceURI( final String source )
    {
        final StoreKey key = StoreKey.fromString( normalize( source ) );
        logger.debug( "Got source-URI store-key: '{}'", key );
        final URI result = toDiscoveryURI( key );
        logger.debug( "Resulting source URI: '{}'", result );
        return result;
    }

    private String normalize( final String source )
    {
        logger.debug( "Normalizing source URI: '{}'", source );
        if ( source.startsWith( APROX_URI_PREFIX ) )
        {
            final String result = source.substring( APROX_URI_PREFIX.length() );
            logger.debug( "Normalized source URI: '{}'", result );
            return result;
        }

        return source;
    }

    @Override
    public String getFormatHint()
    {
        return "<store-type>:<store-name>";
    }

    @Override
    public boolean activateWorkspaceSources( final GraphWorkspace ws, final String... sources )
        throws CartoDataException
    {
        boolean result = false;
        if ( ws != null )
        {
            for ( final String src : sources )
            {
                final StoreKey key = StoreKey.fromString( normalize( src ) );
                if ( key.getType() == StoreType.group )
                {
                    try
                    {
                        final List<ArtifactStore> orderedStores = stores.getOrderedConcreteStoresInGroup( key.getName() );
                        for ( final ArtifactStore store : orderedStores )
                        {
                            final URI uri = toDiscoveryURI( store.getKey() );
                            if ( ws.getActiveSources()
                                   .contains( uri ) )
                            {
                                continue;
                            }

                            ws.addActiveSource( uri );
                            result = result || ws.getActiveSources()
                                                 .contains( uri );
                        }
                    }
                    catch ( final ProxyDataException e )
                    {
                        throw new CartoDataException( "Failed to lookup ordered concrete stores for: {}. Reason: {}", e, key, e.getMessage() );
                    }
                }
                else
                {
                    final URI uri = toDiscoveryURI( key );
                    if ( !ws.getActiveSources()
                            .contains( uri ) )
                    {
                        ws.addActiveSource( uri );
                        result = result || ws.getActiveSources()
                                             .contains( uri );
                    }
                }
            }
        }

        return result;
    }

    @Override
    public Location createLocation( final Object source )
    {
        final StoreKey key = StoreKey.fromString( normalize( source.toString() ) );
        return LocationUtils.toCacheLocation( key );
    }

    @Override
    public List<? extends Location> createLocations( final Object... sources )
    {
        final List<StoreKey> keys = new ArrayList<StoreKey>( sources.length );
        for ( final Object source : sources )
        {
            keys.add( StoreKey.fromString( normalize( source.toString() ) ) );
        }

        return LocationUtils.toCacheLocations( keys );
    }

    @Override
    public List<? extends Location> createLocations( final Collection<Object> sources )
    {
        final List<StoreKey> keys = new ArrayList<StoreKey>( sources.size() );
        for ( final Object source : sources )
        {
            keys.add( StoreKey.fromString( normalize( source.toString() ) ) );
        }

        return LocationUtils.toCacheLocations( keys );
    }

}
