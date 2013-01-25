/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2012 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.proxy.maven.wl.internal.scrape;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.sonatype.nexus.proxy.maven.wl.EntrySource;
import org.sonatype.nexus.proxy.maven.wl.internal.AbstractPrioritized;

/**
 * Abstract class for {@link Scraper} implementations.
 * 
 * @author cstamas
 */
public abstract class AbstractScraper
    extends AbstractPrioritized
    implements Scraper
{
    /**
     * Detection results.
     */
    public static enum RemoteDetectionResult
    {
        /**
         * Remote not recognized, this scraper cannot do anything with it.
         */
        UNRECOGNIZED,

        /**
         * Recognized and we are sure it can and should be scraped.
         */
        RECOGNIZED_SHOULD_BE_SCRAPED,

        /**
         * Recognized and we are sure it should not be scraped.
         */
        RECOGNIZED_SHOULD_NOT_BE_SCRAPED;
    }

    private final String id;

    protected AbstractScraper( final int priority, final String id )
    {
        super( priority );
        this.id = checkNotNull( id );
    }

    @Override
    public String getId()
    {
        return id;
    }

    @Override
    public void scrape( final ScrapeContext context )
        throws IOException
    {
        final RemoteDetectionResult detectionResult = detectRemoteRepository( context );
        switch ( detectionResult )
        {
            case RECOGNIZED_SHOULD_BE_SCRAPED:
                final EntrySource entrySource = diveIn( context );
                context.stop( entrySource, "Remote recognized as " + getTargetedServer() + "." );
                break;

            case RECOGNIZED_SHOULD_NOT_BE_SCRAPED:
                context.stop( "Remote recognized as " + getTargetedServer() + ", but is not a hosted repository." );
                break;

            default:
                // not recognized, just continue with next Scraper
                break;
        }
    }

    // ==

    protected abstract String getTargetedServer();

    protected abstract RemoteDetectionResult detectRemoteRepository( final ScrapeContext context );

    protected abstract EntrySource diveIn( final ScrapeContext context )
        throws IOException;

    // ==

    protected HttpResponse getHttpResponseFor( final ScrapeContext context, final String repositoryPath )
        throws IOException
    {
        final String url = getRemoteUrlForRepositoryPath( context, repositoryPath );
        // TODO: detect redirects
        final HttpGet get = new HttpGet( url );
        return context.getHttpClient().execute( get );
    }

    protected Document getDocumentFor( final ScrapeContext context, final String repositoryPath )
        throws IOException
    {
        final String url = getRemoteUrlForRepositoryPath( context, repositoryPath );
        // TODO: detect redirects
        final HttpGet get = new HttpGet( url );
        HttpResponse response = context.getHttpClient().execute( get );
        try
        {
            if ( response.getStatusLine().getStatusCode() == 200 )
            {
                return Jsoup.parse( response.getEntity().getContent(), null, url );
            }
            else
            {
                throw new IOException( "Unexpected response from remote repository URL " + url + " : "
                    + response.getStatusLine().toString() );
            }
        }
        finally
        {
            EntityUtils.consumeQuietly( response.getEntity() );
        }
    }

    protected String getRemoteUrlForRepositoryPath( final ScrapeContext context, final String repositoryPath )
    {
        // explanation: Nexus "repository paths" are always absolute, using "/" as separators and starting with "/"
        // but, the repo remote URL comes from Nexus config, and Nexus always "normalizes" the URL and it always ends
        // with "/"
        String sp = repositoryPath;
        while ( sp.startsWith( "/" ) )
        {
            sp = sp.substring( 1 );
        }
        return context.getRemoteRepositoryRootUrl() + sp;
    }
}
