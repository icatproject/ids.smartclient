package org.icatproject.ids.smartclient;

import java.io.IOException;

public class Put {

	public Put(String[] rest) throws IOException {
		for (String r : rest) {
			System.out.println("Put " + r);
		}
	
	}

}
