package com.imaginea.jspy;

import java.util.List;

public class ArgumentNode implements Node{
	@Override
	public NodeType getNodeType() {
		return NodeType.ARGUMENT_NODE;
	}

	@Override
	public List<String> getProjectedFieldNames() {
		// TODO Auto-generated method stub
		return null;
	}
}
