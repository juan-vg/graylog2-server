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
package org.graylog2.contentpacks.catalogs;

import org.graylog2.contentpacks.codecs.InputCodec;
import org.graylog2.contentpacks.codecs.InputWithExtractors;
import org.graylog2.contentpacks.model.ModelId;
import org.graylog2.contentpacks.model.ModelType;
import org.graylog2.contentpacks.model.ModelTypes;
import org.graylog2.contentpacks.model.entities.Entity;
import org.graylog2.contentpacks.model.entities.EntityDescriptor;
import org.graylog2.contentpacks.model.entities.EntityExcerpt;
import org.graylog2.database.NotFoundException;
import org.graylog2.inputs.Input;
import org.graylog2.inputs.InputService;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class InputCatalog implements EntityCatalog {
    public static final ModelType TYPE = ModelTypes.INPUT;

    private final InputService inputService;
    private final InputCodec codec;

    @Inject
    public InputCatalog(InputService inputService,
                        InputCodec codec) {
        this.inputService = inputService;
        this.codec = codec;
    }

    @Override
    public boolean supports(ModelType modelType) {
        return TYPE.equals(modelType);
    }

    @Override
    public Set<EntityExcerpt> listEntityExcerpts() {
        return inputService.all().stream()
                .map(InputWithExtractors::create)
                .map(codec::createExcerpt)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<Entity> collectEntity(EntityDescriptor entityDescriptor) {
        final ModelId modelId = entityDescriptor.id();

        try {
            final Input input = inputService.find(modelId.id());
            final InputWithExtractors inputWithExtractors = InputWithExtractors.create(input, inputService.getExtractors(input));
            return Optional.of(codec.encode(inputWithExtractors));
        } catch (NotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public Set<EntityDescriptor> resolve(EntityDescriptor entityDescriptor) {
        return Collections.emptySet();
    }
}
