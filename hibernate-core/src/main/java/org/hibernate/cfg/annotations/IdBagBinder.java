/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.cfg.annotations;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import org.hibernate.MappingException;
import org.hibernate.annotations.CollectionId;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.cfg.AnnotatedColumn;
import org.hibernate.cfg.AnnotatedColumns;
import org.hibernate.cfg.IdGeneratorResolverSecondPass;
import org.hibernate.cfg.PropertyData;
import org.hibernate.cfg.PropertyInferredData;
import org.hibernate.cfg.SecondPass;
import org.hibernate.cfg.WrappedInferredData;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.IdentifierBag;
import org.hibernate.mapping.IdentifierCollection;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.resource.beans.spi.ManagedBean;
import org.hibernate.usertype.UserCollectionType;

import jakarta.persistence.Column;

import static org.hibernate.cfg.BinderHelper.makeIdGenerator;

/**
 * A {@link CollectionBinder} for {@link org.hibernate.collection.spi.PersistentIdentifierBag id bags}
 * whose mapping model type is {@link org.hibernate.mapping.IdentifierBag}.
 *
 * @author Emmanuel Bernard
 */
public class IdBagBinder extends BagBinder {

	public IdBagBinder(
			Supplier<ManagedBean<? extends UserCollectionType>> customTypeBeanResolver,
			MetadataBuildingContext buildingContext) {
		super( customTypeBeanResolver, buildingContext );
	}

	protected Collection createCollection(PersistentClass owner) {
		return new IdentifierBag( getCustomTypeBeanResolver(), owner, getBuildingContext() );
	}

	@Override
	protected boolean bindStarToManySecondPass(Map<String, PersistentClass> persistentClasses) {
		boolean result = super.bindStarToManySecondPass( persistentClasses );

		final CollectionId collectionIdAnn = property.getAnnotation( CollectionId.class );
		if ( collectionIdAnn == null ) {
			//TODO shouldn't this be an assertion?
			throw new MappingException( "idbag mapping missing '@CollectionId' annotation" );
		}

		final PropertyData propertyData = new WrappedInferredData(
				new PropertyInferredData(
						null,
						property,
						//default access should not be useful
						null,
						buildingContext.getBootstrapContext().getReflectionManager()
				),
				"id"
		);

		final AnnotatedColumns idColumns = AnnotatedColumn.buildColumnsFromAnnotations(
				new Column[] { collectionIdAnn.column() },
				null,
				Nullability.FORCED_NOT_NULL,
				propertyHolder,
				propertyData,
				Collections.emptyMap(),
				buildingContext
		);

		//we need to make sure all id columns must be not-null.
		for ( AnnotatedColumn idColumn : idColumns.getColumns() ) {
			idColumn.setNullable( false );
		}

		final BasicValueBinder valueBinder =
				new BasicValueBinder( BasicValueBinder.Kind.COLLECTION_ID, buildingContext );

		final Table table = collection.getCollectionTable();
		valueBinder.setTable( table );
		valueBinder.setColumns( idColumns );

		valueBinder.setType(
				property,
				getElementType(),
				null,
				null
		);

		final BasicValue id = valueBinder.make();
		( (IdentifierCollection) collection ).setIdentifier( id );

		final String namedGenerator = collectionIdAnn.generator();

		if ( "identity".equals( namedGenerator ) ) {
			throw new MappingException( "IDENTITY generation not supported for CollectionId" );
		}

		if ( "assigned".equals( namedGenerator ) ) {
			throw new MappingException( "Assigned generation not supported for CollectionId" );
		}

		if ( "native".equals( namedGenerator ) ) {
			throw new MappingException( "Native generation not supported for CollectionId" );
		}

		final String generatorName;
		final String generatorType;

		if ( "sequence".equals( namedGenerator ) ) {
			generatorType = namedGenerator;
			generatorName = "";
		}
		else if ( "increment".equals( namedGenerator ) ) {
			generatorType = namedGenerator;
			generatorName = "";
		}
		else {
			generatorType = namedGenerator;
			generatorName = namedGenerator;
		}

		id.setIdentifierGeneratorStrategy( generatorType );

		if ( buildingContext.getBootstrapContext().getJpaCompliance().isGlobalGeneratorScopeEnabled() ) {
			SecondPass secondPass = new IdGeneratorResolverSecondPass(
					id,
					property,
					generatorType,
					generatorName,
					getBuildingContext()
			);
			buildingContext.getMetadataCollector().addSecondPass( secondPass );
		}
		else {
			makeIdGenerator(
					id,
					property,
					generatorType,
					generatorName,
					getBuildingContext(),
					localGenerators
			);
		}
		return result;
	}
}
