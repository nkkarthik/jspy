package com.imaginea.jspy;

import java.util.List;

public class MethodEntryNode implements Node{

	@Override
	public NodeType getNodeType() {
		return NodeType.METHOD_ENTRY_NODE;
	}

	@Override
	public List<String> getProjectedFieldNames() {
		// TODO Auto-generated method stub
		return null;
	}

}
