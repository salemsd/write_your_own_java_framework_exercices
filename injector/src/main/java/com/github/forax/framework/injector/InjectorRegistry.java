package com.github.forax.framework.injector;

import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class InjectorRegistry {
  private final HashMap<Class<?>, Supplier<?>> registry = new HashMap<>();

  // <T> -> For every T

  public <T> void registerInstance(Class<T> type, T instance) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(instance);

    registerProvider(type, () -> instance);
  }

  public <T> T lookupInstance(Class<T> type) {
    Objects.requireNonNull(type);

    var registeredProvider = registry.get(type);
    if (registeredProvider == null) {
      throw new IllegalStateException("class " + type + " not registered");
    }

    // Compiler will give a warning if we cast with (T) because generic types
    // don't exist at compile time
    return type.cast(registeredProvider.get());
  }

  public <T> void registerProvider(Class<T> type, Supplier<T> provider) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(provider);

    var registeredProvider = registry.putIfAbsent(type, provider);
    if (registeredProvider != null) {
      throw new IllegalStateException("class " + type + " already registered");
    }
  }

  static <T> List<PropertyDescriptor> findInjectableProperties(Class <T> type) {
    var beanInfo = Utils.beanInfo(type);
    return Arrays.stream(beanInfo.getPropertyDescriptors())
            .filter(property -> !property.getName().equals("class"))
            .filter(property -> {
              var setter = property.getWriteMethod();
              if (setter == null) {
                return false;
              }

              return setter.isAnnotationPresent(Inject.class);
            })
            .toList();
  }

  public <T> void registerProviderClass(Class<T> type, Class<? extends T> providerClass) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(providerClass);

    var defaultConstructor = Utils.defaultConstructor(providerClass);
    var injectableProperties = findInjectableProperties(providerClass);

    registerProvider(type, () -> {
      var newInstance = Utils.newInstance(defaultConstructor);
      injectableProperties.forEach(property -> {
        var setter = property.getWriteMethod();
        var propertyType = property.getPropertyType();
        var instance = lookupInstance(propertyType); // Is the value of the property. if T is String and value is "Hello" then it's an instance of String
        Utils.invokeMethod(newInstance, setter, instance);
      });

      return newInstance;
    });
  }
}