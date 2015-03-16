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

import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.util.Elements;

/**
 * Abstract superclass of types representing a class that needs to be generated.
 */
public abstract class AbstractImpliedClass extends TypeReference {

  private final Name qualifiedName;
  private final PackageElement pkg;
  private final Name simpleName;

  /** Constructor for top-level classes. */
  AbstractImpliedClass(PackageElement pkg, CharSequence simpleName, Elements elementUtils) {
    super(pkg.getQualifiedName(), simpleName.toString(), "");
    this.qualifiedName = elementUtils.getName(pkg.getQualifiedName() + "." + simpleName);
    this.simpleName = elementUtils.getName(simpleName);
    this.pkg = pkg;
  }

  /** Constructor for nested classes. */
  AbstractImpliedClass(
      AbstractImpliedClass enclosingClass, CharSequence simpleName, Elements elementUtils) {
    super(
        enclosingClass.getPackage(),
        enclosingClass.getTopLevelTypeSimpleName(),
        enclosingClass.getNestedSuffix() + "." + simpleName);
    this.qualifiedName = elementUtils.getName(enclosingClass.getQualifiedName() + "." + simpleName);
    this.simpleName = elementUtils.getName(simpleName);
    this.pkg = enclosingClass.getPackageElement();
  }

  @Override
  public Name getQualifiedName() {
    return qualifiedName;
  }

  public Name getSimpleName() {
    return simpleName;
  }

  public PackageElement getPackageElement() {
    return pkg;
  }
}
