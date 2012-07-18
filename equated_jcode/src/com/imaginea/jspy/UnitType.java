package com.imaginea.jspy;

public enum UnitType {
	CLASS("Class", 1), METHOD("Method", 2), FIELD("Field", 3), MULTI("Multi", 4), THREAD(
			"Thread", 5);

	//TODO create Multi data type
	private final String name;
	private final int index;

	private UnitType(String name, int index) {
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
