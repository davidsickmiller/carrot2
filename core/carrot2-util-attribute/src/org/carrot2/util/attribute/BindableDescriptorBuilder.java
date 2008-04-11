package org.carrot2.util.attribute;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;

import org.carrot2.util.CloseableUtils;
import org.carrot2.util.attribute.constraint.IsConstraint;
import org.carrot2.util.resource.Resource;
import org.carrot2.util.resource.ResourceUtilsFactory;
import org.simpleframework.xml.load.Persister;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Builds {@link BindableDescriptor}s based on the provided bindable type instances.
 */
public class BindableDescriptorBuilder
{
    /**
     * Builds a {@link BindableDescriptor} for an initialized instance of a
     * {@link Bindable} type. Notice that the set of {@link AttributeDescriptor} found in
     * the returned {@link BindableDescriptor} may vary depending on how the provided
     * instance is initialized.
     * 
     * @param initializedInstance initialized instance of a {@link Bindable} type for
     *            which to build the descriptor
     * @return the descriptor built
     */
    public static BindableDescriptor buildDescriptor(Object initializedInstance)
    {
        return buildDescriptor(initializedInstance, new HashSet<Object>(), true);
    }

    /**
     * A variant of {@link #buildDescriptor(Object)} that allows to skip loading metadata
     * for the descriptors.
     */
    public static BindableDescriptor buildDescriptor(Object initializedInstance,
        boolean loadMetadata)
    {
        return buildDescriptor(initializedInstance, new HashSet<Object>(), loadMetadata);
    }

    /**
     * Internal implementation of descriptor building.
     */
    private static BindableDescriptor buildDescriptor(Object initializedInstance,
        Set<Object> processedInstances, boolean loadMetadata)
    {
        final Class<?> clazz = initializedInstance.getClass();
        if (clazz.getAnnotation(Bindable.class) == null)
        {
            throw new IllegalArgumentException("Provided instance must be @Bindable");
        }

        if (!processedInstances.add(initializedInstance))
        {
            throw new UnsupportedOperationException(
                "Circular references are not supported");
        }

        // Load metadata
        final BindableMetadata bindableMetadata = buildMetadataForBindableHierarchy(
            clazz, loadMetadata);

        // Build descriptors for direct attributes
        final Map<String, AttributeDescriptor> attributeDescriptors = buildAttributeDescriptors(
            initializedInstance, bindableMetadata);

        // Build descriptors for nested bindables
        final Map<String, BindableDescriptor> bindableDescriptors = Maps
            .newLinkedHashMap();

        final Collection<Field> fieldsFromBindableHierarchy = BindableUtils
            .getFieldsFromBindableHierarchy(clazz);
        for (final Field field : fieldsFromBindableHierarchy)
        {
            // Get class of runtime value
            Object fieldValue = null;
            try
            {
                field.setAccessible(true);
                fieldValue = field.get(initializedInstance);
            }
            catch (final Exception e)
            {
                throw new RuntimeException("Could not retrieve default value of field: "
                    + field.getClass().getName() + "#" + field.getName());
            }

            // Descend only for non-null values
            if (fieldValue != null
                && fieldValue.getClass().getAnnotation(Bindable.class) != null)
            {
                bindableDescriptors.put(field.getName(), buildDescriptor(fieldValue,
                    processedInstances, loadMetadata));
            }
        }

        return new BindableDescriptor(bindableMetadata, bindableDescriptors,
            attributeDescriptors);
    }

    /**
     * Builds descriptors for direct attributes.
     */
    private static Map<String, AttributeDescriptor> buildAttributeDescriptors(
        Object initializedInstance, BindableMetadata bindableMetadata)
    {
        final Class<?> clazz = initializedInstance.getClass();

        final Map<String, AttributeDescriptor> result = Maps.newLinkedHashMap();
        final Collection<Field> fieldsFromBindableHierarchy = BindableUtils
            .getFieldsFromBindableHierarchy(clazz);

        for (final Field field : fieldsFromBindableHierarchy)
        {
            if (field.getAnnotation(Attribute.class) != null)
            {
                result.put(BindableUtils.getKey(field), buildAttributeDescriptor(
                    initializedInstance, field, bindableMetadata));
            }
        }

        return result;
    }

    /**
     * Builds bindable metadata for a {@link Bindable} class.
     */
    private static BindableMetadata buildMetadataForBindableHierarchy(
        final Class<?> clazz, boolean loadMetadata)
    {
        if (!loadMetadata)
        {
            return null;
        }

        final Collection<Class<?>> classesFromBindableHerarchy = BindableUtils
            .getClassesFromBindableHerarchy(clazz);

        final BindableMetadata bindableMetadata = getBindableMetadata(clazz);

        for (final Class<?> bindableClass : classesFromBindableHerarchy)
        {
            if (bindableClass != clazz)
            {
                final BindableMetadata moreMetadata = getBindableMetadata(bindableClass);
                bindableMetadata.getInternalAttributeMetadata().putAll(
                    moreMetadata.getAttributeMetadata());
            }
        }

        return bindableMetadata;
    }

    /**
     * Deserializes metadata for a {@link Bindable} class.
     */
    private static BindableMetadata getBindableMetadata(final Class<?> clazz)
    {
        final Resource metadataXml = ResourceUtilsFactory.getDefaultResourceUtils()
            .getFirst(clazz.getName() + ".xml", clazz);
        BindableMetadata bindableMetadata = null;
        if (metadataXml != null)
        {
            InputStream inputStream = null;
            try
            {
                inputStream = metadataXml.open();
                bindableMetadata = new Persister().read(BindableMetadata.class,
                    inputStream);
            }
            catch (final Exception e)
            {
                throw new RuntimeException("Could not load attribute metadata from: "
                    + metadataXml, e);
            }
            finally
            {
                CloseableUtils.close(inputStream);
            }

            return bindableMetadata;
        }
        else
        {
            throw new RuntimeException("Could not load attribute metadata from: "
                + clazz.getSimpleName() + ".xml");
        }
    }

    /**
     * Builds {@link AttributeDescriptor} for a field from a {@link Bindable} type.
     */
    private static AttributeDescriptor buildAttributeDescriptor(
        Object initializedInstance, Field field, BindableMetadata bindableMetadata)
    {
        Object defaultValue = null;

        try
        {
            field.setAccessible(true);
            defaultValue = field.get(initializedInstance);
        }
        catch (final Exception e)
        {
            throw new RuntimeException("Could not retrieve default value of attribute: "
                + BindableUtils.getKey(field));
        }

        AttributeMetadata attributeMetadata = null;
        if (bindableMetadata != null)
        {
            attributeMetadata = bindableMetadata.getAttributeMetadata().get(
                field.getName());
        }
        return new AttributeDescriptor(field, defaultValue,
            getConstraintAnnotations(field), attributeMetadata);
    }

    /**
     * Gets constraint annotations for a field, ignoring any other annotations.
     */
    private static List<Annotation> getConstraintAnnotations(Field field)
    {
        final Annotation [] annotations = field.getAnnotations();
        final ArrayList<Annotation> constraintAnnotations = Lists.newArrayList();
        for (Annotation annotation : annotations)
        {
            if (annotation.annotationType().isAnnotationPresent(IsConstraint.class))
            {
                constraintAnnotations.add(annotation);
            }
        }

        return constraintAnnotations;
    }
}
