package main;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import java.time.format.DateTimeFormatter;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import java.time.LocalTime;

public class Controller {

	@FXML
	private Button toplavendersquarebutton;

	@FXML
	private Button middlebottomorangerec;

	@FXML
	private Button topleftlavedarbutton;

	@FXML
	private Button bottomleftbeigesquare;

	@FXML
	private Button toporangesquare;

	@FXML
	private Button bottomorangesquare;

	@FXML
	private Button toplavendarrectangleshort;

	@FXML
	private Button bottomlavendarrectangleshort;

	@FXML
	private Button bottomlavenderrectanglelong;

	@FXML
	private Button toplavendarrectanglelong;

	@FXML
	private Button toprightredrectangle;

	@FXML
	private Button bottomrightrectanglered;

	@FXML 
	private Button angledbuttontop;

	@FXML 
	private Button angeledbottom;

	@FXML
	private void handlebuttonclicked(ActionEvent event) {
		System.out.println("clicked");
	}

	@FXML
	private Label topleftlabel;

	private Timeline clock;

	@FXML
	public void initialize() {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

		clock = new Timeline(
				new KeyFrame(Duration.ZERO, e -> {
					String Time = LocalTime.now().format(formatter);
					topleftlabel.setText(Time);
				}),new KeyFrame(Duration.seconds(1))
				);

		clock.setCycleCount(Animation.INDEFINITE);
		clock.play();

	}
}


