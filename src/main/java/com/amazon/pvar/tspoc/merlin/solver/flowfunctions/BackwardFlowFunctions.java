/*
 * Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.pvar.tspoc.merlin.solver.flowfunctions;

import com.amazon.pvar.tspoc.merlin.DebugUtils;
import com.amazon.pvar.tspoc.merlin.ir.Property;
import com.amazon.pvar.tspoc.merlin.ir.Register;
import com.amazon.pvar.tspoc.merlin.ir.Value;
import com.amazon.pvar.tspoc.merlin.ir.Variable;
import com.amazon.pvar.tspoc.merlin.livecollections.LiveCollection;
import com.amazon.pvar.tspoc.merlin.solver.MerlinSolver;
import com.amazon.pvar.tspoc.merlin.solver.QueryManager;
import dk.brics.tajs.flowgraph.Function;
import dk.brics.tajs.flowgraph.jsnodes.*;
import wpds.interfaces.State;

import java.util.*;

public class BackwardFlowFunctions extends AbstractFlowFunctions {

    public BackwardFlowFunctions(MerlinSolver containingSolver, QueryManager queryManager) {
        super(containingSolver, queryManager);
    }

    @Override
    public Collection<Node> nextNodes(Node n) {
        return getPredecessors(n);
    }

    /**
     * Kill flow of the computation result at binary operator nodes
     *
     * @param n
     */
    @Override
    public void visit(BinaryOperatorNode n) {
        Set<Value> killed = new HashSet<>();
        killed.add(usedRegisters.computeIfAbsent(n.getResultRegister(), id -> new Register(id, n.getBlock().getFunction())));
        usedRegisters.computeIfAbsent(n.getArg1Register(), id -> new Register(id, n.getBlock().getFunction()));
        usedRegisters.computeIfAbsent(n.getArg2Register(), id -> new Register(id, n.getBlock().getFunction()));
        killAt(n, killed);
    }

    /**
     * Update the register map with registers discovered at this node and propagate the value
     *
     * @param n
     */
    @Override
    public void visit(CallNode n) {
        java.util.function.Function<Integer, Register> newRegisterLambda =
                id -> new Register(id, n.getBlock().getFunction());
        usedRegisters.computeIfAbsent(n.getResultRegister(), newRegisterLambda);
        usedRegisters.computeIfAbsent(n.getFunctionRegister(), newRegisterLambda);
        usedRegisters.computeIfAbsent(n.getBaseRegister(), newRegisterLambda);
        int numArgs = n.getNumberOfArgs();
        for (int i = 0; i < numArgs; i++) {
            usedRegisters.computeIfAbsent(n.getArgRegister(i), newRegisterLambda);
        }

        // propagate across the call site
        Set<Value> killed = new HashSet<>();
        killed.add(usedRegisters.get(n.getResultRegister()));
        killAt(n, killed);

        // If this is a call to an internal TAJS function, don't try to resolve it
        if (Objects.nonNull(n.getTajsFunctionName())) {
            return;
        }

        // propagate the assigned value to the return value of possibly invoked functions, if necessary

        if (getQueryValue().equals(usedRegisters.get(n.getResultRegister()))) {
            final var targetFunctions = resolveFunctionCall(n);
            final var currentSPDSNode = currentPDSNode;
            if (containingSolver != null) {
                final var queryID = containingSolver.getQueryID(currentSPDSNode, false, false);
                continueWithSubqueryResult(targetFunctions, queryID, (targetFunction) -> {
                    DebugUtils.debug("Discovered new callee for " + n + ": " + targetFunction);
                    final var returnNode = ((Node) targetFunction.getOrdinaryExit().getLastNode());
                    final var returnReg = new Register(1, targetFunction);
                    final var nextState = callPushState(returnNode, returnReg, n);
                    DebugUtils.debug("Propagating to callee: " + nextState + " for node: " + currentSPDSNode);
                    containingSolver.propagate(currentSPDSNode, nextState);
                    DebugUtils.debug("Done propagating to callee");
                });
            }
            usedRegisters.clear();
        }
    }

    /**
     * TODO: Exceptional flow is not currently tracked
     * @param n
     */
    @Override
    public void visit(CatchNode n) {
        treatAsNop(n);
    }

    /**
     * Add the result register to the map of known registers and kill flow, unless the constant node denotes the start
     * of the current procedure. If we are at the start of the procedure, handle the interprocedural flow.
     *
     * @param n
     */
    @Override
    public void visit(ConstantNode n) {
        if (n.getResultRegister() == 1 && getQueryValue() instanceof Variable queryVar) {
            handleflowToFunctionEntry(n, queryVar);
        }

        Set<Value> killed = new HashSet<>();
        killed.add(usedRegisters.computeIfAbsent(n.getResultRegister(), id -> new Register(id, n.getBlock().getFunction())));
        killAt(n, killed);
        usedRegisters.remove(n.getResultRegister());
    }

    /**
     * TODO
     * @param n
     */
    @Override
    public void visit(DeletePropertyNode n) {
        treatAsNop(n);
    }

    /**
     * Merlin does not handle "with" statements, but does flag this unsoundness
     * @param n
     */
    @Override
    public void visit(BeginWithNode n) {
        usedRegisters.computeIfAbsent(n.getObjectRegister(), id -> new Register(id, n.getBlock().getFunction()));
        treatAsNop(n);
        logUnsoundness(n);
    }

    /**
     * TODO: Exceptional flow is not currently tracked
     * @param n
     */
    @Override
    public void visit(ExceptionalReturnNode n) {
        treatAsNop(n);
    }

    /**
     * If this is a top-level function declaration (i.e. we are in main and the function declaration does not
     * assign to a register), kill flow at the function variable.
     *
     * Otherwise, kill flow of the result register at the newly created function
     *
     * @param n
     */
    @Override
    public void visit(DeclareFunctionNode n) {
        Set<Value> killed = new HashSet<>();

        // If a function declaration does not assign to a register, the result register is -1
        if (n.getResultRegister() == -1) {
            Variable newVar = new Variable(
                    n.getFunction().getName(),
                    getDeclaringScope(n.getFunction().getName(), n.getBlock().getFunction())
            );
            declaredVariables.add(newVar);
            killed.add(newVar);
            killAt(n, killed);
            declaredVariables.remove(newVar);
        } else {
            killed.add(usedRegisters.computeIfAbsent(n.getResultRegister(), id -> new Register(id, n.getBlock().getFunction())));
            killAt(n, killed);
            usedRegisters.remove(n.getResultRegister());
        }
    }

    /**
     * TODO
     * @param n
     */
    @Override
    public void visit(BeginForInNode n) {
        treatAsNop(n);
    }

    /**
     * Add the condition register to the set of known registers and propagate all normal flows.  Note that branching
     * is handled by the predecessor relation.
     *
     * @param n
     */
    @Override
    public void visit(IfNode n) {
        usedRegisters.computeIfAbsent(n.getConditionRegister(), id -> new Register(id, n.getBlock().getFunction()));
        treatAsNop(n);
    }

    /**
     * Merlin does not handle "with" statements, but does flag this unsoundness
     * @param n
     */
    @Override
    public void visit(EndWithNode n) {
        treatAsNop(n);
        logUnsoundness(n);
    }

    /**
     * Kill flow of the result register at the newly created object
     *
     * @param n
     */
    @Override
    public void visit(NewObjectNode n) {
        Set<Value> killed = new HashSet<>();
        killed.add(usedRegisters.computeIfAbsent(n.getResultRegister(), id -> new Register(id, n.getBlock().getFunction())));
        killAt(n, killed);
        usedRegisters.remove(n.getResultRegister());
    }

    /**
     * TODO
     * @param n
     */
    @Override
    public void visit(NextPropertyNode n) {
        treatAsNop(n);
    }

    /**
     * TODO
     * @param n
     */
    @Override
    public void visit(HasNextPropertyNode n) {
        treatAsNop(n);
    }

    /**
     * Propagate dataflow through the nop.
     *
     * @param n
     */
    @Override
    public void visit(NopNode n) {
        treatAsNop(n);
    }

    /**
     * Kills flow for the assigned value, adds a property push rule for the value read from the property, and
     * propagates all other values
     * @param n
     */
    @Override
    public void visit(ReadPropertyNode n) {
        Register result = usedRegisters
                .compute(n.getResultRegister(), (id, r) -> new Register(id, n.getBlock().getFunction()));
        Variable base = getBaseForReadPropertyNode(n);
        usedRegisters.compute(n.getBaseRegister(), (id, r) -> new Register(id, n.getBlock().getFunction()));
        if (n.isPropertyFixed()) {
            // Property is a fixed String
            Property property = new Property(n.getPropertyString());
            Set<Value> killed = new HashSet<>();
            killed.add(result);
            killAt(n, killed);
            if (getQueryValue().equals(result)) {
                addPropPushState(n, base, property);
            }
        } else {
            // TODO: dispatch a backward query on the register used for the property read
            treatAsNop(n);
        }
    }

    /**
     * Propagate data from the result register to the read variable, killing the variable's previous flow
     *
     * @param n
     */
    @Override
    public void visit(ReadVariableNode n) {
        Set<Value> killed = new HashSet<>();
        Register resultReg = usedRegisters
                .computeIfAbsent(n.getResultRegister(), id -> new Register(id, n.getBlock().getFunction()));
        Register baseReg = usedRegisters
                .computeIfAbsent(n.getResultBaseRegister(), id -> new Register(id, n.getBlock().getFunction()));
        Variable read = new Variable(
                n.getVariableName(),
                getDeclaringScope(n.getVariableName(), n.getBlock().getFunction())
        );
        declaredVariables.add(read);
        killed.add(resultReg);
        killed.add(baseReg);
        killAt(n, killed);

        // Add register -> variable flow if necessary
        if (getQueryValue().equals(resultReg)) {
            genSingleNormalFlow(n, read);
        } else if (getQueryValue().equals(baseReg)) {
            genSingleNormalFlow(n, read);
        }
    }

    /**
     * The backward flow function across a return node is essentially the same as a nop node:
     * all known values in the scope are propagated across with normal flow.
     * @param n
     */
    @Override
    public void visit(ReturnNode n) {
        treatAsNop(n);
    }

    /**
     * TODO: Exceptional flow is not currently tracked
     * @param n
     */
    @Override
    public void visit(ThrowNode n) {
        treatAsNop(n);
    }

    /**
     * TODO
     * @param n
     */
    @Override
    public void visit(TypeofNode n) {
        treatAsNop(n);
    }

    /**
     * Kill flow of the computation result at unary operator nodes
     *
     * @param n
     */
    @Override
    public void visit(UnaryOperatorNode n) {
        Set<Value> killed = new HashSet<>();
        killed.add(usedRegisters.computeIfAbsent(n.getResultRegister(), id -> new Register(id, n.getBlock().getFunction())));
        usedRegisters.computeIfAbsent(n.getArgRegister(), id -> new Register(id, n.getBlock().getFunction()));
        killAt(n, killed);
    }

    /**
     * TODO
     * @param n
     */
    @Override
    public void visit(DeclareVariableNode n) {
        treatAsNop(n);
    }

    /**
     * Adds a property pop rule for the value written to the property, and propagates all other values
     * @param n
     */
    @Override
    public void visit(WritePropertyNode n) {
        Value val = getValForWritePropertyNode(n);
        usedRegisters.compute(n.getValueRegister(), (id, r) -> new Register(id, n.getBlock().getFunction()));
        Variable base = getBaseForWritePropertyNode(n);
        usedRegisters.compute(n.getBaseRegister(), (id, r) -> new Register(id, n.getBlock().getFunction()));
        if (n.isPropertyFixed()) {
            // Property is a fixed String
            Property property = new Property(n.getPropertyString());
            treatAsNop(n);

            if (getQueryValue().equals(base)) {
                addPropPopState(n, val, property);
            }
        } else {
            // TODO: dispatch a backward query on the register used for the property read
            treatAsNop(n);
        }
    }

    /**
     * Propagate data from the written variable to the argument register, killing the register's previous flow.
     *
     * @param n
     */
    @Override
    public void visit(WriteVariableNode n) {
        Set<Value> killed = new HashSet<>();
        Register argRegister = usedRegisters
                .computeIfAbsent(n.getValueRegister(), id -> new Register(id, n.getBlock().getFunction()));
        Variable write = new Variable(
                n.getVariableName(),
                getDeclaringScope(n.getVariableName(), n.getBlock().getFunction())
        );
        declaredVariables.add(write);
        killed.add(write);
        killAt(n, killed);

        // Add register -> variable flow if necessary
        if (getQueryValue().equals(write)) {
            genSingleNormalFlow(n, argRegister);
        }
    }

    /**
     * Ignore event dispatcher nodes and log potential unsoundness
     *
     * @param n
     */
    @Override
    public void visit(EventDispatcherNode n) {
        treatAsNop(n);
        logUnsoundness(n);
    }

    /**
     * TODO
     * @param n
     */
    @Override
    public void visit(EndForInNode n) {
        treatAsNop(n);
    }

    /**
     * No need to do anything special to handle begin loop nodes for backward analysis.
     * @param n
     */
    @Override
    public void visit(BeginLoopNode n) {
        treatAsNop(n);
    }

    /**
     * No need to do anything special to handle end loop nodes for backward analysis.
     * @param n
     */
    @Override
    public void visit(EndLoopNode n) {
        treatAsNop(n);
    }


    @Override
    protected synchronized void treatAsNop(Node n) {
        getPredecessors(n).forEach(this::addStandardNormalFlow);
    }

    /**
     * Propagate dataflow across Node n, killing flow at the specified values
     *  @param n
     * @param killed
     */
    @Override
    protected synchronized void killAt(Node n, Set<Value> killed) {
        getPredecessors(n)
                .forEach(predecessor -> addStandardNormalFlow(predecessor, killed));
    }

    protected synchronized void genSingleNormalFlow(Node n, Value v) {
        getPredecessors(n)
                .forEach(predecessor -> addSingleState(predecessor, v));
    }

    private synchronized void handleflowToFunctionEntry(ConstantNode entryNode, Variable queryVar) {
        DebugUtils.debug("Handling flow to entry of " + entryNode.getBlock().getFunction() + " looking backwards for " + queryVar);
        Function containingFunction = entryNode.getBlock().getFunction();
        if (containingFunction.isMain()) {
            return;
        }
        usedRegisters.clear();
        Optional<String> paramName = containingFunction.getParameterNames().stream()
                .filter(name -> name.equals(queryVar.getVarName()))
                .findFirst();
        // Launch a forward query on the containing function to find possible invocations
        LiveCollection<CallNode> liveInvokes = findInvocationsOfFunction(containingFunction);
        final var currentSPDSNode = currentPDSNode;
        if (paramName.isPresent()) {
            // continue the backward query from the argument passed to the invocation
            int paramIndex = containingFunction.getParameterNames().indexOf(paramName.get());
            if (containingSolver != null) {
                final var queryID = containingSolver.getQueryID(currentSPDSNode, true, false);
                continueWithSubqueryResult(liveInvokes, queryID, invoke -> {
                    DebugUtils.debug("handleflowToFunctionEntry[param]: found invocation of " + containingFunction +
                            ": " + invoke + " for query: " + queryVar);
                    try {
                        Register reg = new Register(invoke.getArgRegister(paramIndex), invoke.getBlock().getFunction());
                        State nextState = makeSPDSNode(invoke, reg);
                        DebugUtils.debug("Propagating pop state: " + nextState + " where initialQuery=" +
                                containingSolver.initialQuery + " and currentSPDSNode: " + currentSPDSNode);
                        containingSolver.propagate(currentSPDSNode, nextState);
                        DebugUtils.debug("Done propagating: " + nextState);
                    } catch (ArrayIndexOutOfBoundsException ignored) {
                    }
                });
            }
        } else {
            if (containingSolver != null) {
                final var queryID = containingSolver.getQueryID(currentSPDSNode, true, false);
                continueWithSubqueryResult(liveInvokes, queryID, invoke -> {
                    DebugUtils.debug("handleflowToFunctionEntry[non-param]: found invocation of " +
                            containingFunction + ": " + invoke + " for query: " + queryVar);
                    Function invokeScope = invoke.getBlock().getFunction();
                    if (!queryVar.isVisibleIn(invokeScope)) {
                        Node outerScopeReturnNode =
                                ((Node) containingFunction.getNode().getBlock().getFunction().getOrdinaryExit().getFirstNode());
                        final var state = makeSPDSNode(outerScopeReturnNode, queryVar);
                        containingSolver.propagate(currentSPDSNode, state);
                    } else {
                        final var popState = callPopState(invoke, queryVar);
                        containingSolver.propagate(currentSPDSNode, popState);
                    }
                });
            }
        }
    }

    private synchronized void addPropPushState(Node n, Value base, Property property) {
        getPredecessors(n).forEach(pred -> {
            addSinglePropPushState(pred, base, property);
        });
    }

    private synchronized void addPropPopState(Node n, Value val, Property property) {
        getPredecessors(n).forEach(pred -> {
            addSinglePropPopState(pred, val, property);
        });
    }

    private BackwardFlowFunctions(MerlinSolver containingSolver, QueryManager queryManager, FlowFunctionState savedState) {
        super(containingSolver, queryManager);
        this.usedRegisters = new HashMap<>(savedState.usedRegisters());
        this.declaredVariables = new HashSet<>(savedState.declaredVariables());
        this.queryValue = savedState.queryValue();
        this.currentPDSNode = savedState.pdsNode();
    }

    @Override
    protected BackwardFlowFunctions copyFromFlowFunctionState(FlowFunctionState state) {
        return new BackwardFlowFunctions(containingSolver, queryManager, state);
    }
}
