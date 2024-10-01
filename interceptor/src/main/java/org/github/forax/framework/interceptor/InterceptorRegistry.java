package org.github.forax.framework.interceptor;

import jdk.jshell.execution.Util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Arrays;
import java.util.stream.Stream;

public final class InterceptorRegistry {
  private final HashMap<Class<?>, List<Interceptor>> interceptorMap = new HashMap<>();


  public void addAroundAdvice(Class<? extends Annotation> annotationClass, AroundAdvice aroundAdvice) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(aroundAdvice);
    // Could have used a LinkedHashSet to account for duplicates but it doesn't happen often
    addInterceptor(annotationClass, (instance, method, args, invocation) -> {
      aroundAdvice.before(instance, method, args);
      Object result = null;
      try {
        result = invocation.proceed(instance, method, args);
      } finally {
        aroundAdvice.after(instance, method, args, result);
      }

      return result;
    });
  }

  // Returns a proxy that when we call a method, it will call all the before methods, and then the method and then all the after
  public <T> T createProxy(Class<T> interfaceType, T instance) {
    Objects.requireNonNull(interfaceType);
    Objects.requireNonNull(instance);
    // We use the same class loader that loader the interface
    // Since an interface can be implemented by multiple classes, we need an array
    return interfaceType.cast(Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[] { interfaceType },
            (proxy, method, args) -> {
              var interceptors = findInterceptors(method);
              var invocation = getInvocation(interceptors);
              return invocation.proceed(instance, method, args);
            }));
  }

  /* Useless from Q5
  List<AroundAdvice> findAdvices(Method method) {
    Objects.requireNonNull(method);

    return Arrays.stream(method.getAnnotations())
            .flatMap(annotation -> adviceMap.getOrDefault(annotation.annotationType(), List.of()).stream())
            .toList();
  }
  */

  public void addInterceptor(Class <? extends Annotation> annotationClass, Interceptor interceptor) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(interceptor);

    interceptorMap
            .computeIfAbsent(annotationClass, _ -> new ArrayList<>())
            .add(interceptor);
  }

  List<Interceptor> findInterceptors(Method method) {
    Objects.requireNonNull(method);

    return Arrays.stream(method.getAnnotations())
            .flatMap(annotation -> interceptorMap.getOrDefault(annotation.annotationType(), List.of()).stream())
            .toList();
  }

  static Invocation getInvocation(List<Interceptor> interceptors) {
    Invocation invocation = Utils::invokeMethod;

    for (var interceptor : interceptors.reversed()) {
      var copyOfInvocation = invocation;
      invocation = (instance, method, args) ->
              // call the interceptor with the old invocation before changing it
              interceptor.intercept(instance, method, args, copyOfInvocation);
    }

    return invocation;
  }
}

/*
The invocation object is for calling a method

Proxy: object that implements interface and that
delegates the implementation to another object that also implements the same interface
Eg: object helloImpl that implements hello and that has another object that implements
hello in the fields

Dynamic proxy: we can use it to get an instance of an interface, where for every method
of that interface, we'll call the lambda in parameter

Java doesn't load classes that it doesn't use to memory. The class loader takes care of that
by only loading them when needed (new or static method call). Transforms into bytecode(??)

 Interceptor is a more powerful and functional approach to what we did in Q1 and Q2
  Instead of having before and after we have one single method that calls the next interceptor
  and invocation and then the next until calling the method | interceptor + invoc -> interceptor + invoc -> interceptor + invoc -> method
*/
