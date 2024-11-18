// Copyright Ion Fusion contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package dev.ionfusion.fusion.junit;

import static dev.ionfusion.fusion.CoreTestCase.ftstRepositoryDirectory;
import static dev.ionfusion.fusion.CoreTestCase.fusionBootstrapDirectory;

import dev.ionfusion.fusion.FusionException;
import dev.ionfusion.fusion.FusionRuntime;
import dev.ionfusion.fusion.FusionRuntimeBuilder;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.support.TypeBasedParameterResolver;


public class FusionRuntimeInjector
    extends TypeBasedParameterResolver<FusionRuntime>
{
    @Override
    public FusionRuntime resolveParameter(ParameterContext parameterContext,
                                          ExtensionContext extensionContext)
        throws ParameterResolutionException
    {
        try
        {
            return buildRuntime();
        }
        catch (FusionException e)
        {
            throw new ParameterResolutionException("Failed to build runtime", e);
        }
    }


    private FusionRuntime buildRuntime()
        throws FusionException
    {
        FusionRuntimeBuilder b = FusionRuntimeBuilder.standard();

        // This allows tests to run in an IDE, so that we don't have to copy the
        // bootstrap repo into the classpath.  In scripted builds, this has no
        // effect since the classpath includes the code, which will shadow the
        // content of this directory.
        b = b.withBootstrapRepository(fusionBootstrapDirectory().toFile());

        // Enable this to have coverage collected during an IDE run.
//      b = b.withCoverageDataDirectory(new File("build/private/fcoverage"));

        // This has no effect in an IDE, since this file is not on its copy of
        // the test classpath.  In scripted builds, this provides the coverage
        // configuration. Historically, it also provided the bootstrap repo.
        b = b.withConfigProperties(getClass(), "/fusion.properties");

        b.addRepositoryDirectory(ftstRepositoryDirectory().toFile());

        return b.build();
    }
}
