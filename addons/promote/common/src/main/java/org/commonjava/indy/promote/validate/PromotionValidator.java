/**
 * Copyright (C) 2011-2018 Red Hat, Inc. (https://github.com/Commonjava/indy)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.indy.promote.validate;

import org.apache.commons.lang.StringUtils;
import org.commonjava.indy.IndyMetricsNames;
import org.commonjava.indy.audit.ChangeSummary;
import org.commonjava.indy.content.ContentManager;
import org.commonjava.indy.data.IndyDataException;
import org.commonjava.indy.data.StoreDataManager;
import org.commonjava.indy.measure.annotation.IndyMetrics;
import org.commonjava.indy.measure.annotation.Measure;
import org.commonjava.indy.measure.annotation.MetricNamed;
import org.commonjava.indy.model.core.ArtifactStore;
import org.commonjava.indy.model.core.RemoteRepository;
import org.commonjava.indy.promote.metrics.IndyMetricsPromoteNames;
import org.commonjava.indy.promote.model.GroupPromoteRequest;
import org.commonjava.indy.promote.model.PathsPromoteRequest;
import org.commonjava.indy.promote.model.PromoteRequest;
import org.commonjava.indy.promote.model.ValidationResult;
import org.commonjava.indy.promote.model.ValidationRuleSet;
import org.commonjava.indy.promote.validate.model.ValidationRequest;
import org.commonjava.indy.promote.validate.model.ValidationRuleMapping;
import org.commonjava.maven.galley.event.EventMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Created by jdcasey on 9/11/15.
 */
public class PromotionValidator
{
    private static final String PROMOTE_REPO_PREFIX = "Promote_";

    @Inject
    private PromoteValidationsManager validationsManager;

    @Inject
    private PromotionValidationTools validationTools;

    @Inject
    private StoreDataManager storeDataMgr;

    protected PromotionValidator()
    {
    }

    public PromotionValidator( PromoteValidationsManager validationsManager, PromotionValidationTools validationTools,
                               StoreDataManager storeDataMgr )
    {
        this.validationsManager = validationsManager;
        this.validationTools = validationTools;
        this.storeDataMgr = storeDataMgr;
    }

    /**
     * NOTE: As of Indy 1.2.6, ValidationRequest passed back to enable further post-processing, especially of promotion
     * paths, after promotion takes place. This enables us to avoid re-executing recursive path discovery, for instance.
     *
     * @param request
     * @param result
     * @param baseUrl
     * @return
     * @throws PromotionValidationException
     */
    @IndyMetrics( measure = @Measure( timers = @MetricNamed( name =
                    IndyMetricsPromoteNames.METHOD_PROMOTIONVALIDATOR_VALIDATE
                                    + IndyMetricsNames.TIMER ), meters = @MetricNamed( name =
                    IndyMetricsPromoteNames.METHOD_PROMOTIONVALIDATOR_VALIDATE + IndyMetricsNames.METER ) ) )
    public ValidationRequest validate( PromoteRequest request, ValidationResult result, String baseUrl )
            throws PromotionValidationException
    {
        ValidationRuleSet set = validationsManager.getRuleSetMatching( request.getTargetKey() );

        final ArtifactStore store = getRequestStore( request, baseUrl );
        final ValidationRequest req = new ValidationRequest( request, set, validationTools, store );

        Logger logger = LoggerFactory.getLogger( getClass() );
        if ( set != null )
        {
            logger.debug( "Running validation rule-set for promotion: {}", set.getName() );

            result.setRuleSet( set.getName() );
            List<String> ruleNames = set.getRuleNames();
            if ( ruleNames != null && !ruleNames.isEmpty() )
            {
                try
                {
                    for ( String ruleRef : ruleNames )
                    {
                        String ruleName =
                                new File( ruleRef ).getName(); // flatten in case some path fragment leaks in...

                        ValidationRuleMapping rule = validationsManager.getRuleMappingNamed( ruleName );
                        if ( rule != null )
                        {
                            try
                            {
                                logger.debug( "Running promotion validation rule: {}", rule.getName() );
                                String error = rule.getRule().validate( req );
                                if ( StringUtils.isNotEmpty( error ) )
                                {
                                    logger.debug( "{} failed", rule.getName() );
                                    result.addValidatorError( rule.getName(), error );
                                }
                                else
                                {
                                    logger.debug( "{} succeeded", rule.getName() );
                                }
                            }
                            catch ( Exception e )
                            {
                                if ( e instanceof PromotionValidationException )
                                {
                                    throw (PromotionValidationException) e;
                                }

                                throw new PromotionValidationException(
                                        "Failed to run validation rule: {} for request: {}. Reason: {}", e,
                                        rule.getName(), request, e );
                            }
                        }
                    }
                }
                finally
                {
                    if ( needTempRepo( request ) )
                    {
                        try
                        {
                            final String changeSum = String.format( "Removes the temp remote repo [%s] after promote operation.", store );
                            storeDataMgr.deleteArtifactStore( store.getKey(),
                                                              new ChangeSummary( ChangeSummary.SYSTEM_USER,
                                                                                 changeSum ),
                                                              new EventMetadata().set( ContentManager.SUPPRESS_EVENTS,
                                                                                       true ) );

                            logger.debug( "Promotion temporary repo {} has been deleted for {}", store.getKey(),
                                         request.getSource() );
                        }
                        catch ( IndyDataException e )
                        {
                            logger.warn( "StoreDataManager can not remove artifact stores correctly.", e );
                        }
                    }
                }

            }
        }
        else
        {
            logger.info( "No validation rule-sets are defined for: {}", request.getTargetKey() );
        }

        return req;
    }

    private boolean needTempRepo( PromoteRequest promoteRequest )
            throws PromotionValidationException
    {
        if ( promoteRequest instanceof GroupPromoteRequest )
        {
            return false;
        }
        else if ( promoteRequest instanceof PathsPromoteRequest )
        {
            final Set<String> reqPaths = ( (PathsPromoteRequest) promoteRequest ).getPaths();
            return reqPaths != null && !reqPaths.isEmpty();
        }
        else
        {
            throw new PromotionValidationException( "The promote request is not a valid request, should not happen" );
        }
    }

    private ArtifactStore getRequestStore( PromoteRequest promoteRequest, String baseUrl )
            throws PromotionValidationException
    {
        final ArtifactStore store;
        final Logger logger = LoggerFactory.getLogger( getClass() );
        if ( needTempRepo( promoteRequest ) )
        {
            logger.info( "Promotion temporary repo is needed for {}, points to {} ", promoteRequest.getSource(),
                         baseUrl );
            final PathsPromoteRequest pathsReq = (PathsPromoteRequest) promoteRequest;

            String tempName = PROMOTE_REPO_PREFIX + "tmp_" + pathsReq.getSource().getName() + new SimpleDateFormat(
                    "yyyyMMdd.hhmmss.SSSZ" ).format( new Date() );

            final RemoteRepository tempRemote = new RemoteRepository( tempName, baseUrl );

            tempRemote.setPathMaskPatterns( pathsReq.getPaths() );

            store = tempRemote;
            try
            {
                final ChangeSummary changeSummary =
                        new ChangeSummary( ChangeSummary.SYSTEM_USER, "create temp remote repository" );

                storeDataMgr.storeArtifactStore( tempRemote, changeSummary, false, true, new EventMetadata() );
            }
            catch ( IndyDataException e )
            {
                throw new PromotionValidationException( "Can not store the temp remote repository correctly", e );
            }
        }
        else
        {
            logger.info( "Promotion temporary repo is not needed for {} ", promoteRequest.getSource() );
            try
            {
                store = storeDataMgr.getArtifactStore( promoteRequest.getSource() );
            }
            catch ( IndyDataException e )
            {
                throw new PromotionValidationException( "Failed to retrieve source ArtifactStore: {}. Reason: {}", e,
                                                        promoteRequest.getSource(), e.getMessage() );
            }
        }
        return store;
    }
}
