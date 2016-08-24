package org.icatproject.ids.smartclient;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;

import javax.json.Json;
import javax.json.stream.JsonGenerator;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

public class AddServerController {

	@FXML
	private Text status;

	private void setStatus(String msg) {
		status.setText(msg);
	}

	@FXML
	private void addServer(ActionEvent event) {
		String pluginName = method.getSelectionModel().selectedItemProperty().getValue();

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JsonGenerator gen = Json.createGenerator(baos);
		gen.writeStartObject().write("idsUrl", idsUrl.getText());

		if (pluginName.equals("sessionId")) {
			gen.write("sessionId", sessionId.getText());
		} else {
			gen.write("plugin", pluginName);
			gen.writeStartObject("credentials");
			if (pluginName.equals("ldap") || pluginName.equals("db") || pluginName.equals("simple")) {
				gen.write("username", username.getText());
				gen.write("password", password.getText());
			}
			gen.writeEnd();
		}

		gen.writeEnd().close();

		List<NameValuePair> formparams = new ArrayList<>();
		formparams.add(new BasicNameValuePair("json", baos.toString()));

		URI uri;
		try {
			uri = new URIBuilder("http://localhost:8888").setPath("/login").build();
		} catch (URISyntaxException e) {
			setStatus(e.getMessage());
			return;
		}

		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			HttpEntity entity = new UrlEncodedFormEntity(formparams);

			HttpPost httpPost = new HttpPost(uri);
			httpPost.setEntity(entity);
			try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
				GUI.expectNothing(response);
			}
			status.getScene().getWindow().hide();
		} catch (Exception e) {
			setStatus(e.getMessage());
		}

	}

	@FXML
	private TextField idsUrl;

	@FXML
	private ChoiceBox<String> method;

	@FXML
	private GridPane credentials;

	private TextField username;

	protected PasswordField password;

	protected TextField sessionId;

	@FXML
	private void initialize() {
		method.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {

			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				credentials.getChildren().clear();
				setCred(newValue);
				credentials.getScene().getWindow().sizeToScene();
			}

		});

		setCred(method.getSelectionModel().selectedItemProperty().getValue());
	}

	private void setCred(String newValue) {
		if (newValue.equals("ldap") || newValue.equals("db") || newValue.equals("simple")) {
			username = new TextField();
			credentials.addRow(0, new Label("username"), username);
			password = new PasswordField();
			credentials.addRow(1, new Label("password"), password);
		} else if (newValue.equals("sessionId")) {
			sessionId = new TextField();
			credentials.addRow(0, new Label("sessionId"), sessionId);
		}

	}

}