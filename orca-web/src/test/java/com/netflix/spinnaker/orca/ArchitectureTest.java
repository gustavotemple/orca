package com.netflix.spinnaker.orca;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.Architectures;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.util.HashSet;
import java.util.Set;
import org.junit.runner.RunWith;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(
    packages = "com.netflix.spinnaker.orca",
    importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
public class ArchitectureTest {

  @ArchTest
  public static void controllersMayNotBeAccessedByAnyLayer(JavaClasses classes) {
    Architectures.layeredArchitecture()
        .consideringAllDependencies()
        .layer("Controllers")
        .definedBy("com.netflix.spinnaker.orca.controllers")
        .whereLayer("Controllers")
        .mayNotBeAccessedByAnyLayer()
        .check(classes);
  }

  @ArchTest
  public static void controllersShouldOnlyAccessThemselves(JavaClasses classes) {
    ArchRuleDefinition.classes()
        .that()
        .resideInAPackage("com.netflix.spinnaker.orca.controllers")
        .should(onlyBeAccessedByThemselves)
        .check(classes);
  }

  private static final ArchCondition<JavaClass> onlyBeAccessedByThemselves =
      new ArchCondition<>("only be accessed by themselves") {
        @Override
        public void check(JavaClass item, ConditionEvents events) {
          Set<JavaClass> allowedAccessClasses = new HashSet<>();
          allowedAccessClasses.add(item); // Add the class itself to the allowed list

          JavaClass parentClass = item;
          while ((parentClass.isInnerClass() || parentClass.isMemberClass())
              && parentClass.getEnclosingClass().isPresent()) {
            allowedAccessClasses.add(
                parentClass.getEnclosingClass().get()); // Add the parent class to the allowed list
            parentClass = parentClass.getEnclosingClass().get();
          }

          for (Dependency dependency : item.getDirectDependenciesToSelf()) {
            JavaClass classThatAccesses;

            // An inner or member class can access its parent class
            if ((dependency.getOriginClass().isInnerClass()
                    || dependency.getOriginClass().isMemberClass())
                && dependency.getOriginClass().getEnclosingClass().isPresent()) {
              classThatAccesses = dependency.getOriginClass().getEnclosingClass().get();
            } else {
              classThatAccesses = dependency.getOriginClass();
            }

            // Check if the class is accessed by some other class
            if (!allowedAccessClasses.contains(classThatAccesses)) {
              events.add(
                  SimpleConditionEvent.violated(
                      item,
                      String.format(
                          "Class %s should only be accessed by itself and cannot by %s",
                          item.getFullName(), classThatAccesses.getFullName())));
            }
          }
        }
      };

  // sort classes by the first package after 'com.netflix.spinnaker.orca'
  // then check those slices for cyclic dependencies
  @ArchTest
  public static void classesShouldBeFreeOfCycles(JavaClasses classes) {
    SlicesRuleDefinition.slices()
        .matching("com.netflix.spinnaker.orca.(*)..")
        .should()
        .beFreeOfCycles()
        .check(classes);
  }
}
