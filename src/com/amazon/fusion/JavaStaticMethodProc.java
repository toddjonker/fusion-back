// Copyright (c) 2017 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionText.checkNonEmptyTextArg;
import java.lang.reflect.Method;
import net.bytebuddy.ByteBuddy;

/**
 * (java_static_method_to_procedure "my.package.Class" "method")
 */
public class JavaStaticMethodProc
    extends Procedure
{

    @Override
    Object doApply(Evaluator eval, Object[] args) throws FusionException
    {
        checkArityExact(2, args);
        String className  = checkNonEmptyTextArg(eval, this, 0, args);
        String methodName = checkNonEmptyTextArg(eval, this, 1, args);

        Class<?> klass = determineClass(className);
        Method method  = determineMethod(klass, methodName);

        validateMethod(method);

        return new ByteBuddy()
            .subclass(Procedure.class)
            .name("fusion.gen.Proc")       // FIXME
            .defineMethod("doApply", Object.class)
              .withParameter(Evaluator.class)
              .withParameter(Object[].class)
              .withoutCode()                     // FIXME
            .make()
            .load(getClass().getClassLoader())
            .getLoaded()
            .newInstance();
    }


    private Class<?> determineClass(String className)
        throws FusionException
    {
        try
        {
            return Class.forName(className);
        }
        catch (ClassNotFoundException e)
        {
            throw contractFailure("Java class isn't found: " + className, e);
        }
    }


    private Method determineMethod(Class<?> klass, String methodName)
        throws FusionException
    {
        Method foundMethod = null;

        for (Method method : klass.getMethods())
        {
            if (methodName.equals(method.getName()))
            {
                if (foundMethod != null)
                {
                    String msg =
                        "Java class " + klass
                        + " has more than one method named " + methodName;
                    throw contractFailure(msg);
                }

                foundMethod = method;
            }
        }

        return foundMethod;
    }


    private void validateMethod(Method method)
        throws FusionException
    {
        Class<?>[] argTypes = method.getParameterTypes();
        Class<?> returnType = method.getReturnType();

        if (argTypes.length != 2)
        {
            throw contractFailure("Method must have exactly two args");
        }
        if (argTypes[0] != Evaluator.class)
        {
            throw contractFailure("First argument must be Evaluator");
        }

        // TODO check that we know how to eject the args
        if (argTypes[1] != String.class)
        {
            throw contractFailure("First argument must be String");
        }

        // TODO check that we know how to inject the result
        if (returnType != Boolean.TYPE)
        {
            throw contractFailure("Return type must be boolean");
        }
    }
}
