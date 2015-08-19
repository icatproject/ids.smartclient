package org.icatproject.ids.smartclient;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.net.ssl.SSLContext;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class Cli {

	public static void main(String[] args) throws InterruptedException {

		try {

			try {
				URI uri = new URIBuilder("https://localhost:8888").setPath("/ping").build();
				try (CloseableHttpClient httpclient = getHttpsClient()) {
					HttpGet httpGet = new HttpGet(uri);
					try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
						Cli.expectNothing(response);
					}
				}
			} catch (Exception e) {

				ProcessBuilder pb = new ProcessBuilder("sh", "/opt/smartclient/app/server.sh");
				Process p = pb.start();
				p.waitFor();

				int exitValue = p.exitValue();
				if (exitValue != 0) {
					System.err.println("Ping produces " + e.getClass() + " " + e.getMessage());
					System.err.println("Unable to start server please take a look at ~/.smartclient/log");
					System.exit(1);
				}
				System.out.println("Server has been started");
			}

			if (args.length == 0) {
				printHelp();
				System.exit(1);
			} else {
				String cmd = args[0];
				String[] rest = Arrays.copyOfRange(args, 1, args.length);
				if (cmd.equals("login")) {
					new Login(rest);
				} else if (cmd.equals("logout")) {
					new Logout(rest);
				} else if (cmd.equals("get")) {
					new Get(rest);
				} else if (cmd.equals("ready")) {
					new Ready(rest);
				} else if (cmd.equals("status")) {
					new Status(rest);
				} else if (cmd.equals("help")) {
					printHelp();
				} else {
					printHelp();
					System.exit(1);
				}
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	static CloseableHttpClient getHttpsClient() throws KeyManagementException, KeyStoreException,
			NoSuchAlgorithmException, CertificateException, IOException {
		Path home = Paths.get(System.getProperty("user.home"));
		Path store = home.resolve(".smartclient").resolve("local.jks");
		KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
		try (FileInputStream instream = new FileInputStream(store.toFile())) {
			trustStore.load(instream, "password".toCharArray());
		}
		SSLContext sslcontext = SSLContexts.custom().loadTrustMaterial(trustStore).build();
		SSLConnectionSocketFactory factory = new SSLConnectionSocketFactory(sslcontext,
				SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
		return HttpClients.custom().setSSLSocketFactory(factory).build();
	}

	private static void printHelp() {
		System.out.println("First parameter must be one of help, login, logout, get or ready");
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
