package util;

import java.io.Serializable;
import java.util.*;

public class AuthorizedPair implements Serializable{
	private final String name;
	private final String id;
	
	public AuthorizedPair(String n, String id) {
		this.name = n;
		this.id = id;
	}
	
	public String getName() {
		return name;
	}
	
	public String getId() {
		return id;
	}
}
