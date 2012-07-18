package com.imaginea.jspy;

public enum NodeType {
	ARGUMENT_NODE("Argument", 1), METHOD_ENTRY_NODE("Entry", 2), METHOD_EXIT_NODE(
			"Exit", 3), THROW_EXCEPTION_NODE("Throw", 4), CATCH_EXCEPTION_NODE(
			"Catch", 5);

	private final String name;
	private final int index;

	private NodeType(String name, int index) {
		this.name = name;
		this.index = index;
	}

	public String getName() {
		return name;
	}

	public int getIndex() {
		return index;
	}
}
