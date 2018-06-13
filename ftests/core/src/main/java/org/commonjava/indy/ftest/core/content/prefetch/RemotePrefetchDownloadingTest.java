/**
 * Copyright (C) 2013 Red Hat, Inc.
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
package org.commonjava.indy.ftest.core.content.prefetch;

import org.apache.commons.io.FileUtils;
import org.commonjava.indy.ftest.core.AbstractContentManagementTest;
import org.commonjava.indy.model.core.RemoteRepository;
import org.commonjava.indy.pkg.maven.model.MavenPackageTypeDescriptor;
import org.commonjava.indy.test.fixture.core.CoreServerFixture;
import org.commonjava.test.http.expect.ExpectationServer;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import static org.commonjava.indy.model.core.StoreType.remote;
import static org.commonjava.indy.pkg.maven.model.MavenPackageTypeDescriptor.MAVEN_PKG_KEY;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * <b>GIVEN:</b>
 * <ul>
 *     <li>A indy remote store which points to an external repo</li>
 *     <li>The external repo has hierarchy dirs with each html page listing</li>
 *     <li>The external repo has 3 files in different dirs</li>
 *     <li>The indy remote is not set with prefetch enabled</li>
 * </ul>
 *
 * <br/>
 * <b>WHEN:</b>
 * <ul>
 *     <li>Case 1: The indy remote repo is updated with some fields which are not prefetch priority</li>
 *     <li>Case 1: The indy remote repo is updated with prefetch enabled (prefetch priority changed to positive number)</li>
 * </ul>
 *
 * <br/>
 * <b>THEN:</b>
 * <ul>
 *     <li>For case 1: no remote files will be downloaded</li>
 *     <li>For case 2: The 3 remote files will be downloaded automatically after a while without an explicit retrieve through API.(Means by background prefetch)</li>
 * </ul>
 */
public class RemotePrefetchDownloadingTest
        extends AbstractContentManagementTest
{
    @Rule
    public ExpectationServer server = new ExpectationServer();

    @Test
    public void run()
            throws Exception
    {
        final int THREAD_SLEEP_INTERVAL = 1000;

        final String repo1 = "repo1";
        final String pathOrg = "org/";
        final String pathFoo = pathOrg + "foo/";
        final String pathBar = pathFoo + "bar/";
        final String pathVer = pathBar + "1.0/";
        final String pathMeta = pathBar + "maven-metadata.xml";
        final String pathJar = pathVer + "foo-bar-1.0.jar";
        final String pathSrc = pathVer + "foo-bar-1.0-sources.jar";

        // @formatter:off
        final String rootHtml = "<!DOCTYPE html><html><body>"
                + "<a href=\"../\">../</a>"
                + "<a href=\"org/\" title=\"org/\">org/</a>"
                + "</body></html>";
        final String orgHtml="<!DOCTYPE html><html><body>"
                + "<a href=\"../\">../</a>"
                + "<a href=\"foo/\" title=\"foo/\">foo/</a>"
                + "</body></html>";
        final String fooHtml="<!DOCTYPE html><html><body>"
                + "<a href=\"../\">../</a>"
                + "<a href=\"bar/\" title=\"bar/\">bar/</a>"
                + "</body></html>";
        final String barHtml="<!DOCTYPE html><html><body>"
                + "<a href=\"../\">../</a>"
                + "<a href=\"1.0/\" title=\"1.0/\">1.0/</a>"
                + "<a href=\"maven-metadata.xml\" title=\"maven-metadata.xml\">maven-metadata.xml</a>"
                + "</body></html>";
        final String verHtml="<!DOCTYPE html><html><body>"
                + "<a href=\"../\">../</a>"
                + "<a href=\"foo-bar-1.0.jar\" title=\"foo-bar-1.0.jar\">foo-bar-1.0.jar</a>"
                + "<a href=\"foo-bar-1.0-sources.jar\" title=\"foo-bar-1.0-sources.jar\">foo-bar-1.0-sources.jar</a>"
                + "</body></html>";
        final String contentMeta = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                    + "<metadata>"
                                    + "<groupId>org.foo</groupId>"
                                    + "<artifactId>bar</artifactId>"
                                    + "<versioning>"
                                    + "<latest>1.0</latest>"
                                    + "<release>1.0</release>"
                                    + "<versions>"
                                    + "<version>1.0</version>"
                                    + "</versions>"
                                    + "<lastUpdated>20150722164334</lastUpdated>"
                                    + "</versioning>"
                                    + "</metadata>";
        final String contentJar = "This is content for jar";
        final String contentSrc = "This is content for src";
        // @formatter:on

        server.expect( server.formatUrl( repo1, "/" ), 200, rootHtml );
        server.expect( server.formatUrl( repo1, pathOrg ), 200, orgHtml );
        server.expect( server.formatUrl( repo1, pathFoo ), 200, fooHtml );
        server.expect( server.formatUrl( repo1, pathBar ), 200, barHtml );
        server.expect( server.formatUrl( repo1, pathVer ), 200, verHtml );
        server.expect( server.formatUrl( repo1, pathMeta ), 200, contentMeta );
        server.expect( server.formatUrl( repo1, pathJar ), 200, contentJar );
        server.expect( server.formatUrl( repo1, pathSrc ), 200, contentSrc );

        RemoteRepository remote1 =
                new RemoteRepository( MavenPackageTypeDescriptor.MAVEN_PKG_KEY, repo1, server.formatUrl( repo1 ) );
        client.stores().create( remote1, "adding remote", RemoteRepository.class );

        System.out.println( String.format( "Indy HOME:%s", fixture.getBootOptions().getIndyHome() ) );

        File fileMeta = Paths.get( fixture.getBootOptions().getIndyHome(), "var/lib/indy/storage", MAVEN_PKG_KEY,
                                   remote.singularEndpointName() + "-" + repo1, pathMeta ).toFile();
        File fileJar = Paths.get( fixture.getBootOptions().getIndyHome(), "var/lib/indy/storage", MAVEN_PKG_KEY,
                                  remote.singularEndpointName() + "-" + repo1, pathJar ).toFile();
        File fileSrc = Paths.get( fixture.getBootOptions().getIndyHome(), "var/lib/indy/storage", MAVEN_PKG_KEY,
                                  remote.singularEndpointName() + "-" + repo1, pathSrc ).toFile();

        assertThat( fileMeta.exists(), equalTo( false ) );
        assertThat( fileJar.exists(), equalTo( false ) );
        assertThat( fileSrc.exists(), equalTo( false ) );

        // Only when prefetch priority change to positive number will trigger prefetch
        remote1.setNfcTimeoutSeconds( 120 );
        client.stores().update( remote1, "change nfc timeout" );
        Thread.sleep( THREAD_SLEEP_INTERVAL );
        assertThat( fileMeta.exists(), equalTo( false ) );
        assertThat( fileJar.exists(), equalTo( false ) );
        assertThat( fileSrc.exists(), equalTo( false ) );

        remote1.setPrefetchListingType( RemoteRepository.PREFETCH_LISTING_TYPE_HTML );
        remote1.setPrefetchPriority( 1 );
        client.stores().update( remote1, "change prefetch priority" );
        Thread.sleep( THREAD_SLEEP_INTERVAL );
        assertThat( fileMeta.exists(), equalTo( true ) );
        assertThat( fileJar.exists(), equalTo( true ) );
        assertThat( fileSrc.exists(), equalTo( true ) );
        assertContent( fileMeta, contentMeta );
        assertContent( fileJar, contentJar );
        assertContent( fileSrc, contentSrc );
    }

    private void assertContent( File file, String content )
            throws IOException
    {
        assertThat( FileUtils.readFileToString( file ), equalTo( content ) );
    }

    @Override
    protected boolean createStandardTestStructures()
    {
        return false;
    }

    @Override
    protected void initTestConfig( CoreServerFixture fixture )
            throws IOException
    {
        super.initTestConfig( fixture );
        writeConfigFile( "conf.d/prefetch.conf", "[prefetch]\nenabled=true\nprefetch.batchsize=3" );
    }
}
