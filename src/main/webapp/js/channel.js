var Channel = function() {
	var ChannelProto = function() {
		this.listen = function(dataReceivedCallback) {
			var thisReadURL = this.read_url;
			var that = this;
			that.waitForMessage = function() {};
			that.waitForMessage = function() {
				$.ajax({
					type : "GET",
					url : thisReadURL,
					dataType: "text",
					async : true, /* If set to non-async, browser shows page as "Loading.."*/
					cache : false,
					timeout : 50000, /* Timeout in ms */

					success : function(data) {
						console.log("Received data from", thisReadURL);
						console.log("data: ", data);
						if (data !== "") {
							dataReceivedCallback(data);
						}
						setTimeout(that.waitForMessage, 200); // Wait for 200ms and request next message
					},
					error : function(XMLHttpRequest, textStatus, errorThrown) {
						console.log("onReceive error: ",textStatus, errorThrown);
						setTimeout(that.waitForMessage, /* Try again after.. */
						15000); /* milliseconds (15seconds) */
					}
				});
			};
			that.waitForMessage();
		};
		this.send = function(sendData) {
			that = this;
			$.ajax({
				url : that.write_url,
				contentType : 'application/json',
				type : 'POST',
				data : sendData,
				success : function(data) {
					// nothing for now, maybe add callback in future?
					console.log("Sent something to ", that.write_url);
				}
			});
		};
	};
	return {
		setup: function(baseURL, onSucces, onError) {
			$.ajax({
				url : baseURL,
				contentType : 'application/json',
				type : 'POST',
				success : function(data) {
					var c = new ChannelProto();
					c.read_url = data.read_url;
					c.write_url = data.write_url;
					c.qr_url = data.qr_url;
					onSucces(c);
				},
				error : function(data) {
					console.log("Error creating channel");
					onError();
				}
			});				
		},
		fromReadURL: function(read_url) {
			var c = new ChannelProto();
			c.read_url = read_url;
			return c;
		}, 
		fromWriteURL: function(write_url) {
			var c = new ChannelProto();
			c.write_url = write_url;
			return c;
		}
	}; 
}();
