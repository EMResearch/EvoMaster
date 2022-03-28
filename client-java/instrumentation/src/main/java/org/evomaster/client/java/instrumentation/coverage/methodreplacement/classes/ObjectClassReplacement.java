package org.evomaster.client.java.instrumentation.coverage.methodreplacement.classes;

import org.evomaster.client.java.instrumentation.coverage.methodreplacement.MethodReplacementClass;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.Replacement;
import org.evomaster.client.java.instrumentation.shared.ReplacementType;

import java.util.Objects;

public class ObjectClassReplacement implements MethodReplacementClass {


    @Override
    public Class<?> getTargetClass() {
        return Object.class;
    }

    @Replacement(type = ReplacementType.BOOLEAN)
    public static boolean equals(Object left, Object right, String idTemplate) {

        if(left == null) {
           left.equals(right); //throw NPE
        }

        return ObjectsClassReplacement.equals(left, right, idTemplate);
    }

}
