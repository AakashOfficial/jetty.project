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

package org.eclipse.jetty.websocket.api;

/**
 * Basic WebSocket Listener interface for incoming WebSocket message events.
 */
public interface WebSocketListener extends WebSocketConnectionListener
{
    /**
     * A WebSocket binary frame has been received.
     *
     * @param payload the raw payload array received
     * @param offset the offset in the payload array where the data starts
     * @param len the length of bytes in the payload
     */
    void onWebSocketBinary(byte[] payload, int offset, int len);

    /**
     * A WebSocket Text frame was received.
     *
     * @param message the message
     */
    void onWebSocketText(String message);
}
