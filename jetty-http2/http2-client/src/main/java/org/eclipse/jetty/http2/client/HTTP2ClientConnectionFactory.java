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

package org.eclipse.jetty.http2.client;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.eclipse.jetty.http2.FlowControlStrategy;
import org.eclipse.jetty.http2.HTTP2Connection;
import org.eclipse.jetty.http2.ISession;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.frames.PrefaceFrame;
import org.eclipse.jetty.http2.frames.SettingsFrame;
import org.eclipse.jetty.http2.frames.WindowUpdateFrame;
import org.eclipse.jetty.http2.generator.Generator;
import org.eclipse.jetty.http2.parser.Parser;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.ClientConnectionFactory;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Promise;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.Scheduler;

public class HTTP2ClientConnectionFactory implements ClientConnectionFactory
{
    public static final String CLIENT_CONTEXT_KEY = "org.eclipse.jetty.client.http2";
    public static final String SESSION_LISTENER_CONTEXT_KEY = "org.eclipse.jetty.client.http2.sessionListener";
    public static final String SESSION_PROMISE_CONTEXT_KEY = "org.eclipse.jetty.client.http2.sessionPromise";

    private final Connection.Listener connectionListener = new ConnectionListener();

    @Override
    public Connection newConnection(EndPoint endPoint, Map<String, Object> context)
    {
        final HTTP2Client client = (HTTP2Client)context.get(CLIENT_CONTEXT_KEY);
        final ByteBufferPool byteBufferPool = client.getByteBufferPool();
        final Executor executor = client.getExecutor();
        final Scheduler scheduler = client.getScheduler();
        final Session.Listener listener = (Session.Listener)context.get(SESSION_LISTENER_CONTEXT_KEY);
        @SuppressWarnings("unchecked")
        final Promise<Session> promise = (Promise<Session>)context.get(SESSION_PROMISE_CONTEXT_KEY);

        final Generator generator = new Generator(byteBufferPool);
        final FlowControlStrategy flowControl = client.getFlowControlStrategyFactory().newFlowControlStrategy();
        final HTTP2ClientSession session = new HTTP2ClientSession(scheduler, endPoint, generator, listener, flowControl);
        session.setMaxRemoteStreams(client.getMaxConcurrentPushedStreams());
        long streamIdleTimeout = client.getStreamIdleTimeout();
        if (streamIdleTimeout > 0)
            session.setStreamIdleTimeout(streamIdleTimeout);

        final Parser parser = new Parser(byteBufferPool, session, 4096, 8192);
        parser.setMaxFrameLength(client.getMaxFrameLength());
        parser.setMaxSettingsKeys(client.getMaxSettingsKeys());

        final HTTP2ClientConnection connection = new HTTP2ClientConnection(client, byteBufferPool, executor, endPoint,
            parser, session, client.getInputBufferSize(), promise, listener);
        connection.addEventListener(connectionListener);
        return customize(connection, context);
    }

    private class HTTP2ClientConnection extends HTTP2Connection implements Callback
    {
        private final HTTP2Client client;
        private final Promise<Session> promise;
        private final Session.Listener listener;

        private HTTP2ClientConnection(HTTP2Client client, ByteBufferPool byteBufferPool, Executor executor, EndPoint endpoint, Parser parser, ISession session, int bufferSize, Promise<Session> promise, Session.Listener listener)
        {
            super(byteBufferPool, executor, endpoint, parser, session, bufferSize);
            this.client = client;
            this.promise = promise;
            this.listener = listener;
        }

        @Override
        public long getMessagesIn()
        {
            HTTP2ClientSession session = (HTTP2ClientSession)getSession();
            return session.getStreamsOpened();
        }

        @Override
        public long getMessagesOut()
        {
            HTTP2ClientSession session = (HTTP2ClientSession)getSession();
            return session.getStreamsClosed();
        }

        @Override
        public void onOpen()
        {
            Map<Integer, Integer> settings = listener.onPreface(getSession());
            if (settings == null)
                settings = new HashMap<>();
            settings.computeIfAbsent(SettingsFrame.INITIAL_WINDOW_SIZE, k -> client.getInitialStreamRecvWindow());
            settings.computeIfAbsent(SettingsFrame.MAX_CONCURRENT_STREAMS, k -> client.getMaxConcurrentPushedStreams());

            Integer maxFrameLength = settings.get(SettingsFrame.MAX_FRAME_SIZE);
            if (maxFrameLength != null)
                getParser().setMaxFrameLength(maxFrameLength);

            PrefaceFrame prefaceFrame = new PrefaceFrame();
            SettingsFrame settingsFrame = new SettingsFrame(settings, false);

            ISession session = getSession();

            int windowDelta = client.getInitialSessionRecvWindow() - FlowControlStrategy.DEFAULT_WINDOW_SIZE;
            if (windowDelta > 0)
            {
                session.updateRecvWindow(windowDelta);
                session.frames(null, this, prefaceFrame, settingsFrame, new WindowUpdateFrame(0, windowDelta));
            }
            else
            {
                session.frames(null, this, prefaceFrame, settingsFrame);
            }
        }

        @Override
        public void succeeded()
        {
            super.onOpen();
            promise.succeeded(getSession());
            // Only start reading from server after we have sent the client preface,
            // otherwise we risk to read the server preface (a SETTINGS frame) and
            // reply to that before we have the chance to send the client preface.
            produce();
        }

        @Override
        public void failed(Throwable x)
        {
            close();
            promise.failed(x);
        }
    }

    private class ConnectionListener implements Connection.Listener
    {
        @Override
        public void onOpened(Connection connection)
        {
            HTTP2ClientConnection http2Connection = (HTTP2ClientConnection)connection;
            http2Connection.client.addManaged((LifeCycle)http2Connection.getSession());
        }

        @Override
        public void onClosed(Connection connection)
        {
            HTTP2ClientConnection http2Connection = (HTTP2ClientConnection)connection;
            http2Connection.client.removeBean(http2Connection.getSession());
        }
    }
}
