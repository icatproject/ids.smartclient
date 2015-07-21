package org.icatproject.ids.smartclient;

import java.net.URL;

import org.scenicview.ScenicView;

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
			scene.getStylesheets().add(getClass().getResource("gui.css").toExternalForm());
			primaryStage.setScene(scene);
			ScenicView.show(scene);
			primaryStage.show();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void main(String[] args) {
		launch(args);
	}
}
