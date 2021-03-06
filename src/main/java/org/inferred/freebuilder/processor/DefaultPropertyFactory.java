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
package org.inferred.freebuilder.processor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.util.SourceBuilder;

/** Default {@link PropertyCodeGenerator.Factory}, providing reference semantics for any type. */
public class DefaultPropertyFactory implements PropertyCodeGenerator.Factory {

  private static final String SET_PREFIX = "set";

  @Override
  public Optional<? extends PropertyCodeGenerator> create(Config config) {
    String setterName = SET_PREFIX + config.getProperty().getCapitalizedName();
    boolean hasDefault = config.getMethodsInvokedInBuilderConstructor().contains(setterName);
    return Optional.of(new CodeGenerator(config.getProperty(), setterName, hasDefault));
  }

  @VisibleForTesting static class CodeGenerator extends PropertyCodeGenerator {

    final String setterName;
    final boolean hasDefault;

    CodeGenerator(Property property, String setterName, boolean hasDefault) {
      super(property);
      this.setterName = setterName;
      this.hasDefault = hasDefault;
    }

    @Override
    public Type getType() {
      return hasDefault ? Type.HAS_DEFAULT : Type.REQUIRED;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      code.addLine("  private %s %s;", property.getType(), property.getName());
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code, Metadata metadata) {
      // Setter
      code.addLine("")
          .addLine("  /**")
          .addLine("   * Sets the value to be returned by {@link %s#%s()}.",
              metadata.getType(), property.getGetterName())
          .addLine("   *")
          .addLine("   * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!property.getType().getKind().isPrimitive()) {
        code.addLine("   * @throws NullPointerException if {@code %s} is null", property.getName());
      }
      code.addLine("   */")
          .addLine("  public %s %s(%s %s) {",
              metadata.getBuilder(),
              setterName,
              property.getType(),
              property.getName());
      if (property.getType().getKind().isPrimitive()) {
        code.addLine("    this.%1$s = %1$s;", property.getName());
      } else {
        code.addLine("    this.%1$s = %2$s.checkNotNull(%1$s);",
            property.getName(), Preconditions.class);
      }
      if (!hasDefault) {
        code.addLine("    _unsetProperties.remove(%s.%s);",
            metadata.getPropertyEnum(), property.getAllCapsName());
      }
      if ((metadata.getBuilder() == metadata.getGeneratedBuilder())) {
        code.addLine("    return this;");
      } else {
        code.addLine("    return (%s) this;", metadata.getBuilder());
      }
      code.addLine("  }");

      // Getter
      code.addLine("")
          .addLine("  /**")
          .addLine("   * Returns the value that will be returned by {@link %s#%s()}.",
              metadata.getType(), property.getGetterName());
      if (!hasDefault) {
        code.addLine("   *")
            .addLine("   * @throws IllegalStateException if the field has not been set");
      }
      code.addLine("   */")
          .addLine("  public %s %s() {", property.getType(), property.getGetterName());
      if (!hasDefault) {
        code.addLine("    %s.checkState(", Preconditions.class)
            .addLine("        !_unsetProperties.contains(%s.%s),",
                metadata.getPropertyEnum(), property.getAllCapsName())
            .addLine("        \"%s not set\");", property.getName());
      }
      code.addLine("    return %s;", property.getName())
          .addLine("  }");
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, String finalField, String builder) {
      code.addLine("      %s = %s.%s;", finalField, builder, property.getName());
    }

    @Override
    public void addMergeFromValue(SourceBuilder code, String value) {
      code.addLine("    %s(%s.%s());", setterName, value, property.getGetterName());
    }

    @Override
    public void addMergeFromBuilder(SourceBuilder code, Metadata metadata, String builder) {
      code.addLine("    %s(%s.%s());", setterName, builder, property.getGetterName());
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("        %s.%s(%s);", builder, setterName, variable);
    }

    @Override
    public boolean isTemplateRequiredInClear() {
      return true;
    }

    @Override
    public void addClear(SourceBuilder code, String template) {
      code.addLine("    %1$s = %2$s.%1$s;", property.getName(), template);
    }

    @Override
    public void addPartialClear(SourceBuilder code) { }
  }
}
