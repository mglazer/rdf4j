/*******************************************************************************
 * Copyright (c) 2021 Eclipse RDF4J contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/
package org.eclipse.rdf4j.query.algebra.evaluation.impl.evaluationsteps;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.algebra.Projection;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryEvaluationStep;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.QueryEvaluationContext;
import org.eclipse.rdf4j.query.algebra.evaluation.iterator.ProjectionIterator;

public final class ProjectionQueryEvaluationStep implements QueryEvaluationStep {
	private final Projection projection;
	private final QueryEvaluationStep qes;
	private final QueryEvaluationContext context;

	public ProjectionQueryEvaluationStep(Projection projection, QueryEvaluationStep qes,
			QueryEvaluationContext context) {
		this.projection = projection;
		this.qes = qes;
		this.context = context;
	}

	@Override
	public CloseableIteration<BindingSet, QueryEvaluationException> evaluate(BindingSet bindings) {

		CloseableIteration<BindingSet, QueryEvaluationException> evaluate = qes.evaluate(bindings);
		if (evaluate == null) {
			return null;
		}
		try {
			return new ProjectionIterator(projection, evaluate, bindings, context);
		} catch (Throwable t) {
			evaluate.close();
			throw t;
		}
	}
}
