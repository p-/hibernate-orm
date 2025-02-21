/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.tuple;

import org.hibernate.annotations.Generated;
import org.hibernate.dialect.Dialect;

import java.lang.reflect.Member;

import static org.hibernate.internal.util.StringHelper.isEmpty;

/**
 * A {@link AnnotationValueGeneration} which marks a property as generated in the database.
 *
 * @author Steve Ebersole
 * @author Gunnar Morling
 */
public class GeneratedValueGeneration implements InDatabaseGenerator {

	private GenerationTiming timing;
	private boolean writable;
	private String[] sql;

	public GeneratedValueGeneration() {
	}

	public GeneratedValueGeneration(GenerationTiming timing) {
		this.timing = timing;
	}

	public GeneratedValueGeneration(Generated annotation) {
		timing = annotation.timing().isAlways()
				? annotation.value().getEquivalent()
				: annotation.timing();
		sql = isEmpty( annotation.sql() ) ? null : new String[] { annotation.sql() };
		writable = annotation.writable() || sql != null;
	}

	@Override
	public GenerationTiming getGenerationTiming() {
		return timing;
	}

	@Override
	public boolean referenceColumnsInSql() {
		return writable;
	}

	@Override
	public String[] getReferencedColumnValues(Dialect dialect) {
		return sql;
	}

	@Override
	public boolean writePropertyValue() {
		return writable && sql==null;
	}
}

