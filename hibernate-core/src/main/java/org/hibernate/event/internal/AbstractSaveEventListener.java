/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.event.internal;

import java.util.Map;

import org.hibernate.LockMode;
import org.hibernate.NonUniqueObjectException;
import org.hibernate.action.internal.AbstractEntityInsertAction;
import org.hibernate.action.internal.EntityIdentityInsertAction;
import org.hibernate.action.internal.EntityInsertAction;
import org.hibernate.classic.Lifecycle;
import org.hibernate.engine.internal.Cascade;
import org.hibernate.engine.internal.CascadePoint;
import org.hibernate.engine.internal.Versioning;
import org.hibernate.engine.spi.CascadingAction;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.EntityEntryExtraState;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.SelfDirtinessTracker;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.Status;
import org.hibernate.event.spi.EventSource;
import org.hibernate.id.IdentifierGenerationException;
import org.hibernate.id.IdentifierGeneratorHelper;
import org.hibernate.internal.CoreLogging;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.jpa.event.spi.CallbackRegistry;
import org.hibernate.jpa.event.spi.CallbackRegistryConsumer;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.pretty.MessageHelper;
import org.hibernate.type.Type;
import org.hibernate.type.TypeHelper;

import static org.hibernate.engine.internal.ManagedTypeHelper.processIfSelfDirtinessTracker;

/**
 * A convenience base class for listeners responding to save events.
 *
 * @author Steve Ebersole.
 */
public abstract class AbstractSaveEventListener<C>
		extends AbstractReassociateEventListener
		implements CallbackRegistryConsumer {
	private static final CoreMessageLogger LOG = CoreLogging.messageLogger( AbstractSaveEventListener.class );

	private CallbackRegistry callbackRegistry;

	public void injectCallbackRegistry(CallbackRegistry callbackRegistry) {
		this.callbackRegistry = callbackRegistry;
	}

	/**
	 * Prepares the save call using the given requested id.
	 *
	 * @param entity The entity to be saved.
	 * @param requestedId The id to which to associate the entity.
	 * @param entityName The name of the entity being saved.
	 * @param context Generally cascade-specific information.
	 * @param source The session which is the source of this save event.
	 *
	 * @return The id used to save the entity.
	 */
	protected Object saveWithRequestedId(
			Object entity,
			Object requestedId,
			String entityName,
			C context,
			EventSource source) {
		callbackRegistry.preCreate( entity );

		return performSave(
				entity,
				requestedId,
				source.getEntityPersister( entityName, entity ),
				false,
				context,
				source,
				true
		);
	}

	/**
	 * Prepares the save call using a newly generated id.
	 *
	 * @param entity The entity to be saved
	 * @param entityName The entity-name for the entity to be saved
	 * @param context Generally cascade-specific information.
	 * @param source The session which is the source of this save event.
	 * @param requiresImmediateIdAccess does the event context require
	 * access to the identifier immediately after execution of this method (if
	 * not, post-insert style id generators may be postponed if we are outside
	 * a transaction).
	 *
	 * @return The id used to save the entity; may be null depending on the
	 *         type of id generator used and the requiresImmediateIdAccess value
	 */
	protected Object saveWithGeneratedId(
			Object entity,
			String entityName,
			C context,
			EventSource source,
			boolean requiresImmediateIdAccess) {
		callbackRegistry.preCreate( entity );

		processIfSelfDirtinessTracker( entity, SelfDirtinessTracker::$$_hibernate_clearDirtyAttributes );

		EntityPersister persister = source.getEntityPersister( entityName, entity );
		Object generatedId = persister.getGenerator().generate( source, entity, null );
		if ( generatedId == null ) {
			throw new IdentifierGenerationException( "null id generated for: " + entity.getClass() );
		}
		else if ( generatedId == IdentifierGeneratorHelper.SHORT_CIRCUIT_INDICATOR ) {
			return source.getIdentifier( entity );
		}
		else if ( generatedId == IdentifierGeneratorHelper.POST_INSERT_INDICATOR ) {
			return performSave( entity, null, persister, true, context, source, requiresImmediateIdAccess );
		}
		else {
			// TODO: define toString()s for generators
			if ( LOG.isDebugEnabled() ) {
				LOG.debugf(
						"Generated identifier: %s, using strategy: %s",
						persister.getIdentifierType().toLoggableString( generatedId, source.getFactory() ),
						persister.getGenerator().getClass().getName()
				);
			}
			return performSave( entity, generatedId, persister, false, context, source, true );
		}
	}

	/**
	 * Prepares the save call by checking the session caches for a pre-existing
	 * entity and performing any lifecycle callbacks.
	 *
	 * @param entity The entity to be saved.
	 * @param id The id by which to save the entity.
	 * @param persister The entity's persister instance.
	 * @param useIdentityColumn Is an identity column being used?
	 * @param context Generally cascade-specific information.
	 * @param source The session from which the event originated.
	 * @param requiresImmediateIdAccess does the event context require
	 * access to the identifier immediately after execution of this method (if
	 * not, post-insert style id generators may be postponed if we are outside
	 * a transaction).
	 *
	 * @return The id used to save the entity; may be null depending on the
	 *         type of id generator used and the requiresImmediateIdAccess value
	 */
	protected Object performSave(
			Object entity,
			Object id,
			EntityPersister persister,
			boolean useIdentityColumn,
			C context,
			EventSource source,
			boolean requiresImmediateIdAccess) {

		if ( LOG.isTraceEnabled() ) {
			LOG.tracev( "Saving {0}", MessageHelper.infoString( persister, id, source.getFactory() ) );
		}

		final EntityKey key = entityKey( entity, id, persister, useIdentityColumn, source );
		if ( invokeSaveLifecycle( entity, persister, source ) ) {
			return id;
		}
		else {
			return performSaveOrReplicate(
					entity,
					key,
					persister,
					useIdentityColumn,
					context,
					source,
					requiresImmediateIdAccess
			);
		}
	}

	private static EntityKey entityKey(Object entity, Object id, EntityPersister persister, boolean useIdentityColumn, EventSource source) {
		if ( !useIdentityColumn) {
			final EntityKey key = source.generateEntityKey( id, persister );
			final PersistenceContext persistenceContext = source.getPersistenceContextInternal();
			final Object old = persistenceContext.getEntity( key );
			if ( old != null ) {
				if ( persistenceContext.getEntry( old ).getStatus() == Status.DELETED ) {
					source.forceFlush( persistenceContext.getEntry( old ) );
				}
				else {
					throw new NonUniqueObjectException( id, persister.getEntityName() );
				}
			}
			persister.setIdentifier( entity, id, source );
			return key;
		}
		else {
			return null;
		}
	}

	protected boolean invokeSaveLifecycle(Object entity, EntityPersister persister, EventSource source) {
		// Sub-insertions should occur before containing insertion so
		// Try to do the callback now
		if ( persister.implementsLifecycle() ) {
			LOG.debug( "Calling onSave()" );
			if ( ((Lifecycle) entity).onSave( source ) ) {
				LOG.debug( "Insertion vetoed by onSave()" );
				return true;
			}
		}
		return false;
	}

	/**
	 * Performs all the actual work needed to save an entity (well to get the save moved to
	 * the execution queue).
	 *
	 * @param entity The entity to be saved
	 * @param key The id to be used for saving the entity (or null, in the case of identity columns)
	 * @param persister The entity's persister instance.
	 * @param useIdentityColumn Should an identity column be used for id generation?
	 * @param context Generally cascade-specific information.
	 * @param source The session which is the source of the current event.
	 * @param requiresImmediateIdAccess Is access to the identifier required immediately
	 * after the completion of the save?  persist(), for example, does not require this...
	 *
	 * @return The id used to save the entity; may be null depending on the
	 *         type of id generator used and the requiresImmediateIdAccess value
	 */
	protected Object performSaveOrReplicate(
			Object entity,
			EntityKey key,
			EntityPersister persister,
			boolean useIdentityColumn,
			C context,
			EventSource source,
			boolean requiresImmediateIdAccess) {

		final Object id = key == null ? null : key.getIdentifier();

		boolean shouldDelayIdentityInserts = !source.isTransactionInProgress() && !requiresImmediateIdAccess;
		final PersistenceContext persistenceContext = source.getPersistenceContextInternal();

		// Put a placeholder in entries, so we don't recurse back and try to save() the
		// same object again. QUESTION: should this be done before onSave() is called?
		// likewise, should it be done before onUpdate()?
		EntityEntry original = persistenceContext.addEntry(
				entity,
				Status.SAVING,
				null,
				null,
				id,
				null,
				LockMode.WRITE,
				useIdentityColumn,
				persister,
				false
		);

		cascadeBeforeSave( source, persister, entity, context );

		final AbstractEntityInsertAction insert = addInsertAction(
				cloneAndSubstituteValues( entity, persister, context, source, id ),
				id,
				entity,
				persister,
				useIdentityColumn,
				source,
				shouldDelayIdentityInserts
		);

		// postpone initializing id in case the insert has non-nullable transient dependencies
		// that are not resolved until cascadeAfterSave() is executed
		cascadeAfterSave( source, persister, entity, context );

		final Object finalId = handleGeneratedId( useIdentityColumn, id, insert );

		EntityEntry newEntry = persistenceContext.getEntry( entity );
		if ( newEntry != original ) {
			EntityEntryExtraState extraState = newEntry.getExtraState( EntityEntryExtraState.class );
			if ( extraState == null ) {
				newEntry.addExtraState( original.getExtraState( EntityEntryExtraState.class ) );
			}
		}

		return finalId;
	}

	private static Object handleGeneratedId(boolean useIdentityColumn, Object id, AbstractEntityInsertAction insert) {
		if ( useIdentityColumn && insert.isEarlyInsert() ) {
			if ( insert instanceof EntityIdentityInsertAction ) {
				Object generatedId = ((EntityIdentityInsertAction) insert).getGeneratedId();
				insert.handleNaturalIdPostSaveNotifications( generatedId );
				return generatedId;
			}
			else {
				throw new IllegalStateException(
						"Insert should be using an identity column, but action is of unexpected type: "
								+ insert.getClass().getName()
				);
			}
		}
		else {
			return id;
		}
	}

	private Object[] cloneAndSubstituteValues(Object entity, EntityPersister persister, C context, EventSource source, Object id) {
		Object[] values = persister.getPropertyValuesToInsert(entity, getMergeMap(context), source);
		Type[] types = persister.getPropertyTypes();

		boolean substitute = substituteValuesIfNecessary(entity, id, values, persister, source);
		if ( persister.hasCollections() ) {
			substitute = visitCollectionsBeforeSave(entity, id, values, types, source) || substitute;
		}

		if ( substitute ) {
			persister.setValues(entity, values );
		}

		TypeHelper.deepCopy(
				values,
				types,
				persister.getPropertyUpdateability(),
				values,
				source
		);
		return values;
	}

	private AbstractEntityInsertAction addInsertAction(
			Object[] values,
			Object id,
			Object entity,
			EntityPersister persister,
			boolean useIdentityColumn,
			EventSource source,
			boolean shouldDelayIdentityInserts) {
		if ( useIdentityColumn ) {
			EntityIdentityInsertAction insert = new EntityIdentityInsertAction(
					values,
					entity,
					persister,
					isVersionIncrementDisabled(),
					source,
					shouldDelayIdentityInserts
			);
			source.getActionQueue().addAction( insert );
			return insert;
		}
		else {
			final EntityInsertAction insert = new EntityInsertAction(
					id,
					values,
					entity,
					Versioning.getVersion( values, persister ),
					persister,
					isVersionIncrementDisabled(),
					source
			);
			source.getActionQueue().addAction( insert );
			return insert;
		}
	}

	protected Map<Object,Object> getMergeMap(C anything) {
		return null;
	}

	/**
	 * After the save, will te version number be incremented
	 * if the instance is modified?
	 *
	 * @return True if the version will be incremented on an entity change after save;
	 *         false otherwise.
	 */
	protected boolean isVersionIncrementDisabled() {
		return false;
	}

	protected boolean visitCollectionsBeforeSave(
			Object entity,
			Object id,
			Object[] values,
			Type[] types,
			EventSource source) {
		WrapVisitor visitor = new WrapVisitor( entity, id, source );
		// substitutes into values by side effect
		visitor.processEntityPropertyValues( values, types );
		return visitor.isSubstitutionRequired();
	}

	/**
	 * Perform any property value substitution that is necessary
	 * (interceptor callback, version initialization...)
	 *
	 * @param entity The entity
	 * @param id The entity identifier
	 * @param values The snapshot entity state
	 * @param persister The entity persister
	 * @param source The originating session
	 *
	 * @return True if the snapshot state changed such that
	 *         reinjection of the values into the entity is required.
	 */
	protected boolean substituteValuesIfNecessary(
			Object entity,
			Object id,
			Object[] values,
			EntityPersister persister,
			SessionImplementor source) {
		boolean substitute = source.getInterceptor().onSave(
				entity,
				id,
				values,
				persister.getPropertyNames(),
				persister.getPropertyTypes()
		);

		//keep the existing version number in the case of replicate!
		if ( persister.isVersioned() ) {
			substitute = Versioning.seedVersion(
					values,
					persister.getVersionProperty(),
					persister.getVersionMapping(),
					source
			) || substitute;
		}
		return substitute;
	}

	/**
	 * Handles the calls needed to perform pre-save cascades for the given entity.
	 *
	 * @param source The session from which the save event originated.
	 * @param persister The entity's persister instance.
	 * @param entity The entity to be saved.
	 * @param context Generally cascade-specific data
	 */
	protected void cascadeBeforeSave(
			EventSource source,
			EntityPersister persister,
			Object entity,
			C context) {
		// cascade-save to many-to-one BEFORE the parent is saved
		final PersistenceContext persistenceContext = source.getPersistenceContextInternal();
		persistenceContext.incrementCascadeLevel();
		try {
			Cascade.cascade(
					getCascadeAction(),
					CascadePoint.BEFORE_INSERT_AFTER_DELETE,
					source,
					persister,
					entity,
					context
			);
		}
		finally {
			persistenceContext.decrementCascadeLevel();
		}
	}

	/**
	 * Handles calls needed to perform post-save cascades.
	 *
	 * @param source The session from which the event originated.
	 * @param persister The entity's persister instance.
	 * @param entity The entity being saved.
	 * @param context Generally cascade-specific data
	 */
	protected void cascadeAfterSave(
			EventSource source,
			EntityPersister persister,
			Object entity,
			C context) {
		// cascade-save to collections AFTER the collection owner was saved
		final PersistenceContext persistenceContext = source.getPersistenceContextInternal();
		persistenceContext.incrementCascadeLevel();
		try {
			Cascade.cascade(
					getCascadeAction(),
					CascadePoint.AFTER_INSERT_BEFORE_DELETE,
					source,
					persister,
					entity,
					context
			);
		}
		finally {
			persistenceContext.decrementCascadeLevel();
		}
	}

	protected abstract CascadingAction<C> getCascadeAction();

}
