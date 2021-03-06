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

package org.eclipse.jetty.websocket.javax.common;

import java.util.concurrent.CompletableFuture;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class CompletableFutureCallback extends CompletableFuture<Callback> implements Callback
{
    private static final Logger LOG = Log.getLogger(CompletableFutureCallback.class);

    @Override
    public void failed(Throwable cause)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("failed()", cause);

        completeExceptionally(cause);
    }

    @Override
    public void succeeded()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("succeeded()");

        complete(this);
    }
}
