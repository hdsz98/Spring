package com.demo.service.impl;

import com.demo.service.IDemoService;
import com.mvcframework.annotation.GPService;

import java.lang.annotation.Annotation;

@GPService
public class DemoService implements IDemoService {

    @Override
    public String get(String name) {
        return "My name is " + name;
    }
}
