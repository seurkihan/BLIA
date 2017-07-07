package edu.skku.selab.blp.utils;

import java.util.HashMap;
import java.util.Map;

public class ContractionsExpansor {
	
	static Map<String, String> con = null;
	public static String expand(String str) {
		if(con==null){
			con = new HashMap<String, String>();
			con.put("'s", " is");
		    con.put("'d", " would");
		    con.put("'re", " are");
		    con.put("'ll", " will");
		    con.put("n't", " not");
		    con.put("'nt", " not");
		}

	    for(String key : con.keySet()) {
	        str = str.replaceAll(key + "\\b" , con.get(key));
	    }
	    
	    return str;

	}
    
}
