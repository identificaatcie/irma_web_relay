package org.irmacard.cardproxywebrelay;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet for implementing a simple (semi-)asynchronous message relay webservice.
 * Messages that come in here are eventually send out by the RelayRead servlet through
 * the MessageSender class.
 * 
 * @author Maarten Everts <maarten.everts@tno.nl>
 */
public class RelayWrite extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final int MAXMESSAGESIZE = 4094;
    
    /**
     * @see HttpServlet#HttpServlet()
     */
    public RelayWrite() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
	}

	/**
	 * Write requests are of the form /w/channelid/side
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String [] pathParts = Utils.parsePath(request.getPathInfo());
		
		if (pathParts.length == 2) {
			// A post to a channel
			String channelID = pathParts[0];
			String fromSide = pathParts[1];
			
			// Read the message
			int contentLength = request.getContentLength();
			if (contentLength > MAXMESSAGESIZE) {
				throw new ServletException("Message too big, max size: " + MAXMESSAGESIZE);
			}
			byte[] buffer = new byte[contentLength];
			int bytesRead;
			int offset = 0;
			while ((bytesRead = request.getInputStream().read(buffer, offset,
					contentLength - offset)) != -1) {
				offset += bytesRead;
			}
			if(offset != contentLength) {
				throw new ServletException("Reading all bytes failed: (read = "
						+ offset + ", total = " + contentLength + ")");
			}
			
			System.out.println("Message <<" + buffer + ">> on channel " + channelID + " from side " + fromSide);

			MessageSender.Send(channelID, fromSide, buffer);
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
