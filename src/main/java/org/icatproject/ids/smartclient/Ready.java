package org.icatproject.ids.smartclient;

import static java.util.Arrays.asList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.json.Json;
import javax.json.stream.JsonGenerator;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class Ready {

	public Ready(String[] rest) throws IOException, URISyntaxException, KeyManagementException, KeyStoreException,
			NoSuchAlgorithmException, CertificateException {
		OptionParser parser = new OptionParser();

		OptionSpec<String> preparedIds = parser.acceptsAll(asList("prepared", "p")).withRequiredArg()
				.ofType(String.class).describedAs("Prepared id");

		parser.acceptsAll(asList("h", "?", "help"), "show help").forHelp();

		OptionSet options;

		options = parser.parse(rest);

		if (options.has("h")) {
			parser.printHelpOn(System.out);
		} else {

			if (options.nonOptionArguments().size() != 1) {
				Cli.abort("ready expects one argument with the server url");

			} else {
				String idsUrl = (String) options.nonOptionArguments().get(0);

				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				JsonGenerator gen = Json.createGenerator(baos);
				gen.writeStartObject().write("idsUrl", idsUrl);

				if (!options.valuesOf(preparedIds).isEmpty()) {
					gen.writeStartArray("preparedIds");
					for (String pid : options.valuesOf(preparedIds)) {
						gen.write(pid);
					}
					gen.writeEnd();
				}
				gen.writeEnd().close();

				URI uri = new URIBuilder("http://localhost:8888").setPath("/isReady")
						.addParameter("json", baos.toString()).build();
				try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
					HttpGet httpGet = new HttpGet(uri);
					try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
						Cli.checkStatus(response);
						HttpEntity entity = response.getEntity();
						if (entity == null) {
							Cli.abort("http entity expected in response");
						} else {
							String jsonString = EntityUtils.toString(entity);
							System.out.println(jsonString);
						}
					}
				}
			}
		}
	}

}
