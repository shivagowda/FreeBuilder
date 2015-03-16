package org.inferred.freebuilder.processor.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import javassist.util.proxy.MethodFilter;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;

public class Partial {

  public static <T> T of(Class<T> cls, Object... args) {
    ProxyFactory factory = new ProxyFactory();
    factory.setSuperclass(cls);
    factory.setFilter(new MethodFilter() {
      @Override public boolean isHandled(Method m) {
        return Modifier.isAbstract(m.getModifiers());
      }
    });
    try {
      checkArgument(
          cls.getDeclaredConstructors().length == 1,
          "Partial class %s must have exactly one constructor (found %s)",
          cls,
          cls.getDeclaredConstructors().length);
      Constructor<?> constructor = cls.getDeclaredConstructors()[0];
      checkArgument(!Modifier.isPrivate(constructor.getModifiers()),
          "Partial class %s must have a package-visible constructor", cls);
      @SuppressWarnings("unchecked")
      T partial = (T) factory.create(
          constructor.getParameterTypes(), args, new ThrowingMethodHandler());
      return partial;
    } catch (Exception e) {
      throw new RuntimeException("Failed to instantiate " + cls, e);
    }
  }

  private static final class ThrowingMethodHandler implements MethodHandler {
    @Override
    public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) {
      throw new UnsupportedOperationException();
    }
  }

  private Partial() {}
}
