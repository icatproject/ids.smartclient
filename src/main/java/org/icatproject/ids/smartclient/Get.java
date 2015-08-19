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
import org.apache.http.message.BasicNameValuePair;

public class Get {

	public Get(String[] rest) throws IOException, URISyntaxException, KeyManagementException, KeyStoreException,
			NoSuchAlgorithmException, CertificateException {
		OptionParser parser = new OptionParser();

		OptionSpec<Long> investigations = parser.acceptsAll(asList("investigation", "i")).withRequiredArg()
				.ofType(Long.class).describedAs("Investigation id");
		OptionSpec<Long> datasets = parser.acceptsAll(asList("dataset", "s")).withRequiredArg().ofType(Long.class)
				.describedAs("Dataset id");
		OptionSpec<Long> datafiles = parser.acceptsAll(asList("datafile", "f")).withRequiredArg().ofType(Long.class)
				.describedAs("Datafile id");
		OptionSpec<String> preparedIds = parser.acceptsAll(asList("prepared", "p")).withRequiredArg()
				.ofType(String.class).describedAs("Prepared id");

		parser.acceptsAll(asList("h", "?", "help"), "show help").forHelp();

		OptionSet options;

		options = parser.parse(rest);

		if (options.has("h")) {
			parser.printHelpOn(System.out);
		} else {

			if (options.nonOptionArguments().size() != 1) {
				Cli.abort("get expects one argument with the server url");

			} else {
				String idsUrl = (String) options.nonOptionArguments().get(0);

				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				JsonGenerator gen = Json.createGenerator(baos);
				gen.writeStartObject().write("idsUrl", idsUrl);

				if (!options.valuesOf(investigations).isEmpty()) {
					gen.writeStartArray("investigationIds");
					for (Long i : options.valuesOf(investigations)) {
						gen.write(i);
					}
					gen.writeEnd();
				}
				if (!options.valuesOf(datasets).isEmpty()) {
					gen.writeStartArray("datasetIds");
					for (Long i : options.valuesOf(datasets)) {
						gen.write(i);
					}
					gen.writeEnd();
				}
				if (!options.valuesOf(datafiles).isEmpty()) {
					gen.writeStartArray("datafileIds");
					for (Long i : options.valuesOf(datafiles)) {
						gen.write(i);
					}
					gen.writeEnd();
				}
				if (!options.valuesOf(preparedIds).isEmpty()) {
					gen.writeStartArray("preparedIds");
					for (String i : options.valuesOf(preparedIds)) {
						gen.write(i);
					}
					gen.writeEnd();
				}
				gen.writeEnd().close();
				System.out.println(baos.toString());

				URI uri = new URIBuilder("https://localhost:8888").setPath("/getData").build();

				List<NameValuePair> formparams = new ArrayList<>();
				formparams.add(new BasicNameValuePair("json", baos.toString()));

				try (CloseableHttpClient httpclient = Cli.getHttpsClient()) {
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

}
