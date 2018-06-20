/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.contentpacks.model;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import org.graylog2.contentpacks.model.entities.EntityDescriptor;
import org.graylog2.contentpacks.model.entities.references.ValueReference;

import javax.annotation.Nullable;
import java.util.Map;

@AutoValue
public abstract class InstallationContext {
    public abstract ImmutableMap<String, ValueReference> parameters();

    public abstract ImmutableMap<EntityDescriptor, Object> entities();

    @Nullable
    public abstract String comment();

    public abstract String username();

    abstract Builder toBuilder();

    public static Builder builder() {
        return new AutoValue_InstallationContext.Builder()
                .parameters(ImmutableMap.of())
                .entities(ImmutableMap.of());
    }

    public InstallationContext addEntity(EntityDescriptor entityDescriptor, Object entity) {
        final ImmutableMap<EntityDescriptor, Object> entities = ImmutableMap.<EntityDescriptor, Object>builder()
                .putAll(entities())
                .put(entityDescriptor, entity)
                .build();
        return toBuilder()
                .entities(entities)
                .build();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder parameters(Map<String, ValueReference> parameters);

        public abstract Builder entities(Map<EntityDescriptor, Object> entities);

        public abstract Builder comment(@Nullable String username);

        public abstract Builder username(String username);

        public abstract InstallationContext build();
    }
}
