/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect.identity;

import java.util.function.Consumer;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.model.ast.TableInsert;

/**
 * @author Andrea Boriero
 */
public class H2IdentityColumnSupport extends IdentityColumnSupportImpl {

	public static final H2IdentityColumnSupport INSTANCE = new H2IdentityColumnSupport();

	protected H2IdentityColumnSupport() {
	}

	@Override
	public boolean supportsIdentityColumns() {
		return true;
	}

	@Override
	public String getIdentityColumnString(int type) {
		// not null is implicit
		return "generated by default as identity";
	}

	@Override
	public String getIdentitySelectString(String table, String column, int type) {
		return "call identity()";
	}

	@Override
	public String getIdentityInsertString() {
		return "default";
	}
	
	@FunctionalInterface
	public interface InsertValuesHandler {
		void renderInsertValues();
	}

	public void render(
			TableInsert tableInsert,
			Consumer<String> sqlAppender,
			Consumer<ColumnReference> returnColumnHandler,
			InsertValuesHandler insertValuesHandler,
			SessionFactoryImplementor sessionFactory) {
		insertValuesHandler.renderInsertValues();
		sqlAppender.accept( " call identity();" );
	}
}
