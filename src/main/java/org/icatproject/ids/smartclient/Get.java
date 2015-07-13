package org.icatproject.ids.smartclient;

import static java.util.Arrays.asList;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

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

public class Get {

	public Get(String[] rest) throws IOException, URISyntaxException {
		OptionParser parser = new OptionParser();

		OptionSpec<Long> investigations = parser.acceptsAll(asList("investigation", "i")).withRequiredArg()
				.ofType(Long.class).describedAs("Investigation id");
		OptionSpec<Long> datasets = parser.acceptsAll(asList("dataset", "s")).withRequiredArg().ofType(Long.class)
				.describedAs("Dataset id");
		OptionSpec<Long> datafiles = parser.acceptsAll(asList("datafile", "f")).withRequiredArg().ofType(Long.class)
				.describedAs("Datafile id");

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
				URI uri = new URIBuilder("http://localhost:8888").setPath("/getData").build();

				List<NameValuePair> formparams = new ArrayList<>();
				formparams.add(new BasicNameValuePair("idsUrl", idsUrl));
				if (!options.valuesOf(investigations).isEmpty()) {
					formparams.add(new BasicNameValuePair("investigationIds", listToString(options
							.valuesOf(investigations))));
				}
				if (!options.valuesOf(datasets).isEmpty()) {
					formparams.add(new BasicNameValuePair("datasetIds", listToString(options.valuesOf(datasets))));
				}
				if (!options.valuesOf(datafiles).isEmpty()) {
					formparams.add(new BasicNameValuePair("datafileIds", listToString(options.valuesOf(datafiles))));
				}
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

	private String listToString(List<Long> ids) {
		StringBuilder sb = new StringBuilder();
		for (long id : ids) {
			if (sb.length() != 0) {
				sb.append(',');
			}
			sb.append(Long.toString(id));
		}
		return sb.toString();
	}

}
