package org.irmacard.cardproxywebrelay;

import javax.servlet.http.HttpServletRequest;

public class Utils {
    public static String getBaseURL(HttpServletRequest request) {
    	String baseURL = null;
    	
    	String fwdURL = request.getHeader("X-Forwarded-URL");
    	if (fwdURL != null) {
    		System.out.println("Behind proxy, accessed using URL: " + fwdURL + " (stripping " + request.getServletPath() + " for baseURL).");
   			baseURL = fwdURL.substring(0, fwdURL.indexOf(request.getServletPath()));
    	} else {
    		baseURL = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
    	}
    	
    	return baseURL;
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
