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
package org.sonatype.nexus.proxy.maven.wl.discovery;

import java.io.IOException;

import org.sonatype.nexus.proxy.maven.MavenProxyRepository;
import org.sonatype.nexus.proxy.maven.wl.EntrySource;

/**
 * Remote strategy is used to discover remote content. It might employ multiple means to obtain (or build)
 * {@link EntrySource} it returns, like scraping, etc.
 * 
 * @author cstamas
 * @since 2.4
 */
public interface RemoteStrategy
    extends Strategy
{
    /**
     * Discovers the remote content of the {@link MavenProxyRepository} and returns an {@link EntrySource} if
     * successful.
     * 
     * @param mavenProxyRepository to have remote content discovered.
     * @return entry source with discovered entries.
     * @throws StrategyFailedException if "soft" failure detected.
     * @throws IOException in case of IO problem.
     */
    EntrySource discover( MavenProxyRepository mavenProxyRepository )
        throws StrategyFailedException, IOException;
}
