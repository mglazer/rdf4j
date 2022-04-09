/*******************************************************************************
 * Copyright (c) 2020 Eclipse RDF4J contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/

package org.eclipse.rdf4j.sail.shacl.ast.constraintcomponents;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.vocabulary.SHACL;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.sail.shacl.ConnectionsGroup;
import org.eclipse.rdf4j.sail.shacl.ShaclSail;
import org.eclipse.rdf4j.sail.shacl.SourceConstraintComponent;
import org.eclipse.rdf4j.sail.shacl.ast.Cache;
import org.eclipse.rdf4j.sail.shacl.ast.NodeShape;
import org.eclipse.rdf4j.sail.shacl.ast.PropertyShape;
import org.eclipse.rdf4j.sail.shacl.ast.ShaclProperties;
import org.eclipse.rdf4j.sail.shacl.ast.ShaclUnsupportedException;
import org.eclipse.rdf4j.sail.shacl.ast.Shape;
import org.eclipse.rdf4j.sail.shacl.ast.StatementMatcher;
import org.eclipse.rdf4j.sail.shacl.ast.ValidationQuery;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.AllTargetsPlanNode;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.BulkedExternalLeftOuterJoin;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.GroupByCountFilter;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.LeftOuterJoin;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.NotValuesIn;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.PlanNode;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.PlanNodeProvider;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.ShiftToPropertyShape;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.TrimToTarget;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.TupleMapper;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.UnionNode;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.Unique;
import org.eclipse.rdf4j.sail.shacl.ast.planNodes.ValidationTuple;
import org.eclipse.rdf4j.sail.shacl.ast.targets.EffectiveTarget;
import org.eclipse.rdf4j.sail.shacl.ast.targets.TargetChain;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import static org.eclipse.rdf4j.model.util.Values.literal;

public class QualifiedMinCountConstraintComponent extends AbstractConstraintComponent {
	Shape qualifiedValueShape;
	Boolean qualifiedValueShapesDisjoint;
	Long qualifiedMinCount;

	public QualifiedMinCountConstraintComponent(Resource id, RepositoryConnection connection, Cache cache, ShaclSail shaclSail, Boolean qualifiedValueShapesDisjoint, Long qualifiedMinCount) {
		super(id);

		ShaclProperties p = new ShaclProperties(id, connection);

		this.qualifiedValueShapesDisjoint = qualifiedValueShapesDisjoint;
		this.qualifiedMinCount = qualifiedMinCount;

		if (p.getType() == SHACL.NODE_SHAPE) {
			qualifiedValueShape = NodeShape.getInstance(p, connection, cache, false, shaclSail);
		} else if (p.getType() == SHACL.PROPERTY_SHAPE) {
			qualifiedValueShape = PropertyShape.getInstance(p, connection, cache, shaclSail);
		} else {
			throw new IllegalStateException("Unknown shape type for " + p.getId());
		}

	}

	public QualifiedMinCountConstraintComponent(QualifiedMinCountConstraintComponent constraintComponent) {
		super(constraintComponent.getId());
		this.qualifiedValueShape = (Shape) constraintComponent.qualifiedValueShape.deepClone();
		this.qualifiedValueShapesDisjoint = constraintComponent.qualifiedValueShapesDisjoint;
		this.qualifiedMinCount = constraintComponent.qualifiedMinCount;
	}

	@Override
	public void toModel(Resource subject, IRI predicate, Model model, Set<Resource> cycleDetection) {
		model.add(subject, SHACL.QUALIFIED_VALUE_SHAPE, getId());

		if (qualifiedValueShapesDisjoint != null) {
			model.add(subject, SHACL.QUALIFIED_VALUE_SHAPES_DISJOINT, literal(qualifiedValueShapesDisjoint));
		}

		if (qualifiedMinCount != null) {
			model.add(subject, SHACL.QUALIFIED_MIN_COUNT, literal(BigInteger.valueOf(qualifiedMinCount)));
		}

		qualifiedValueShape.toModel(null, null, model, cycleDetection);

	}

	@Override
	public void setTargetChain(TargetChain targetChain) {
		super.setTargetChain(targetChain);
		qualifiedValueShape.setTargetChain(targetChain.setOptimizable(false));
	}

	@Override
	public SourceConstraintComponent getConstraintComponent() {
		return SourceConstraintComponent.QualifiedMinCountConstraintComponent;
	}

	@Override
	public ValidationQuery generateSparqlValidationQuery(ConnectionsGroup connectionsGroup, boolean logValidationPlans, boolean negatePlan, boolean negateChildren, Scope scope) {
		assert scope == Scope.propertyShape;
		throw new ShaclUnsupportedException();
	}

	@Override
	public PlanNode generateTransactionalValidationPlan(ConnectionsGroup connectionsGroup, boolean logValidationPlans, PlanNodeProvider overrideTargetNode, Scope scope) {
		assert scope == Scope.propertyShape;

		PlanNode target;

		if (overrideTargetNode != null) {
			target = getTargetChain().getEffectiveTarget("_target", scope, connectionsGroup.getRdfsSubClassOfReasoner()).extend(overrideTargetNode.getPlanNode(), connectionsGroup, scope, EffectiveTarget.Extend.right, false, null);
		} else {
			target = getAllTargetsPlan(connectionsGroup, scope);
		}

		PlanNode planNode = negated(connectionsGroup, logValidationPlans, overrideTargetNode, scope);

		planNode = new LeftOuterJoin(target, planNode);

		GroupByCountFilter groupByCountFilter = new GroupByCountFilter(planNode, count -> count < qualifiedMinCount);
		return Unique.getInstance(new TrimToTarget(groupByCountFilter), false);

	}

	public PlanNode negated(ConnectionsGroup connectionsGroup, boolean logValidationPlans, PlanNodeProvider overrideTargetNode, Scope scope) {

		StatementMatcher.StableRandomVariableProvider stableRandomVariableProvider = new StatementMatcher.StableRandomVariableProvider();

		PlanNodeProvider planNodeProvider = () -> {

			PlanNode target = getAllTargetsPlan(connectionsGroup, scope);

			if (overrideTargetNode != null) {
				PlanNode planNode = overrideTargetNode.getPlanNode();
				if (planNode instanceof AllTargetsPlanNode) {
					return planNode;
				}
				target = getTargetChain().getEffectiveTarget("_target", scope, connectionsGroup.getRdfsSubClassOfReasoner()).extend(planNode, connectionsGroup, scope, EffectiveTarget.Extend.right, false, null);
			}

			target = Unique.getInstance(new TrimToTarget(target), false);

			PlanNode relevantTargetsWithPath = new BulkedExternalLeftOuterJoin(target, connectionsGroup.getBaseConnection(), connectionsGroup.getBaseValueFactory(), getTargetChain().getPath().get().getTargetQueryFragment(new StatementMatcher.Variable("a"), new StatementMatcher.Variable("c"), connectionsGroup.getRdfsSubClassOfReasoner(), stableRandomVariableProvider), false, null, (b) -> new ValidationTuple(b.getValue("a"), b.getValue("c"), scope, true));

			return new TupleMapper(relevantTargetsWithPath, t -> {
				List<Value> targetChain = t.getTargetChain(true);
				return new ValidationTuple(targetChain, Scope.propertyShape, false);
			});

		};

		PlanNode planNode = qualifiedValueShape.generateTransactionalValidationPlan(connectionsGroup, logValidationPlans, planNodeProvider, scope);

		PlanNode invalid = Unique.getInstance(planNode, false);

		PlanNode allTargetsPlan = getAllTargetsPlan(connectionsGroup, scope);

		if (overrideTargetNode != null) {
			allTargetsPlan = getTargetChain().getEffectiveTarget("_target", scope, connectionsGroup.getRdfsSubClassOfReasoner()).extend(overrideTargetNode.getPlanNode(), connectionsGroup, scope, EffectiveTarget.Extend.right, false, null);
		}

		if (overrideTargetNode != null && overrideTargetNode.getPlanNode() instanceof AllTargetsPlanNode) {
			allTargetsPlan = new ShiftToPropertyShape(getTargetChain().getEffectiveTarget("_target", Scope.nodeShape, connectionsGroup.getRdfsSubClassOfReasoner()).getAllTargets(connectionsGroup, Scope.nodeShape));
		} else {
			allTargetsPlan = Unique.getInstance(new TrimToTarget(allTargetsPlan), false);
			allTargetsPlan = new BulkedExternalLeftOuterJoin(allTargetsPlan, connectionsGroup.getBaseConnection(), connectionsGroup.getBaseValueFactory(), getTargetChain().getPath().get().getTargetQueryFragment(new StatementMatcher.Variable("a"), new StatementMatcher.Variable("c"), connectionsGroup.getRdfsSubClassOfReasoner(), stableRandomVariableProvider), false, null, (b) -> new ValidationTuple(b.getValue("a"), b.getValue("c"), scope, true));
		}

		invalid = new NotValuesIn(allTargetsPlan, invalid);

		return invalid;

	}

	@Override
	public PlanNode getAllTargetsPlan(ConnectionsGroup connectionsGroup, Scope scope) {
		assert scope == Scope.propertyShape;

		PlanNode allTargets = getTargetChain().getEffectiveTarget("target_", Scope.propertyShape, connectionsGroup.getRdfsSubClassOfReasoner()).getPlanNode(connectionsGroup, Scope.propertyShape, true, null);

		PlanNode subTargets = qualifiedValueShape.getAllTargetsPlan(connectionsGroup, scope);

		return Unique.getInstance(new TrimToTarget(UnionNode.getInstanceDedupe(allTargets, subTargets)), false);

	}

	@Override
	public ConstraintComponent deepClone() {
		return new QualifiedMinCountConstraintComponent(this);
	}

	@Override
	public boolean requiresEvaluation(ConnectionsGroup connectionsGroup, Scope scope) {
		return true;
	}

}
