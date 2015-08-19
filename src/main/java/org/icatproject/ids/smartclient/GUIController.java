package org.icatproject.ids.smartclient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.stream.JsonGenerator;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

public class GUIController {
	@FXML
	private Text requests;

	private final ToggleGroup group = new ToggleGroup();

	@FXML
	private Text dfids;

	@FXML
	private Text status;

	@FXML
	private Text result;

	private boolean windows;

	@FXML
	private Label title;

	@FXML
	Button addServer;

	@FXML
	private VBox table;

	@FXML
	ChoiceBox<String> getType;

	@FXML
	TextField getWhat;

	private void setStatus(String msg) {
		status.setText(msg);
	}

	private void setBadResult(String msg) {
		result.setText(msg);
		result.setFill(Color.RED);
	}

	private void setGoodResult(String msg) {
		result.setText(msg);
		result.setFill(Color.GREEN);
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
			stage.setTitle("Add an IDS");
			URL uri = getClass().getResource("addServer.fxml");
			Parent p = FXMLLoader.load(uri);
			Scene scene = new Scene(p);
			stage.setScene(scene);
			// scene.getStylesheets().add(getClass().getResource("gui.css").toExternalForm());
			stage.initModality(Modality.WINDOW_MODAL);
			stage.initOwner(((Node) event.getSource()).getScene().getWindow());
			stage.show();
			setGoodResult("");
		} catch (IOException e) {
			setBadResult(e.getClass() + " " + e.getMessage());
		}
	}

	@FXML
	private void removeServer(ActionEvent event) {
		String idsUrl = (String) group.getSelectedToggle().getUserData();

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		JsonGenerator gen = Json.createGenerator(baos);
		gen.writeStartObject().write("idsUrl", idsUrl).writeEnd().close();

		try {
			URI uri = new URIBuilder("https://localhost:8888").setPath("/logout").build();

			List<NameValuePair> formparams = new ArrayList<>();
			formparams.add(new BasicNameValuePair("json", baos.toString()));

			try (CloseableHttpClient httpclient = GUI.getHttpsClient()) {
				HttpEntity entity = new UrlEncodedFormEntity(formparams);
				HttpPost httpPost = new HttpPost(uri);
				httpPost.setEntity(entity);
				try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
					GUI.expectNothing(response);
				}
			}
			setGoodResult("IDS Removed");
		} catch (Exception e) {
			setBadResult(e.getMessage());
		}
	}

	@FXML
	private void submitGet(ActionEvent event) {

		try {
			String idsUrl = (String) group.getSelectedToggle().getUserData();
			String type = getType.getSelectionModel().selectedItemProperty().getValue();
			long id = Long.parseLong(getWhat.getText());
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			JsonGenerator gen = Json.createGenerator(baos);
			gen.writeStartObject().write("idsUrl", idsUrl);

			if (type.equals("Investigation")) {
				gen.writeStartArray("investigationIds");

			} else if (type.equals("Dataset")) {
				gen.writeStartArray("datasetIds");

			} else if (type.equals("Datafile")) {
				gen.writeStartArray("datafileIds");

			}
			gen.write(id).writeEnd().writeEnd().close();
			System.out.println(baos.toString());

			URI uri = new URIBuilder("https://localhost:8888").setPath("/getData").build();

			List<NameValuePair> formparams = new ArrayList<>();
			formparams.add(new BasicNameValuePair("json", baos.toString()));

			try (CloseableHttpClient httpclient = GUI.getHttpsClient()) {
				HttpEntity entity = new UrlEncodedFormEntity(formparams);
				HttpPost httpPost = new HttpPost(uri);
				httpPost.setEntity(entity);
				try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
					GUI.expectNothing(response);
				}
			}
			getWhat.clear();
			setGoodResult("Submitted");
		} catch (Exception e) {
			setBadResult(e.getClass() + " " + e.getMessage());
		}
	}

	private void update() {
		try {

			try {
				URI uri = new URIBuilder("https://localhost:8888").setPath("/ping").build();
				try (CloseableHttpClient httpclient = GUI.getHttpsClient()) {
					HttpGet httpGet = new HttpGet(uri);
					try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
						GUI.expectNothing(response);
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

			URI uri = new URIBuilder("https://localhost:8888").setPath("/status").build();
			try (CloseableHttpClient httpclient = GUI.getHttpsClient()) {
				HttpGet httpGet = new HttpGet(uri);
				try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
					GUI.checkStatus(response);
					HttpEntity entity = response.getEntity();
					if (entity == null) {
						GUI.abort("http entity expected in response");
					} else {
						String jsonString = EntityUtils.toString(entity);
						try (JsonReader jsonReader = Json.createReader(new StringReader(jsonString))) {
							JsonObject json = jsonReader.readObject();
							setRequests(json.getJsonNumber("requests").intValueExact());
							setDfids(json.getJsonNumber("dfids").intValueExact());
							JsonArray servers = json.getJsonArray("servers");
							ObservableList<Node> children = table.getChildren();
							children.clear();

							for (int n = 0; n < servers.size(); n++) {
								JsonObject server = servers.getJsonObject(n);
								Hyperlink link = new Hyperlink(server.getString("idsUrl"));
								link.setOnAction(new EventHandler<ActionEvent>() {
									@Override
									public void handle(ActionEvent e) {
										System.out.println("This link is clicked");
									}
								});
								String idsUrl = server.getString("idsUrl");
								RadioButton rb = new RadioButton(idsUrl + " ("
										+ server.getString("user", "Not logged in") + ")");
								rb.setToggleGroup(group);
								rb.setUserData(idsUrl);
								table.getChildren().add(rb);
								if (n == 0) {
									rb.setSelected(true);
								}
							}
							if (table.getScene() != null) {
								table.getScene().getWindow().sizeToScene();
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