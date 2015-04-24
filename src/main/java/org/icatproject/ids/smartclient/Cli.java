package org.icatproject.ids.smartclient;

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.Arrays;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonReader;

import joptsimple.OptionException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

public class Cli {

	public static void main(String[] args) {

		try {
			if (args.length == 0) {
				printHelp();
				System.exit(1);
			} else {
				String cmd = args[0];
				String[] rest = Arrays.copyOfRange(args, 1, args.length);
				if (cmd.equals("login")) {
					new Login(rest);
				} else if (cmd.equals("get")) {
					new Get(rest);
				} else if (cmd.equals("put")) {
					new Put(rest);
				} else if (cmd.equals("help")) {
					printHelp();
				} else {
					printHelp();
					System.exit(1);
				}
			}
		} catch (IOException e) {
			System.out.println(e.getMessage());

		} catch (OptionException e) {
			System.out.println(e.getMessage());

		} catch (URISyntaxException e) {
			System.out.println(e.getMessage());

		}
	}

	private static void printHelp() {
		System.out.println("First parameter must be one of help, get or put");
	}

	static void checkStatus(HttpResponse response) throws IOException {
		StatusLine status = response.getStatusLine();
		if (status == null) {
			abort("Status line returned is empty");
		}
		int rc = status.getStatusCode();
		if (rc / 100 != 2) {
			HttpEntity entity = response.getEntity();
			if (entity == null) {
				abort("No explanation provided");
			} else {
				try {
					String error = EntityUtils.toString(entity);
					String code;
					String message;
					try (JsonReader jsonReader = Json.createReader(new StringReader(error))) {
						JsonObject json = jsonReader.readObject();
						code = json.getString("code");
						message = json.getString("message");
						abort(code + " " + message);
					} catch (JsonException e) {
						abort("Status code " + rc + " returned but message not json: " + error);
					}
				} catch (ParseException e) {
					abort(e.getMessage());
				}
			}
		}
	}

	static void abort(String msg) {
		System.err.println(msg);
		System.exit(1);
	}

	static void expectNothing(CloseableHttpResponse response) throws IOException {
		checkStatus(response);
		HttpEntity entity = response.getEntity();
		if (entity != null) {
			if (!EntityUtils.toString(entity).isEmpty()) {
				abort("No http entity expected in response");
			}
		}
	}

}
