package com.github.forax.framework.injector;

import java.util.HashMap;
import java.util.Objects;
import java.util.function.Supplier;

public final class InjectorRegistry {
  private final HashMap<Class<?>, Object> registry = new HashMap<>();

  // <T> -> For every T

  public <T> void registerInstance(Class<T> type, T instance) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(instance);

    var registeredValue = registry.putIfAbsent(type, instance);
    if (registeredValue != null) {
      throw new IllegalStateException("class " + type + " already registered");
    }
  }

  public <T> T lookupInstance(Class<T> type) {
    Objects.requireNonNull(type);

    var registeredValue = registry.get(type);
    if (registeredValue == null) {
      throw new IllegalStateException("class " + type + " not registered");
    }

    // Compiler will give a warning if we cast with (T) because generic types
    // don't exist at compile time
    return type.cast(registeredValue);
  }

  public <T> void registerProvider(Class<T> type, Supplier<T> supplier) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(supplier);
  }
}