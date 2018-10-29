// Copyright (c) 2018 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.GlobalState.FUSION_SOURCE_EXTENSION;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 *
 */
public class UrlModuleRepository
    extends ModuleRepository
{
    private final URI myBaseUri;
    private final URL myBaseUrl;

    /**
     * @throws URISyntaxException
     * @throws MalformedURLException
     *
     */
    public UrlModuleRepository(URL manifestLocation)
        throws MalformedURLException, URISyntaxException
    {
        System.out.println("[UrlModuleRepository] manifestUri: " + manifestLocation.toURI());

        // Find the FUSION-REPO URI.
        myBaseUri = manifestLocation.toURI().resolve("");

        System.out.println("[UrlModuleRepository] baseUri: " + myBaseUri);

        myBaseUrl = myBaseUri.toURL();
    }

    @Override
    String identify()
    {
        return myBaseUri.toString();
    }

    @Override
    ModuleLocation locateModule(Evaluator eval, final ModuleIdentity id)
        throws FusionException
    {
        final String path = id.absolutePath();
        final String fileName = path.substring(1) + FUSION_SOURCE_EXTENSION;

        final URL url;
        try
        {
            url = myBaseUri.resolve("src/" + fileName).toURL();
        }
        catch (MalformedURLException e)
        {
            // This shouldn't happen because module paths are valid URI paths.
            throw new FusionErrorException("Unable to resolve module path", e);
        }

        if (true) // FIXME must check presence
        {
            ModuleLocation loc = new InputStreamModuleLocation()
            {
                // TODO Maybe not the best output this way.
                private final SourceName myName =
                    SourceName.forDisplay(id + " (at " + url.toString() + ")");

                @Override
                SourceName sourceName()
                {
                    return myName;
                }

                @Override
                InputStream open()
                    throws IOException
                {
                    return url.openStream();
                }
            };

            return loc;
        }

        return null;
    }

    @Override
    void collectModules(Predicate<ModuleIdentity> selector,
                        Consumer<ModuleIdentity> results)
        throws FusionException
    {
        // TODO Auto-generated method stub

    }

    InputStream openStream(String path)
        throws IOException
    {
        // TODO prevent resolving up from the base
        URL url = myBaseUri.resolve(path).toURL();
        return url.openStream();
    }
}
