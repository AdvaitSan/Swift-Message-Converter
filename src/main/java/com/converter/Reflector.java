package com.converter;

import com.prowidesoftware.swift.model.mx.dic.*;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.Arrays;

public class Reflector {
    public static void main(String[] args) {
        System.out.println("--- CorporateActionGeneralInformation165 ---");
        for (Method m : CorporateActionGeneralInformation165.class.getDeclaredMethods()) {
            if (m.getName().startsWith("set")) {
                System.out.println(m.getName() + " -> " + m.getParameterTypes()[0].getName());
            }
        }
        
        System.out.println("--- CorporateActionNotification5 ---");
        for (Method m : CorporateActionNotification5.class.getDeclaredMethods()) {
            if (m.getName().startsWith("set")) {
                System.out.println(m.getName() + " -> " + m.getParameterTypes()[0].getName());
            }
        }

        System.out.println("--- CorporateAction60 ---");
        for (Method m : CorporateAction60.class.getDeclaredMethods()) {
            if (m.getName().startsWith("set")) {
                System.out.println(m.getName() + " -> " + m.getParameterTypes()[0].getName());
            }
        }
        
        System.out.println("--- MT564 Sequences ---");
        for (Method m : com.prowidesoftware.swift.model.mt.mt5xx.MT564.class.getDeclaredMethods()) {
            if (m.getName().startsWith("getSequenceD")) {
                System.out.println(m.getName() + " -> " + m.getReturnType().getName());
            }
        }
    }
}
