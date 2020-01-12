//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.websocket.common;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketSessionListener;

public class SessionTracker extends AbstractLifeCycle implements WebSocketSessionListener
{
    private List<Session> sessions = new CopyOnWriteArrayList<>();

    public Collection<Session> getSessions()
    {
        return sessions;
    }

    @Override
    public void onWebSocketSessionOpened(Session session)
    {
        sessions.add(session);
    }

    @Override
    public void onWebSocketSessionClosed(Session session)
    {
        sessions.remove(session);
    }

    @Override
    protected void doStop() throws Exception
    {
        for (Session session : sessions)
        {
            // SHUTDOWN is abnormal close status so it will hard close connection after sent.
            session.close(StatusCode.SHUTDOWN, "Container being shut down");
        }

        super.doStop();
    }
}
