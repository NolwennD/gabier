package eu.gabier.testing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.lang.classfile.Annotation;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What the selection of tests reads in a compiled class, once and with {@code java.lang.classfile}: the dependencies of
 * the class (the classes its constant pool names, the types of its fields and the types of its annotations, as
 * Infinitest reads them), and what tells a test from another class. Names are binary names with dots ({@code
 * app.Outer$Inner}); the descriptors of the parameters of a constructor are kept as the class file writes them.
 *
 * @param name the binary name of the class
 * @param sourceFile the name of the source file the compiler recorded, none when it recorded none
 * @param superclass the binary name of its superclass, none for {@code java.lang.Object}
 * @param interfaces the binary names of the interfaces it implements
 * @param abstractType whether it is an interface or an abstract class
 * @param dependencies the classes it depends on, itself included
 * @param annotations the types of its annotations
 * @param memberAnnotations the types of the annotations of its fields and of every method it declares, the private ones
 *     included
 * @param methods its methods that are not private, constructors apart
 * @param constructors its constructors that are not private
 * @param archUnit whether a field or a method it declares is annotated as an ArchUnit test, which loads classes by
 *     itself, so that its dependencies cannot be read
 */
record ClassFacts(
        String name,
        Optional<String> sourceFile,
        Optional<String> superclass,
        List<String> interfaces,
        boolean abstractType,
        Set<String> dependencies,
        Set<String> annotations,
        Set<String> memberAnnotations,
        List<MethodFacts> methods,
        List<ConstructorFacts> constructors,
        boolean archUnit) {

    private static final Set<String> ARCHUNIT =
            Set.of("com.tngtech.archunit.junit.ArchTest", "com.tngtech.archunit.junit.AnalyzeClasses");

    ClassFacts {
        Assert.notBlank("name", name);
        Assert.notNull("sourceFile", sourceFile);
        Assert.notNull("superclass", superclass);
        Assert.notNull("interfaces", interfaces);
        Assert.notNull("dependencies", dependencies);
        Assert.notNull("annotations", annotations);
        Assert.notNull("memberAnnotations", memberAnnotations);
        Assert.notNull("methods", methods);
        Assert.notNull("constructors", constructors);

        interfaces = List.copyOf(interfaces);
        dependencies = Set.copyOf(dependencies);
        annotations = Set.copyOf(annotations);
        memberAnnotations = Set.copyOf(memberAnnotations);
        methods = List.copyOf(methods);
        constructors = List.copyOf(constructors);
    }

    /**
     * A method that is not private.
     *
     * @param name its name
     * @param annotations the types of its annotations
     */
    record MethodFacts(String name, Set<String> annotations) {

        MethodFacts {
            Assert.notBlank("name", name);
            Assert.notNull("annotations", annotations);

            annotations = Set.copyOf(annotations);
        }
    }

    /**
     * A constructor that is not private.
     *
     * @param publicConstructor whether it is public
     * @param parameterTypes the descriptors of its parameters
     */
    record ConstructorFacts(boolean publicConstructor, List<String> parameterTypes) {

        ConstructorFacts {
            Assert.notNull("parameterTypes", parameterTypes);

            parameterTypes = List.copyOf(parameterTypes);
        }
    }

    /**
     * Reads the bytes of a class file.
     *
     * @param file where the bytes come from, named by the refusal
     * @throws TestingException if the bytes are no class file this JDK reads (a class compiled for a newer Java)
     */
    static ClassFacts read(Path file, byte[] bytes) {
        try {
            return facts(ClassFile.of().parse(bytes));
        } catch (RuntimeException unreadable) {
            throw TestingException.readFailed(file, unreadable);
        }
    }

    private static ClassFacts facts(ClassModel model) {
        Set<String> dependencies = new LinkedHashSet<>();
        for (var entry : model.constantPool()) {
            if (entry instanceof ClassEntry classEntry) {
                typeOfInternalName(classEntry.asInternalName()).ifPresent(dependencies::add);
            }
        }
        Set<String> annotations = annotationTypes(model);
        dependencies.addAll(annotations);
        Set<String> memberAnnotations = new LinkedHashSet<>();
        for (FieldModel field : model.fields()) {
            typeOfDescriptor(field.fieldType().stringValue()).ifPresent(dependencies::add);
            memberAnnotations.addAll(annotationTypes(field));
        }
        List<MethodFacts> methods = new ArrayList<>();
        List<ConstructorFacts> constructors = new ArrayList<>();
        for (MethodModel method : model.methods()) {
            Set<String> onMethod = annotationTypes(method);
            memberAnnotations.addAll(onMethod);
            boolean notPrivate = !method.flags().has(AccessFlag.PRIVATE);
            if (!notPrivate) {
                continue;
            }
            if (method.methodName().equalsString("<init>")) {
                constructors.add(new ConstructorFacts(
                        method.flags().has(AccessFlag.PUBLIC),
                        method.methodTypeSymbol().parameterList().stream()
                                .map(ClassDesc::descriptorString)
                                .toList()));
            } else {
                methods.add(new MethodFacts(method.methodName().stringValue(), onMethod));
            }
        }
        dependencies.addAll(memberAnnotations);

        return new ClassFacts(
                binaryName(model.thisClass().asInternalName()),
                model.findAttribute(Attributes.sourceFile())
                        .map(attribute -> attribute.sourceFile().stringValue()),
                model.superclass().map(superclass -> binaryName(superclass.asInternalName())),
                model.interfaces().stream()
                        .map(anInterface -> binaryName(anInterface.asInternalName()))
                        .toList(),
                model.flags().has(AccessFlag.ABSTRACT),
                dependencies,
                annotations,
                memberAnnotations,
                methods,
                constructors,
                memberAnnotations.stream().anyMatch(ARCHUNIT::contains));
    }

    /** The types of the annotations of a class, a field or a method, and of the parameters of a method. */
    private static Set<String> annotationTypes(AttributedElement element) {
        List<Annotation> found = new ArrayList<>();
        element.findAttribute(Attributes.runtimeVisibleAnnotations())
                .ifPresent(attribute -> found.addAll(attribute.annotations()));
        element.findAttribute(Attributes.runtimeInvisibleAnnotations())
                .ifPresent(attribute -> found.addAll(attribute.annotations()));
        element.findAttribute(Attributes.runtimeVisibleParameterAnnotations())
                .ifPresent(attribute -> attribute.parameterAnnotations().forEach(found::addAll));
        element.findAttribute(Attributes.runtimeInvisibleParameterAnnotations())
                .ifPresent(attribute -> attribute.parameterAnnotations().forEach(found::addAll));

        Set<String> types = new LinkedHashSet<>();
        for (Annotation annotation : found) {
            typeOfDescriptor(annotation.className().stringValue()).ifPresent(types::add);
        }

        return types;
    }

    private static String binaryName(String internalName) {
        return internalName.replace('/', '.');
    }

    /** The class an internal name names: the element of an array, none for an array of a primitive type. */
    private static Optional<String> typeOfInternalName(String name) {
        return name.startsWith("[") ? typeOfDescriptor(name) : Optional.of(binaryName(name));
    }

    /** The class a field descriptor names: the element of an array, none for a primitive type. */
    private static Optional<String> typeOfDescriptor(String descriptor) {
        int element = 0;
        while (descriptor.charAt(element) == '[') {
            element++;
        }

        return descriptor.charAt(element) == 'L'
                ? Optional.of(binaryName(descriptor.substring(element + 1, descriptor.length() - 1)))
                : Optional.empty();
    }
}
