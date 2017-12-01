/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.testing.transaction;

import java.util.function.Consumer;

import org.hibernate.Transaction;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;

import org.jboss.logging.Logger;

/**
 * @author Steve Ebersole
 */
public class TransactionUtil2 {
	private static final Logger log = Logger.getLogger( TransactionUtil2.class );

	public static void inSession(SessionFactoryImplementor sfi, Consumer<SessionImplementor> action) {
		log.trace( "#inSession(SF,action)" );

		try (SessionImplementor session = (SessionImplementor) sfi.openSession()) {
			log.trace( "Session opened, calling action" );
			action.accept( session );
			log.trace( "called action" );
		}
		finally {
			log.trace( "Session close - auto-close block" );
		}
	}


	public static void inTransaction(SessionFactoryImplementor factory, Consumer<SessionImplementor> action) {
		log.trace( "#inTransaction(factory, action)");

		try (SessionImplementor session = (SessionImplementor) factory.openSession()) {
			log.trace( "Session opened, calling action" );
			inTransaction( session, action );
			log.trace( "called action" );
		}
		finally {
			log.trace( "Session close - auto-close lock" );
		}
	}

	public static void inTransaction(SessionImplementor session, Consumer<SessionImplementor> action) {
		log.trace( "inTransaction(session,action)" );

		final Transaction txn = session.beginTransaction();
		log.trace( "Started transaction" );

		try {
			log.trace( "Calling action in txn" );
			action.accept( session );
			log.trace( "Called action - in txn" );

			if ( txn.isActive() ) {
				if ( txn.getRollbackOnly() ) {
					log.trace( "Rolling back transaction due to being marked for rollback only" );
					txn.rollback();
					log.trace( "Rolled back transaction due to being marked for rollback only" );
				}
				else {
					log.trace( "Committing transaction" );
					txn.commit();
					log.trace( "Committed transaction" );
				}
			}
		}
		catch (Exception e) {
			if ( txn.isActive() ) {
				log.tracef(
						"Error calling action: %s (%s) - rolling back",
						e.getClass().getName(),
						e.getMessage()
				);

				try {
					txn.rollback();
				}
				catch (Exception ignore) {
					log.trace( "Was unable to roll back transaction" );
					// really nothing else we can do here - the attempt to
					//		rollback already failed and there is nothing else
					// 		to clean up.
				}
			}
			else {
				log.tracef(
						"Error calling action: %s (%s) - transaction was already rolled back",
						e.getClass().getName(),
						e.getMessage()
				);
			}

			throw e;
		}
	}
}
