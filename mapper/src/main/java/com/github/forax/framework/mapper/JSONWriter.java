package com.github.forax.framework.mapper;

import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class JSONWriter {
  private static final ClassValue<PropertyDescriptor[]> PROPERTY_DESCRIPTOR_CLASS_VALUE = new ClassValue<PropertyDescriptor[]>() { // Creates a new class that inherits ClassValue
    @Override
    protected PropertyDescriptor[] computeValue(Class<?> aClass) {
      return Utils.beanInfo(aClass).getPropertyDescriptors();
    }
  };

  public String toJSON(Object o) {
    return switch (o) {
      case Boolean b -> "" + b;
      case Integer i -> "" + i;
      case Double d -> "" + d;
      case String s -> '"' + s + '"';
      case null -> "null";
      default -> {
        var beanInfoProperties = PROPERTY_DESCRIPTOR_CLASS_VALUE.get(o.getClass());
        // var properties = beanInfo.getPropertyDescriptors(); // Bad performance because it will still return a new array since they're mutable (that's why we made the ClassValue return the array instead

        yield Arrays.stream(beanInfoProperties)
                .filter(property -> !property.getName().equals("class")) // Avoid stack overflow by returning classes infinitely
                .map(property -> {
                  var getter = property.getReadMethod();
                  var annotation = getter.getAnnotation(JSONProperty.class); // Check if the getter has a json annotation (for example first-name instead of firstName
                  return '"' + (annotation == null ? property.getName() : annotation.value()) + "\": " + toJSON(Utils.invokeMethod(o, getter));
                })
                .collect(Collectors.joining(", ", "{", "}"));



        // throw new IllegalArgumentException("unsupported arg + " + o);
      }
    };

    //throw new UnsupportedOperationException("TODO");
  }
}
