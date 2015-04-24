package org.icatproject.ids.smartclient;

import static java.util.Arrays.asList;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

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

	public Login(String[] rest) throws IOException, URISyntaxException {
		OptionParser parser = new OptionParser();

		parser.acceptsAll(asList("h", "?", "help"), "show help").forHelp();

		OptionSet options;

		options = parser.parse(rest);

		if (options.has("h")) {
			parser.printHelpOn(System.out);
		} else {

			if (options.nonOptionArguments().size() != 2) {
				System.err.println("Whoops");

			} else {
				String idsUrl = (String) options.nonOptionArguments().get(0);
				String sessionId = (String) options.nonOptionArguments().get(1);
				URI uri = new URIBuilder("http://localhost:8888").setPath("/login").build();
				System.out.println("Uri " + uri);

				List<NameValuePair> formparams = new ArrayList<>();
				formparams.add(new BasicNameValuePair("sessionId", sessionId));
				formparams.add(new BasicNameValuePair("idsUrl", idsUrl));

				try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
					HttpEntity entity = new UrlEncodedFormEntity(formparams);
					HttpPost httpPost = new HttpPost(uri);
					httpPost.setEntity(entity);
					try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
						Cli.expectNothing(response);
					} // catch (InsufficientStorageException |
						// DataNotOnlineException e) {
					// throw new InternalException(e.getClass() + " " +
					// e.getMessage());
					// }

				}

			}
		}

	}

}
