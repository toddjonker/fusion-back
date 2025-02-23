// Copyright Ion Fusion contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package dev.ionfusion.runtime.base;

import java.net.URL;
import java.nio.file.Path;

public interface ResourceName
{
    String display();


    /**
     * Returns a URL for the resource. The protocol can vary; at least {@code file}
     * and {@code jar} are possible. In general, {@link URL#openStream()} is
     * expected to work.
     *
     * @return null if this resource cannot be identified as a URL.
     */
    URL getUrl();

    Path getPath();
}
