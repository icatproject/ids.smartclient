package org.icatproject.ids.smartclient;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.namespace.QName;

import org.icatproject.EntityBaseBean;
import org.icatproject.ICAT;
import org.icatproject.ICATService;
import org.icatproject.IcatException_Exception;
import org.icatproject.Login.Credentials;
import org.icatproject.Login.Credentials.Entry;

public class WSession {

	private final ICAT icat;

	private final String sessionId;

	public WSession() throws MalformedURLException, IcatException_Exception {
		String url = System.getProperty("serverUrl");
		System.out.println("Using ICAT service at " + url);
		final URL icatUrl = new URL(url + "/ICATService/ICAT?wsdl");
		final ICATService icatService = new ICATService(icatUrl, new QName("http://icatproject.org", "ICATService"));
		icat = icatService.getICATPort();
		this.sessionId = login("db", "username", "root", "password", "password");
		System.out.println("Logged in");
	}

	private String login(String plugin, String... credbits) throws IcatException_Exception {
		Credentials credentials = new Credentials();
		List<Entry> entries = credentials.getEntry();
		int i = 0;
		while (i < credbits.length) {
			Entry e = new Entry();
			e.setKey(credbits[i]);
			e.setValue(credbits[i + 1]);
			entries.add(e);
			i += 2;
		}
		return this.icat.login(plugin, credentials);
	}

	public void clear() throws Exception {
		deleteAll(Arrays.asList("Facility", "Log", "DataCollection"));
		deleteAll(Arrays.asList("Rule", "UserGroup", "User", "Grouping", "PublicStep"));
	}

	private void deleteAll(List<String> names) throws IcatException_Exception {
		List<EntityBaseBean> toDelete = new ArrayList<EntityBaseBean>();
		StringBuilder sb = new StringBuilder();
		for (String type : names) {
			List<Object> lo = icat.search(sessionId, type);
			if (lo.size() > 0) {
				if (sb.length() == 0) {
					sb.append("Will delete");
				} else {
					sb.append(" and");
				}
				sb.append(" " + lo.size() + " object" + (lo.size() == 1 ? "" : "s") + " of type " + type);
				for (Object o : lo) {
					toDelete.add((EntityBaseBean) o);
				}
			}
		}
		if (sb.length() != 0) {
			System.out.println(sb);
		}
		icat.deleteMany(sessionId, toDelete);
	}

}