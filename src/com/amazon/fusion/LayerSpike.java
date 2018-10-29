// Copyright (c) 2018 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class LayerSpike
{
    // Starting with '/' prevents finding the path in directory-based layers.
    private final static String MANIFEST_PATH = "FUSION-REPO/manifest.ion";


    public static void main(String[] args)
    {
        try
        {
            new LayerSpike().run();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }


    private void run() throws Exception
    {
        ClassLoader cl = getClass().getClassLoader();

        System.out.println("Discovering layers:");
        List<UrlModuleRepository> layers = discoverRepositories(cl);

        System.out.println("\nHere are the layers:");
        for (UrlModuleRepository repo : layers)
        {
            System.out.println(repo.identify());

            try (InputStreamReader isr = new InputStreamReader(repo.openStream("where"));
                 BufferedReader in = new BufferedReader(isr))
            {
                String where = in.readLine();
                System.out.println("  where: " + where);
            }
        }
    }


    private List<UrlModuleRepository> discoverRepositories(ClassLoader loader)
        throws Exception
    {
        List<UrlModuleRepository> repos = new ArrayList<>();

        Enumeration<URL> manifests = loader.getResources(MANIFEST_PATH);
        while (manifests.hasMoreElements())
        {
            URL manifest = manifests.nextElement();
            System.out.println("Manifest URL: " + manifest);
            System.out.println("Manifest URI: " + manifest.toURI());

            URI where = manifest.toURI().resolve("where");
            System.out.println("  Resolved where:  " + where.toString());

            repos.add(new UrlModuleRepository(manifest));
        }

        return repos;
    }
}
