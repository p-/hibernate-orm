/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

//$Id: Bunny.java 14761 2008-06-11 13:51:06Z hardy.ferentschik $
package org.hibernate.orm.test.annotations.id.sequences.entities;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Set;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import org.hibernate.annotations.GenericGenerator;

/**
 * Blown precision on related entity when &#064;JoinColumn is used.
 * 
 * @see ANN-748
 * @author Andrew C. Oliver andyspam@osintegrators.com
 */
@Entity
public class Bunny implements Serializable {
	@Id
	@GeneratedValue(generator = "java5_uuid")
	@GenericGenerator(name = "java5_uuid", type = org.hibernate.orm.test.annotations.id.UUIDGenerator.class)
	@Column(name = "id", precision = 128, scale = 0)
	private BigDecimal id;

	@OneToMany(mappedBy = "bunny", cascade = { CascadeType.PERSIST })
	Set<PointyTooth> teeth;
	
	@OneToMany(mappedBy = "bunny", cascade = { CascadeType.PERSIST })
	Set<TwinkleToes> toes;

	public void setTeeth(Set<PointyTooth> teeth) {
		this.teeth = teeth;
	}

	public BigDecimal getId() {
		return id;
	}
}
