package com.mvcframework.v1.servlet;

import com.mvcframework.annotation.GPAutowired;
import com.mvcframework.annotation.GPController;
import com.mvcframework.annotation.GPRequestMapping;
import com.mvcframework.annotation.GPService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class GPDispatcherServlet extends HttpServlet {
    private Map<String, Object> map = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            this.doDispathch(req, resp);
        }
        catch (Exception e) {
            resp.getWriter().write("500 Exception " + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispathch(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String url = request.getRequestURI();
        String contextPath = request.getContextPath();
        url = url.replace(contextPath, "".replaceAll("/+", "/"));
        if (!this.map.containsKey(url)) {
            response.getWriter().write("404 Not Found!!");
            return;
        }
        Method method = (Method) this.map.get(url);
        Map<String, String[]> params = request.getParameterMap();
        method.invoke(this.map.get(method.getDeclaringClass().getName()), new Object[]{request, response, params.get("name")[0]});
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        InputStream is = null;
        try {
            Properties configContext = new Properties();
            is = this.getClass().getClassLoader().getResourceAsStream(config.getInitParameter("contextConfigLocation"));
            configContext.load(is);
            String scanPackage = configContext.getProperty("scanPackage");
            doScanner(scanPackage);
            for (String className : map.keySet()) {
                if (!className.contains(".")) continue;
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(GPController.class)) {
                    map.put(className, clazz.newInstance());
                    String baseUrl = "";
                    if (clazz.isAnnotationPresent(GPRequestMapping.class)) {
                        GPRequestMapping requestMapping = clazz.getAnnotation(GPRequestMapping.class);
                        baseUrl = requestMapping.value();
                    }
                    Method[] methods = clazz.getMethods();
                    for (Method method : methods) {
                        if (!method.isAnnotationPresent(GPRequestMapping.class)) continue;
                        GPRequestMapping requestMapping = method.getAnnotation(GPRequestMapping.class);
                        String url = (baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");
                        map.put(url, method);
                        System.out.println("Mapper " + url + ", " + method);
                    }
                }
                else if (clazz.isAnnotationPresent(GPService.class)) {
                    GPService service = clazz.getAnnotation(GPService.class);
                    String beanName = service.value();
                    if ("".equals(beanName))
                        beanName = clazz.getName();
                    Object instance = clazz.newInstance();
                    map.put(beanName, instance);
                    for (Class<?> i : clazz.getInterfaces())
                        map.put(i.getName(), instance);
                }
                else
                    continue;
            }
            for (Object object : map.values()) {
                if (object == null)
                    continue;
                Class clazz = object.getClass();
                if (clazz.isAnnotationPresent(GPController.class)) {
                    Field[] fields = clazz.getDeclaredFields();
                    for (Field field : fields) {
                        if (!field.isAnnotationPresent(GPAutowired.class))
                            continue;
                        GPAutowired autowired = field.getAnnotation(GPAutowired.class);
                        String beanName = autowired.value();
                        if ("".equals(beanName))
                            beanName = field.getType().getName();
                        field.setAccessible(true);
                        field.set(map.get(clazz.getName()), map.get(beanName));
                    }
                }
            }

        } catch (Exception e) {
        }
        finally {
            if (is == null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("GPMVC framework is init.");
    }
    public void doScanner (String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()) {
            if (file.isDirectory())
                doScanner(scanPackage + "." + file.getName());
            else {
                if (!file.getName().endsWith(".class"))
                    continue;
                String clazzName = (scanPackage + "/" + file.getName().replace(".class", ""));
                map.put(clazzName, null);
            }
        }
    }
}
