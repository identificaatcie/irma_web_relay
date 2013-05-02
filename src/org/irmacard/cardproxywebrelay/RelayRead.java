package org.irmacard.cardproxywebrelay;

import java.io.IOException;

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
        HttpServletResponse response = event.getHttpServletResponse();
        if (event.getEventType() == CometEvent.EventType.BEGIN) {
            MessageSender.AddListener(channelID, response);
        } else if (event.getEventType() == CometEvent.EventType.ERROR) {
        	MessageSender.RemoveListener(channelID, response);
            event.close();
        } else if (event.getEventType() == CometEvent.EventType.END) {
            MessageSender.RemoveListener(channelID, response);
            event.close();
        } else if (event.getEventType() == CometEvent.EventType.READ) {
        	// This event is not used at the moment
        }
    }
}
