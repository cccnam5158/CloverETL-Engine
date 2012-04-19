/*
 * jETeL/CloverETL - Java based ETL application framework.
 * Copyright (c) Javlin, a.s. (info@cloveretl.com)
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jetel.graph;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.exception.ConfigurationProblem;
import org.jetel.exception.ConfigurationStatus;
import org.jetel.exception.ConfigurationStatus.Priority;
import org.jetel.exception.ConfigurationStatus.Severity;
import org.jetel.exception.GraphConfigurationException;
import org.jetel.exception.JetelException;


/**
 * A class that represents processing Phase of Transformation Graph
 *
 * @author      D.Pavlis
 * @since       July 23, 2003
 * @revision    $Revision$
 * @see         OtherClasses
 */

public class Phase extends GraphElement implements Comparable {

	// Attributes

	// Associations
	/**
	 * @since    April 2, 2002
	 */
	private Map<String, Node> nodes;
	private Map<String, Edge> edges; //edges with the source component in this phase
    private List <Node> leafNodes;

	// specifies the order of this phase within graph
	private int phaseNum;

	private Result result;
    private boolean isCheckPoint;

	protected TransformationGraph graph;

	static Log logger = LogFactory.getLog(Phase.class);

	// Operations

	/**
	 *Constructor for the TransformationGraph object
	 *
	 * @param  phaseNum  Description of the Parameter
	 * @since            April 2, 2002
	 */
	public Phase(int phaseNum) {
		super(Integer.toString(phaseNum));
		
		this.phaseNum = phaseNum;
		nodes = new LinkedHashMap<String, Node>();
		edges = new LinkedHashMap<String, Edge>();
        result=Result.N_A;
	}


	/**
	 *  Sets the Graph attribute of the Phase object
	 *
	 * @param  graph  The new Graph value
	 * @since         April 5, 2002
	 */
	@Override
	public void setGraph(TransformationGraph graph) {
		super.setGraph(graph);
		
		//sets related graph to all included nodes
		for(Node node : nodes.values()) {
			node.setGraph(graph);
		}
		
		//sets related graph to all included nodes
		for(Edge edge : edges.values()) {
			edge.setGraph(graph);
		}
	}

	/**
	 *  Gets the phaseNum attribute of the Phase object
	 *
	 * @return    The phaseNum value
	 */
	public int getPhaseNum() {
		return phaseNum;
	}


	/**
	 * An operation that starts execution of graph
	 *
	 * @param  out  OutputStream - if defined, info messages are printed there
	 * @return      True if all nodes successfully started, otherwise False
	 * @since       April 2, 2002
	 */

	/**
	 *  Description of the Method
	 *
	 * @param  out  OutputStream - if defined, info messages are printed thereDescription of Parameter
	 * @return      returns TRUE if succeeded or FALSE if some Node or Edge failed initialization
	 * @since       April 10, 2002
	 */
	@Override
	public void init() throws ComponentNotReadyException {
        if(isInitialized()) return;
		super.init();

        // list of leaf nodes -will be filled later
        leafNodes = new LinkedList<Node>();
        
		logger.info("[Clover] Initializing phase: " + phaseNum);

        //initialization of all edges
		logger.debug(" initializing edges: ");
        for (Edge edge : edges.values()) {
        	try {
        		edge.init();
        	} catch (ComponentNotReadyException e) {
				result = Result.ERROR;
        		throw new ComponentNotReadyException(this, "Edge " + edge.getId() + " initialization faild.", e);
        	}
        }
		logger.debug(" all edges initialized successfully... ");

		// iterate through all nodes and initialize them
		logger.debug(" initializing nodes: ");
		for(Node node : nodes.values()) {
			ClassLoader formerClassLoader = Thread.currentThread().getContextClassLoader();
			try {
                // is it a leaf node ?
                if (node.isLeaf() || node.isPhaseLeaf()) {
                    leafNodes.add(node);
                }
				node.init();
				logger.debug("\t" + node.getId() + " ...OK");
			} catch (ComponentNotReadyException ex) {
				node.setResultCode(Result.ERROR);
				result = Result.ERROR;
				throw new ComponentNotReadyException(node.getId() + " ...FAILED ! \nReason: " +  ex.getMessage(), ex);
			} catch (Exception ex) {
				node.setResultCode(Result.ERROR);
				result = Result.ERROR;
				throw new ComponentNotReadyException(node.getId() + " ...FATAL ERROR !\nReason: " +  ex.getMessage(), ex);
			} catch (Throwable ex) {
				node.setResultCode(Result.ERROR);
				result = Result.ERROR;
				throw new ComponentNotReadyException(node.getId() + " ...FATAL ERROR !\nReason: " +  ex.getMessage(), new JetelException(ex.getMessage(), ex));
			}
		}
        

		logger.info("[Clover] phase: " + phaseNum + " initialized successfully.");
		
        result = Result.READY;
		// initialized OK
	}

	/* (non-Javadoc)
	 * @see org.jetel.graph.GraphElement#preExecute()
	 */
	@Override
	public synchronized void preExecute() throws ComponentNotReadyException {
		super.preExecute();
		
        //pre-execute initialization of all edges
        for (Edge edge : edges.values()) {
        	try {
        		edge.preExecute();
        	} catch (ComponentNotReadyException e) {
				result = Result.ERROR;
        		throw new ComponentNotReadyException(this, "Edge " + edge.getId() + " initialization failed.", e);
        	}
        }

		//nodes are pre-executed at theirs own threads
		// iterate through all nodes and initialize them
//		logger.debug(" pre-execute initializing nodes: ");
//		for(Node node : nodes.values()) {
//			try {
//				node.preExecute();
//				logger.debug("\t" + node.getId() + " ...OK");
//			} catch (ComponentNotReadyException ex) {
//				node.setResultCode(Result.ERROR);
//				result = Result.ERROR;
//				throw new ComponentNotReadyException(node.getId() + " ...FAILED ! \nReason: " +  ex.getMessage(), ex);
//			} catch (Exception ex) {
//				node.setResultCode(Result.ERROR);
//				result = Result.ERROR;
//				throw new ComponentNotReadyException(node.getId() + " ...FATAL ERROR !\nReason: " +  ex.getMessage(), ex);
//			}
//		}
	}
	
	@Override
	@Deprecated
	public synchronized void reset() throws ComponentNotReadyException {
		super.reset();
		
		//phase reset
        result=Result.N_A;
        
		//reset all components
		for(Node node : nodes.values()) {
			node.reset();
		}
		
		//reset all edges
		for(Edge edge : edges.values()) {
			edge.reset();
		}

	}
	
	/* (non-Javadoc)
	 * @see org.jetel.graph.GraphElement#postExecute(org.jetel.graph.TransactionMethod)
	 */
	@Override
	public void postExecute() throws ComponentNotReadyException {
		super.postExecute();

		logger.info("[Clover] Post-execute phase finalization: " + phaseNum);

		Map<GraphElement, Exception> failedElements = new HashMap<GraphElement, Exception>();
		// post-execute finalization of all edges
		logger.debug(" post-execute edges finalizing: ");
		for (Edge edge : edges.values()) {
			try {
				edge.postExecute();
			} catch (ComponentNotReadyException e) {
				result = Result.ERROR;
				failedElements.put(edge, e);
				logger.error("Edge " + edge.getId() + " finalization failed.", e);
			}
		}
		logger.debug(" edges finalized " + (failedElements.size() != 0 ? "with errors... " : "successfully... "));

		// iterate through all nodes and finalize them
		logger.debug(" post-execute nodes finalizing: ");
		for (Node node : nodes.values()) {
			try {
				node.postExecute();
				logger.debug("\t" + node.getId() + " ...OK");
			} catch (ComponentNotReadyException ex) {
				node.setResultCode(Result.ERROR);
				result = Result.ERROR;
				failedElements.put(node, ex);
				logger.error(node.getId() + " ...FAILED ! \nReason: " + ex.getMessage(), ex);
			} catch (Exception ex) {
				node.setResultCode(Result.ERROR);
				result = Result.ERROR;
				failedElements.put(node, ex);
				logger.error(node.getId() + " ...FATAL ERROR !\nReason: " + ex.getMessage(), ex);
			}
		}
		if (failedElements.isEmpty()) {
			logger.info("[Clover] phase: " + phaseNum + " post-execute finalization successfully.");
		} else {
			StringBuffer sb = new StringBuffer();
			sb.append("[Clover] phase: ").append(phaseNum).append(" post-execute FAILED at following elements:\n");
			for (Entry<GraphElement, Exception> element : failedElements.entrySet()) {
				sb.append(element.getKey().getId()).append('\n').append(element.getValue());
			}
			throw new ComponentNotReadyException(sb.toString());
		}
	}
	
	/* (non-Javadoc)
	 * @see org.jetel.graph.GraphElement#commit()
	 */
	@Override
	public void commit() {
		super.commit();

        //commit of all edges
        for (Edge edge : edges.values()) {
        	edge.commit();
        }

		//commit of all nodes
		for (Node node : nodes.values()) {
			node.commit();
		}
	}

	/* (non-Javadoc)
	 * @see org.jetel.graph.GraphElement#rollback()
	 */
	@Override
	public void rollback() {
		super.rollback();
		
        //rollback of all edges
        for (Edge edge : edges.values()) {
        	edge.rollback();
        }

		//rollback of all nodes
		for (Node node : nodes.values()) {
			node.rollback();
		}
	}
	
	/**
	 *  Description of the Method
	 *
	 * @return    Description of the Return Value
	 */
	@Override
	public ConfigurationStatus checkConfig(ConfigurationStatus status) {
		super.checkConfig(status);

		//check nodes configuration
        for(Node node : nodes.values()) {
        	try {
        		node.checkConfig(status);
        	} catch (Exception e) {
        		ConfigurationProblem problem = new ConfigurationProblem("Unexpected error: " + e.getMessage(), Severity.ERROR, node, Priority.HIGH);
        		problem.setCauseException(e);
        		status.add(problem);
        	}
        }

        //check edges configuration
        for(Edge edge : edges.values()) {
            edge.checkConfig(status);
        }

        return status;
	}

	/**
	 *  Adds node to the graph (through this Phase).<br>
     *  Node is registered to belong to this phase and also
     *  is globally registered within graph
	 *
	 * @param  node  The feature to be added to the Node attribute
     * @throws GraphConfigurationException in case node with the same ID has already
     * been registered withing graph
	 */
	public void addNode(Node node) throws GraphConfigurationException {
		nodes.put(node.getId(), node);
        node.setPhase(this);
        node.setGraph(getGraph());
	}

	/**
     * Bulk adding nodes into this phase
     * 
     * @param nodes
     * @throws GraphConfigurationException
     */
    public void addNode(Node ... nodes) throws GraphConfigurationException{
    	for(int i=0;i<nodes.length;i++){
    		addNode(nodes[i]);
    	}
    }
	
	/**
     * Bulk adding nodes into this phase
     * 
     * @param nodes
     * @throws GraphConfigurationException
     */
    public void addAllNodes(Collection<Node> nodes) throws GraphConfigurationException{
    	for(Node node : nodes) {
    		addNode(node);
    	}
    }

    /**
     *  Deletes node from the phase.
     * @param node the node to be removed from the phase
     */
    public void deleteNode(Node node) {
    	Node removedNode = nodes.remove(node.getId());
        if(removedNode != null) {
        	removedNode.setPhase(null);
        	removedNode.setGraph(null);
        }
    }

    //TODO this method should be removed; edges in a phase are determined by a set of nodes
	public void addEdge(Edge edge) throws GraphConfigurationException{
		Node writer = edge.getWriter();
		if(writer == null) {
			throw new GraphConfigurationException("Edge cannot be added into the phase without source component.");
		}
		if(writer.getPhase() != this) {
			throw new GraphConfigurationException("Edge cannot be added to this phase.");
		}

		edges.put(edge.getId(), edge);
		edge.setGraph(getGraph());
	}

	/**
     * Bulk adding edges into this phase
     * 
     * @param edges
     * @throws GraphConfigurationException
     */
    //TODO this method should be removed; edges in a phase are determined by a set of nodes
    public void addEdge(Edge ... edges) throws GraphConfigurationException{
    	for(int i=0;i<edges.length;i++){
    		addEdge(edges[i]);
    	}
    }
	
    /**
     *  Deletes edge from the phase.
     * @param edge the edge to be removed from the edge
     */
    public void deleteEdge(Edge edge) {
    	Edge removedEdge = edges.remove(edge.getId());
    	
    	if(removedEdge != null) {
    		removedEdge.setGraph(null);
    	}
    }

//	/**
//	 *  Adds edge to the graph (through this Phase).<br>
//     *  Edge is registered globally within Graph 
//	 *
//	 * @param  edge  The feature to be added to the Edge attribute
//     * @throws GraphConfigurationException in case node with the same ID has already
//     * been registered withing graph
//	 */
//	public void addEdge(Edge edge) throws GraphConfigurationException {
//        assignEdge(edge);
//		graph.addEdge(edge);
//	}

	/**
	 *  Removes all Nodes from Phase
	 *
	 * @since    April 2, 2002
     * @deprecated please use free method instead
	 */
    @Deprecated
	public void destroy() {
        free();
	}

    /**
     *  Removes all Nodes from Phase
     *
     * @since    April 2, 2002
     */
    @Override
	public void free() {
        
        //free all nodes in this phase
        for(Node node : nodes.values()) {
        	try {
        		node.free();
        	} catch (Exception e) {
        		logger.error("Node " + node.getId() + " releasing failed.", e);
        	}
        }
        
        //free all edges in this phase
        for(Edge edge : edges.values()) {
        	edge.free();
        }

    }
    
	/**
	 * Returns reference to Nodes contained in this phase.
	 *
	 * @return    The nodes value
	 * @since     July 29, 2002
	 */
	public Map<String, Node> getNodes() {
		return Collections.unmodifiableMap(nodes);
	}


	/**
	 *  Gets the edges attribute of the Phase object
	 *
	 * @return    The edges value
	 */
	public Map<String, Edge> getEdges() {
		return Collections.unmodifiableMap(edges);
	}


	/**
	 *  Description of the Method
	 *
	 * @param  to  Description of the Parameter
	 * @return     Description of the Return Value
	 */
	@Override
	public int compareTo(Object to) {
		int toPhaseNum = ((Phase) to).getPhaseNum();
		if (phaseNum > toPhaseNum) {
			return 1;
		} else if (phaseNum < toPhaseNum) {
			return -1;
		} else {
			return 0;
		}
	}
    
    @Override public int hashCode(){
        return phaseNum;
    }

    @Override public boolean equals(Object obj){
        if (obj instanceof Phase){
            return ((Phase)obj).getPhaseNum()==phaseNum;
        }else{
            return false;
        }
    }

    public Result getResult() {
        return result;
    }


    public void setResult(Result result) {
        this.result = result;
    }


    public boolean isCheckPoint() {
        return isCheckPoint;
    }


    public void setCheckPoint(boolean isCheckPoint) {
        this.isCheckPoint = isCheckPoint;
    }

    /**
     * @return the leafNodes
     * @since 10.1.2007
     */
    public List<Node> getLeafNodes() {
        return leafNodes;
    }

}
/*
 *  end class Phase
 */

