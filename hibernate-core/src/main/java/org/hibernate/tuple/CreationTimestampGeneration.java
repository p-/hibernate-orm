/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.tuple;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import org.hibernate.dialect.Dialect;

/**
 * Value generation implementation for {@link CreationTimestamp}.
 *
 * @author Gunnar Morling
 *
 * @see org.hibernate.annotations.CurrentTimestampGeneration
 */
public class CreationTimestampGeneration implements AnnotationValueGeneration<CreationTimestamp> {

	private ValueGenerator<?> generator;

	@Override
	public void initialize(CreationTimestamp annotation, Class<?> propertyType) {
		if ( annotation.source() == SourceType.VM ) {
			generator = TimestampGenerators.get( propertyType );
		}
	}

	@Override
	public GenerationTiming getGenerationTiming() {
		return GenerationTiming.INSERT;
	}

	@Override
	public ValueGenerator<?> getValueGenerator() {
		return generator;
	}

	@Override
	public boolean referenceColumnInSql() {
		return false;
	}

	@Override
	public String getDatabaseGeneratedReferencedColumnValue() {
		return "current_timestamp";
	}

	@Override
	public String getDatabaseGeneratedReferencedColumnValue(Dialect dialect) {
		return dialect.currentTimestamp();
	}
}
