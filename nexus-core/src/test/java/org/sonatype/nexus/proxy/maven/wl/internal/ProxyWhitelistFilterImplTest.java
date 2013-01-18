package org.sonatype.nexus.proxy.maven.wl.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.sonatype.nexus.ApplicationStatusSource;
import org.sonatype.nexus.SystemStatus;
import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.maven.MavenProxyRepository;
import org.sonatype.nexus.proxy.maven.MavenRepository;
import org.sonatype.nexus.proxy.maven.wl.EntrySource;
import org.sonatype.nexus.proxy.maven.wl.WLConfig;
import org.sonatype.nexus.proxy.maven.wl.WLManager;
import org.sonatype.sisu.goodies.eventbus.EventBus;
import org.sonatype.sisu.litmus.testsupport.TestSupport;

public class ProxyWhitelistFilterImplTest
    extends TestSupport
{
    @Mock
    private EventBus eventBus;

    @Mock
    private SystemStatus systemStatus;

    @Mock
    private ApplicationStatusSource applicationStatusSource;

    @Mock
    private WLManager wlManager;

    @Mock
    private MavenProxyRepository mavenProxyRepository;

    private WLConfig config = new WLConfigImpl();

    @Before
    public void prepare()
    {
        Mockito.when( systemStatus.isNexusStarted() ).thenReturn( true );
        Mockito.when( applicationStatusSource.getSystemStatus() ).thenReturn( systemStatus );
        Mockito.when( mavenProxyRepository.getId() ).thenReturn( "central" );
        Mockito.when( mavenProxyRepository.getName() ).thenReturn( "Central Repository" );
    }

    protected void doTestAllowed( final ProxyWhitelistFilterImpl filter, final String path,
                                  final boolean shouldBeAllowed )
    {
        final ResourceStoreRequest resourceStoreRequest = new ResourceStoreRequest( path );
        assertThat( String.format( "%s path is expected to return %s", path, shouldBeAllowed ),
            filter.allowed( mavenProxyRepository, resourceStoreRequest ), is( shouldBeAllowed ) );
    }

    @Test
    public void smoke()
    {
        // no WL exists at all, we must pass all
        final ProxyWhitelistFilterImpl filter =
            new ProxyWhitelistFilterImpl( eventBus, applicationStatusSource, config, wlManager );

        doTestAllowed( filter, "/some/path/and/file/at/the/end.txt", true );
        doTestAllowed( filter, "/foo/bar", true );
        doTestAllowed( filter, "/.meta/prefix.txt", true );
    }

    @Test
    public void withWl()
    {
        final EntrySource entrySource = new ArrayListEntrySource( Arrays.asList( "/org/apache", "/org/sonatype" ) );
        Mockito.when( wlManager.getEntrySourceFor( Mockito.any( MavenProxyRepository.class ) ) ).thenReturn(
            entrySource );

        // WL will be built, not every request should be allowed
        final ProxyWhitelistFilterImpl filter =
            new ProxyWhitelistFilterImpl( eventBus, applicationStatusSource, config, wlManager );

        // ping (this would happen on event)
        filter.buildWhitelistFor( mavenProxyRepository );

        // +1
        doTestAllowed( filter, "/org/apache/maven/foo/1.0/foo-1.0.jar", true );
        // +1
        doTestAllowed( filter, "/org/sonatype/maven/foo/1.0/foo-1.0.jar", true );
        // -1 com
        doTestAllowed( filter, "/com/sonatype/maven/foo/1.0/foo-1.0.jar", false );
        // -1 not in WL
        doTestAllowed( filter, "/.meta/prefix.txt", false ); // this file is handled in AbstractMavenRepository, using
                                                             // UID attributes to test for IsHidden attribute
    }
}
