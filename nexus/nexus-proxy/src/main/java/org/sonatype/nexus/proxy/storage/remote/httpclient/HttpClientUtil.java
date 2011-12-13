/**
 * Copyright (c) 2008-2011 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions
 *
 * This program is free software: you can redistribute it and/or modify it only under the terms of the GNU Affero General
 * Public License Version 3 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License Version 3
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License Version 3 along with this program.  If not, see
 * http://www.gnu.org/licenses.
 *
 * Sonatype Nexus (TM) Open Source Version is available from Sonatype, Inc. Sonatype and Sonatype Nexus are trademarks of
 * Sonatype, Inc. Apache Maven is a trademark of the Apache Foundation. M2Eclipse is a trademark of the Eclipse Foundation.
 * All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.proxy.storage.remote.httpclient;

import static org.sonatype.nexus.proxy.storage.remote.DefaultRemoteStorageContext.BooleanFlagHolder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.params.AuthPNames;
import org.apache.http.client.HttpClient;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.codehaus.plexus.logging.Logger;
import org.sonatype.nexus.proxy.repository.ClientSSLRemoteAuthenticationSettings;
import org.sonatype.nexus.proxy.repository.NtlmRemoteAuthenticationSettings;
import org.sonatype.nexus.proxy.repository.RemoteAuthenticationSettings;
import org.sonatype.nexus.proxy.repository.RemoteProxySettings;
import org.sonatype.nexus.proxy.repository.UsernamePasswordRemoteAuthenticationSettings;
import org.sonatype.nexus.proxy.storage.remote.RemoteStorageContext;
import org.sonatype.nexus.util.SystemPropertiesHelper;

/**
 * Utilities related to HTTP client.
 *
 * @since 1.10.0
 */
class HttpClientUtil
{

    // ----------------------------------------------------------------------
    // Constants
    // ----------------------------------------------------------------------

    /**
     * Context key of HTTP client.
     */
    private static final String CTX_KEY_CLIENT = ".client";

    /**
     * Context key of a flag present in case that remote server is an Amazon S3.
     */
    private static final String CTX_KEY_S3_FLAG = ".remoteIsAmazonS3";

    /**
     * Context key of a flag present in case that NTLM authentication is configured.
     */
    private static final String CTX_KEY_NTLM_IS_IN_USE = ".ntlmIsInUse";

    /**
     * Key of optional system property for customizing the connection pool size.
     * If not present HTTP client default is used (20 connections)
     */
    public static final String CONNECTION_POOL_SIZE_KEY = "httpClient.connectionPoolSize";

    /**
     * Marker used to determine that {@link #CONNECTION_POOL_SIZE_KEY} system property is not set.
     */
    private static final int UNDEFINED_POOL_SIZE = -1;

    // ----------------------------------------------------------------------
    // Implementation fields
    // ----------------------------------------------------------------------

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger( HttpClientUtil.class );

    // ----------------------------------------------------------------------
    // Public methods
    // ----------------------------------------------------------------------

    /**
     * Creates and prepares an http client instance by using configuration present in {@link RemoteStorageContext}.
     * <p/>
     * This implies:<br/>
     * * setting up connection pool using number of connections specified by system property
     * {@link #CONNECTION_POOL_SIZE_KEY}<br/>
     * * setting timeout as configured for repository<br/>
     * * (if necessary) configure authentication<br/>
     * * (if necessary) configure proxy as configured for repository
     *
     * @param ctxPrefix context keys prefix
     * @param ctx       remote repository context
     * @param logger    logger
     */
    static void configure( final String ctxPrefix,
                           final RemoteStorageContext ctx,
                           final Logger logger )
    {
        final DefaultHttpClient httpClient = new DefaultHttpClient(
            createConnectionManager(), createHttpParams( ctx )
        );

        ctx.putContextObject( ctxPrefix + CTX_KEY_CLIENT, httpClient );

        configureAuthentication( httpClient, ctxPrefix, ctx, ctx.getRemoteAuthenticationSettings(), logger );
        configureProxy( httpClient, ctxPrefix, ctx, logger );

        // NEXUS-3338: we don't know after config change is remote S3 (url changed maybe)
        ctx.putContextObject( ctxPrefix + CTX_KEY_S3_FLAG, new BooleanFlagHolder() );
    }

    /**
     * Releases the current HTTP client (if any) and removes context objects.
     *
     * @param ctxPrefix context keys prefix
     * @param ctx       remote repository context
     */
    static void release( final String ctxPrefix,
                         final RemoteStorageContext ctx )
    {
        if ( ctx.hasContextObject( ctxPrefix + CTX_KEY_CLIENT ) )
        {
            HttpClient httpClient = (HttpClient) ctx.getContextObject( ctxPrefix + CTX_KEY_CLIENT );
            httpClient.getConnectionManager().shutdown();
            ctx.removeContextObject( ctxPrefix + CTX_KEY_CLIENT );
        }
        ctx.removeContextObject( ctxPrefix + CTX_KEY_S3_FLAG );
        ctx.putContextObject( ctxPrefix + CTX_KEY_NTLM_IS_IN_USE, Boolean.FALSE );
    }

    /**
     * Returns the HTTP client for context.
     *
     * @param ctxPrefix context keys prefix
     * @param ctx       remote repository context
     * @return HTTP client or {@code null} if not yet configured
     */
    static HttpClient getHttpClient( final String ctxPrefix,
                                     final RemoteStorageContext ctx )
    {
        return (HttpClient) ctx.getContextObject( ctxPrefix + CTX_KEY_CLIENT );
    }

    /**
     * Whether or not the NTLM authentication is used.
     *
     * @param ctxPrefix context keys prefix
     * @param ctx       remote repository context
     * @return {@code true} if NTLM authentication is used, {@code false} otherwise
     */
    static Boolean isNTLMAuthenticationUsed( final String ctxPrefix,
                                             final RemoteStorageContext ctx )
    {
        final Object ntlmInUse = ctx.getContextObject( ctxPrefix + CTX_KEY_NTLM_IS_IN_USE );
        return ntlmInUse != null && Boolean.parseBoolean( ntlmInUse.toString() );
    }

    /**
     * Exposes the Amazon S3 flag key.
     *
     * @param ctxPrefix context keys prefix
     * @returnAmazon S3 flag key
     */
    static String getS3FlagKey( final String ctxPrefix )
    {
        return ctxPrefix + CTX_KEY_S3_FLAG;
    }

    // ----------------------------------------------------------------------
    // Implementation methods
    // ----------------------------------------------------------------------

    private static void configureAuthentication( final DefaultHttpClient httpClient,
                                                 final String ctxPrefix,
                                                 final RemoteStorageContext ctx,
                                                 final RemoteAuthenticationSettings ras,
                                                 final Logger logger )
    {
        if ( ras != null )
        {
            List<String> authorisationPreference = new ArrayList<String>( 2 );
            authorisationPreference.add( AuthPolicy.DIGEST );
            authorisationPreference.add( AuthPolicy.BASIC );

            Credentials credentials = null;

            if ( ras instanceof ClientSSLRemoteAuthenticationSettings )
            {
                // ClientSSLRemoteAuthenticationSettings cras = (ClientSSLRemoteAuthenticationSettings) ras;

                // TODO - implement this
            }
            else if ( ras instanceof NtlmRemoteAuthenticationSettings )
            {
                final NtlmRemoteAuthenticationSettings nras = (NtlmRemoteAuthenticationSettings) ras;

                // Using NTLM auth, adding it as first in policies
                authorisationPreference.add( 0, AuthPolicy.NTLM );

                log( Level.INFO, "... authentication setup for NTLM domain \"" + nras.getNtlmDomain() + "\"", logger );

                credentials = new NTCredentials(
                    nras.getUsername(), nras.getPassword(), nras.getNtlmHost(), nras.getNtlmDomain()
                );

                ctx.putContextObject( ctxPrefix + CTX_KEY_NTLM_IS_IN_USE, Boolean.TRUE );
            }
            else if ( ras instanceof UsernamePasswordRemoteAuthenticationSettings )
            {
                UsernamePasswordRemoteAuthenticationSettings uras = (UsernamePasswordRemoteAuthenticationSettings) ras;

                // Using Username/Pwd auth, will not add NTLM
                log( Level.INFO, "... authentication setup for remote storage with username \"" + uras.getUsername()
                    + "\"", logger );

                credentials = new UsernamePasswordCredentials( uras.getUsername(), uras.getPassword() );
            }

            if ( credentials != null )
            {
                httpClient.getCredentialsProvider().setCredentials( AuthScope.ANY, credentials );
            }

            httpClient.getParams().setParameter( AuthPNames.PROXY_AUTH_PREF, authorisationPreference );
        }
    }

    private static void configureProxy( final DefaultHttpClient httpClient,
                                        final String ctxPrefix,
                                        final RemoteStorageContext ctx,
                                        final Logger logger )
    {
        final RemoteProxySettings rps = ctx.getRemoteProxySettings();

        if ( rps.isEnabled() )
        {
            log( Level.INFO, "... proxy setup with host \"" + rps.getHostname() + "\"", logger );

            final HttpHost proxy = new HttpHost( rps.getHostname(), rps.getPort() );
            httpClient.getParams().setParameter( ConnRoutePNames.DEFAULT_PROXY, proxy );

            // check if we have non-proxy hosts
            if ( rps.getNonProxyHosts() != null && !rps.getNonProxyHosts().isEmpty() )
            {
                final Set<Pattern> nonProxyHostPatterns = new HashSet<Pattern>( rps.getNonProxyHosts().size() );
                for ( String nonProxyHostRegex : rps.getNonProxyHosts() )
                {
                    try
                    {
                        nonProxyHostPatterns.add( Pattern.compile( nonProxyHostRegex, Pattern.CASE_INSENSITIVE ) );
                    }
                    catch ( PatternSyntaxException e )
                    {
                        LOG.warn( "Invalid non proxy host regex: " + nonProxyHostRegex, e );
                    }
                }
                httpClient.setRoutePlanner(
                    new NonProxyHostsAwareHttpRoutePlanner(
                        httpClient.getConnectionManager().getSchemeRegistry(), nonProxyHostPatterns
                    )
                );

            }

            configureAuthentication( httpClient, ctxPrefix, ctx, rps.getProxyAuthentication(), logger );

            if ( rps.getProxyAuthentication() != null )
            {
                if ( ctx.getRemoteAuthenticationSettings() != null
                    && ( ctx.getRemoteAuthenticationSettings() instanceof NtlmRemoteAuthenticationSettings ) )
                {
                    log( Level.WARNING, "... Apache Commons HttpClient 3.x is unable to use NTLM auth scheme\n"
                        + " for BOTH server side and proxy side authentication!\n"
                        + " You MUST reconfigure server side auth and use BASIC/DIGEST scheme\n"
                        + " if you have to use NTLM proxy, otherwise it will not work!\n"
                        + " *** SERVER SIDE AUTH OVERRIDDEN", logger );
                }

            }
        }
    }

    private static HttpParams createHttpParams( final RemoteStorageContext ctx )
    {
        final HttpParams params = new BasicHttpParams();

        // getting the timeout from RemoteStorageContext. The value we get depends on per-repo and global settings.
        // The value will "cascade" from repo level to global level, see implementation.
        int timeout = ctx.getRemoteConnectionSettings().getConnectionTimeout();

        params.setParameter( CoreConnectionPNames.CONNECTION_TIMEOUT, timeout );
        params.setParameter( CoreConnectionPNames.SO_TIMEOUT, timeout );
        return params;
    }

    private static ThreadSafeClientConnManager createConnectionManager()
    {
        final ThreadSafeClientConnManager connManager = new ThreadSafeClientConnManager();

        int connectionPoolSize = SystemPropertiesHelper.getInteger( CONNECTION_POOL_SIZE_KEY, UNDEFINED_POOL_SIZE );
        if ( connectionPoolSize != UNDEFINED_POOL_SIZE )
        {
            connManager.setMaxTotal( connectionPoolSize );
        }
        // NOTE: connPool is _per_ repo, hence all of those will connect to same host (unless mirrors are used)
        // so, we are violating intentionally the RFC and we let the whole pool size to chase same host
        connManager.setDefaultMaxPerRoute( connManager.getMaxTotal() );

        return connManager;
    }

    /**
     * Coding around plexus logger as this class is NOT a component and should not be using this type of logging.
     */
    private static void log( Level level, String message, Logger logger )
    {
        if ( logger != null )
        {
            if ( level.equals( Level.SEVERE ) )
            {
                logger.error( message );
            }
            else if ( level.equals( Level.WARNING ) )
            {
                logger.warn( message );
            }
            else if ( level.equals( Level.INFO ) )
            {
                logger.info( message );
            }
            else
            {
                logger.debug( message );
            }
        }
        else
        {
            if ( level.equals( Level.SEVERE ) )
            {
                LOG.error( message );
            }
            else if ( level.equals( Level.WARNING ) )
            {
                LOG.warn( message );
            }
            else if ( level.equals( Level.INFO ) )
            {
                LOG.info( message );
            }
            else
            {
                LOG.debug( message );
            }
        }

    }

}
