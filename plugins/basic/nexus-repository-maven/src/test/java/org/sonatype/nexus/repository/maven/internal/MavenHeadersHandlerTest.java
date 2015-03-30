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
package org.sonatype.nexus.repository.maven.internal;

import org.sonatype.nexus.repository.http.HttpResponses;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Request;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.payloads.StringPayload;
import org.sonatype.sisu.goodies.common.Iso8601Date;
import org.sonatype.sisu.litmus.testsupport.TestSupport;

import com.google.common.net.HttpHeaders;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

/**
 * UT for {@link MavenHeadersHandler}.
 *
 * @since 3.0
 */
public class MavenHeadersHandlerTest
    extends TestSupport
{
  final DateTime now = DateTime.now();

  final MavenHeadersHandler subject = new MavenHeadersHandler();

  final String payloadString = "testPayload";

  @Mock
  Context context;

  @Mock
  Request request;

  @Before
  public void before() throws Exception {
    when(context.getRequest()).thenReturn(request);
  }

  @Test
  public void okResponse() throws Exception {
    when(context.proceed())
        .thenReturn(HttpResponses.ok(new Content(new StringPayload(payloadString, "text/plain"), now, "etag")));
    final Response r = subject.handle(context);
    assertThat(r.getStatus().isSuccessful(), is(true));
    assertThat(r.getHeaders().get(HttpHeaders.LAST_MODIFIED), equalTo(Iso8601Date.format(now.toDate())));
    assertThat(r.getHeaders().get(HttpHeaders.ETAG), equalTo("\"etag\""));
  }

  @Test
  public void okResponseNoExtraData() throws Exception {
    when(context.proceed()).thenReturn(
        HttpResponses.ok(new Content(new StringPayload(payloadString, "text/plain"), null, null)));
    final Response r = subject.handle(context);
    assertThat(r.getStatus().isSuccessful(), is(true));
    assertThat(r.getHeaders().get(HttpHeaders.LAST_MODIFIED), nullValue());
    assertThat(r.getHeaders().get(HttpHeaders.ETAG), nullValue());
  }

  @Test
  public void okResponseNotContent() throws Exception {
    when(context.proceed()).thenReturn(HttpResponses.ok(new StringPayload("test", "text/plain")));
    final Response r = subject.handle(context);
    assertThat(r.getStatus().isSuccessful(), is(true));
    assertThat(r.getHeaders().get(HttpHeaders.LAST_MODIFIED), nullValue());
    assertThat(r.getHeaders().get(HttpHeaders.ETAG), nullValue());
  }

  @Test
  public void notOkResponse() throws Exception {
    when(context.proceed()).thenReturn(HttpResponses.notFound());
    final Response r = subject.handle(context);
    assertThat(r.getStatus().isSuccessful(), is(false));
    assertThat(r.getHeaders().get(HttpHeaders.LAST_MODIFIED), nullValue());
    assertThat(r.getHeaders().get(HttpHeaders.ETAG), nullValue());
  }

}