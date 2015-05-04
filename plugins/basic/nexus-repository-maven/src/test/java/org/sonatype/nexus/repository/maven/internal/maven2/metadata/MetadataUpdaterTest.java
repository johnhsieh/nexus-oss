/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-2015 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.maven.internal.maven2.metadata;

import java.io.IOException;

import org.sonatype.nexus.repository.maven.MavenFacet;
import org.sonatype.nexus.repository.maven.MavenPath;
import org.sonatype.nexus.repository.maven.internal.maven2.Maven2MavenPathParser;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.payloads.StringPayload;
import org.sonatype.sisu.litmus.testsupport.TestSupport;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UT for {@link MetadataUpdater}
 *
 * @since 3.0
 */
public class MetadataUpdaterTest
    extends TestSupport
{
  @Mock
  private MavenFacet mavenFacet;

  private final MavenPath mavenPath = new Maven2MavenPathParser().parsePath("/foo/bar");

  private MetadataUpdater testSubject;

  @Before
  public void prepare() {
    this.testSubject = new MetadataUpdater(mavenFacet);
  }

  @Test
  public void updateWithNonExisting() throws IOException {
    testSubject.update(mavenPath, MavenMetadata.newGroupLevel(DateTime.now(), "group", null));
    verify(mavenFacet, times(1)).get(eq(mavenPath));
    verify(mavenFacet, times(1)).put(eq(mavenPath), any(Payload.class));
  }

  @Test
  public void updateWithExisting() throws IOException {
    when(mavenFacet.get(mavenPath)).thenReturn(
        new Content(new StringPayload("<?xml version=\"1.0\" encoding=\"UTF-8\"?><metadata/>", "text/xml")));
    testSubject.update(mavenPath, MavenMetadata.newGroupLevel(DateTime.now(), "group", null));
    verify(mavenFacet, times(1)).get(eq(mavenPath));
    verify(mavenFacet, times(1)).put(eq(mavenPath), any(Payload.class));
  }

  @Test
  public void updateWithExistingCorrupted() throws IOException {
    when(mavenFacet.get(mavenPath)).thenReturn(
        new Content(new StringPayload("ThisIsNotAnXml", "text/xml")));
    testSubject.update(mavenPath, MavenMetadata.newGroupLevel(DateTime.now(), "group", null));
    verify(mavenFacet, times(1)).get(eq(mavenPath));
    verify(mavenFacet, times(1)).put(eq(mavenPath), any(Payload.class));
  }

  @Test
  public void replace() throws IOException {
    testSubject.replace(mavenPath, MavenMetadata.newGroupLevel(DateTime.now(), "group", null));
    verify(mavenFacet, times(0)).get(eq(mavenPath));
    verify(mavenFacet, times(1)).put(eq(mavenPath), any(Payload.class));
  }

  @Test
  public void delete() throws IOException {
    testSubject.delete(mavenPath);
    verify(mavenFacet, times(0)).get(eq(mavenPath));
    verify(mavenFacet, times(0)).put(eq(mavenPath), any(Payload.class));
    verify(mavenFacet, times(1)).delete(eq(mavenPath));
  }
}
