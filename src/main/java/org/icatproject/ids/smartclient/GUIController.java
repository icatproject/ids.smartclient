package org.icatproject.ids.smartclient;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class GUIController {
	@FXML
	private Text requests;

	@FXML
	private Text dfids;

	@FXML
	private Text status;

	private boolean windows;

	@FXML
	private Label title;

	@FXML
	Button addServer;

	@FXML
	private GridPane table;

	private void setStatus(String msg) {
		status.setText(msg);
	}

	private void setRequests(int num) {
		requests.setText(Integer.toString(num));
	}

	private void setDfids(int num) {
		dfids.setText(Integer.toString(num));
	}

	@FXML
	private void initialize() {
		windows = System.getProperty("os.name").startsWith("Windows");
		update();
		Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(10), ae -> update()));
		timeline.setCycleCount(Animation.INDEFINITE);
		timeline.play();
	}

	@FXML
	private void addServer(ActionEvent event) {
		try {
			Stage stage = new Stage();
			stage.setTitle("TuneUs");
			URL uri = getClass().getResource("addServer.fxml");
			Parent p = FXMLLoader.load(uri);
			Scene scene = new Scene(p);
			stage.setScene(scene);
			// scene.getStylesheets().add(getClass().getResource("gui.css").toExternalForm());
			stage.initModality(Modality.WINDOW_MODAL);
			stage.initOwner(((Node) event.getSource()).getScene().getWindow());
			stage.show();
		} catch (IOException e) {
			setStatus(e.getClass() + " " + e.getMessage());
		}
	}

	private void update() {
		try {

			try {
				URI uri = new URIBuilder("http://localhost:8888").setPath("/ping").build();
				try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
					HttpGet httpGet = new HttpGet(uri);
					try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
						Cli.expectNothing(response);
					}
					setStatus("Server is running");
				}
			} catch (Exception e) {
				setStatus("Server is being started");

				ProcessBuilder pb;
				if (windows) {
					String home = System.getProperty("user.home");
					pb = new ProcessBuilder(home + "/AppData/Local/smartclient/server");
				} else {
					pb = new ProcessBuilder("sh", "/opt/smartclient/app/server.sh");
				}
				Process p;
				try {
					p = pb.start();
				} catch (Exception e1) {
					setStatus("Unable to start server - " + e1.getMessage());
					return;
				}
				if (!windows) {
					p.waitFor();

					int exitValue = p.exitValue();
					if (exitValue != 0) {
						setStatus("Unable to start server - please take a look at ~/.smartclient/log");
						return;
					} else {
						setStatus("Server has been started");
					}
				}
			}

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
						try (JsonReader jsonReader = Json.createReader(new StringReader(jsonString))) {
							JsonObject json = jsonReader.readObject();
							setRequests(json.getJsonNumber("requests").intValueExact());
							setDfids(json.getJsonNumber("dfids").intValueExact());
							JsonArray servers = json.getJsonArray("servers");
							for (int n = 0; n < servers.size(); n++) {
								JsonObject server = servers.getJsonObject(n);
								table.add(new Text(server.getString("idsUrl")), 0, n + 1);
								table.add(new Text(server.getString("user", "Not logged in")), 1, n + 1);
							}

						} catch (JsonException e) {
							setStatus("Internal error " + jsonString + " is not json");
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}