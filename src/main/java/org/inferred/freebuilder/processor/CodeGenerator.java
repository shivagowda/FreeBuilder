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

import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Iterables.getOnlyElement;
import static org.inferred.freebuilder.processor.Metadata.Property.GET_CODE_GENERATOR;
import static org.inferred.freebuilder.processor.Metadata.UnderrideLevel.ABSENT;
import static org.inferred.freebuilder.processor.Metadata.UnderrideLevel.FINAL;
import static org.inferred.freebuilder.processor.PropertyCodeGenerator.IS_TEMPLATE_REQUIRED_IN_CLEAR;
import static org.inferred.freebuilder.processor.util.SourceBuilders.withIndent;

import java.io.Serializable;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Generated;
import javax.lang.model.element.TypeElement;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.Metadata.StandardMethod;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Type;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.TypeReference;

import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Code generation for the &#64;{@link FreeBuilder} annotation.
 */
public class CodeGenerator {

  private static final TypeReference CUSTOM_FIELD_SERIALIZER =
      TypeReference.to("com.google.gwt.user.client.rpc", "CustomFieldSerializer");
  private static final TypeReference SERIALIZATION_EXCEPTION =
      TypeReference.to("com.google.gwt.user.client.rpc", "SerializationException");
  private static final TypeReference SERIALIZATION_STREAM_READER =
      TypeReference.to("com.google.gwt.user.client.rpc", "SerializationStreamReader");
  private static final TypeReference SERIALIZATION_STREAM_WRITER =
      TypeReference.to("com.google.gwt.user.client.rpc", "SerializationStreamWriter");

  /** Write the source code for a generated builder. */
  void writeBuilderSource(SourceBuilder code, Metadata metadata) {
    if (metadata.getBuilder() == metadata.getGeneratedBuilder()) {
      writeStubSource(code, metadata);
      return;
    }
    boolean hasRequiredProperties = any(metadata.getProperties(), IS_REQUIRED);
    code.addLine("/**")
        .addLine(" * Auto-generated superclass of {@link %s},", metadata.getBuilder())
        .addLine(" * derived from the API of {@link %s}.", metadata.getType())
        .addLine(" */")
        .addLine("@%s(\"%s\")", Generated.class, this.getClass().getName());
    if (metadata.isGwtCompatible()) {
      code.addLine("@%s", GwtCompatible.class);
    }
    code.add("abstract class %s", metadata.getGeneratedBuilder().getSimpleName());
    if (metadata.isBuilderSerializable()) {
      code.add(" implements %s", Serializable.class);
    }
    code.addLine(" {");
    // Static fields
    if (metadata.getProperties().size() > 1) {
      code.addLine("")
          .addLine("  private static final %1$s COMMA_JOINER = %1$s.on(\", \").skipNulls();",
              Joiner.class);
    }
    // Property enum
    if (hasRequiredProperties) {
      addPropertyEnum(metadata, code);
    }
    // Property fields
    code.addLine("");
    for (Property property : metadata.getProperties()) {
      PropertyCodeGenerator codeGenerator = property.getCodeGenerator();
      codeGenerator.addBuilderFieldDeclaration(code);
    }
    // Unset properties
    if (hasRequiredProperties) {
      code.addLine("  private final %s<%s> _unsetProperties =",
              EnumSet.class, metadata.getPropertyEnum())
          .addLine("      %s.allOf(%s.class);", EnumSet.class, metadata.getPropertyEnum());
    }
    // Setters and getters
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addBuilderFieldAccessors(code, metadata);
    }
    // Value type
    String inheritsFrom = getInheritanceKeyword(metadata.getType());
    code.addLine("");
    if (metadata.isGwtSerializable()) {
      // Due to a bug in GWT's handling of nested types, we have to declare Value as package scoped
      // so Value_CustomFieldSerializer can access it.
      code.addLine("  @%s(serializable = true)", GwtCompatible.class)
          .addLine("  static final class %s %s %s {",
              metadata.getValueType().getSimpleName(),
              inheritsFrom,
              metadata.getType());
    } else {
      code.addLine("  private static final class %s %s %s {",
          metadata.getValueType().getSimpleName(),
          inheritsFrom,
          metadata.getType());
    }
    // Fields
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addValueFieldDeclaration(code, property.getName());
    }
    // Constructor
    code.addLine("")
        .addLine("    private %s(%s builder) {",
            metadata.getValueType().getSimpleName(),
            metadata.getGeneratedBuilder());
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator()
          .addFinalFieldAssignment(code, "this." + property.getName(), "builder");
    }
    code.addLine("    }");
    // Getters
    for (Property property : metadata.getProperties()) {
      code.addLine("")
          .addLine("    @%s", Override.class);
      for (TypeElement nullableAnnotation : property.getNullableAnnotations()) {
        code.addLine("    @%s", nullableAnnotation);
      }
      code.addLine("    public %s %s() {", property.getType(), property.getGetterName());
      code.add("      return ");
      property.getCodeGenerator().addReadValueFragment(code, property.getName());
      code.add(";\n");
      code.addLine("    }");
    }
    // Equals
    switch (metadata.standardMethodUnderride(StandardMethod.EQUALS)) {
      case ABSENT:
        // Default implementation if no user implementation exists.
        code.addLine("")
            .addLine("    @%s", Override.class)
            .addLine("    public boolean equals(Object obj) {")
            .addLine("      if (!(obj instanceof %s)) {", metadata.getValueType())
            .addLine("        return false;")
            .addLine("      }")
            .addLine("      %1$s other = (%1$s) obj;", metadata.getValueType());
        for (Property property : metadata.getProperties()) {
          switch (property.getType().getKind()) {
            case FLOAT:
            case DOUBLE:
              code.addLine("      if (%s.doubleToLongBits(%s)", Double.class, property.getName())
                  .addLine("          != %s.doubleToLongBits(other.%s)) {",
                      Double.class, property.getName());
              break;

            default:
              if (property.getType().getKind().isPrimitive()) {
                code.addLine("      if (%1$s != other.%1$s) {", property.getName());
              } else if (property.getCodeGenerator().getType() == Type.OPTIONAL) {
                code.addLine("      if (%1$s != other.%1$s", property.getName())
                .addLine("          && (%1$s == null || !%1$s.equals(other.%1$s))) {",
                    property.getName());
              } else {
                code.addLine("      if (!%1$s.equals(other.%1$s)) {", property.getName());
              }
          }
          code.addLine("        return false;")
              .addLine("      }");
        }
        code.addLine("      return true;")
            .addLine("    }");
        break;

      case OVERRIDEABLE:
        // Partial-respecting override if a non-final user implementation exists.
        code.addLine("")
            .addLine("    @%s", Override.class)
            .addLine("    public boolean equals(Object obj) {")
            .addLine("      return (!(obj instanceof %s) && super.equals(obj));",
                metadata.getPartialType())
            .addLine("    }");
        break;

      case FINAL:
        // Cannot override if a final user implementation exists.
        break;
    }
    // Hash code
    if (metadata.standardMethodUnderride(StandardMethod.HASH_CODE) == ABSENT) {
      code.addLine("")
          .addLine("    @%s", Override.class)
          .addLine("    public int hashCode() {")
          .addLine("      return %s.hashCode(new Object[] { %s });",
              Arrays.class, Joiner.on(", ").join(getNames(metadata.getProperties())))
          .addLine("    }");
    }
    // toString
    if (metadata.standardMethodUnderride(StandardMethod.TO_STRING) == ABSENT) {
      code.addLine("")
          .addLine("    @%s", Override.class)
          .addLine("    public %s toString() {", String.class)
          .add("      return \"%s{", metadata.getType().getSimpleName());
      switch (metadata.getProperties().size()) {
        case 0: {
          code.add("}\";\n");
          break;
        }

        case 1: {
          Property property = getOnlyElement(metadata.getProperties());
          if (property.getCodeGenerator().getType() == Type.OPTIONAL) {
            code.add("\" + (%1$s != null ? \"%1$s=\" + %1$s : \"\") + \"}\";\n",
                property.getName());
          } else {
            code.add("%1$s=\" + %1$s + \"}\";\n", property.getName());
          }
          break;
        }

        default: {
          // If one or more of the properties are optional, use COMMA_JOINER for readability.
          // Otherwise, use string concatenation for performance.
          if (any(metadata.getProperties(), IS_OPTIONAL)) {
            code.add("\"\n")
                .add("          + COMMA_JOINER.join(\n");
            Property lastProperty = getLast(metadata.getProperties());
            for (Property property : metadata.getProperties()) {
              code.add("              ");
              if (property.getCodeGenerator().getType() == Type.OPTIONAL) {
                code.add("(%s != null ? ", property.getName());
              }
              code.add("\"%1$s=\" + %1$s", property.getName());
              if (property.getCodeGenerator().getType() == Type.OPTIONAL) {
                code.add(" : null)");
              }
              if (property != lastProperty) {
                code.add(",\n");
              } else {
                code.add(")\n");
              }
            }
            code.addLine("          + \"}\";");
          } else {
            code.add("\"\n");
            Property lastProperty = getLast(metadata.getProperties());
            for (Property property : metadata.getProperties()) {
              code.add("          + \"%1$s=\" + %1$s", property.getName());
              if (property != lastProperty) {
                code.add(" + \", \"\n");
              } else {
                code.add(" + \"}\";\n");
              }
            }
          }
          break;
        }
      }
      code.addLine("    }");
    }
    code.addLine("  }");
    if (metadata.isGwtSerializable()) {
      addCustomValueSerializer(metadata, code);
    }
    // build()
    code.addLine("")
        .addLine("  /**")
        .addLine("   * Returns a newly-created {@link %s} based on the contents of the {@code %s}.",
            metadata.getType(), metadata.getBuilder().getSimpleName());
    if (hasRequiredProperties) {
      code.addLine("   *")
          .addLine("   * @throws IllegalStateException if any field has not been set");
    }
    code.addLine("   */")
        .addLine("  public %s build() {", metadata.getType());
    if (hasRequiredProperties) {
      code.addLine(
          "    %s.checkState(_unsetProperties.isEmpty(), \"Not set: %%s\", _unsetProperties);",
          Preconditions.class);
    }
    code.addLine("    return new %s(this);", metadata.getValueType())
        .addLine("  }");
    // mergeFrom(Value)
    code.addLine("")
        .addLine("  /**")
        .addLine("   * Sets all property values using the given {@code %s} as a template.",
            metadata.getType())
        .addLine("   */")
        .addLine("  public %s mergeFrom(%s value) {",
            metadata.getBuilder(), metadata.getType());
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addMergeFromValue(code, "value");
    }
    code.addLine("    return (%s) this;", metadata.getBuilder());
    code.addLine("  }");
    // mergeFrom(Builder)
    code.addLine("")
        .addLine("  /**")
        .addLine("   * Copies values from the given {@code %s}.",
            metadata.getBuilder().getSimpleName());
    if (hasRequiredProperties) {
      code.addLine("   * Does not affect any properties not set on the input.");
    }
    code.addLine("   */")
        .addLine("  public %1$s mergeFrom(%1$s template) {", metadata.getBuilder());
    if (hasRequiredProperties) {
      code.addLine("    // Upcast to access the private _unsetProperties field.")
          .addLine("    // Otherwise, oddly, we get an access violation.")
          .addLine("    %s<%s> _templateUnset = ((%s) template)._unsetProperties;",
              EnumSet.class,
              metadata.getPropertyEnum(),
              metadata.getGeneratedBuilder());
    }
    for (Property property : metadata.getProperties()) {
      if (property.getCodeGenerator().getType() == Type.REQUIRED) {
        code.addLine("    if (!_templateUnset.contains(%s.%s)) {",
            metadata.getPropertyEnum(), property.getAllCapsName());
        property.getCodeGenerator().addMergeFromBuilder(withIndent(code, 2), metadata, "template");
        code.addLine("    }");
      } else {
        property.getCodeGenerator().addMergeFromBuilder(code, metadata, "template");
      }
    }
    code.addLine("    return (%s) this;", metadata.getBuilder());
    code.addLine("  }");
    // clear()
    if (metadata.getBuilderFactory().isPresent()) {
      code.addLine("")
          .addLine("  /**")
          .addLine("   * Resets the state of this builder.")
          .addLine("   */")
          .addLine("  public %s clear() {", metadata.getBuilder());
      List<PropertyCodeGenerator> codeGenerators =
          Lists.transform(metadata.getProperties(), GET_CODE_GENERATOR);
      if (Iterables.any(codeGenerators, IS_TEMPLATE_REQUIRED_IN_CLEAR)) {
        code.add("    %s _template = ", metadata.getGeneratedBuilder());
        metadata.getBuilderFactory().get().addNewBuilder(code, metadata.getBuilder());
        code.add(";\n");
      }
      for (PropertyCodeGenerator codeGenerator : codeGenerators) {
        if (codeGenerator.isTemplateRequiredInClear()) {
          codeGenerator.addClear(code, "_template");
        } else {
          codeGenerator.addClear(code, null);
        }
      }
      if (hasRequiredProperties) {
        code.addLine("    _unsetProperties.clear();")
            .addLine("    _unsetProperties.addAll(_template._unsetProperties);",
                metadata.getGeneratedBuilder());
      }
      code.addLine("    return (%s) this;", metadata.getBuilder())
          .addLine("  }");
    } else {
      code.addLine("")
          .addLine("  /**")
          .addLine("   * Ensures a subsequent mergeFrom call will make a clone of its input.")
          .addLine("   *")
          .addLine("   * <p>The exact implementation of this method is not guaranteed to remain")
          .addLine("   * stable; it should always be followed directly by a mergeFrom call.")
          .addLine("   */")
          .addLine("  public %s clear() {", metadata.getBuilder());
      for (Property property : metadata.getProperties()) {
        property.getCodeGenerator().addPartialClear(code);
      }
      code.addLine("    return (%s) this;", metadata.getBuilder())
          .addLine("  }");
    }
    // GWT whitelist type
    if (metadata.isGwtSerializable()) {
      code.addLine("")
          .addLine("  /** This class exists solely to ensure GWT whitelists all required types. */")
          .addLine("  @%s(serializable = true)", GwtCompatible.class)
          .addLine("  static final class GwtWhitelist %s %s {",
              inheritsFrom, metadata.getType())
          .addLine("");
      for (Property property : metadata.getProperties()) {
        code.addLine("    %s %s;", property.getType(), property.getName());
      }
      code.addLine("")
          .addLine("    private GwtWhitelist() {")
          .addLine("      throw new %s();", UnsupportedOperationException.class)
          .addLine("    }");
      for (Property property : metadata.getProperties()) {
        code.addLine("")
            .addLine("    @%s", Override.class)
            .addLine("    public %s %s() {", property.getType(), property.getGetterName());
        code.addLine("      throw new %s();", UnsupportedOperationException.class)
            .addLine("    }");
      }
      code.addLine("  }");
    }
    // Partial value type
    code.addLine("")
        .addLine("  private static final class %s %s %s {",
            metadata.getPartialType().getSimpleName(),
            inheritsFrom,
            metadata.getType());
    // Fields
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addValueFieldDeclaration(code, property.getName());
    }
    if (hasRequiredProperties) {
      code.addLine("    private final %s<%s> _unsetProperties;",
          EnumSet.class, metadata.getPropertyEnum());
    }
    // Constructor
    code.addLine("")
        .addLine("    %s(%s builder) {",
            metadata.getPartialType().getSimpleName(),
            metadata.getGeneratedBuilder());
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator()
          .addPartialFieldAssignment(code, "this." + property.getName(), "builder");
    }
    if (hasRequiredProperties) {
      code.addLine("      this._unsetProperties = builder._unsetProperties.clone();");
    }
    code.addLine("    }");
    // Getters
    for (Property property : metadata.getProperties()) {
      code.addLine("")
          .addLine("    @%s", Override.class);
      for (TypeElement nullableAnnotation : property.getNullableAnnotations()) {
        code.addLine("    @%s", nullableAnnotation);
      }
      code.addLine("    public %s %s() {", property.getType(), property.getGetterName());
      if (property.getCodeGenerator().getType() == Type.REQUIRED) {
        code.addLine("      if (_unsetProperties.contains(%s.%s)) {",
                metadata.getPropertyEnum(), property.getAllCapsName())
            .addLine("        throw new %s(\"%s not set\");",
                UnsupportedOperationException.class, property.getName())
            .addLine("      }");
      }
      code.add("      return ");
      property.getCodeGenerator().addReadValueFragment(code, property.getName());
      code.add(";\n");
      code.addLine("    }");
    }
    // Equals
    if (metadata.standardMethodUnderride(StandardMethod.EQUALS) != FINAL) {
      code.addLine("")
          .addLine("    @%s", Override.class)
          .addLine("    public boolean equals(Object obj) {")
          .addLine("      if (!(obj instanceof %s)) {", metadata.getPartialType())
          .addLine("        return false;")
          .addLine("      }")
          .addLine("      %1$s other = (%1$s) obj;", metadata.getPartialType());
      for (Property property : metadata.getProperties()) {
        switch (property.getType().getKind()) {
          case FLOAT:
          case DOUBLE:
            code.addLine("      if (%s.doubleToLongBits(%s)", Double.class, property.getName())
                .addLine("          != %s.doubleToLongBits(other.%s)) {",
                    Double.class, property.getName());
            break;

          default:
            if (property.getType().getKind().isPrimitive()) {
              code.addLine("      if (%1$s != other.%1$s) {", property.getName());
            } else if (property.getCodeGenerator().getType() == Type.HAS_DEFAULT) {
              code.addLine("      if (!%1$s.equals(other.%1$s)) {", property.getName());
            } else {
              code.addLine("      if (%1$s != other.%1$s", property.getName())
                  .addLine("          && (%1$s == null || !%1$s.equals(other.%1$s))) {",
                      property.getName());
            }
        }
        code.addLine("        return false;")
            .addLine("      }");
      }
      if (hasRequiredProperties) {
        code.addLine("      return _unsetProperties.equals(other._unsetProperties);");
      } else {
        code.addLine("      return true;");
      }
      code.addLine("    }");
    }
    // Hash code
    if (metadata.standardMethodUnderride(StandardMethod.HASH_CODE) != FINAL) {
      code.addLine("")
          .addLine("    @%s", Override.class)
          .addLine("    public int hashCode() {")
          .addLine("      int result = 1;");
      for (Property property : metadata.getProperties()) {
        code.addLine("      result *= 31;");
        if (property.getType().getKind().isPrimitive()) {
          code.addLine("      result += ((%s) %s).hashCode();",
              property.getBoxedType(), property.getName());

        } else {
          code.addLine("      result += ((%1$s == null) ? 0 : %1$s.hashCode());",
              property.getName());
        }
      }
      if (hasRequiredProperties) {
        code.addLine("      result *= 31;")
            .addLine("      result += _unsetProperties.hashCode();");
      }
      code.addLine("      return result;")
          .addLine("    }");
    }
    // toString
    if (metadata.standardMethodUnderride(StandardMethod.TO_STRING) != FINAL) {
      code.addLine("")
          .addLine("    @%s", Override.class)
          .addLine("    public %s toString() {", String.class);
      code.add("      return \"partial %s{", metadata.getType().getSimpleName());
      switch (metadata.getProperties().size()) {
        case 0: {
          code.add("}\";\n");
          break;
        }

        case 1: {
          Property property = getOnlyElement(metadata.getProperties());
          switch (property.getCodeGenerator().getType()) {
            case HAS_DEFAULT:
              code.add("%1$s=\" + %1$s + \"}\";\n", property.getName());
              break;

            case OPTIONAL:
              code.add("\"\n")
                  .addLine("          + (%1$s != null ? \"%1$s=\" + %1$s : \"\")",
                      property.getName())
                  .addLine("          + \"}\";");
              break;

            case REQUIRED:
              code.add("\"\n")
                  .addLine("          + (!_unsetProperties.contains(%s.%s)",
                      metadata.getPropertyEnum(), property.getAllCapsName())
                  .addLine("              ? \"%1$s=\" + %1$s : \"\")", property.getName())
                  .addLine("          + \"}\";");
              break;
          }
          break;
        }

        default: {
          code.add("\"\n")
              .add("          + COMMA_JOINER.join(\n");
          Property lastProperty = getLast(metadata.getProperties());
          for (Property property : metadata.getProperties()) {
            code.add("              ");
            switch (property.getCodeGenerator().getType()) {
              case HAS_DEFAULT:
                code.add("\"%1$s=\" + %1$s", property.getName());
                break;

              case OPTIONAL:
                code.add("(%1$s != null ? \"%1$s=\" + %1$s : null)", property.getName());
                break;

              case REQUIRED:
                code.add("(!_unsetProperties.contains(%s.%s)\n",
                        metadata.getPropertyEnum(), property.getAllCapsName())
                    .add("                  ? \"%1$s=\" + %1$s : null)", property.getName());
                break;
            }
            if (property != lastProperty) {
              code.add(",\n");
            } else {
              code.add(")\n");
            }
          }
          code.addLine("          + \"}\";");
          break;
        }
      }
      code.addLine("    }");
    }
    code.addLine("  }");
    // buildPartial()
    code.addLine("")
        .addLine("  /**")
        .addLine("   * Returns a newly-created partial {@link %s}", metadata.getType())
        .addLine("   * based on the contents of the {@code %s}.",
            metadata.getBuilder().getSimpleName())
        .addLine("   * State checking will not be performed.");
    if (hasRequiredProperties) {
      code.addLine("   * Unset properties will throw an {@link %s}",
              UnsupportedOperationException.class)
          .addLine("   * when accessed via the partial object.");
    }
    code.addLine("   *")
        .addLine("   * <p>Partials should only ever be used in tests.")
        .addLine("   */")
        .addLine("  @%s()", VisibleForTesting.class)
        .addLine("  public %s buildPartial() {", metadata.getType())
        .addLine("    return new %s(this);", metadata.getPartialType())
        .addLine("  }")
        .addLine("}");
  }

  private static void addPropertyEnum(Metadata metadata, SourceBuilder code) {
    code.addLine("")
        .addLine("  private enum %s {", metadata.getPropertyEnum().getSimpleName());
    for (Property property : metadata.getProperties()) {
      if (property.getCodeGenerator().getType() == Type.REQUIRED) {
        code.addLine("    %s(\"%s\"),", property.getAllCapsName(), property.getName());
      }
    }
    code.addLine("    ;")
        .addLine("")
        .addLine("    private final %s name;", String.class)
        .addLine("")
        .addLine("    private %s(%s name) {",
            metadata.getPropertyEnum().getSimpleName(), String.class)
        .addLine("      this.name = name;")
        .addLine("    }")
        .addLine("")
        .addLine("    @%s public %s toString() {", Override.class, String.class)
        .addLine("      return name;")
        .addLine("    }")
        .addLine("  }");
  }

  private static void addCustomValueSerializer(Metadata metadata, SourceBuilder code) {
    code.addLine("")
        .addLine("  @%s", GwtCompatible.class)
        .addLine("  public static class %s_CustomFieldSerializer",
            metadata.getValueType().getSimpleName())
        .addLine("      extends %s<%s> {",
            CUSTOM_FIELD_SERIALIZER, metadata.getValueType())
        .addLine("")
        .addLine("    @%s", Override.class)
        .addLine("    public void deserializeInstance(%s reader, %s instance) { }",
            SERIALIZATION_STREAM_READER, metadata.getValueType())
        .addLine("")
        .addLine("    @%s", Override.class)
        .addLine("    public boolean hasCustomInstantiateInstance() {")
        .addLine("      return true;")
        .addLine("    }")
        .addLine("")
        .addLine("    @%s", Override.class)
        .addLine("    public %s instantiateInstance(%s reader)",
            metadata.getValueType(), SERIALIZATION_STREAM_READER)
        .addLine("        throws %s {", SERIALIZATION_EXCEPTION)
        .addLine("      %1$s builder = new %1$s();", metadata.getBuilder());
    for (Property property : metadata.getProperties()) {
      if (property.getType().getKind().isPrimitive()) {
        code.addLine("        %s %s = reader.read%s();",
            property.getType(), property.getName(), withInitialCapital(property.getType()));
        property.getCodeGenerator().addSetFromResult(code, "builder", property.getName());
      } else if (String.class.getName().equals(property.getType().toString())) {
        code.addLine("        %s %s = reader.readString();",
            property.getType(), property.getName());
        property.getCodeGenerator().addSetFromResult(code, "builder", property.getName());
      } else {
        code.addLine("      try {");
        if (!property.isFullyCheckedCast()) {
          code.addLine("        @SuppressWarnings(\"unchecked\")");
        }
        code.addLine("        %1$s %2$s = (%1$s) reader.readObject();",
                property.getType(), property.getName());
        property.getCodeGenerator().addSetFromResult(code, "builder", property.getName());
        code.addLine("      } catch (%s e) {", ClassCastException.class)
            .addLine("        throw new %s(", SERIALIZATION_EXCEPTION)
            .addLine("            \"Wrong type for property '%s'\", e);", property.getName())
            .addLine("      }");
      }
    }
    code.addLine("      return (%s) builder.build();", metadata.getValueType())
        .addLine("    }")
        .addLine("")
        .addLine("    @%s", Override.class)
        .addLine("    public void serializeInstance(%s writer, %s instance)",
            SERIALIZATION_STREAM_WRITER, metadata.getValueType())
        .addLine("        throws %s {", SERIALIZATION_EXCEPTION);
    for (Property property : metadata.getProperties()) {
      if (property.getType().getKind().isPrimitive()) {
        code.add("      writer.write%s(",
            withInitialCapital(property.getType()), property.getName());
      } else if (String.class.getName().equals(property.getType().toString())) {
        code.add("      writer.writeString(", property.getName());
      } else {
        code.add("      writer.writeObject(", property.getName());
      }
      property.getCodeGenerator().addReadValueFragment(code, "instance." + property.getName());
      code.add(");\n");
    }
    code.addLine("    }")
        .addLine("")
        .addLine("    private static final Value_CustomFieldSerializer INSTANCE ="
            + " new Value_CustomFieldSerializer();")
        .addLine("")
        .addLine("    public static void deserialize(%s reader, %s instance) {",
            SERIALIZATION_STREAM_READER, metadata.getValueType())
        .addLine("      INSTANCE.deserializeInstance(reader, instance);")
        .addLine("    }")
        .addLine("")
        .addLine("    public static %s instantiate(%s reader)",
            metadata.getValueType(), SERIALIZATION_STREAM_READER)
        .addLine("        throws %s {", SERIALIZATION_EXCEPTION)
        .addLine("      return INSTANCE.instantiateInstance(reader);")
        .addLine("    }")
        .addLine("")
        .addLine("    public static void serialize(%s writer, %s instance)",
            SERIALIZATION_STREAM_WRITER, metadata.getValueType())
        .addLine("        throws %s {", SERIALIZATION_EXCEPTION)
        .addLine("      INSTANCE.serializeInstance(writer, instance);")
        .addLine("    }")
        .addLine("  }");
  }

  private void writeStubSource(SourceBuilder code, Metadata metadata) {
    code.addLine("/**")
        .addLine(" * Placeholder. Create {@code %s.Builder} and subclass this type.",
            metadata.getType())
        .addLine(" */")
        .addLine("@%s(\"%s\")", Generated.class, this.getClass().getName())
        .addLine("abstract class %s {}", metadata.getGeneratedBuilder().getSimpleName());
  }

  /** Returns the correct keyword to use to inherit from the given type: implements, or extends. */
  private static String getInheritanceKeyword(TypeElement type) {
    if (type.getKind().isInterface()) {
      return "implements";
    } else {
      return "extends";
    }
  }

  private static String withInitialCapital(Object obj) {
    String s = obj.toString();
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }

  private static ImmutableList<String> getNames(Iterable<Property> properties) {
    ImmutableList.Builder<String> result = ImmutableList.builder();
    for (Property property : properties) {
      result.add(property.getName());
    }
    return result.build();
  }

  private static final Predicate<Property> IS_REQUIRED = new Predicate<Property>() {
    @Override public boolean apply(Property property) {
      return property.getCodeGenerator().getType() == Type.REQUIRED;
    }
  };

  private static final Predicate<Property> IS_OPTIONAL = new Predicate<Property>() {
    @Override public boolean apply(Property property) {
      return property.getCodeGenerator().getType() == Type.OPTIONAL;
    }
  };
}
