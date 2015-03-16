/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.inferred.freebuilder.processor.util;

import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.base.Predicates.notNull;
import static com.google.common.collect.Iterables.addAll;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;

/**
 * Manages the imports for a source file, and produces short type references by adding extra
 * imports when possible.
 *
 * <p>To ensure we never import common names like 'Builder', nested classes are never directly
 * imported. This is necessarily less readable when types are used as namespaces, e.g. in proto2.
 */
class ImportManager extends SimpleTypeVisitor6<String, Void>
    implements Function<TypeMirror, String>, TypeShortener {

  private static final String JAVA_LANG_PACKAGE = "java.lang";
  private static final String PACKAGE_PREFIX = "package ";

  /**
   * Builder of {@link ImportManager} instances.
   */
  public static class Builder {

    /**
     * Simple names of implicitly imported types, mapped to qualified name if that type is safe to
     * use, null otherwise.
     */
    private final Map<Name, Name> implicitImports = new LinkedHashMap<Name, Name>();

    /**
     * Adds a type which is implicitly imported into the current compilation unit.
     */
    public Builder addImplicitImport(TypeElement type) {
      Name simpleName = type.getSimpleName();
      boolean isTopLevel = type.getEnclosingElement().getKind().equals(ElementKind.PACKAGE);
      if (isTopLevel && !implicitImports.containsKey(simpleName)) {
        implicitImports.put(simpleName, type.getQualifiedName());
      } else if (!type.getQualifiedName().equals(implicitImports.get(simpleName))) {
        implicitImports.put(simpleName, null);
      }
      return this;
    }

    /**
     * Adds a type which is implicitly imported into the current compilation unit.
     */
    public Builder addImplicitImport(AbstractImpliedClass type) {
      Name simpleName = type.getSimpleName();
      boolean isTopLevel = type.getNestedSuffix().isEmpty();
      if (isTopLevel && !implicitImports.containsKey(simpleName)) {
        implicitImports.put(simpleName, type.getQualifiedName());
      } else if (!type.getQualifiedName().equals(implicitImports.get(simpleName))) {
        implicitImports.put(simpleName, null);
      }
      return this;
    }

    public ImportManager build() {
      return new ImportManager(
          FluentIterable.from(implicitImports.keySet())
              .transform(toStringFunction()),
          FluentIterable.from(implicitImports.values())
              .filter(notNull())
              .transform(toStringFunction()));
    }
  }

  private final Set<String> visibleSimpleNames = new HashSet<String>();
  private final Set<String> implicitImports = new HashSet<String>();
  private final Set<String> explicitImports = new TreeSet<String>();

  private ImportManager(Iterable<String> visibleSimpleNames, Iterable<String> implicitImports) {
    addAll(this.visibleSimpleNames, visibleSimpleNames);
    addAll(this.implicitImports, implicitImports);
  }

  public Set<String> getClassImports() {
    return Collections.unmodifiableSet(explicitImports);
  }

  @Override
  public String shorten(Class<?> cls) {
    if (cls.getEnclosingClass() != null) {
      return shorten(cls.getEnclosingClass()) + "." + cls.getSimpleName();
    } else if (cls.getPackage() != null) {
      return getPrefixForTopLevelClass(cls.getPackage().getName(), cls.getSimpleName())
          + cls.getSimpleName();
    } else {
      return cls.getSimpleName();
    }
  }

  @Override
  public String shorten(TypeElement type) {
    Element parent = type.getEnclosingElement();
    if (parent.getKind() == ElementKind.PACKAGE) {
      return getPrefixForTopLevelClass(parent.toString(), type.getSimpleName())
          + type.getSimpleName();
    } else if (parent.getKind().isInterface() || parent.getKind().isClass()) {
      return shorten((TypeElement) parent) + "." + type.getSimpleName();
    } else {
      return type.getQualifiedName().toString();
    }
  }

  @Override
  public String shorten(TypeMirror mirror) {
    return mirror.accept(this, null);
  }

  @Override
  public String shorten(TypeReference type) {
    String prefix = getPrefixForTopLevelClass(
        type.getPackage().toString(), type.getTopLevelTypeSimpleName());
    return prefix + type.getTopLevelTypeSimpleName() + type.getNestedSuffix();
  }

  @Override
  public String apply(TypeMirror mirror) {
    return mirror.accept(this, null);
  }

  @Override
  public String visitDeclared(DeclaredType mirror, Void p) {
    Name name = mirror.asElement().getSimpleName();
    final String prefix;
    Element enclosingElement = mirror.asElement().getEnclosingElement();
    if (mirror.getEnclosingType().getKind() != TypeKind.NONE) {
      prefix = visit(mirror.getEnclosingType()) + ".";
    } else if (enclosingElement.getKind() == ElementKind.PACKAGE) {
      PackageElement pkg = (PackageElement) enclosingElement;
      prefix = getPrefixForTopLevelClass(pkg.getQualifiedName().toString(), name);
    } else if (enclosingElement.getKind().isClass() || enclosingElement.getKind().isInterface()) {
      prefix = shorten((TypeElement) enclosingElement) + ".";
    } else {
      prefix = enclosingElement.toString() + ".";
    }
    final String suffix;
    if (!mirror.getTypeArguments().isEmpty()) {
      List<String> shortTypeArguments = Lists.transform(mirror.getTypeArguments(), this);
      suffix = "<" + Joiner.on(", ").join(shortTypeArguments) + ">";
    } else {
      suffix = "";
    }
    return prefix + name + suffix;
  }

  private String getPrefixForTopLevelClass(String pkg, CharSequence name) {
    if (pkg.startsWith(PACKAGE_PREFIX)) {
      pkg = pkg.substring(PACKAGE_PREFIX.length());
    }
    String qualifiedName = pkg + "." + name;
    if (implicitImports.contains(qualifiedName) || explicitImports.contains(qualifiedName)) {
      return "";
    } else if (visibleSimpleNames.contains(name.toString())) {
      return pkg + ".";
    } else if (pkg.equals(JAVA_LANG_PACKAGE)) {
      return "";
    } else {
      visibleSimpleNames.add(name.toString());
      explicitImports.add(qualifiedName);
      return "";
    }
  }

  @Override
  protected String defaultAction(TypeMirror mirror, Void p) {
    return mirror.toString();
  }
}
