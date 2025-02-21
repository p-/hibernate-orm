/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Specifies how optimistic lock checking works for the annotated entity.
 * We may detect that an optimistic lock has failed by checking either:
 * <ul>
 * <li>the {@linkplain OptimisticLockType#VERSION version or timestamp},
 * <li>the {@linkplain OptimisticLockType#DIRTY dirty fields} of the
 *     entity instance, or
 * <li>{@linkplain OptimisticLockType#ALL all fields} of the entity.
 * </ul>
 * An optimistic lock is usually checked by including a restriction in a
 * SQL {@code update} or {@code delete} statement. If the database reports
 * that zero rows were updated, we may infer that another transaction has
 * already updated or deleted the row, and report the {@linkplain
 * jakarta.persistence.OptimisticLockException failure} of the optimistic
 * lock.
 * <p>
 * In an inheritance hierarchy, this annotation may only be applied to the
 * root entity, since the optimistic lock checking strategy is inherited
 * by entity subclasses.
 *
 * @author Steve Ebersole
 *
 * @see org.hibernate.LockMode
 * @see jakarta.persistence.LockModeType
 */
@Target( TYPE )
@Retention( RUNTIME )
public @interface OptimisticLocking {
	/**
	 * The optimistic lock checking strategy.
	 */
	OptimisticLockType type() default OptimisticLockType.VERSION;
}
