package org.icatproject.ids.smartclient;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

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
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class GUI extends Application {
	@Override
	public void start(Stage primaryStage) {

		try {
			URL uri = getClass().getResource("gui.fxml");
			Parent p = FXMLLoader.load(uri);
			Scene scene = new Scene(p);
			primaryStage.setScene(scene);
			primaryStage.setTitle("Smartclient");
			primaryStage.show();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void main(String[] args) {
		launch(args);
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

	static void abort(String msg) throws IOException {
		throw new IOException(msg);
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

}
