package org.sonatype.nexus.proxy.maven.wl.internal.scrape;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.jsoup.nodes.Document;
import org.sonatype.nexus.proxy.maven.wl.EntrySource;

/**
 * Request for scraping.
 * 
 * @author cstamas
 */
public class ScrapeContext
{
    private final HttpClient httpClient;

    private final String remoteRepositoryRootUrl;

    private final int scrapeDepth;

    private final HttpResponse remoteRepositoryRootResponse;

    private final Document remoteRepositoryRootDocument;

    private boolean stopped;

    private EntrySource entrySource;

    private String message;

    /**
     * Constructor, none of the parameters might be {@code null}.
     * 
     * @param httpClient
     * @param remoteRepositoryRootUrl
     * @param scrapeDepth
     * @param remoteRepositoryRootResponse
     * @param remoteRepositoryRootDocument
     */
    public ScrapeContext( final HttpClient httpClient, final String remoteRepositoryRootUrl, final int scrapeDepth,
                          final HttpResponse remoteRepositoryRootResponse, final Document remoteRepositoryRootDocument )
    {
        this.httpClient = checkNotNull( httpClient );
        this.remoteRepositoryRootUrl = checkNotNull( remoteRepositoryRootUrl );
        this.scrapeDepth = checkNotNull( scrapeDepth );
        this.remoteRepositoryRootResponse = checkNotNull( remoteRepositoryRootResponse );
        this.remoteRepositoryRootDocument = checkNotNull( remoteRepositoryRootDocument );
        this.stopped = false;
    }

    /**
     * Marks the context to be stopped, with successful outcome (when scraping succeeded).
     * 
     * @param entrySource
     * @param message
     */
    public void stop( final EntrySource entrySource, final String message )
    {
        this.stopped = true;
        this.entrySource = checkNotNull( entrySource );
        this.message = checkNotNull( message );
    }

    /**
     * Marks the context to be stopped, with unsuccessful outcome (when scraping not possible or should be avoided, like
     * remote is detected as MRM Proxy).
     * 
     * @param message
     */
    public void stop( final String message )
    {
        this.stopped = true;
        this.entrySource = null;
        this.message = checkNotNull( message );
    }

    /**
     * Is this context stopped or not.
     * 
     * @return {@code true} if context is stopped, not other {@link Scraper} should be invoked with this context.
     */
    public boolean isStopped()
    {
        return stopped;
    }

    /**
     * Is this context stopped with successful outcome or not.
     * 
     * @return {@code true} if context is stopped with successful outcome.
     */
    public boolean isSuccessful()
    {
        return isStopped() && entrySource != null;
    }

    /**
     * The {@link EntrySource} if scraping succeeded.
     * 
     * @return scraped entries or {@code null}.
     */
    public EntrySource getEntrySource()
    {
        return entrySource;
    }

    /**
     * The last message of {@link Scraper}.
     * 
     * @return message.
     */
    public String getMessage()
    {
        return message;
    }

    // ==

    /**
     * Returns the {@link HttpClient} to use for remote requests.
     * 
     * @return the HTTP client.
     */
    public HttpClient getHttpClient()
    {
        return httpClient;
    }

    /**
     * The remote repository root URL.
     * 
     * @return root url to scrape.
     */
    public String getRemoteRepositoryRootUrl()
    {
        return remoteRepositoryRootUrl;
    }

    /**
     * The needed depth for scraping.
     * 
     * @return the depth.
     */
    public int getScrapeDepth()
    {
        return scrapeDepth;
    }

    /**
     * The response of doing HTTP GET against {@link #getRemoteRepositoryRootUrl()}, but with {@link HttpEntity}
     * consumed. See {@link #getRemoteRepositoryRootDocument()} for entity parsed.
     * 
     * @return response with entity consumed.
     */
    public HttpResponse getRemoteRepositoryRootResponse()
    {
        return remoteRepositoryRootResponse;
    }

    /**
     * The document built from {@link #getRemoteRepositoryRootResponse()} entity.
     * 
     * @return the root {@link Document}.
     */
    public Document getRemoteRepositoryRootDocument()
    {
        return remoteRepositoryRootDocument;
    }
}
