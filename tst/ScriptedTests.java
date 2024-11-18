// Copyright Ion Fusion contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import dev.ionfusion.fusion.FusionRuntime;
import dev.ionfusion.fusion.junit.FusionRuntimeInjector;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;


/**
 * Run all the `*.test.fusion` scripts under the `ftst` directory as individual
 * unit tests.
 * Each test passes if script evaluation completes without failure.
 * <p>
 * All tests run using a single {@link FusionRuntime}.
 * If `ftst/repo` is a directory, it's added as a repository.
 */
@ExtendWith(FusionRuntimeInjector.class)
public class ScriptedTests
{
    /* 2024-03-28 Concurrent execution runs notably slower than same-thread,
     * presumably due to contention over symbol interning and/or module loading.
     */
    @TestFactory
    @DisplayName("ftst/")
    Stream<DynamicNode> ftst(FusionRuntime runtime)
        throws Exception
    {
        return forDir(Paths.get("ftst"), runtime);
    }


    //========================================================================


    private Stream<DynamicNode> forDir(Path dir, FusionRuntime runtime)
    {
        String[] fileNames = dir.toFile().list();
        assert fileNames != null : "Not a directory: " + dir.toAbsolutePath();

        // Sort the fileNames so they are listed in order.
        // This is not a functional requirement, but it helps humans scanning
        // the output looking for a specific file.
        Arrays.sort(fileNames);

        return Arrays.stream(fileNames)
                     .map(n -> forChild(dir.resolve(n), runtime))
                     .filter(Objects::nonNull);
    }


    private DynamicNode forChild(Path file, FusionRuntime runtime)
    {
        String name = file.getFileName().toString();
        URI    uri  = file.toUri();

        if (Files.isDirectory(file))
        {
            return dynamicContainer(name + "/", uri, forDir(file, runtime));
        }
        else if (name.endsWith(".test.fusion"))
        {
            return dynamicTest(name, uri, () -> runtime.makeTopLevel().load(file.toFile()));
        }
        return null;
    }
}
