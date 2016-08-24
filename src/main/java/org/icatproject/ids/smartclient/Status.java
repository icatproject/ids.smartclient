package org.icatproject.ids.smartclient;

import static java.util.Arrays.asList;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class Status {

	public Status(String[] rest) throws IOException, URISyntaxException, KeyManagementException, KeyStoreException,
			NoSuchAlgorithmException, CertificateException {
		OptionParser parser = new OptionParser();

		parser.acceptsAll(asList("h", "?", "help"), "show help").forHelp();

		OptionSet options;

		options = parser.parse(rest);

		if (options.has("h")) {
			parser.printHelpOn(System.out);
		} else {

			URI uri = new URIBuilder("http://localhost:8888").setPath("/status").build();
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
