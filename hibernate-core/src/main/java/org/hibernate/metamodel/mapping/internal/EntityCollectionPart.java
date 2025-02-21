/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.metamodel.mapping.internal;

import org.hibernate.Internal;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.mapping.Collection;
import org.hibernate.metamodel.mapping.CollectionPart;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.NonTransientException;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.sql.results.graph.entity.EntityValuedFetchable;
import org.hibernate.type.descriptor.java.JavaType;

/**
 * An entity-valued collection-part.
 *
 * @apiNote This mapping does not include {@linkplain DiscriminatedCollectionPart "ANY"} mappings
 *
 * @implSpec Allows for 2-phase initialization via {@link #finishInitialization}
 *
 * @author Steve Ebersole
 */
public interface EntityCollectionPart extends CollectionPart, EntityValuedFetchable {
	enum Cardinality { ONE_TO_MANY, MANY_TO_MANY }

	Cardinality getCardinality();

	NotFoundAction getNotFoundAction();

	EntityMappingType getAssociatedEntityMappingType();

	@Override
	default String getFetchableName() {
		return getPartName();
	}

	@Override
	default EntityMappingType getPartMappingType() {
		return getAssociatedEntityMappingType();
	}

	@Override
	default EntityMappingType getEntityMappingType() {
		return getAssociatedEntityMappingType();
	}

	@Override
	default JavaType<?> getJavaType() {
		return getAssociatedEntityMappingType().getJavaType();
	}

	@Override
	default JavaType<?> getExpressibleJavaType() {
		return getJavaType();
	}

	/**
	 * Perform any delayed initialization.
	 * <p/>
	 * The initialization is considered successful if the result is {@code true}.  It is
	 * considered unsuccessful if the result is {@code false} or an exception is thrown.
	 * Unsuccessful initializations are generally retried "later", to allow waiting for
	 * model-parts being available e.g.
	 * <p/>
	 * If the exception is something that will just never succeed, consider throwing
	 * an exception with the {@link NonTransientException} marker to allow the creation
	 * process to stop immediately
	 */
	@Internal
	boolean finishInitialization(
			CollectionPersister collectionDescriptor,
			Collection bootValueMapping,
			String fkTargetModelPartName,
			MappingModelCreationProcess creationProcess);
}
