/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.mapping.internal;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Incubating;
import org.hibernate.LockOptions;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.loader.ast.internal.LoaderSelectBuilder;
import org.hibernate.metamodel.UnsupportedMappingException;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.GeneratedValueResolver;
import org.hibernate.metamodel.mapping.InDatabaseGeneratedValueResolver;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.query.spi.QueryParameterBindings;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.exec.internal.JdbcParameterBindingsImpl;
import org.hibernate.sql.exec.spi.Callback;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcOperationQuerySelect;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.results.spi.ListResultsConsumer;
import org.hibernate.tuple.GenerationTiming;
import org.hibernate.tuple.Generator;

/**
 * @author Steve Ebersole
 */
@Incubating
public class GeneratedValuesProcessor {
	private final SelectStatement selectStatement;
	private final List<GeneratedValueDescriptor> valueDescriptors = new ArrayList<>();
	private final List<JdbcParameter> jdbcParameters = new ArrayList<>();

	private final EntityMappingType entityDescriptor;
	private final SessionFactoryImplementor sessionFactory;

	public GeneratedValuesProcessor(
			EntityMappingType entityDescriptor,
			GenerationTiming timing,
			SessionFactoryImplementor sessionFactory) {
		this.entityDescriptor = entityDescriptor;
		this.sessionFactory = sessionFactory;

		// NOTE: we only care about db-generated values here. in-memory generation
		// is applied before the insert/update happens.

		// todo (6.0): for now, we rely on the entity metamodel as composite attributes report
		//             GenerationTiming.NEVER even if they have attributes that would need generation
		final List<AttributeMapping> generatedValuesToSelect = getGeneratedValues( entityDescriptor, timing );
		if ( generatedValuesToSelect.isEmpty() ) {
			selectStatement = null;
		}
		else {
			selectStatement = LoaderSelectBuilder.createSelect(
					entityDescriptor,
					generatedValuesToSelect,
					entityDescriptor.getIdentifierMapping(),
					null,
					1,
					LoadQueryInfluencers.NONE,
					LockOptions.READ,
					jdbcParameters::add,
					sessionFactory
			);
		}
	}

	private List<AttributeMapping> getGeneratedValues(EntityMappingType entityDescriptor, GenerationTiming timing) {
		final Generator[] generators = entityDescriptor.getEntityPersister().getEntityMetamodel().getGenerators();
		final List<AttributeMapping> generatedValuesToSelect = new ArrayList<>();
		entityDescriptor.visitAttributeMappings( mapping -> {
			final Generator generator = generators[ mapping.getStateArrayPosition() ];
			if ( generator != null
					&& generator.generatedByDatabase()
					&& generator.getGenerationTiming().isNotNever() ) {
				// this attribute is generated for the timing we are processing...
				valueDescriptors.add( new GeneratedValueDescriptor(
						new InDatabaseGeneratedValueResolver( timing, generatedValuesToSelect.size() ),
						mapping
				) );
				generatedValuesToSelect.add( mapping );
			}
		} );
		return generatedValuesToSelect;
	}

	public void processGeneratedValues(Object entity, Object id, Object[] state, SharedSessionContractImplementor session) {
		if ( selectStatement == null ) {
			return;
		}

		final JdbcServices jdbcServices = sessionFactory.getJdbcServices();
		final JdbcEnvironment jdbcEnvironment = jdbcServices.getJdbcEnvironment();
		final SqlAstTranslatorFactory sqlAstTranslatorFactory = jdbcEnvironment.getSqlAstTranslatorFactory();

		final JdbcParameterBindings jdbcParamBindings = new JdbcParameterBindingsImpl( jdbcParameters.size() );
		int offset = jdbcParamBindings.registerParametersForEachJdbcValue(
				id,
				Clause.WHERE,
				entityDescriptor.getIdentifierMapping(),
				jdbcParameters,
				session
		);
		assert offset == jdbcParameters.size();
		final JdbcOperationQuerySelect jdbcSelect = sqlAstTranslatorFactory
				.buildSelectTranslator( sessionFactory, selectStatement )
				.translate( jdbcParamBindings, QueryOptions.NONE );

		final List<Object[]> results = session.getFactory().getJdbcServices().getJdbcSelectExecutor().list(
				jdbcSelect,
				jdbcParamBindings,
				new ExecutionContext() {
					@Override
					public SharedSessionContractImplementor getSession() {
						return session;
					}

					@Override
					public QueryOptions getQueryOptions() {
						return QueryOptions.NONE;
					}

					@Override
					public String getQueryIdentifier(String sql) {
						return sql;
					}

					@Override
					public QueryParameterBindings getQueryParameterBindings() {
						return QueryParameterBindings.NO_PARAM_BINDINGS;
					}

					@Override
					public Callback getCallback() {
						throw new UnsupportedMappingException( "Follow-on locking not supported yet" );
					}

				},
				(row) -> row,
				ListResultsConsumer.UniqueSemantic.FILTER
		);

		assert results.size() == 1;
		final Object[] dbSelectionResults = results.get( 0 );

		for ( int i = 0; i < valueDescriptors.size(); i++ ) {
			final GeneratedValueDescriptor descriptor = valueDescriptors.get( i );
			final Object generatedValue = descriptor.resolver.resolveGeneratedValue( dbSelectionResults, entity, session, state[i] );
			state[ descriptor.attribute.getStateArrayPosition() ] = generatedValue;
			descriptor.attribute.getAttributeMetadataAccess()
					.resolveAttributeMetadata( entityDescriptor )
					.getPropertyAccess()
					.getSetter()
					.set( entity, generatedValue );
		}
	}

	private static class GeneratedValueDescriptor {
		public final GeneratedValueResolver resolver;
		public final AttributeMapping attribute;

		public GeneratedValueDescriptor(GeneratedValueResolver resolver, AttributeMapping attribute) {
			this.resolver = resolver;
			this.attribute = attribute;
		}
	}
}
