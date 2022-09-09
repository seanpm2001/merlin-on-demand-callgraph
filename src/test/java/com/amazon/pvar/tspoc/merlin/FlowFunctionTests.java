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

package com.amazon.pvar.tspoc.merlin;

import com.amazon.pvar.tspoc.merlin.ir.NodeState;
import com.amazon.pvar.tspoc.merlin.ir.Register;
import com.amazon.pvar.tspoc.merlin.ir.Value;
import com.amazon.pvar.tspoc.merlin.ir.Variable;
import com.amazon.pvar.tspoc.merlin.solver.CallGraph;
import com.amazon.pvar.tspoc.merlin.solver.MerlinSolverFactory;
import com.amazon.pvar.tspoc.merlin.solver.flowfunctions.BackwardFlowFunctions;
import com.amazon.pvar.tspoc.merlin.solver.flowfunctions.ForwardFlowFunctions;
import dk.brics.tajs.flowgraph.FlowGraph;
import dk.brics.tajs.flowgraph.jsnodes.*;
import dk.brics.tajs.js2flowgraph.FlowGraphBuilder;
import org.junit.Ignore;
import org.junit.Test;
import sync.pds.solver.SyncPDSSolver;
import sync.pds.solver.nodes.CallPopNode;
import sync.pds.solver.nodes.PushNode;
import wpds.interfaces.State;

import java.util.Set;
import java.util.stream.Collectors;

public class FlowFunctionTests extends AbstractCallGraphTest{

    private void logTest(Node node, Value val, Set<State> nextStates, boolean isBackward) {
        String direction = isBackward ? "Backward" : "Forward";
        System.out.println(direction + " flow function test on " + node.getClass().getSimpleName() + ".\n" +
                "\tInput: " + node + ", " + val + "\n" +
                "\tOutput Set: " + nextStates + "\n");
    }

    @Test
    public void unaryOperatorBackward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/unaryOperator.js");
        BackwardFlowFunctions ff = new BackwardFlowFunctions(new CallGraph());
        UnaryOperatorNode uon = (UnaryOperatorNode) flowGraph.getMain().getBlocks().stream()
                .flatMap(basicBlock -> basicBlock.getNodes().stream())
                .filter(n -> n instanceof UnaryOperatorNode)
                .findFirst()
                .orElseThrow();

        // Check that flow is killed for the result register
        Register val = new Register(6, flowGraph.getMain());
        Set<State> nextStates = ff.computeNextStates(uon, val);
        assert nextStates.isEmpty();
        logTest(uon, val, nextStates, true);

        // Check that flow is propagated for other values
        Register val2 = new Register(7, flowGraph.getMain());
        Set<State> nextStates2 = ff.computeNextStates(uon, val2);
        sync.pds.solver.nodes.Node<NodeState, Value> target =
                new sync.pds.solver.nodes.Node<>(
                        new NodeState(
                                (Node) FlowGraphBuilder
                                        .makeNodePredecessorMap(flowGraph.getMain())
                                        .get(uon)
                                        .stream()
                                        .findFirst()
                                        .orElseThrow()
                        ),
                        val2
                );
        assert nextStates2.contains(target);
        logTest(uon, val2, nextStates2, true);
    }

    @Test
    public void unaryOperatorForward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/unaryOperator.js");
        ForwardFlowFunctions ff = new ForwardFlowFunctions(new CallGraph());
        UnaryOperatorNode uon = (UnaryOperatorNode) flowGraph.getMain().getBlocks().stream()
                .flatMap(basicBlock -> basicBlock.getNodes().stream())
                .filter(n -> n instanceof UnaryOperatorNode)
                .findFirst()
                .orElseThrow();

        // Check that flow is killed for the argument register
        Register val = new Register(7, flowGraph.getMain());
        Set<State> nextStates = ff.computeNextStates(uon, val);
        assert nextStates.isEmpty();
        logTest(uon, val, nextStates, false);

        // Check that flow is propagated for other values
        Register val2 = new Register(6, flowGraph.getMain());
        Set<State> nextStates2 = ff.computeNextStates(uon, val2);
        sync.pds.solver.nodes.Node<NodeState, Value> target =
                new sync.pds.solver.nodes.Node<>(
                        new NodeState(
                                (Node) ForwardFlowFunctions
                                        .invertMapping(FlowGraphBuilder.makeNodePredecessorMap(flowGraph.getMain()))
                                        .get(uon)
                                        .stream()
                                        .findFirst()
                                        .orElseThrow()
                        ),
                        val2
                );
        assert nextStates2.contains(target);
        logTest(uon, val, nextStates, false);
    }

    @Test
    public void binaryOperatorBackward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/binaryOperator.js");
        BackwardFlowFunctions ff = new BackwardFlowFunctions(new CallGraph());
        BinaryOperatorNode bon = (BinaryOperatorNode) flowGraph.getMain().getBlocks().stream()
                .flatMap(basicBlock -> basicBlock.getNodes().stream())
                .filter(n -> n instanceof BinaryOperatorNode)
                .findFirst()
                .orElseThrow();
        assert bon.getIndex() == 14;

        // Check that flow is killed for the result register
        Register val = new Register(7, flowGraph.getMain());
        Set<State> nextStates = ff.computeNextStates(bon, val);
        assert nextStates.isEmpty();
        logTest(bon, val, nextStates, true);

        // Check that flow is propagated for other values
        Register val2 = new Register(8, flowGraph.getMain());
        Set<State> nextStates2 = ff.computeNextStates(bon, val2);
        sync.pds.solver.nodes.Node<NodeState, Value> target =
                new sync.pds.solver.nodes.Node<>(
                        new NodeState(
                                (Node) FlowGraphBuilder
                                        .makeNodePredecessorMap(flowGraph.getMain())
                                        .get(bon)
                                        .stream()
                                        .findFirst()
                                        .orElseThrow()
                        ),
                        val2
                );
        assert nextStates2.contains(target);
        logTest(bon, val2, nextStates2, true);
    }

    @Test
    public void binaryOperatorForward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/binaryOperator.js");
        ForwardFlowFunctions ff = new ForwardFlowFunctions(new CallGraph());
        BinaryOperatorNode bon = (BinaryOperatorNode) flowGraph.getMain().getBlocks().stream()
                .flatMap(basicBlock -> basicBlock.getNodes().stream())
                .filter(n -> n instanceof BinaryOperatorNode)
                .findFirst()
                .orElseThrow();
        assert bon.getIndex() == 14;

        // Check that flow is killed for the argument registers
        Register val = new Register(8, flowGraph.getMain());
        Set<State> nextStates = ff.computeNextStates(bon, val);
        assert nextStates.isEmpty();
        logTest(bon, val, nextStates, false);

        Register val2 = new Register(9, flowGraph.getMain());
        Set<State> nextStates2 = ff.computeNextStates(bon, val2);
        assert nextStates2.isEmpty();
        logTest(bon, val2, nextStates2, false);

        // Check that flow is propagated for other values
        Register val3 = new Register(7, flowGraph.getMain());
        Set<State> nextStates3 = ff.computeNextStates(bon, val3);
        sync.pds.solver.nodes.Node<NodeState, Value> target =
                new sync.pds.solver.nodes.Node<>(
                        new NodeState(
                                (Node) ForwardFlowFunctions
                                        .invertMapping(FlowGraphBuilder.makeNodePredecessorMap(flowGraph.getMain()))
                                        .get(bon)
                                        .stream()
                                        .findFirst()
                                        .orElseThrow()
                        ),
                        val3
                );
        assert nextStates3.contains(target);
        logTest(bon, val3, nextStates3, false);
    }

    @Test
    public void constantNodeBackward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/binaryOperator.js");
        BackwardFlowFunctions ff = new BackwardFlowFunctions(new CallGraph());
        ConstantNode cn = (ConstantNode) flowGraph.getMain().getBlocks().stream()
                .flatMap(basicBlock -> basicBlock.getNodes().stream())
                .filter(n -> n instanceof ConstantNode && ((ConstantNode) n).getType() == ConstantNode.Type.NUMBER)
                .findFirst()
                .orElseThrow();

        // Check that flow is killed for the constant register
        Register val = new Register(5, flowGraph.getMain());
        Set<State> nextStates = ff.computeNextStates(cn, val);
        assert nextStates.isEmpty();
        logTest(cn, val, nextStates, true);
    }

    @Test
    public void constantNodeForward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/binaryOperator.js");
        ForwardFlowFunctions ff = new ForwardFlowFunctions(new CallGraph());
        ConstantNode cn = (ConstantNode) flowGraph.getMain().getBlocks().stream()
                .flatMap(basicBlock -> basicBlock.getNodes().stream())
                .filter(n -> n instanceof ConstantNode && ((ConstantNode) n).getType() == ConstantNode.Type.NUMBER)
                .findFirst()
                .orElseThrow();

        // Check that flow is propagated for the constant register
        Register val = new Register(5, flowGraph.getMain());
        Set<State> nextStates = ff.computeNextStates(cn, val);
        sync.pds.solver.nodes.Node<NodeState, Value> target =
                new sync.pds.solver.nodes.Node<>(
                        new NodeState(
                                (Node) ForwardFlowFunctions
                                        .invertMapping(FlowGraphBuilder.makeNodePredecessorMap(flowGraph.getMain()))
                                        .get(cn)
                                        .stream()
                                        .findFirst()
                                        .orElseThrow()
                        ),
                        val
                );
        assert nextStates.contains(target);
        logTest(cn, val, nextStates, false);
    }

    @Test
    public void variableReadBackward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/binaryOperator.js");
        BackwardFlowFunctions ff = new BackwardFlowFunctions(new CallGraph());
        ReadVariableNode rvn = (ReadVariableNode) flowGraph.getMain().getBlocks().stream()
                .flatMap(basicBlock -> basicBlock.getNodes().stream())
                .filter(n -> n instanceof ReadVariableNode)
                .findFirst()
                .orElseThrow();

        // Check that flow is killed at the register being read into
        Value val = new Register(8, rvn.getBlock().getFunction());
        Set<State> nextStates = ff.computeNextStates(rvn, val);
        assert nextStates.stream()
                        .filter(s -> {
                            if (s instanceof sync.pds.solver.nodes.Node n) {
                                return n.fact().equals(val);
                            }
                            return false;
                        })
                        .collect(Collectors.toSet())
                        .isEmpty();

        // Check that flow is propagated from the result register to the variable being read
        Register val2 = new Register(8, flowGraph.getMain());
        Value val3 = new Variable("x", flowGraph.getMain());
        Set<State> nextStates2 = ff.computeNextStates(rvn, val2);
        sync.pds.solver.nodes.Node<NodeState, Value> target =
                new sync.pds.solver.nodes.Node<>(
                        new NodeState(
                                (Node) FlowGraphBuilder
                                        .makeNodePredecessorMap(flowGraph.getMain())
                                        .get(rvn)
                                        .stream()
                                        .findFirst()
                                        .orElseThrow()
                        ),
                        val3
                );
        assert nextStates2.contains(target);
        logTest(rvn, val2, nextStates2, true);
    }

    @Test
    public void variableReadForward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/binaryOperator.js");
        ForwardFlowFunctions ff = new ForwardFlowFunctions(new CallGraph());
        ReadVariableNode rvn = (ReadVariableNode) flowGraph.getMain().getBlocks().stream()
                .flatMap(basicBlock -> basicBlock.getNodes().stream())
                .filter(n -> n instanceof ReadVariableNode)
                .findFirst()
                .orElseThrow();

        // Check that flow is killed at the result register
        Register val = new Register(8, flowGraph.getMain());
        Set<State> nextStates = ff.computeNextStates(rvn, val);
        assert nextStates.isEmpty();
        logTest(rvn, val, nextStates, false);

        // Check that flow is propagated from the read value to the result register
        Variable val2 = new Variable("x", flowGraph.getMain());
        Set<State> nextStates2 = ff.computeNextStates(rvn, val2);
        sync.pds.solver.nodes.Node<NodeState, Value> target =
                new sync.pds.solver.nodes.Node<>(
                        new NodeState(
                                (Node) ForwardFlowFunctions
                                        .invertMapping(FlowGraphBuilder.makeNodePredecessorMap(flowGraph.getMain()))
                                        .get(rvn)
                                        .stream()
                                        .findFirst()
                                        .orElseThrow()
                        ),
                        val
                );
        assert nextStates2.contains(target);
        logTest(rvn, val2, nextStates2, false);
    }

    @Test
    public void variableWriteBackward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/binaryOperator.js");
        BackwardFlowFunctions ff = new BackwardFlowFunctions(new CallGraph());
        WriteVariableNode wvn = (WriteVariableNode) flowGraph.getMain().getBlocks().stream()
                .flatMap(basicBlock -> basicBlock.getNodes().stream())
                .filter(n -> n instanceof WriteVariableNode)
                .findFirst()
                .orElseThrow();

        // Check that flow is killed at the variable written to
        Value val = new Variable("x", flowGraph.getMain());
        Set<State> nextStates = ff.computeNextStates(wvn, val);
        assert nextStates.stream()
                .filter(s -> {
                    if (s instanceof sync.pds.solver.nodes.Node n) {
                        return n.fact().equals(val);
                    }
                    return false;
                })
                .collect(Collectors.toSet())
                .isEmpty();

        // Check that flow is propagated from the written variable to the arg register
        Variable val2 = new Variable("x", flowGraph.getMain());
        Value val3 = new Register(5, wvn.getBlock().getFunction());
        Set<State> nextStates2 = ff.computeNextStates(wvn, val2);
        sync.pds.solver.nodes.Node<NodeState, Value> target =
                new sync.pds.solver.nodes.Node<>(
                        new NodeState(
                                (Node) FlowGraphBuilder
                                        .makeNodePredecessorMap(flowGraph.getMain())
                                        .get(wvn)
                                        .stream()
                                        .findFirst()
                                        .orElseThrow()
                        ),
                        val3
                );
        assert nextStates2.contains(target);
        logTest(wvn, val2, nextStates2, true);
    }

    @Test
    public void variableWriteForward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/binaryOperator.js");
        ForwardFlowFunctions ff = new ForwardFlowFunctions(new CallGraph());
        WriteVariableNode wvn = (WriteVariableNode) flowGraph.getMain().getBlocks().stream()
                .flatMap(basicBlock -> basicBlock.getNodes().stream())
                .filter(n -> n instanceof WriteVariableNode)
                .findFirst()
                .orElseThrow();

        // Check that flow is killed at the written variable
        Value val = new Variable("x", flowGraph.getMain());
        Set<State> nextStates = ff.computeNextStates(wvn, val);
        assert nextStates.isEmpty();
        logTest(wvn, val, nextStates, false);

        // Check that flow is propagated from the read value to the result register
        Value val2 = new Register(5, flowGraph.getMain());
        Set<State> nextStates2 = ff.computeNextStates(wvn, val2);
        sync.pds.solver.nodes.Node<NodeState, Value> target =
                new sync.pds.solver.nodes.Node<>(
                        new NodeState(
                                (Node) ForwardFlowFunctions
                                        .invertMapping(FlowGraphBuilder.makeNodePredecessorMap(flowGraph.getMain()))
                                        .get(wvn)
                                        .stream()
                                        .findFirst()
                                        .orElseThrow()
                        ),
                        val
                );
        assert nextStates2.contains(target);
        logTest(wvn, val2, nextStates2, false);
    }

    @Test
    public void testUnsupportedBackward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/unsupported.js");
        BackwardFlowFunctions ff = new BackwardFlowFunctions(new CallGraph());
        BeginWithNode bwn = (BeginWithNode) flowGraph.getMain().getBlocks().stream()
                .flatMap(basicBlock -> basicBlock.getNodes().stream())
                .filter(n -> n instanceof BeginWithNode)
                .findFirst()
                .orElseThrow();

        Value val = new Register(8, flowGraph.getMain());
        Set<State> nextStates = ff.computeNextStates(bwn, val);
        logTest(bwn, val, nextStates, true);
    }

    @Test
    public void testUnsupportedForward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/unsupported.js");
        BackwardFlowFunctions ff = new BackwardFlowFunctions(new CallGraph());
        BeginWithNode bwn = (BeginWithNode) flowGraph.getMain().getBlocks().stream()
                .flatMap(basicBlock -> basicBlock.getNodes().stream())
                .filter(n -> n instanceof BeginWithNode)
                .findFirst()
                .orElseThrow();

        Value val = new Register(8, flowGraph.getMain());
        Set<State> nextStates = ff.computeNextStates(bwn, val);
        logTest(bwn, val, nextStates, false);
    }

    @Test
    public void ifBackward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/ifStmt.js");
        BackwardFlowFunctions ff = new BackwardFlowFunctions(new CallGraph());
        IfNode in = (IfNode) flowGraph.getMain().getBlocks().stream()
                .flatMap(basicBlock -> basicBlock.getNodes().stream())
                .filter(n -> n instanceof IfNode)
                .findFirst()
                .orElseThrow();


        Value val = new Register(8, flowGraph.getMain());
        Set<State> nextStates = ff.computeNextStates(in, val);
        sync.pds.solver.nodes.Node<NodeState, Value> target =
                new sync.pds.solver.nodes.Node<>(
                        new NodeState(
                                (Node) FlowGraphBuilder
                                        .makeNodePredecessorMap(flowGraph.getMain())
                                        .get(in)
                                        .stream()
                                        .findFirst()
                                        .orElseThrow()
                        ),
                        val
                );

        assert nextStates.contains(target);
        logTest(in, val, nextStates, true);
    }

    @Test
    public void ifForward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/ifStmt.js");
        ForwardFlowFunctions ff = new ForwardFlowFunctions(new CallGraph());
        IfNode in = (IfNode) flowGraph.getMain().getBlocks().stream()
                .flatMap(basicBlock -> basicBlock.getNodes().stream())
                .filter(n -> n instanceof IfNode)
                .findFirst()
                .orElseThrow();

        Value val = new Register(8, flowGraph.getMain());
        Set<State> nextStates = ff.computeNextStates(in, val);
        Set<sync.pds.solver.nodes.Node<NodeState, Value>> targets =
                ForwardFlowFunctions
                        .invertMapping(FlowGraphBuilder.makeNodePredecessorMap(flowGraph.getMain()))
                        .get(in)
                        .stream()
                        .map(abstractNode ->
                            new sync.pds.solver.nodes.Node<>(
                                    new NodeState((Node) abstractNode),
                                    val
                            )
                        )
                        .collect(Collectors.toSet());

        targets.forEach(target -> {
            assert nextStates.contains(target);
        });

        logTest(in, val, nextStates, false);
    }

    @Test
    public void declareFunctionNodeBackward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/declareFunction.js");
        BackwardFlowFunctions ff = new BackwardFlowFunctions(new CallGraph());
        DeclareFunctionNode dfn = (DeclareFunctionNode) getNodeByIndex(6, flowGraph);

        // Check that flow is killed for the function register
        Register val = new Register(5, dfn.getBlock().getFunction());
        Set<State> nextStates = ff.computeNextStates(dfn, val);
        assert nextStates.isEmpty();
        logTest(dfn, val, nextStates, true);
    }

    @Test
    public void declareFunctionNodeForward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/declareFunction.js");
        ForwardFlowFunctions ff = new ForwardFlowFunctions(new CallGraph());
        DeclareFunctionNode dfn = (DeclareFunctionNode) getNodeByIndex(6, flowGraph);

        // Check that flow is propagated for the function register
        Register val = new Register(5, dfn.getBlock().getFunction());
        Set<State> nextStates = ff.computeNextStates(dfn, val);
        sync.pds.solver.nodes.Node<NodeState, Value> target =
                new sync.pds.solver.nodes.Node<>(
                        new NodeState(getNodeByIndex(7, flowGraph)),
                        val
                );
        assert nextStates.contains(target);
        logTest(dfn, val, nextStates, false);
    }

    @Test
    public void newObjectNodeBackward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/newObject.js");
        BackwardFlowFunctions ff = new BackwardFlowFunctions(new CallGraph());
        NewObjectNode non = (NewObjectNode) getNodeByIndex(6, flowGraph);

        // Check that flow is killed for the object register
        Register val = new Register(5, non.getBlock().getFunction());
        Set<State> nextStates = ff.computeNextStates(non, val);
        assert nextStates.isEmpty();
        logTest(non, val, nextStates, true);
    }

    @Test
    public void newObjectNodeForward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/newObject.js");
        ForwardFlowFunctions ff = new ForwardFlowFunctions(new CallGraph());
        NewObjectNode non = (NewObjectNode) getNodeByIndex(6, flowGraph);

        // Check that flow is propagated for the function register
        Register val = new Register(5, non.getBlock().getFunction());
        Set<State> nextStates = ff.computeNextStates(non, val);
        sync.pds.solver.nodes.Node<NodeState, Value> target =
                new sync.pds.solver.nodes.Node<>(
                        new NodeState(getNodeByIndex(7, flowGraph)),
                        val
                );
        assert nextStates.contains(target);
        logTest(non, val, nextStates, false);
    }

    @Test
    @Ignore
    public void callNodeForward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/functionCall.js");
        ForwardFlowFunctions ff = new ForwardFlowFunctions(new CallGraph());
        CallNode cn = (CallNode) getNodeByIndex(18, flowGraph);

        // Check that flow is propagated from argument to parameter in the called function
        Value valArg = new Register(11, cn.getBlock().getFunction());
        Set<State> nextStates = ff.computeNextStates(cn, valArg);
        Node funcEntryNode = getNodeByIndex(22, flowGraph);
        Value valParam = new Variable("x", funcEntryNode.getBlock().getFunction());
        sync.pds.solver.nodes.Node<NodeState, Value> targetParam =
                new PushNode<>(
                        new NodeState(funcEntryNode),
                        valParam,
                        new NodeState(cn),
                        SyncPDSSolver.PDSSystem.CALLS
                );
        assert nextStates.contains(targetParam);
        logTest(cn, valArg, nextStates, false);

        // Check that flow is also propagated from environment to called function
//        TODO: Commenting this out for now until closures are handled properly
//        Value valEnv1 = new Variable("otherObj");
//        Set<State> nextStatesEnv1 = ff.computeNextStates(cn, valEnv1);
//        sync.pds.solver.nodes.Node<NodeState, Value> targetEnv1 =
//                new PushNode<>(
//                        new NodeState(getNodeByIndex(22, flowGraph)),
//                        valEnv1,
//                        new NodeState(cn),
//                        SyncPDSSolver.PDSSystem.CALLS
//                );
//        assert nextStatesEnv1.contains(targetEnv1);
//        logTest(cn, valEnv1, nextStatesEnv1, false);

        // Check that flow is NOT propagated from environment when names are reused in the target function scope
        Value valEnv2 = new Variable("x", flowGraph.getMain());
        Set<State> nextStatesEnv2 = ff.computeNextStates(cn, valEnv2);
        sync.pds.solver.nodes.Node<NodeState, Value> targetEnv2 =
                new sync.pds.solver.nodes.Node<>(
                        new NodeState(funcEntryNode),
                        valEnv2
                );
        assert !nextStatesEnv2.contains(targetEnv2);
        logTest(cn, valEnv2, nextStatesEnv2, false);



    }

    @Test
    @Ignore
    public void callNodeBackward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/functionCall.js");
        BackwardFlowFunctions ff = new BackwardFlowFunctions(new CallGraph());
        CallNode cn = (CallNode) getNodeByIndex(18, flowGraph);
        // Check that flow is propagated from return value to return statement in the invoked function
        Value val = new Register(8, cn.getBlock().getFunction());
        Set<State> nextStates = ff.computeNextStates(cn, val);

        Node returnNode = getNodeByIndex(24, flowGraph);
        Value valReturn = new Register(1, returnNode.getBlock().getFunction());
        PushNode<NodeState, Value, NodeState> targetReturn = new PushNode<>(
                new NodeState(returnNode),
                valReturn,
                new NodeState(cn),
                SyncPDSSolver.PDSSystem.CALLS
        );

        assert nextStates.contains(targetReturn);

        logTest(cn, val, nextStates, true);
    }

    @Test
    @Ignore
    public void returnNodeForward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/functionCall.js");
        CallGraph cg = new CallGraph();
        ForwardFlowFunctions ff = new ForwardFlowFunctions(cg);
        Node callNode = getNodeByIndex(18, flowGraph);
        ReturnNode rn = (ReturnNode) getNodeByIndex(24, flowGraph);

        cg.addEdge(((CallNode) callNode), rn.getBlock().getFunction());

        Value val = new Register(1, rn.getBlock().getFunction());
        Set<State> nextStates = ff.computeNextStates(rn, val);

        // Check that return values are propagated to return sites
        Value returnVal = new Register(8, callNode.getBlock().getFunction());
        CallPopNode<Value, NodeState> target =
                new CallPopNode<>(
                        returnVal,
                        SyncPDSSolver.PDSSystem.CALLS,
                        new NodeState(callNode)
                );

        assert nextStates.contains(target);
    }

    @Test
    public void returnNodeBackward() {
        FlowGraph flowGraph =
                initializeFlowgraph("src/test/resources/js/callgraph/flow-function-unit-tests/functionCall.js");
        BackwardFlowFunctions ff = new BackwardFlowFunctions(new CallGraph());
        ReturnNode rn = (ReturnNode) getNodeByIndex(24, flowGraph);

        Value val = new Register(1, rn.getBlock().getFunction());
        Set<State> nextStates = ff.computeNextStates(rn, val);

        // Check that values are propagated across the return node
        Node nextNode = getNodeByIndex(23, flowGraph);
        sync.pds.solver.nodes.Node<NodeState, Value> target =
                new sync.pds.solver.nodes.Node<>(
                        new NodeState(nextNode),
                        val
                );

        assert nextStates.contains(target);
    }
}