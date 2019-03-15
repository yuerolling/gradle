/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.reflect;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

class MutableClassDetails implements ClassDetails {
    private final Class<?> type;
    private final MethodSet instanceMethods = new MethodSet();
    private final Map<String, MutablePropertyDetails> properties = new TreeMap<>();
    private final List<MutableClassDetails> superTypes = new ArrayList<>();
    private final List<Method> methods;
    private final List<Field> fields;

    MutableClassDetails(Class<?> type) {
        this.type = type;
        this.methods = ImmutableList.copyOf(type.getDeclaredMethods());
        this.fields = ImmutableList.copyOf(type.getDeclaredFields());
    }

    @Override
    public void visitAllMethods(Action<? super Method> visitor) {
        visitDeclaredMethods(visitor);
        for (MutableClassDetails superType : superTypes) {
            superType.visitDeclaredMethods(visitor);
        }
    }

    private void visitDeclaredMethods(Action<? super Method> visitor) {
        for (Method method : methods) {
            visitor.execute(method);
        }
    }

    @Override
    public void visitAllFields(Action<? super Field> visitor) {
        visitDeclaredFields(visitor);
        for (MutableClassDetails superType : superTypes) {
            superType.visitDeclaredFields(visitor);
        }
    }

    private void visitDeclaredFields(Action<? super Field> visitor) {
        for (Field field : fields) {
            visitor.execute(field);
        }
    }

    @Override
    public void visitInstanceMethods(Action<? super Method> visitor) {
        for (Method method : instanceMethods) {
            visitor.execute(method);
        }
    }

    @Override
    public void visitTypes(Action<? super ClassDetails> visitor) {
        visitor.execute(this);
        for (MutableClassDetails superType : superTypes) {
            visitor.execute(superType);
        }
    }

    /*
     * Does a defensive copy to avoid leaking class references through the MutablePropertyDetails
     * contained in the maps values. The keyset would keep a strong reference back to the map
     * and all its entries.
     */
    @Override
    public Set<String> getPropertyNames() {
        return ImmutableSet.copyOf(properties.keySet());
    }

    @Override
    public Collection<? extends PropertyDetails> getProperties() {
        return properties.values();
    }

    @Override
    public PropertyDetails getProperty(String name) throws NoSuchPropertyException {
        MutablePropertyDetails property = properties.get(name);
        if (property == null) {
            throw new NoSuchPropertyException(String.format("No property '%s' found on %s.", name, type));
        }
        return property;
    }

    void superType(MutableClassDetails type) {
        if (!superTypes.contains(type)) {
            superTypes.add(type);
        }
    }

    void instanceMethod(Method method) {
        instanceMethods.add(method);
    }

    MutablePropertyDetails property(String propertyName) {
        MutablePropertyDetails property = properties.get(propertyName);
        if (property == null) {
            property = new MutablePropertyDetails(propertyName);
            properties.put(propertyName, property);
        }
        return property;
    }

    public void applyTo(MutableClassDetails classDetails) {
        classDetails.superType(this);
        for (MutableClassDetails superType : superTypes) {
            classDetails.superType(superType);
        }
        for (Method method : instanceMethods) {
            classDetails.instanceMethod(method);
        }
        for (MutablePropertyDetails property : properties.values()) {
            MutablePropertyDetails destProperty = classDetails.property(property.getName());
            for (Method getter : property.getGetters()) {
                destProperty.addGetter(getter);
            }
            for (Method setter : property.getSetters()) {
                destProperty.addSetter(setter);
            }
        }
    }
}
