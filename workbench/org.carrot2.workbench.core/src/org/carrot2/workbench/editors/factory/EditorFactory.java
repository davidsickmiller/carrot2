package org.carrot2.workbench.editors.factory;

import static org.apache.commons.lang.ClassUtils.*;

import java.lang.annotation.Annotation;
import java.util.Comparator;
import java.util.List;

import org.carrot2.core.ProcessingComponent;
import org.carrot2.util.attribute.AttributeDescriptor;
import org.carrot2.util.attribute.AttributeUtils;
import org.carrot2.workbench.editors.EditorNotFoundException;
import org.carrot2.workbench.editors.IAttributeEditor;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

public class EditorFactory
{
    public static IAttributeEditor getEditorFor(
        Class<? extends ProcessingComponent> clazz, AttributeDescriptor attribute)
    {
        IAttributeEditor editor = findDedicatedEditor(clazz, attribute);
        if (editor == null)
        {
            editor = findTypeEditor(clazz, attribute);
        }
        if (editor == null)
        {
            throw new EditorNotFoundException("No suitable editor found for attribute "
                + attribute.toString());
        }
        return editor;
    }

    private static IAttributeEditor findTypeEditor(
        Class<? extends ProcessingComponent> clazz, AttributeDescriptor attribute)
    {
        List<TypeEditorWrapper> typeCandidates = filterTypeEditors(attribute);
        if (!typeCandidates.isEmpty())
        {
            typeCandidates = sortTypeEditors(typeCandidates, attribute);
            return typeCandidates.get(0).getExecutableComponent();
        }
        return null;
    }

    private static IAttributeEditor findDedicatedEditor(
        final Class<? extends ProcessingComponent> clazz,
        final AttributeDescriptor attribute)
    {
        List<DedicatedEditorWrapper> candidates =
            filterDedicatedEditors(clazz, attribute);

        if (!candidates.isEmpty())
        {
            return candidates.get(0).getExecutableComponent();
        }
        return null;
    }

    private static List<TypeEditorWrapper> sortTypeEditors(
        List<TypeEditorWrapper> editors, final AttributeDescriptor attribute)
    {
        final boolean constraintsPreffered = !attribute.constraints.isEmpty();
        return Lists.sortedCopy(editors, new Comparator<TypeEditorWrapper>()
        {

            public int compare(TypeEditorWrapper o1, TypeEditorWrapper o2)
            {
                int result =
                    distance(attribute.type, o1.attributeClass)
                        - distance(attribute.type, o2.attributeClass);
                if (result != 0)
                {
                    return result;
                }
                if (o1.constraints.isEmpty() && o2.constraints.isEmpty())
                {
                    result = 0;
                }
                else if (!o2.constraints.isEmpty() && !o2.constraints.isEmpty())
                {
                    result = 0;
                }
                else if (o1.constraints.isEmpty() ^ constraintsPreffered)
                {
                    result = -1;
                }
                else
                {
                    result = 1;
                }
                return result;
            }

        });
    }

    private static List<TypeEditorWrapper> filterTypeEditors(
        final AttributeDescriptor attribute)
    {
        return AttributeEditorLoader.INSTANCE
            .filterTypeEditors(new Predicate<TypeEditorWrapper>()
            {

                public boolean apply(TypeEditorWrapper editor)
                {
                    boolean result = isCompatible(attribute.type, editor.attributeClass);
                    if (!editor.constraints.isEmpty())
                    {
                        boolean all = true;
                        boolean one = false;
                        for (String constraintName : editor.constraints)
                        {

                            boolean contains =
                                containsAnnotation(attribute.constraints, constraintName);
                            one |= contains;
                            all &= contains;
                        }
                        if (editor.allAtOnce)
                        {
                            result &= all;
                        }
                        else
                        {
                            result &= one;
                        }
                    }
                    return result;
                }

            });
    }

    private static boolean containsAnnotation(List<Annotation> list, String annotationName)
    {
        for (Annotation annotation : list)
        {
            if (annotation.annotationType().getName().equals(annotationName))
            {
                return true;
            }
        }
        return false;
    }

    private static List<DedicatedEditorWrapper> filterDedicatedEditors(
        final Class<? extends ProcessingComponent> clazz,
        final AttributeDescriptor attribute)
    {
        return AttributeEditorLoader.INSTANCE
            .filterDedicatedEditors(new Predicate<DedicatedEditorWrapper>()
            {

                public boolean apply(DedicatedEditorWrapper editor)
                {
                    return isCompatible(clazz, editor.componentClass)
                        && AttributeUtils.getKey(clazz, editor.attributeId).equals(
                            attribute.key);
                }

            });
    }

    @SuppressWarnings("unchecked")
    private static boolean isCompatible(Class<?> clazz, String className)
    {
        boolean compatible = clazz.getName().equals(className);
        if (!compatible)
        {
            List<String> superClasses =
                convertClassesToClassNames(getAllSuperclasses(clazz));
            compatible = superClasses.contains(className);
        }
        if (!compatible && clazz.isInterface())
        {
            compatible = "java.lang.Object".equals(className);
        }
        if (!compatible)
        {
            List<String> interfaces = convertClassesToClassNames(getAllInterfaces(clazz));
            compatible = interfaces.contains(className);
        }
        return compatible;
    }

    @SuppressWarnings("unchecked")
    public static int distance(Class<?> clazz, String className)
    {
        if (clazz.getName().equals(className))
        {
            return 0;
        }
        if (className.equals("java.lang.Object"))
        {
            return Integer.MAX_VALUE;
        }
        List<String> superclasses = convertClassesToClassNames(getAllSuperclasses(clazz));
        if (superclasses.contains(className))
        {
            return superclasses.indexOf(className) + 1;
        }
        List<String> interfaces = convertClassesToClassNames(getAllInterfaces(clazz));
        if (interfaces.contains(className))
        {
            return interfaces.indexOf(className) + 1;
        }
        return 0;
    }
}
