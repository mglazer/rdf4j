/*******************************************************************************
 * Copyright (c) 2022 Eclipse RDF4J contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 ******************************************************************************/

package org.eclipse.rdf4j.examples;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.vocabulary.RDF4J;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.eclipse.rdf4j.sail.shacl.Stats;
import org.eclipse.rdf4j.sail.shacl.ValidationSettings;
import org.eclipse.rdf4j.sail.shacl.ast.ContextWithShapes;
import org.eclipse.rdf4j.sail.shacl.ast.Shape;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.SingleCloseablePlanNode;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.ValidationExecutionLogger;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.ValidationTuple;
import org.eclipse.rdf4j.sail.shacl.results.ValidationReport;
import org.eclipse.rdf4j.sail.shacl.results.lazy.LazyValidationReport;
import org.eclipse.rdf4j.sail.shacl.results.lazy.ValidationResultIterator;
import org.eclipse.rdf4j.sail.shacl.wrapper.data.ConnectionsGroup;
import org.eclipse.rdf4j.sail.shacl.wrapper.shape.ForwardChainingShapeSource;

public class TempShaclValidation {

	public static void main(String[] args) throws IOException {

		SailRepository shapesRepo = new SailRepository(new MemoryStore());
		SailRepository dataRepo = new SailRepository(new MemoryStore());

		try (RepositoryConnection connection = shapesRepo.getConnection()) {

			StringReader shaclRules = new StringReader(
					String.join("\n", "",
							"@prefix ex: <http://example.com/ns#> .",
							"@prefix sh: <http://www.w3.org/ns/shacl#> .",
							"@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .",
							"@prefix foaf: <http://xmlns.com/foaf/0.1/>.",

							"ex:PersonShape",
							"  a sh:NodeShape  ;",
							"  sh:targetClass foaf:Person ;",
							"  sh:property ex:PersonMustHaveIntAge, ex:MaxOneAgePerPerson .",

							"ex:PersonMustHaveIntAge ",
							"  sh:path foaf:age ;",
							"  sh:datatype xsd:int ;",
							"  sh:minCount 1 .",

							"ex:MaxOneAgePerPerson ",
							"  sh:path foaf:age ;",
							"  sh:severity sh:Warning ;",
							"  sh:maxCount 1 ."
					));

			connection.add(shaclRules, "", RDFFormat.TURTLE, RDF4J.SHACL_SHAPE_GRAPH);

		}

		try (RepositoryConnection connection = dataRepo.getConnection()) {
			StringReader invalidSampleData = new StringReader(
					String.join("\n", "",
							"@prefix ex: <http://example.com/ns#> .",
							"@prefix foaf: <http://xmlns.com/foaf/0.1/>.",
							"@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .",

							"ex:peter a foaf:Person ;",
							"  foaf:age 20, \"30\"^^xsd:int  ."

					));

			connection.add(invalidSampleData, "", RDFFormat.TURTLE);
		}

		ValidationReport validationReport = validate(shapesRepo, dataRepo);

		System.out.println(validationReport);
	}

	private static ValidationReport validate(SailRepository shapesRepo, SailRepository dataRepo) {
		try (RepositoryConnection shapesConnection = shapesRepo.getConnection()) {
			try (ForwardChainingShapeSource shapeSource = new ForwardChainingShapeSource(shapesConnection)) {

				List<ContextWithShapes> shapes = Shape.Factory.getShapes(shapeSource,
						new Shape.ParseSettings(true, true));

				try (SailRepositoryConnection dataRepoConnection = dataRepo.getConnection()) {
					return performValidation(shapes, new ConnectionsGroup(dataRepoConnection.getSailConnection(), null,
							null, null, new Stats(), null, null, true));
				}

			}
		}
	}

	private static ValidationReport performValidation(List<ContextWithShapes> shapes,
			ConnectionsGroup connectionsGroup) {

		List<ValidationResultIterator> collect = shapes
				.stream()
				.flatMap(contextWithShapes -> contextWithShapes
						.getShapes()
						.stream()
						.map(shape -> shape.generatePlans(connectionsGroup,
								new ValidationSettings(new Resource[] { null }, false, true, false)))
				)
				.map(planNode -> {
					planNode = new SingleCloseablePlanNode(planNode, ValidationExecutionLogger.getInstance(false));
					return planNode;
				})
				.map(planNode -> {
					try (CloseableIteration<? extends ValidationTuple, SailException> iterator = planNode.iterator()) {
						return new ValidationResultIterator(iterator, -1);
					}
				})
				.collect(Collectors.toList());

		return new LazyValidationReport(collect, -1);

	}

}
