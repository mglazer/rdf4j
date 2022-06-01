/*******************************************************************************
 * Copyright (c) 2020 Eclipse RDF4J contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 ******************************************************************************/
package org.eclipse.rdf4j.sail.shacl.ast;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.vocabulary.SHACL;

public enum Severity {
	Info(SHACL.INFO),
	Warning(SHACL.WARNING),
	Violation(SHACL.VIOLATION);

	Value iri;

	Severity(Value iri) {
		this.iri = iri;
	}

	public static Severity fromIri(IRI iri) {
		for (Severity severity : Severity.values()) {
			if (severity.iri == iri)
				return severity;
		}
		for (Severity severity : Severity.values()) {
			if (severity.iri.equals(iri))
				return severity;
		}
		throw new IllegalArgumentException("No know SHACL Severity could be found for <>" + iri);
	}

	public Value getIri() {
		return iri;
	}
}
