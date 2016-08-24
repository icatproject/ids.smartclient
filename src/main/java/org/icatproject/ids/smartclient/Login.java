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
import java.util.ArrayList;
import java.util.List;

import javax.json.Json;
import javax.json.stream.JsonGenerator;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

public class Login {

	public Login(String[] rest) throws IOException, URISyntaxException, KeyManagementException, KeyStoreException,
			NoSuchAlgorithmException, CertificateException {
		OptionParser parser = new OptionParser();

		OptionSpec<String> plugin = parser.acceptsAll(asList("plugin", "p"), "name of authentication plugin")
				.withRequiredArg().ofType(String.class).describedAs("plugin");

		OptionSpec<String> sessionId = parser.acceptsAll(asList("sid", "s"), "sessionId instead of plugin")
				.withRequiredArg().ofType(String.class).describedAs("sessionId");

		parser.acceptsAll(asList("h", "?", "help"), "show help").forHelp();

		OptionSet options;

		options = parser.parse(rest);

		if (options.has("h")) {
			parser.printHelpOn(System.out);
		} else {

			if (options.has(plugin) && options.has(sessionId)) {
				Cli.abort("Can't specify plugin and sessionId");
			}

			if (!options.has(plugin) && !options.has(sessionId)) {
				Cli.abort("Must specify plugin or sessionId");
			}

			if (options.has(sessionId)) {
				if (options.nonOptionArguments().size() != 1) {
					Cli.abort("No idsUrl specified");
				}
			} else {
				if (options.nonOptionArguments().size() % 2 != 1) {
					Cli.abort("idsUrl must be followed by even number of credentials");
				}
			}

			String idsUrl = (String) options.nonOptionArguments().get(0);

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			JsonGenerator gen = Json.createGenerator(baos);
			gen.writeStartObject().write("idsUrl", idsUrl);

			if (options.has(sessionId)) {
				gen.write("sessionId", options.valueOf(sessionId));
			} else {
				gen.write("plugin", options.valueOf(plugin));
				gen.writeStartObject("credentials");
				for (int i = 1; i < options.nonOptionArguments().size(); i += 2) {
					gen.write((String) options.nonOptionArguments().get(i),
							(String) options.nonOptionArguments().get(i + 1));
				}
				gen.writeEnd();
			}

			gen.writeEnd().close();
			System.out.println(baos.toString());

			URI uri = new URIBuilder("http://localhost:8888").setPath("/login").build();

			List<NameValuePair> formparams = new ArrayList<>();
			formparams.add(new BasicNameValuePair("json", baos.toString()));

			try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
				HttpEntity entity = new UrlEncodedFormEntity(formparams);
				HttpPost httpPost = new HttpPost(uri);
				httpPost.setEntity(entity);
				try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
					Cli.expectNothing(response);
				}
			}

		}
	}

}
