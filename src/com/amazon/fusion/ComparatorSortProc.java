package com.amazon.fusion;

import java.util.Arrays;
import java.util.Comparator;

public class ComparatorSortProc
    extends Procedure
{
    @SuppressWarnings("serial")
    private class ComparatorException
        extends RuntimeException
    {
        private final FusionException myCause;
        public ComparatorException(String message, FusionException cause) {
            super(message, cause);
            myCause = cause;
        }

        @Override
        public FusionException getCause() {
            return myCause;
        }
    }

    @Override
    Object doApply(final Evaluator eval, final Object[] args)
        throws FusionException
    {

        checkArityExact(2, args);

        Object listObject =
            FusionList.checkNullableListArg(eval, this, 0, args);
        final Procedure compareProc = checkProcArg(1, args);

        Object[] list = FusionList.unsafeListExtract(eval, listObject);
        try
        {
            Arrays.sort(list, new Comparator<Object>()
            {
                @Override
                public int compare(Object l, Object r)
                {
                    try
                    {
                        Object resultObject =
                            eval.callNonTail(compareProc, l, r);
                        int result =
                            FusionNumber.unsafeTruncateIntToJavaInt(eval,
                                                                    resultObject);

                        return result;
                    }
                    catch (FusionException e)
                    {
                        throw new ComparatorException(String.format("Comparison for sort failed on the elements %s and %s: %s",
                                                                    l, r, e),
                                                      e);
                    }
                }
            });
        }
        catch (ComparatorException t)
        {
            throw t.getCause();
        }

        return FusionList.immutableList(eval, list);
    }
}
