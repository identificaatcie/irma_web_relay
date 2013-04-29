package org.irmacard.cardproxywebrelay;

import java.io.IOException;
import java.security.SecureRandom;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.codec.binary.Base64;

import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;

/**
 * A servlet for implementing a simple (semi-)asynchronous message relay webservice.
 * Messages that come in here are eventually send out by the RelayRead servlet through
 * the MessageSender class.
 * 
 * @author Maarten Everts <maarten.everts@tno.nl>
 */
public class RelayWrite extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final int MAXMESSAGESIZE = 2048;
	private static final int CHANNELIDSIZE = 8; // size in bytes
	private static final String RELAYWRITESUBPATH = "/w"; // Note, if changed, also change web.xml!
	private static final String RELAYREADSUBPATH = "/r"; // Note, if changed, also change web.xml!
    private static SecureRandom random = null;
    
    static {
    	random = new SecureRandom();
    	// Make sure that the random number generator is ready and has enough entropy by 
    	// requesting a few bytes
    	byte bytes[] = new byte[20];
        random.nextBytes(bytes);
    }
    /**
     * @see HttpServlet#HttpServlet()
     */
    public RelayWrite() {
        super();
        // TODO Auto-generated constructor stub
    }
    
    public static String getBaseURL(HttpServletRequest request) {
    	String result = request.getScheme() + "://" + request.getServerName();
    	if (request.getServerPort() != 80) {
    		result += ":" + request.getServerPort();
    	}
    	result += request.getContextPath();
    	return result;
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		String [] pathParts = parsePath(request.getPathInfo());
		
		if (pathParts.length < 2) {
			response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		} else {
			String channelID = pathParts[0];
			String resource = pathParts[1];
			
			if (resource.equals("qr")) {
				String qrContent = getBaseURL(request) + RELAYREADSUBPATH + "/" + channelID;
				response.setContentType("image/png");
				QRCode.
		    		from(qrContent).
		    		to(ImageType.PNG).
		    		withSize(300, 300).
		    		writeTo(response.getOutputStream());
			} else {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			}
		}
	}

	
	private String[] parsePath(String pathInfo) {
		if (pathInfo == null) {
			return new String[0];
		}
		String[] result = pathInfo.substring(1).split("/");
		if (result.length == 1 && result[0].length() == 0) {
			return new String[0]; 
		}
		return result;
	}

	private String generateChannelID() {
		byte[] randomID = new byte[CHANNELIDSIZE];
		random.nextBytes(randomID);
		return Base64.encodeBase64URLSafeString(randomID);
	}
	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String [] pathParts = parsePath(request.getPathInfo());
		
		if (pathParts.length == 0) {
			// A create channel request
			String channelID = generateChannelID();
			ChannelCreationResponse creationResponse = new ChannelCreationResponse();
			creationResponse.read_url = getBaseURL(request) + RELAYREADSUBPATH + "/" + channelID;
			creationResponse.write_url = getBaseURL(request) + RELAYWRITESUBPATH + "/" + channelID;
			creationResponse.qr_url = getBaseURL(request) + RELAYWRITESUBPATH + "/" + channelID + "/qr";
			Gson gson = new GsonBuilder().create();
			response.setContentType("application/json");
			response.getWriter().print(gson.toJson(creationResponse));
		} else if (pathParts.length == 1) {
			// A post to a channel
			String channelID = pathParts[0];
			// Read the message
			int contentLength = request.getContentLength();
			if (contentLength > MAXMESSAGESIZE) {
				throw new ServletException("Message too big, max size: " + MAXMESSAGESIZE);
			}
			byte[] buffer = new byte[contentLength];
			int bytesRead = request.getInputStream().read(buffer);
			if (bytesRead > 0) {
				MessageSender.Send(channelID, buffer);
			}
		} else {
			// An unexpected URL, throw a 404
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	public class ChannelCreationResponse {
		String read_url;
		String write_url;
		String qr_url;
	}
}
