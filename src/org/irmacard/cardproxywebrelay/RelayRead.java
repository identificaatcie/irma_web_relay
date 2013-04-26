package org.irmacard.cardproxywebrelay;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.CometEvent;
import org.apache.catalina.CometProcessor;

/**
 * A servlet for sending messages to long-polling clients. Works in close collaboration
 * with the class MessageSender.
 * 
 * @author Maarten Everts <maarten.everts@tno.nl>
 */
public class RelayRead extends HttpServlet implements CometProcessor {
	private static final long serialVersionUID = 1L;
	
    /**
     * @see HttpServlet#HttpServlet()
     */
    public RelayRead() {
        super();
    }
    
    
    public void init() throws ServletException {

    }

    public void destroy() {
    	MessageSender.stopThread();
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
    		throws ServletException, IOException {
    	// This method does not do anything, only here to make sure the event()
    	// method is called
    	super.doGet(req, resp);
    }
    
	/**
     * @throws IOException 
	 * @see CometProcessor#event(CometEvent)
     */
    public void event(CometEvent event) throws IOException {
    	String path = event.getHttpServletRequest().getPathInfo();
    	String channelID = path.substring(1);
    	String method = event.getHttpServletRequest().getMethod();
    	log("Channel ID: " + channelID);
    	HttpServletRequest request = event.getHttpServletRequest();
        HttpServletResponse response = event.getHttpServletResponse();
        if (event.getEventType() == CometEvent.EventType.BEGIN) {
            log("Begin for session: " + request.getSession(true).getId());
            MessageSender.AddListener(channelID, response);

        } else if (event.getEventType() == CometEvent.EventType.ERROR) {
            log("Error for session: " + request.getSession(true).getId());
            MessageSender.RemoveListener(channelID);
            event.close();
        } else if (event.getEventType() == CometEvent.EventType.END) {
            log("End for session: " + request.getSession(true).getId());
            MessageSender.RemoveListener(channelID);
            event.close();
        } else if (event.getEventType() == CometEvent.EventType.READ) {
        	log("Reading!");
            InputStream is = request.getInputStream();
            byte[] buf = new byte[512];
            do {
                int n = is.read(buf); //can throw an IOException
                if (n > 0) {
                    log("Read " + n + " bytes: " + new String(buf, 0, n) 
                            + " for session: " + request.getSession(true).getId());
                } else if (n < 0) {
                    error(event, request, response);
                    return;
                }
            } while (is.available() > 0);
        }
    }

    
	private void error(CometEvent event, HttpServletRequest request,
			HttpServletResponse response) {
		// TODO Auto-generated method stub
		
	}

}
