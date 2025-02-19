//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.websocket.javax.common;

import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import org.eclipse.jetty.websocket.core.CoreSession;
import org.eclipse.jetty.websocket.core.WebSocketComponents;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public abstract class AbstractSessionTest
{
    protected static JavaxWebSocketSession session;
    protected static JavaxWebSocketContainer container;
    protected static WebSocketComponents components;

    @BeforeAll
    public static void initSession() throws Exception
    {
        container = new DummyContainer();
        container.start();
        components = new WebSocketComponents();
        components.start();
        Object websocketPojo = new DummyEndpoint();
        UpgradeRequest upgradeRequest = new UpgradeRequestAdapter();
        JavaxWebSocketFrameHandler frameHandler = container.newFrameHandler(websocketPojo, upgradeRequest);
        CoreSession coreSession = new CoreSession.Empty()
        {
            @Override
            public WebSocketComponents getWebSocketComponents()
            {
                return components;
            }
        };
        session = new JavaxWebSocketSession(container, coreSession, frameHandler, container.getFrameHandlerFactory()
            .newDefaultEndpointConfig(websocketPojo.getClass()));
    }

    @AfterAll
    public static void stopContainer() throws Exception
    {
        components.stop();
        container.stop();
    }

    public static class DummyEndpoint extends Endpoint
    {
        @Override
        public void onOpen(Session session, EndpointConfig config)
        {
        }
    }
}
