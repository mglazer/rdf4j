/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/
package org.eclipse.rdf4j.model.impl;

import java.util.Collection;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ModelFactory;
import org.eclipse.rdf4j.model.Statement;

/**
 * Creates {@link TreeModel}.
 *
 * @author James Leigh
 */
public class TreeModelFactory implements ModelFactory {

	@Override
	public TreeModel createEmptyModel() {
		return new TreeModel();
	}

	@Override
	public Model createFrom(Collection<? extends Statement> collection) {
		return new TreeModel(collection);
	}

}
