
import aima.core.probability.CategoricalDistribution;
import aima.core.probability.Factor;
import aima.core.probability.RandomVariable;
import aima.core.probability.bayes.BayesianNetwork;
import aima.core.probability.bayes.FiniteNode;
import aima.core.probability.bayes.Node;
import aima.core.probability.bayes.impl.BayesNet;
import aima.core.probability.bayes.impl.FullCPTNode;
import aima.core.probability.domain.BooleanDomain;
import aima.core.probability.example.BayesNetExampleFactory;
import aima.core.probability.proposition.AssignmentProposition;
import aima.core.probability.util.RandVar;
import bnparser.BifReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import utils.Graph;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author confo
 */
public class Pruning {

    public final String MINDEGREEORDER = "minDegreeOrder";
    public final String MINFILLORDER = "minFillOrder";
    
    public BayesianNetwork theorem1(BayesianNetwork bn,
            RandomVariable[] queryVars,
            AssignmentProposition[] assignmentPropositions) {
        List<RandomVariable> topologicalOrder = bn.getVariablesInTopologicalOrder();
        List<RandomVariable> relevantRVs = theorem1Help(bn, queryVars, assignmentPropositions);
        Collections.reverse(relevantRVs);
        return newNetFromRandomVars(bn, topologicalOrder, relevantRVs, assignmentPropositions);
    }
    
    /*
        creates a new Bayesian network where the relevant variables are newNetVars and the others are deleted.
        oldNetVars must be in topological order
    */
    private BayesNet newNetFromRandomVars(BayesianNetwork oldNet,
            List<RandomVariable> oldNetVars,
            List<RandomVariable> newNetVars,
            AssignmentProposition[] aps){
        List<FiniteNode> newNodes = new ArrayList<>();
        for (RandomVariable var : oldNetVars) {
            if (newNetVars.contains(var)) {
                Node node = oldNet.getNode(var);
                FiniteNode fn = (FiniteNode) node;
                Set<Node> parents = node.getParents();
                Set<Node> newParents = new HashSet<>();
                for (Node p : parents) {  // I need to set as parents the new nodes, not the ones of the old bn
                    for (Node np : newNodes) {
                        if (p.equals(np)) {
                            newParents.add(np);
                        }
                    }
                }
                double[] cptVal = getNewCPT(fn, newParents, aps);
                newNodes.add(new FullCPTNode(var, cptVal, newParents.toArray(new Node[newParents.size()])));
            }
        }
        List<Node> roots = new ArrayList<>();
        for (Node node : newNodes) {
            if (node.isRoot()) {
                roots.add(node);
            }
        }

        return new BayesNet(roots.toArray(new Node[roots.size()]));
    }

    /*
    iterate through the random variables in inverse topological order;
    delete the variables in the last level if they are not query or evidence;
    at each subsequent level keep the new query and evidence variables found and
    all of those which still have at least one child (ancestors of relevant variables)
    @returns a list of the relevant random variables in inverse topological order
     */
    private List<RandomVariable> theorem1Help(BayesianNetwork bn,
            RandomVariable[] queryVars,
            AssignmentProposition[] assignmentPropositions) {
        List<RandomVariable> topologicalOrder = bn.getVariablesInTopologicalOrder();
        List<RandomVariable> varList = new ArrayList<>(topologicalOrder);
        Collections.reverse(varList);
        RandomVariable[] evidenceVars = assignmentPropositionToRandomVariable(assignmentPropositions);
        for (Iterator<RandomVariable> iterator = varList.iterator(); iterator.hasNext();) {
            RandomVariable var = iterator.next();
            boolean keep = false;
            for (int i = 0; i < queryVars.length; i++) {
                if (var.getName().equals(queryVars[i].getName())) {
                    keep = true;
                }
            }
            for (int i = 0; i < evidenceVars.length; i++) {
                if (var.getName().equals(evidenceVars[i].getName())) {
                    keep = true;
                }
            }
            for (Node child : bn.getNode(var).getChildren()) {
                if (varList.contains(child.getRandomVariable())) {
                    keep = true;
                }
            }
            if (!keep) {
                iterator.remove();
            }
        }
        return varList;
    }
    
    public RandomVariable[] assignmentPropositionToRandomVariable(AssignmentProposition[] aps){
        RandomVariable[] rvs = new RandomVariable[aps.length];
        for(int i = 0; i < aps.length; ++i){
            rvs[i] = aps[i].getTermVariable();
        }
        return rvs;
    }
    
    public BayesianNetwork theorem2(BayesianNetwork bn,
            RandomVariable[] queryVars,
            AssignmentProposition[] assignmentPropositions) {
        List<RandomVariable> topologicalOrder = bn.getVariablesInTopologicalOrder();
        List<RandomVariable> topologicalOrderCopy = new ArrayList<>(topologicalOrder);
        Graph moralGraph = moralGraph(bn);
        RandomVariable[] evidenceVars = assignmentPropositionToRandomVariable(assignmentPropositions);
        topologicalOrderCopy.removeAll(Arrays.asList(queryVars));
        topologicalOrderCopy.removeAll(Arrays.asList(evidenceVars));
        List<RandomVariable> relevantVars = new ArrayList<>();
        relevantVars.addAll(Arrays.asList(queryVars));
        relevantVars.addAll(Arrays.asList(evidenceVars));
        for(RandomVariable rvar : topologicalOrderCopy){
            Node end = bn.getNode(rvar);
            for(int i = 0; i < queryVars.length; ++i){
                Node start = bn.getNode(queryVars[i]);
                if(!relevantVars.contains(rvar) && !isMSeparated(moralGraph, start, end, evidenceVars)){
                    relevantVars.add(rvar);
                }
            }
        }
        return newNetFromRandomVars(bn, topologicalOrder, relevantVars, assignmentPropositions);
    }

    public Graph moralGraph(BayesianNetwork bn) {
        Graph<Node> graph = new Graph<>();

        List<RandomVariable> variables = bn.getVariablesInTopologicalOrder();

        for (RandomVariable r : variables) {
            Node node = bn.getNode(r);
            graph.addVertex(node);
        }

        for (RandomVariable r : variables) {
            Node node = bn.getNode(r);
            Node[] parents = node.getParents().toArray(new Node[node.getParents().size()]);

            for (int i = 0; i < parents.length; i++) {
                graph.addEdge(parents[i], node, true);
                for (int j = i + 1; j < parents.length; j++) {
                    graph.addEdge(parents[i], parents[j], true);
                }
            }
        }
        return graph;
    }

    /*
    This method and its helper are not efficient but are easy to write and read
    */
    private boolean isMSeparated(Graph<Node> graph, Node start, Node end, RandomVariable[] eviVar) {
        return isMSeparatedHelp(graph.copy(), start, end, eviVar);
    }

    private boolean isMSeparatedHelp(Graph<Node> graph, Node start, Node end, RandomVariable[] eviVar) {
        boolean ret = true;
        for (RandomVariable r : eviVar) {
            String nameEvi = r.getName();
            String nameVar = start.getRandomVariable().getName();
            if (nameVar.contentEquals(nameEvi)) {
                return true;
            }
        }

        if (start.equals(end)) {
            return false;
        } else {
            Set<Node> neighbors = graph.neighborsDestructive(start);
            if(neighbors != null){
                for (Node n : neighbors) {
                    ret = ret && isMSeparatedHelp(graph, n, end, eviVar);
                }
            }
        }
        return ret;
    }
    
    public static double[] getNewCPT(FiniteNode node, Set<Node> parentsNewNode, AssignmentProposition[] ap){
        double[] newCPT = null;
        List<RandomVariable> toSumOut = new ArrayList<>();
        for(Node parent : node.getParents()){
            if(!parentsNewNode.contains(parent)){
                toSumOut.add(parent.getRandomVariable());
            }
        }
        Factor f = node.getCPT().getFactorFor();
        // normalize sums
        if(toSumOut.isEmpty()){
            newCPT = f.getValues();
        } else{
            newCPT = f.sumOut(toSumOut.toArray(new RandomVariable[toSumOut.size()])).getValues();
            // works for boolean domain

            for(int i = 0; i < newCPT.length; i = i+2){
                double num1 = newCPT[i] / (newCPT[i] + newCPT[i+1]);
                double num2 = newCPT[i+1] / (newCPT[i] + newCPT[i+1]);
                newCPT[i] = num1;
                newCPT[i+1] = num2;
            }
        }
        return newCPT;
    }
    
    /*
        implements minDegreeOrder and minFillOrder
        Which order is chosen through the String parameter (use the constants provided in this class)
    */
    public List<RandomVariable> order(BayesianNetwork bn, String order){
        if(!order.equals(MINDEGREEORDER) && !order.equals(MINFILLORDER)){
            System.out.println("order: wrong String parameter");
            return null;
        }
        List<RandomVariable> minDegreeOrder = new ArrayList<>();
        Graph<Node> interactionGraph = moralGraph(bn);
        Set<Node> nodeSet = interactionGraph.vertexSet();
        while(!nodeSet.isEmpty()){
            Node next = new FullCPTNode(new RandVar("ERROR", new BooleanDomain()), new double[] {0.5,0.5}); // make compiler happy;
            if(order.equals(MINDEGREEORDER)){
                next = minDegreeOrder(interactionGraph);
            } else if(order.equals(MINFILLORDER)){
                next = minFillOrder(interactionGraph);
            }
            minDegreeOrder.add(next.getRandomVariable());
            Set<Node> neighbors = interactionGraph.neighborsDestructive(next);
            Node[] neighborsArr = neighbors.toArray(new Node[neighbors.size()]);
            for(int i = 0; i < neighborsArr.length; ++i){
                for(int j = i+1; j < neighborsArr.length; ++j){
                    if(!interactionGraph.hasEdge(neighborsArr[i], neighborsArr[j])){
                        interactionGraph.addEdge(neighborsArr[i], neighborsArr[j], true);
                    }
                }
            }
            interactionGraph.removeVertex(next);
            nodeSet = interactionGraph.vertexSet();
        }
        return minDegreeOrder;
    }
    
    /*
        return the node with the smallest number of neighbors
    */
    private Node minDegreeOrder(Graph<Node> interactionGraph){
        Set<Node> nodeSet = interactionGraph.vertexSet();
        int min = (int) Math.pow(nodeSet.size(), 2)+ 1;
        Node next = null;
        for(Node n : nodeSet){
            int degree = interactionGraph.neighbors(n).size();
            if(degree < min){
                min = degree;
                next = n;
            }
        }
        return next;
    }
    
    /*
        return the node whose elimination will produce the least number of new
        edges in the interaction graph
    */
    private Node minFillOrder(Graph<Node> interactionGraph){
        Set<Node> nodeSet = interactionGraph.vertexSet();
        int min = (int) Math.pow(nodeSet.size(), 2);
        Node next = null;
        for(Node n : nodeSet){
            int curr = 0;
            Node[] neighbors = interactionGraph.neighbors(n).toArray(new Node[0]);
            for(int i = 0; i < neighbors.length; ++i){
                for(int j = i+1; j < neighbors.length; ++j){
                    if(!interactionGraph.hasEdge(neighbors[i], neighbors[j])){
                        ++curr;
                    }
                }
            }
            if(curr < min){
                min = curr;
                next = n;
            }
        }
        return next;
    }

    public static void main(String[] args) {
        Pruning p = new Pruning();
        BayesianNetwork bn = BifReader.readBIF("test/link.xbif");
        //BayesianNetwork bn = BayesNetExampleFactory.constructBurglaryAlarmNetwork();
        
        List<RandomVariable> varList = bn.getVariablesInTopologicalOrder();
        RandomVariable[] queryVars = new RandomVariable[1];
        AssignmentProposition[] ap = new AssignmentProposition[1];
        System.out.println(varList);
        for (RandomVariable rv : varList) {
            if (rv.getName().equals("D1_27_a_f")) {
                queryVars[0] = rv;
            }
            if (rv.getName().equals("D0_9_d_p")) {
                System.out.println(rv.getDomain());
                ap[0] = new AssignmentProposition(rv, "N");
            }
        }
        EliminationAskPlus ea = new EliminationAskPlus();
        List<String> orders = new ArrayList<>();
        orders.add("topological");
        orders.add(p.MINDEGREEORDER);
        orders.add(p.MINFILLORDER);
        CategoricalDistribution cd;
        long START;
        long END;
        long CREAT;
        for(String ord : orders){
//            START = System.nanoTime();
//            System.out.println("\nold bn, " + ord);
//            try{
//                cd = ea.eliminationAsk(queryVars, ap, bn, ord);
//                END = System.nanoTime();
//                System.out.println("Time taken : " + ((END - START) / 1e+9) + " seconds");
//            } catch(RuntimeException e){
//                System.out.println("Specified list deatailing order of mulitplier is inconsistent.");
//            } catch(OutOfMemoryError e){
//                System.out.println("Out of memory: java heap space");
//            }
//            START = System.nanoTime();
//            BayesianNetwork newBN = p.theorem1(bn, queryVars, ap);
//            newBN = p.theorem2(newBN, queryVars, ap);
//            //newBN = IrrelevantEdge.irrelevantEdgeGraph(newBN, ap);
//            CREAT = System.nanoTime();
//            System.out.println("\nnew bn, " + ord);
//            try{
//                cd = ea.eliminationAsk(queryVars, ap, newBN, ord);
//                END = System.nanoTime();
//                System.out.println("Time taken (w/ creation)  : " + ((END - START) / 1e+9) + " seconds");
//                System.out.println("Time taken (w/o creation) : " + ((END - CREAT) / 1e+9) + " seconds");
//            } catch(RuntimeException e){
//                System.out.println("Specified list deatailing order of mulitplier is inconsistent.");
//            } catch(OutOfMemoryError e){
//                System.out.println("Out of memory: java heap space");
//            }
            
            // theorem 1
            BayesianNetwork newBN = null;
            System.out.println("\n" + ord);
            try{
                START = System.nanoTime();
                newBN = p.theorem1(bn, queryVars, ap);
                cd = ea.eliminationAsk(queryVars, ap, newBN, ord);
                END = System.nanoTime();
                System.out.println("\nTime taken theorem 1 : " + ((END - START) / 1e+9) + " seconds");
            } catch(OutOfMemoryError e){
                System.out.println(e.getMessage());
            } 
            
            // theorem 2
            
            try{
                START = System.nanoTime();
                newBN = p.theorem2(bn, queryVars, ap);
                cd = ea.eliminationAsk(queryVars, ap, newBN, ord);
                END = System.nanoTime();
                System.out.println("\nTime taken theorem 2 : " + ((END - START) / 1e+9) + " seconds");
            } catch(OutOfMemoryError e){
                System.out.println(e.getMessage());
            } 
            
            // prune edges
            
            try{
                START = System.nanoTime();
                newBN = IrrelevantEdge.irrelevantEdgeGraph(bn, ap);
                cd = ea.eliminationAsk(queryVars, ap, newBN, ord);
                END = System.nanoTime();
                System.out.println("\nTime taken prune edges : " + ((END - START) / 1e+9) + " seconds");
            } catch(OutOfMemoryError | Exception e){
                System.out.println(e.getMessage());
            }
            
            // theorem 1 + 2
            
            try{
                START = System.nanoTime();
                newBN = p.theorem1(bn, queryVars, ap);
                newBN = p.theorem2(newBN, queryVars, ap);
                cd = ea.eliminationAsk(queryVars, ap, newBN, ord);
                END = System.nanoTime();
                System.out.println("\nTime taken theorem 1 + 2 : " + ((END - START) / 1e+9) + " seconds");
            } catch(OutOfMemoryError e){
                System.out.println(e.getMessage());
            } 
            
            // theorem 1 + edges
           
            try{
                 START = System.nanoTime();
                newBN = IrrelevantEdge.irrelevantEdgeGraph(bn, ap);
                newBN = p.theorem1(newBN, queryVars, ap);
                cd = ea.eliminationAsk(queryVars, ap, newBN, ord);
                END = System.nanoTime();
                System.out.println("\nTime taken theorem 1 + edges : " + ((END - START) / 1e+9) + " seconds");
            } catch(OutOfMemoryError | Exception e){
                System.out.println(e.getMessage());
            } 
            
            // theorem 2 + edges
            
            try{
                START = System.nanoTime();
                newBN = IrrelevantEdge.irrelevantEdgeGraph(bn, ap);
                newBN = p.theorem2(newBN, queryVars, ap);
                cd = ea.eliminationAsk(queryVars, ap, newBN, ord);
                END = System.nanoTime();
                System.out.println("\nTime taken theorem 2 + edges: " + ((END - START) / 1e+9) + " seconds");
            } catch(OutOfMemoryError | Exception e){
                System.out.println(e.getMessage());
            }
            
        }
    }
}
