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
package org.graylog2.contentpacks;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.Traverser;
import org.graylog2.contentpacks.constraints.ConstraintChecker;
import org.graylog2.contentpacks.model.ContentPack;
import org.graylog2.contentpacks.model.ContentPackInstallation;
import org.graylog2.contentpacks.model.ContentPackV1;
import org.graylog2.contentpacks.model.constraints.Constraint;
import org.graylog2.contentpacks.model.entities.Entity;
import org.graylog2.contentpacks.model.entities.EntityDescriptor;
import org.graylog2.contentpacks.model.entities.references.ValueReference;
import org.graylog2.contentpacks.model.entities.references.ValueType;
import org.graylog2.contentpacks.model.parameters.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
public class ContentPackService {
    private static final Logger LOG = LoggerFactory.getLogger(ContentPackService.class);

    private final ContentPackPersistenceService contentPackPersistenceService;
    private final ContentPackInstallationPersistenceService contentPackInstallationPersistenceService;
    private final Set<ConstraintChecker> constraintCheckers;

    @Inject
    public ContentPackService(ContentPackPersistenceService contentPackPersistenceService,
                              ContentPackInstallationPersistenceService contentPackInstallationPersistenceService,
                              Set<ConstraintChecker> constraintCheckers) {
        this.contentPackPersistenceService = contentPackPersistenceService;
        this.contentPackInstallationPersistenceService = contentPackInstallationPersistenceService;
        this.constraintCheckers = constraintCheckers;
    }

    public ContentPackInstallation installContentPack(ContentPack contentPack,
                                                      Map<String, ValueReference> parameters,
                                                      String comment,
                                                      String user) {
        if (contentPack instanceof ContentPackV1) {
            return installContentPack((ContentPackV1) contentPack, parameters, comment, user);
        } else {
            throw new IllegalArgumentException("Unsupported content pack version: " + contentPack.version());
        }
    }

    public ContentPackInstallation installContentPack(ContentPackV1 contentPack,
                                                      Map<String, ValueReference> parameters,
                                                      String comment,
                                                      String user) {
        checkConstraints(contentPack.requires());

        final ImmutableMap<String, ValueReference> validatedParameters = validateParameters(parameters, contentPack.parameters());

        final ImmutableGraph<Entity> entityGraph = buildEntityGraph(contentPack.entities());

        // TODO: Create entities in order
        final Traverser<Entity> entityTraverser = Traverser.forGraph(entityGraph);

        /*
         * Create entities in the correct order
         * - If a "singleton" entity already exists, use the existing instance (e. g. Grok pattern, lookup data adapter, lookup cache)
         * - Create a mapping of the content pack entity IDs to the actual database IDs
         * - In case of an error: Remove all newly created entities in reverse order
         * - In case of success: Create a "snapshot" of the content pack instance, the parameters, and the actual entity references (ID?) so that modified entities can be detected later
         */

        final ContentPackInstallation installation = ContentPackInstallation.builder()
                .contentPackId(contentPack.id())
                .contentPackRevision(contentPack.revision())
                .parameters(validatedParameters)
                .comment(comment)
                .createdAt(Instant.now())
                .createdBy(user)
                .build();
        return installation;
    }

    public ContentPackInstallation uninstallContentPack(ContentPackInstallation installation) {
        /*
         * - Show entities marked for removal and ask user for confirmation
         * - Resolve dependency order of the previously created entities
         * - Stop/pause entities in reverse order
         *      - In case of error: Ignore, log error message (or create system notification), and continue
         * - Remove entities in reverse order
         *      - In case of error: Ignore, log error message (or create system notification), and continue
         * - Remove content pack snapshot
         */

        contentPackInstallationPersistenceService.deleteById(installation.id());

        throw new UnsupportedOperationException();
    }

    private ImmutableGraph<Entity> buildEntityGraph(Set<Entity> entities) {
        final Map<EntityDescriptor, Entity> descriptors = entities.stream()
                .collect(Collectors.toMap(Entity::toEntityDescriptor, Function.identity()));
        // TODO: resolve entities from Entity (not EntityDescriptor)
        final Set<Entity> resolvedEntities = null;

        final Predicate<Entity> containsEntity = descriptors.values()::contains;
        final Set<Entity> unexpectedEntities = resolvedEntities.stream()
                .filter(containsEntity.negate())
                .collect(Collectors.toSet());

        if (!unexpectedEntities.isEmpty()) {
            // TODO: Create specific exception
            throw new IllegalArgumentException("Unexpected entities in content pack: " + unexpectedEntities);
        }

        return null;
    }

    private void checkConstraints(Set<Constraint> requiredConstraints) {
        final Set<Constraint> fulfilledConstraints = new HashSet<>();
        for (ConstraintChecker constraintChecker : constraintCheckers) {
            fulfilledConstraints.addAll(constraintChecker.checkConstraints(requiredConstraints));
        }

        if (!fulfilledConstraints.equals(requiredConstraints)) {
            final Sets.SetView<Constraint> unfulfilledConstraints = Sets.difference(requiredConstraints, fulfilledConstraints);

            // TODO: Create specific exception
            throw new IllegalArgumentException("Unfulfilled constraints: " + unfulfilledConstraints);
        }
    }


    private ImmutableMap<String, ValueReference> validateParameters(Map<String, ValueReference> parameters,
                                                                    Set<Parameter> contentPackParameters) {
        final Set<String> contentPackParameterNames = contentPackParameters.stream()
                .map(Parameter::name)
                .collect(Collectors.toSet());

        final Predicate<String> containsContentPackParameter = contentPackParameterNames::contains;
        final Set<String> unusedParameters = parameters.keySet().stream()
                .filter(containsContentPackParameter.negate())
                .collect(Collectors.toSet());
        if (!unusedParameters.isEmpty()) {
            LOG.debug("Unused parameters: {}", unusedParameters);
        }

        final Predicate<String> containsParameter = parameters::containsKey;
        final Set<String> missingParameters = contentPackParameterNames.stream()
                .filter(containsParameter.negate())
                .collect(Collectors.toSet());
        if (!missingParameters.isEmpty()) {
            // TODO: Create specific exception
            throw new IllegalArgumentException("Missing parameters: " + missingParameters);
        }

        final Set<String> invalidParameters = parameters.entrySet().stream()
                .filter(entry -> entry.getValue().valueType() != ValueType.PARAMETER)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (!invalidParameters.isEmpty()) {
            // TODO: Create specific exception
            throw new IllegalArgumentException("Invalid parameter types: " + invalidParameters);
        }

        final ImmutableMap.Builder<String, ValueReference> validatedParameters = ImmutableMap.builder();
        for (Parameter contentPackParameter : contentPackParameters) {
            final String name = contentPackParameter.name();
            final ValueReference providedParameter = parameters.get(name);

            if (providedParameter == null) {
                final Optional<?> defaultValue = contentPackParameter.defaultValue();
                // TODO: Create specific exception
                final Object value = defaultValue.orElseThrow(() -> new IllegalArgumentException("Empty default value for missing parameter " + name));
                final ValueReference valueReference = ValueReference.builder()
                        .valueType(contentPackParameter.valueType())
                        .value(value)
                        .build();
                validatedParameters.put(name, valueReference);
            } else if (providedParameter.valueType() != contentPackParameter.valueType()) {
                // TODO: Create specific exception
                throw new IllegalArgumentException("Incompatible value types, content pack expected " + contentPackParameter.valueType() + ", parameters provided " + providedParameter.valueType());
            } else {
                validatedParameters.put(name, providedParameter);
            }
        }

        return validatedParameters.build();
    }
}
