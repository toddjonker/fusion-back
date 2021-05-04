// Copyright (c) 2021-2024 Amazon.com, Inc.  All rights reserved.

package example;

import com.amazon.fusion.FusionException;
import com.amazon.fusion.FusionIo;
import com.amazon.fusion.FusionRuntime;
import com.amazon.fusion.FusionRuntimeBuilder;
import com.amazon.fusion.SourceName;
import com.amazon.fusion.TopLevel;
import com.amazon.ion.IonReader;
import com.amazon.ion.system.IonReaderBuilder;
import java.io.File;

public class Calculator
{
    public static final String DSL_MODULE_ID = "/calculator";

    private static final String DSL_MODULE_CODE =
        "(module calculator '/fusion/number' \n" +
        "  (provide + - * /))";

    private final FusionRuntime runtime;

    public Calculator()
        throws FusionException
    {
        runtime = FusionRuntimeBuilder.standard()
                    .withBootstrapRepository(new File("./fusion"))
                    .build();

        instantiateDslModule();
    }

    private void instantiateDslModule()
        throws FusionException
    {
        TopLevel top = runtime.makeTopLevel();
        IonReader sourceReader = IonReaderBuilder.standard().build(DSL_MODULE_CODE);
        SourceName sourceName = SourceName.forDisplay("Calculator Language");
        top.loadModule(DSL_MODULE_ID, sourceReader, sourceName);
        top.requireModule(DSL_MODULE_ID);
    }

    private void evaluate(String expression)
        throws FusionException
    {
        TopLevel localNamespace = runtime.makeTopLevel(DSL_MODULE_ID);
        Object result = localNamespace.eval(expression);
        FusionIo.write(localNamespace, result, System.out);
        System.out.println();
    }

    public static void main(String[] args)
    {
        try
        {
            Calculator calc = new Calculator();
            calc.evaluate(args[0]);
        }
        catch (Throwable e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
