package org.irmacard.cardproxywebrelay;

import javax.servlet.http.HttpServletRequest;

public class Utils {
    public static String getBaseURL(HttpServletRequest request) {
    	String result = request.getScheme() + "://" + request.getServerName();
    	if (request.getServerPort() != 80) {
    		result += ":" + request.getServerPort();
    	}
    	result += request.getContextPath();
    	return result;
    }
	
	public static String[] parsePath(String pathInfo) {
		if (pathInfo == null) {
			return new String[0];
		}
		String[] result = pathInfo.substring(1).split("/");
		if (result.length == 1 && result[0].length() == 0) {
			return new String[0]; 
		}
		return result;
	}
}
