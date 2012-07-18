package com.imaginea.jspy;

import java.util.List;

public interface Node {
	NodeType getNodeType();
	List<String> getProjectedFieldNames();
}
