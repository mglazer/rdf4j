/*******************************************************************************
 * Copyright (c) 2020 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/

package org.eclipse.rdf4j.sail.shacl.ast.paths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.sail.shacl.ast.ShaclAstLists;
import org.eclipse.rdf4j.sail.shacl.ast.ShaclUnsupportedException;
import org.eclipse.rdf4j.sail.shacl.ast.SparqlFragment;
import org.eclipse.rdf4j.sail.shacl.ast.StatementMatcher;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.PlanNode;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.PlanNodeWrapper;
import org.eclipse.rdf4j.sail.shacl.wrapper.data.ConnectionsGroup;
import org.eclipse.rdf4j.sail.shacl.wrapper.data.RdfsSubClassOfReasoner;
import org.eclipse.rdf4j.sail.shacl.wrapper.shape.ShapeSource;

public class SequencePath extends Path {

	private final List<Path> sequence;

	public SequencePath(Resource id, ShapeSource shapeSource) {
		super(id);
		sequence = ShaclAstLists.toList(shapeSource, id, Resource.class)
				.stream()
				.map(p -> Path.buildPath(shapeSource, p))
				.collect(Collectors.toList());

	}

	@Override
	public String toString() {
		return "SequencePath{ " + Arrays.toString(sequence.toArray()) + " }";
	}

	@Override
	public void toModel(Resource subject, IRI predicate, Model model, Set<Resource> cycleDetection) {

		List<Resource> values = sequence.stream().map(Path::getId).collect(Collectors.toList());

		if (!model.contains(id, null, null)) {
			ShaclAstLists.listToRdf(values, id, model);
		}

		sequence.forEach(p -> p.toModel(p.getId(), null, model, cycleDetection));

	}

	@Override
	public PlanNode getAdded(ConnectionsGroup connectionsGroup, Resource[] dataGraph,
			PlanNodeWrapper planNodeWrapper) {
		throw new ShaclUnsupportedException();
	}

	@Override
	public boolean isSupported() {
		return true;
	}

	@Override
	public SparqlFragment getTargetQueryFragment(StatementMatcher.Variable subject, StatementMatcher.Variable object,
			RdfsSubClassOfReasoner rdfsSubClassOfReasoner,
			StatementMatcher.StableRandomVariableProvider stableRandomVariableProvider) {

		String variablePrefix = getVariablePrefix(subject, object);

		List<SparqlFragment> sparqlFragments = new ArrayList<>(sequence.size());

		StatementMatcher.Variable head = subject;
		StatementMatcher.Variable tail = null;

		for (int i = 0; i < sequence.size(); i++) {
			if (tail != null) {
				head = tail;
			}
			if (i + 1 == sequence.size()) {
				// last element
				tail = object;
			} else {
				tail = new StatementMatcher.Variable(variablePrefix + i);
			}

			Path path = sequence.get(i);
			SparqlFragment targetQueryFragment = path.getTargetQueryFragment(head, tail, rdfsSubClassOfReasoner,
					stableRandomVariableProvider);
			sparqlFragments.add(targetQueryFragment);
		}

		return SparqlFragment.join(sparqlFragments);
	}

}
