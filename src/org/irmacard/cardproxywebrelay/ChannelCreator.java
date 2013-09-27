package org.irmacard.cardproxywebrelay;

import java.io.IOException;
import java.security.SecureRandom;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;

import org.apache.commons.codec.binary.Base64;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * A servlet setting up a new channel that runs through the proxy. Please be 
 * aware that this servlet needs to have some knowledge of the url structure 
 * used by this application.
 * 
 * @author Wouter Lueks <lueks@cs.ru.nl>
 * @author Maarten Everts <maarten.everts@tno.nl>
 */
public class ChannelCreator extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private static final int CHANNELIDSIZE = 8; // size in bytes
	private static final String RELAYWRITESUBPATH = "/w"; // Note, if changed, also change web.xml!
	private static final String RELAYREADSUBPATH = "/r"; // Note, if changed, also change web.xml!
	private static final String RELAYCREATESUBPATH = "/create"; // Note, if changed, also change web.xml!
	
	private static SecureRandom random = null;
	
	static {
    	random = new SecureRandom();
    	// Make sure that the random number generator is ready and has enough entropy by 
    	// requesting a few bytes
    	byte bytes[] = new byte[20];
        random.nextBytes(bytes);
    }
	
	/**
	 * Handle requests of the form /create/qr/channel/side
	 * 
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String [] pathParts = Utils.parsePath(request.getPathInfo());
		
		if (pathParts.length < 3) {
			response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		} else {
			String resource = pathParts[0];
			String channelID = pathParts[1];
			String side = pathParts[2];
			
			if (resource.equals("qr")) {
				String qrContent = Utils.getBaseURL(request) + RELAYREADSUBPATH + "/" + channelID + "/" + side;
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
	
	/**
	 * Upon a post request create two channels, one from side A to side B and
	 * one the other way around. It returns a JSON object representing a
	 * ChannelCreateResponse. This object describes all the endpoints. Clearly
	 * the receiving side should forward the appropriate end-points to the other
	 * party.
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String [] pathParts = Utils.parsePath(request.getPathInfo());
		
		if (pathParts.length == 0) {
			// A create channel request
			String channelID = generateChannelID();
			String baseURL = Utils.getBaseURL(request);
			
			ChannelCreationResponse creationResponse = new ChannelCreationResponse();
			creationResponse.side_a_read_url = baseURL + RELAYREADSUBPATH + "/" + channelID + "/" + MessageSender.SIDE_A;
			creationResponse.side_a_write_url = baseURL + RELAYWRITESUBPATH + "/" + channelID + "/" + MessageSender.SIDE_A;
			creationResponse.side_b_read_url = baseURL + RELAYREADSUBPATH + "/" + channelID + "/" + MessageSender.SIDE_B;
			creationResponse.side_b_write_url = baseURL + RELAYWRITESUBPATH + "/" + channelID + "/" + MessageSender.SIDE_B;
			creationResponse.qr_url = baseURL + RELAYCREATESUBPATH + "/qr/" + channelID + "/" + MessageSender.SIDE_B;
			
			MessageSender.AddChannel(channelID);
			
			Gson gson = new GsonBuilder().create();
			response.setContentType("application/json");
			response.getWriter().print(gson.toJson(creationResponse));
		}  else {
			// An unexpected URL, throw a 404
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}
	
	private String generateChannelID() {
		byte[] randomID = new byte[CHANNELIDSIZE];
		random.nextBytes(randomID);
		return Base64.encodeBase64URLSafeString(randomID);
	}
	
	public class ChannelCreationResponse {
		String side_a_read_url;
		String side_a_write_url;
		String side_b_write_url;
		String side_b_read_url;
		String qr_url;
	}
}
