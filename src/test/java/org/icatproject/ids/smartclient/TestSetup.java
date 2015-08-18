package org.icatproject.ids.smartclient;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonReader;

import org.icatproject.icat.client.ICAT;
import org.icatproject.icat.client.Session;
import org.icatproject.icat.client.Session.Attributes;
import org.icatproject.icat.client.Session.DuplicateAction;
import org.icatproject.ids.client.DataSelection;
import org.icatproject.ids.client.IdsClient;
import org.icatproject.ids.client.IdsClient.Flag;

public class TestSetup {

	public static void main(String[] args) throws Exception {

		WSession wSession = new WSession();
		wSession.clear();

		ICAT icat = new ICAT(System.getProperty("serverUrl"));
		Map<String, String> credentials = new HashMap<>();
		credentials.put("username", "root");
		credentials.put("password", "password");
		Session session = icat.login("db", credentials);

		Path path = Paths.get(ClassLoader.class.getResource("/icat.port").toURI());
		session.importMetaData(path, DuplicateAction.CHECK, Attributes.USER);
		String jsonString = session.search("SELECT fmt.id FROM DatafileFormat fmt LIMIT 0,1");

		long formatId;
		try (JsonReader parser = Json.createReader(new ByteArrayInputStream(jsonString.getBytes()))) {
			formatId = parser.readArray().getJsonNumber(0).longValueExact();
		}

		jsonString = session.search("SELECT ds.id FROM Dataset ds");
		List<Long> dsids = new ArrayList<>();
		try (JsonReader parser = Json.createReader(new ByteArrayInputStream(jsonString.getBytes()))) {
			JsonArray array = parser.readArray();
			for (int i = 0; i < array.size(); i++) {
				dsids.add(array.getJsonNumber(i).longValueExact());
			}
		}
		IdsClient idsClient = new IdsClient(new URL(System.getProperty("serverUrl")));
		Path p = Files.createTempFile(null, null);
		try (PrintWriter os = new PrintWriter(p.toFile())) {
			os.println("sessionId");
		}
		for (int i = 0; i < dsids.size(); i++) {
			long dsId = dsids.get(i);
			for (int j = 0; j < 3; j++) {
				String name;
				try (PrintWriter os = new PrintWriter(p.toFile())) {
					name = "dsid " + dsId + " j " + j;
					os.println(name);

				}
				idsClient.put(session.getId(), Files.newInputStream(p), name, dsId, formatId, name
						+ " is a rather splendid datafile");
			}
		}

		System.out.println("PreparedId "
				+ idsClient.prepareData(session.getId(), new DataSelection().addDatasets(dsids), Flag.NONE));
		System.out.println("Done");

	}

}