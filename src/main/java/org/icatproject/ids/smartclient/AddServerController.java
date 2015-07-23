package org.icatproject.ids.smartclient;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

public class AddServerController {

	@FXML
	private void addServer(ActionEvent event) {
		System.out.println(event + " for " + method.getSelectionModel().selectedItemProperty().getValue());
	}

	@FXML
	private ChoiceBox<String> method;

	@FXML
	private GridPane credentials;

	@FXML
	private void initialize() {
		method.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {

			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				credentials.getChildren().clear();
				if (newValue.equals("ldap") || newValue.equals("db")) {
					credentials.addRow(0, new Label("username"), new TextField());
					credentials.addRow(1, new Label("password"), new PasswordField());
				} else if (newValue.equals("sessionId")) {
					credentials.addRow(0, new Label("sessionId"), new TextField());
				}
				credentials.getScene().getWindow().sizeToScene();
			}
		});
	}

}