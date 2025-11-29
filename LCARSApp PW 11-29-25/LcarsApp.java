// LcarsApp.java — Embedded views version
// - CUT + PASTE (true move) and keeps LCARS desktop tiles in sync
// - CUT + PASTE moves (Files.move with fallback), COPY + PASTE duplicates
// - After paste, tiles update: removed if moved off Desktop, added if moved onto Desktop
// - Desktop tiles persist via ~/.lcars_desktop.txt
// - UPDATE: Top-right HOLO DRIVE w/ bigger red hub + compact storage bar
// - UPDATE: Username appears on the amber header bar in a small right-side pill
// - UPDATE: Explorer / Settings / Command Prompt now appear INSIDE the right desktop pane

package main;

import javafx.animation.*;
import java.awt.Desktop;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;


public class LcarsApp extends Application {

    // ===== LCARS palette (Classic TNG) =====
	public static Color BG     = Color.web("#000000");
	public static  Color TEXT   = Color.web("#F0F0F0");
	public static  Color PEACH  = Color.web("#FFB266");
	public static Color LAVENDER = Color.web("#D3D3FF");
	public static  Color SALMON = Color.web("#FF8C78");
	public static  Color AMBER  = Color.web("#FFCC66");
	public static  Color ORANGE = Color.web("#f89d00");
	public static  Color BLUE   = Color.web("#6699FF");
	public static Color RED = Color.web("#cd666c");
	public static  Color TEAL   = Color.web("#60C8C8");
	public static  Color PANEL  = Color.web("#0D0D0D");
	public static  Color EDGE   = Color.web("#333333");
    private int lastVolumeSteps = 25;  // remember last applied system volume (0–50 scale)

    // Desktop tiles / trash sizing
    private static final double ICON_SIZE = 80; // exact square size
    private static final double ICON_RADIUS = 20;
    private Timeline clock;
    private BorderPane mainBody;
    private static class Settings {
        boolean ambientSound = true;
        boolean glowEnabled = true;
        boolean useLcarsFont = true;
        double uiScale = 1.0;
        boolean transparentUI = false;
        boolean clickSound = true;
        String theme = "STANDARD";  // default theme
    }

    private Stage primaryStage = null;

    private static final Settings SETTINGS = new Settings();
    private static final File SETTINGS_FILE =
            new File(System.getProperty("user.home"), ".lcars_settings.txt");

    private MediaPlayer bgm;

    private static final File DESKTOP_STATE_FILE =
            new File(System.getProperty("user.home"), ".lcars_desktop.txt");

    private ImageView headerLogo;
    private static DesktopCanvas DESKTOP_CANVAS;

    // Holder for right-hand desktop pane contents (default = desktop canvas + holo)
    private StackPane desktopContentHolder;
    private Node desktopDefaultContent;

    // ===== Font helper (optional LCARS font) =====
    private static Font lcarsFontOrDefault(double size, boolean bold) {
        double scaled = size * SETTINGS.uiScale;

        if (SETTINGS.useLcarsFont) {
            try (FileInputStream in = new FileInputStream("lcars.ttf")) {
                Font f = Font.loadFont(in, scaled);
                if (f != null)
                    return bold ? Font.font(f.getFamily(), FontWeight.BOLD, scaled) : f;
            } catch (Exception ignored) {}
        }

        return bold ? Font.font("System", FontWeight.BOLD, scaled) : Font.font(scaled);
    }

    // Glow Helper
    private void applyGlowSetting(Scene scene) {
        if (scene == null) return;
        if (SETTINGS.glowEnabled) {
            DropShadow shadow = new DropShadow();
            shadow.setRadius(8);
            shadow.setSpread(0.15);
            shadow.setOffsetX(0);
            shadow.setOffsetY(0);
            shadow.setColor(Color.web("#66CCFF55"));
            scene.getRoot().setEffect(shadow);
        } else {
            scene.getRoot().setEffect(null);
        }
    }

    private DropShadow createGlow(Color color) {
        if (!SETTINGS.glowEnabled) return null;

        DropShadow glow = new DropShadow();
        glow.setRadius(10);
        glow.setSpread(0.20);
        glow.setColor(color.deriveColor(0, 1, 1, 0.45));
        glow.setOffsetX(0);
        glow.setOffsetY(0);

        return glow;
    }

    private void refreshGlow(Node node) {
        if (node instanceof Button b) {
            Color base = (Color) b.getUserData();
            if (SETTINGS.glowEnabled) {
                b.setEffect(createGlow(base));
            } else {
                b.setEffect(null);
            }
        }
        if (node instanceof Label lbl) {
            // Only apply glow to labels that had glow originally
            if (lbl.getTextFill().equals(AMBER)) {
                lbl.setEffect(SETTINGS.glowEnabled ? createGlow(AMBER) : null);
            }
        }

        if (node instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable())
                refreshGlow(child);
        }
    }
    private static final String POWERSHELL =
            "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";

    // Settings Helpers
    private void saveSettings() {
        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(SETTINGS_FILE, false), StandardCharsets.UTF_8))) {
        	
        	w.write("theme=" + SETTINGS.theme);
        	w.newLine();
            w.write("ambientSound=" + SETTINGS.ambientSound);
            w.newLine();

            w.write("glowEnabled=" + SETTINGS.glowEnabled);
            w.newLine();

            w.write("useLcarsFont=" + SETTINGS.useLcarsFont);
            w.newLine();
            w.write("uiScale=" + SETTINGS.uiScale);
            w.newLine();
            w.write("transparentUI=" + SETTINGS.transparentUI);
            w.newLine();
            w.write("clickSound=" + SETTINGS.clickSound);
            w.newLine();

        } catch (Exception ignored) {}
    }

    private void loadSettings() {
        if (!SETTINGS_FILE.exists()) return;

        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(SETTINGS_FILE), StandardCharsets.UTF_8))) {

            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("ambientSound=")) {
                    SETTINGS.ambientSound = Boolean.parseBoolean(line.substring("ambientSound=".length()));
                }
                if (line.startsWith("glowEnabled=")) {
                    SETTINGS.glowEnabled = Boolean.parseBoolean(line.substring("glowEnabled=".length()));
                }
                if (line.startsWith("useLcarsFont="))
                    SETTINGS.useLcarsFont = Boolean.parseBoolean(line.substring(13));

                if (line.startsWith("uiScale="))
                    SETTINGS.uiScale = Double.parseDouble(line.substring(8));

                if (line.startsWith("transparentUI="))
                    SETTINGS.transparentUI = Boolean.parseBoolean(line.substring(15));

                if (line.startsWith("clickSound="))
                    SETTINGS.clickSound = Boolean.parseBoolean(line.substring(12));
                
                if (line.startsWith("theme="))
                    SETTINGS.theme = line.substring(6);

            }
        } catch (Exception ignored) {}
    }

    @Override
    public void start(Stage primary) {
        if (SETTINGS.transparentUI) {
            primary.initStyle(StageStyle.TRANSPARENT);
        }
        this.primaryStage = primary;
        loadSettings();

        Parent content = buildMainConsole(primary);

        playBackgroundSounds();

        StackPane root = new StackPane(content);

        root.setBackground(
                SETTINGS.transparentUI
                        ? Background.EMPTY
                        : new Background(new BackgroundFill(BG, CornerRadii.EMPTY, Insets.EMPTY))
        );

        if (SETTINGS.transparentUI) {
            root.setBackground(Background.EMPTY);
        } else {
            root.setBackground(new Background(new BackgroundFill(BG, CornerRadii.EMPTY, Insets.EMPTY)));
        }

        attachConnectivityMonitor(root);

        Scene scene = new Scene(root, 1280, 800,
                SETTINGS.transparentUI ? Color.TRANSPARENT : BG);

        primary.setTitle("LCARS File Console");
        primary.setScene(scene);
        primary.setFullScreen(true);
        primary.show();

        // adds the on click sound
        AudioClip click = new AudioClip(getClass().getResource("clicky.mp3").toExternalForm());
        click.setVolume(.06);
        scene.setOnMousePressed(event -> {
            if (!SETTINGS.clickSound) return;

            if (event.getButton() == MouseButton.PRIMARY)
                click.play();
        });

        Platform.runLater(() -> {
            if (DESKTOP_CANVAS != null)
                DESKTOP_CANVAS.loadState();
        });

        playIntroOverlay(root);
        applyGlowSetting(scene);
        if (SETTINGS.transparentUI) {
            primary.setOpacity(0.92);
        } else {
            primary.setOpacity(1.0);
        }
    }

    // plays the background noises
    private void playBackgroundSounds() {
        if (!SETTINGS.ambientSound) return;
        String backgroundPath = getClass().getResource("background.mp3").toString();
        bgm = new MediaPlayer(new Media(backgroundPath));
        bgm.setCycleCount(MediaPlayer.INDEFINITE);
        bgm.setVolume(.3);
        bgm.play();
    }

    private Parent buildMainConsole(Stage owner) {
		BorderPane root = new BorderPane();
		root.setPadding(new Insets(0));
		
		

		// Header with username pill on amber bar
		VBox bars = buildHeaderBarsWithUserPill();
		BorderPane titleRow = new BorderPane();
		headerLogo = new ImageView();
		headerLogo.setFitWidth(320); headerLogo.setFitHeight(96); headerLogo.setPreserveRatio(true);
		titleRow.setRight(headerLogo);
		Region r3 = new Region();
		r3.setMinHeight(23);
		r3.setPrefHeight(23);
		r3.setBackground(new Background(
			    new BackgroundFill(
			        LAVENDER,
			        new CornerRadii(30, 0, 0, 0, false), // TL, TR, BR, BL
			        Insets.EMPTY
			    )
			));
		root.setTop(new VBox(
			    bars,         // your clock + amber bar header
			    spacer(8),    // optional
			    r3            // THE PURPLE BAR GOES HERE
			));


		GridPane grid = new GridPane(); grid.setHgap(12); grid.setVgap(12);
		VBox controls = buildLeftButtonBars();
		
		//VBox controls = new VBox(12, sectionLabel("PRIMARY CONTROLS"), grid);
		

		DESKTOP_CANVAS = new DesktopCanvas();
		Region desktopCard = roundedCard(DESKTOP_CANVAS, PANEL);
		VBox centerbody = new VBox();
		desktopCard.setMaxHeight(1200);   // adjusts to your layout height
		desktopCard.setPrefHeight(1200);
		desktopCard.setMinHeight(1200);

		centerbody.getChildren().add(desktopCard);


		// Top-right: larger holo drive + compact storage bar
		StackPane desktopStack = new StackPane(centerbody);
		

		BorderPane body = new BorderPane();
		controls.setTranslateY(-23);   // adjust the number as needed
		body.setLeft(controls);
		body.setCenter(desktopStack);
		BorderPane.setMargin(desktopStack, new Insets(40, 0, 0, 0));
		this.mainBody = body;
		this.desktopContentHolder = desktopStack;
		this.desktopDefaultContent = centerbody;
		root.setCenter(body);

		Button cmdButton = lcarsButton("Command Prompt", SALMON);
		Button b2 = lcarsButton("PLACEHOLDER", BLUE);   b2.setDisable(true);
		Button btnExplorer = lcarsButton("OPEN FOLDER VIEW", TEAL);
		Button b4 = lcarsButton("PLACEHOLDER", PEACH);  b4.setDisable(true);

		grid.add(cmdButton, 0, 0); grid.add(b2, 1, 0);
		grid.add(btnExplorer, 0, 1); grid.add(b4, 1, 1);

		btnExplorer.setOnAction(e -> openExplorerWindow(primaryStage));
		cmdButton.setOnAction(e -> openCMDPane(primaryStage));

		return root;
	}
    private void applyTheme(String themeName) {

        switch (themeName) {
            case "TNG PEACH" -> {
                PEACH = Color.web("#FFB266");
                SALMON = Color.web("#FF8C78");
                AMBER = Color.web("#FFCC66");
                BLUE = Color.web("#6699FF");
                TEAL = Color.web("#60C8C8");
                LAVENDER = Color.web("#D6D4FF");
            }

            case "VOYAGER GOLD" -> {
                PEACH = Color.web("#F5C98A");
                SALMON = Color.web("#E8936F");
                AMBER = Color.web("#FFD366");
                BLUE = Color.web("#88AFFF");
                TEAL = Color.web("#6DD5C2");
                LAVENDER = Color.web("#E2D9FF");
            }

            case "SOVEREIGN BLUE" -> {
                PEACH = Color.web("#9EC1FF");
                SALMON = Color.web("#5CA7FF");
                AMBER = Color.web("#C8E0FF");
                BLUE = Color.web("#3E7BFF");
                TEAL = Color.web("#5FE3E3");
                LAVENDER = Color.web("#DCE6FF");
            }

            case "ALTERNATE DARK" -> {
                PEACH = Color.web("#C47E48");
                SALMON = Color.web("#B46652");
                AMBER = Color.web("#C9A24A");
                BLUE = Color.web("#4A69C9");
                TEAL = Color.web("#52B4A3");
                LAVENDER = Color.web("#9A93B4");
            }
        }

        SETTINGS.theme = themeName;
        saveSettings();

        recolorUI(primaryStage.getScene().getRoot());
    }


    private void recolorUI(Node n) {

        if (n instanceof Button b) {
            Color c = (Color) b.getUserData();       // stored the original color in userData
            b.setBackground(new Background(
                new BackgroundFill(c, new CornerRadii(999), Insets.EMPTY)));
            if (SETTINGS.glowEnabled)
                b.setEffect(createGlow(c));
        }

        if (n instanceof Label lbl) {
            if (lbl.getTextFill().equals(AMBER) ||
                lbl.getTextFill().equals(SALMON) ||
                lbl.getTextFill().equals(PEACH))
                lbl.setTextFill(AMBER);   // or specific logic per label type
        }

        if (n instanceof Rectangle r) {
            // Repaint rectangles that use theme colors
            // You may want custom logic per left button
        }

        if (n instanceof SVGPath svg) {
            svg.setFill(PEACH); // or whichever color applies
        }

        if (n instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable())
                recolorUI(child);
        }
    }

    private Node buildLcarsTopBar(Color color) {
        HBox bar = new HBox();
        bar.setPadding(Insets.EMPTY);
        bar.setSpacing(0);

        // Left rounded block
        Region leftBlock = new Region();
        leftBlock.setPrefSize(140, 32);
        leftBlock.setBackground(new Background(
                new BackgroundFill(color, new CornerRadii(32, 0, 0, 32, false), Insets.EMPTY)
        ));
        leftBlock.setBorder(new Border(new BorderStroke(
                EDGE, BorderStrokeStyle.SOLID,
                new CornerRadii(32, 0, 0, 32, false),
                new BorderWidths(2)
        )));

        // Full-width stretch bar
        Region stretch = new Region();
        stretch.setPrefHeight(32);
        stretch.setBackground(new Background(
                new BackgroundFill(color, CornerRadii.EMPTY, Insets.EMPTY)
        ));
        HBox.setHgrow(stretch, Priority.ALWAYS);

        bar.getChildren().addAll(leftBlock, stretch);

        return bar;
    }

    private Node buildInvertedLcarsCap(Color color) {
        // Same size as your left block, but rounded on the BOTTOM instead of top
        Region block = new Region();
        block.setPrefSize(140, 32);
        block.setBackground(new Background(
            new BackgroundFill(
                color,
                new CornerRadii(0, 0, 32, 32, false),  // bottom-rounded
                Insets.EMPTY
            )
        ));

        block.setBorder(new Border(new BorderStroke(
            EDGE,
            BorderStrokeStyle.SOLID,
            new CornerRadii(0, 0, 32, 32, false),
            new BorderWidths(2)
        )));

        return block;
    }
    private Node buildLcarsTopCap(Color color) {
        Region cap = new Region();
        cap.setPrefSize(200, 26);
        cap.setBackground(new Background(
            new BackgroundFill(color,
                    new CornerRadii(0, 0, 26, 26, false),
                    Insets.EMPTY)
        ));
        cap.setBorder(new Border(new BorderStroke(
            EDGE, BorderStrokeStyle.SOLID,
            new CornerRadii(0, 0, 26, 26, false),
            new BorderWidths(2)
        )));

        return cap;
    }
    private Region buildLcarsGap() {
        Region gap = new Region();
        gap.setPrefHeight(6);   // tune between 4–8px
        gap.setBackground(new Background(new BackgroundFill(BG, CornerRadii.EMPTY, Insets.EMPTY)));
        return gap;
    }
    private Node buildLcarsMainBar(Color color) {
        HBox bar = new HBox();

        Region left = new Region();
        left.setPrefSize(170, 34);
        left.setBackground(new Background(
            new BackgroundFill(color,
                    new CornerRadii(34, 0, 0, 34, false),
                    Insets.EMPTY)
        ));
        left.setBorder(new Border(new BorderStroke(
            EDGE, BorderStrokeStyle.SOLID,
            new CornerRadii(34, 0, 0, 34, false),
            new BorderWidths(2)
        )));

        Region stretch = new Region();
        stretch.setPrefHeight(34);
        stretch.setBackground(new Background(
            new BackgroundFill(color, CornerRadii.EMPTY, Insets.EMPTY)
        ));
        HBox.setHgrow(stretch, Priority.ALWAYS);

        bar.getChildren().addAll(left, stretch);
        return bar;
    }

    private Node buildLcarsTopBar() {
        HBox bar = new HBox();
        bar.setPadding(Insets.EMPTY);
        bar.setSpacing(0);

        // Left rounded block (shorter + perfectly aligned)
        Region leftBlock = new Region();
        leftBlock.setPrefSize(140, 32);   // shorter height for LCARS alignment
        leftBlock.setBackground(new Background(
                new BackgroundFill(PEACH, new CornerRadii(32, 0, 0, 32, false), Insets.EMPTY)
        ));
        leftBlock.setBorder(new Border(new BorderStroke(
                EDGE, BorderStrokeStyle.SOLID,
                new CornerRadii(32, 0, 0, 32, false),
                new BorderWidths(2)
        )));

        // Horizontal bar (flush + same height)
        Region stretch = new Region();
        stretch.setPrefHeight(32);
        stretch.setBackground(new Background(
                new BackgroundFill(PEACH, CornerRadii.EMPTY, Insets.EMPTY)
        ));
        HBox.setHgrow(stretch, Priority.ALWAYS);

        bar.getChildren().addAll(leftBlock, stretch);

        // IMPORTANT: remove margins so bar sits at absolute top
        VBox.setMargin(bar, Insets.EMPTY);

        return bar;
    }


    private void openCommArrayPane() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(22));
        root.setBackground(new Background(
                new BackgroundFill(BG, CornerRadii.EMPTY, Insets.EMPTY)
        ));
        

        Label title = new Label("COMM ARRAY");
        title.setFont(lcarsFontOrDefault(22, true));
        title.setTextFill(AMBER);
        if (SETTINGS.glowEnabled) title.setEffect(createGlow(AMBER));

        // ==== WIFI ====
        Label wifiLabel = lcarsCaption("WIFI NETWORKS");
        Button wifiScanBtn = lcarsButton("SCAN WIFI", TEAL);

        TextArea wifiArea = new TextArea();
        wifiArea.setEditable(false);
        wifiArea.setWrapText(true);
        wifiArea.setPrefRowCount(10);
        wifiArea.setStyle("""
            -fx-control-inner-background: black;
            -fx-text-fill: #ffcc66;
            -fx-font-family: Consolas;
        """);

        wifiScanBtn.setOnAction(e -> scanWifiNetworks(wifiArea));
        VBox wifiBox = new VBox(8, wifiLabel, wifiScanBtn, wifiArea);

        // ==== BLUETOOTH ====
        Label btLabel = lcarsCaption("BLUETOOTH DEVICES");
        Button btScanBtn = lcarsButton("REFRESH BLUETOOTH", BLUE);

        TextArea btArea = new TextArea();
        btArea.setEditable(false);
        btArea.setWrapText(true);
        btArea.setPrefRowCount(8);
        btArea.setStyle("""
            -fx-control-inner-background: black;
            -fx-text-fill: #ffcc66;
            -fx-font-family: Consolas;
        """);

        btScanBtn.setOnAction(e -> refreshBluetoothDevices(btArea));
        VBox btBox = new VBox(8, btLabel, btScanBtn, btArea);

        // ==== AUDIO ====
        Label audioLabel = lcarsCaption("AUDIO SETTINGS");
        Slider volumeSlider = new Slider(0, 1, 0.5);
        volumeSlider.setShowTickLabels(true);
        volumeSlider.setShowTickMarks(true);

        Button muteBtn = lcarsButton("TOGGLE MUTE", SALMON);

        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            setSystemVolume(newVal.doubleValue());
        });

        muteBtn.setOnAction(e -> toggleSystemMute());

       

        Button close = lcarsButton("CLOSE", SALMON);
        close.setOnAction(e -> returnToDesktop());

        VBox audioPanel = buildAudioPanel();

        root.getChildren().addAll(
            title,
            wifiBox,
            btBox,
            audioPanel,
            spacer(8),
            close
        );


        showInDesktopPane(root);
    }
    private VBox buildLeftButtonBars() {
        VBox leftBarButtons = new VBox(6);

        // Top curve from LcarsApp2
        SVGPath curve = new SVGPath();
        curve.setContent("M0 0 200 0 200-82C200-100 220-100 220-100L220-123 50-123C23-123 0-120 0-90Z");
        curve.setFill(LAVENDER);
        leftBarButtons.setPadding(new Insets(0, 0, 0, 0));
        // Four LCARS rectangular blocks
        leftBarButtons.getChildren().addAll(
                curve,
                createLcarsSquare("FILE EXPLORER", PEACH, () -> openExplorerWindow(primaryStage)),
                createLcarsSquare("COMMAND PROMPT", AMBER, () -> openCMDPane(primaryStage)),
                createLcarsSquare("SETTINGS", SALMON, () -> openSettingsWindow(primaryStage)),
                createLcarsSquare("COMM ARRAY", BLUE, this::openCommArrayPane),
                createLcarsSquare("PLACEHOLDER", PEACH, this ::openCommArrayPane)
        );
        // ⭐ Add the system monitor at the bottom
        VBox monitor = buildSystemMonitor();
        leftBarButtons.getChildren().add(monitor);
        return leftBarButtons;
    }
    private StackPane createLcarsSquare(String label, Color color, Runnable action) {
        Rectangle box = new Rectangle(200, 200);
        box.setArcWidth(6);
        box.setArcHeight(6);
        box.setFill(color);

        Label text = new Label(label);
        text.setFont(lcarsFontOrDefault(20, true));
        text.setTextFill(Color.BLACK);

        StackPane pane = new StackPane(box, text);
        pane.setAlignment(Pos.CENTER_LEFT);
        StackPane.setMargin(text, new Insets(0, 18, 0, 0));

        pane.setOnMouseClicked(e -> {
            if (action != null) action.run();
        });

        return pane;
    }

    private VBox buildSystemMonitor() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(10));
        box.setBackground(new Background(
                new BackgroundFill(PANEL, new CornerRadii(12), Insets.EMPTY)
        ));
        box.setBorder(new Border(new BorderStroke(
                EDGE, BorderStrokeStyle.SOLID,
                new CornerRadii(12), new BorderWidths(1)
        )));

        Label cpuLabel = new Label("CPU USAGE");
        cpuLabel.setFont(lcarsFontOrDefault(12, true));
        cpuLabel.setTextFill(AMBER);

        ProgressBar cpuBar = new ProgressBar(0);
        cpuBar.setPrefWidth(160);
        cpuBar.setStyle("-fx-accent: #FF8C78;"); // SALMON

        Label ramLabel = new Label("RAM USAGE");
        ramLabel.setFont(lcarsFontOrDefault(12, true));
        ramLabel.setTextFill(AMBER);

        ProgressBar ramBar = new ProgressBar(0);
        ramBar.setPrefWidth(160);
        ramBar.setStyle("-fx-accent: #6699FF;"); // BLUE

        box.getChildren().addAll(cpuLabel, cpuBar, ramLabel, ramBar);

        // Start updating
        startSystemMonitor(cpuBar, ramBar);

        return box;
    }

    private void animateBar(ProgressBar bar, double value) {
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(bar.progressProperty(), bar.getProgress())),
                new KeyFrame(Duration.seconds(0.5),
                        new KeyValue(bar.progressProperty(), value))
        );
        t.play();
    }

    private void startSystemMonitor(ProgressBar cpuBar, ProgressBar ramBar) {
        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(1), e -> {

            new Thread(() -> {
                double cpu = getCpuUsage();
                Platform.runLater(() -> cpuBar.setProgress(cpu));
            }).start();

            new Thread(() -> {
                double ram = getRamUsage();
                Platform.runLater(() -> ramBar.setProgress(ram));
            }).start();

        }));
        tl.setCycleCount(Animation.INDEFINITE);
        tl.play();
    }

    private double getCpuUsage() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-Command",
                    "(Get-Counter '\\Processor(_Total)\\% Processor Time').CounterSamples.CookedValue"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();

            if (line != null) {
                double val = Double.parseDouble(line.trim());
                return Math.min(1.0, Math.max(0.0, val / 100.0));
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    private double getRamUsage() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-Command",
                    "$t = Get-CimInstance Win32_OperatingSystem;" +
                    "($t.TotalVisibleMemorySize - $t.FreePhysicalMemory) / $t.TotalVisibleMemorySize"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();

            if (line != null) {
                return Math.min(1.0, Math.max(0.0, Double.parseDouble(line.trim())));
            }
        } catch (Exception ignored) {}
        return 0.0;
    }
    private VBox buildAudioPanel() {

        VBox box = new VBox(12);
        box.setPadding(new Insets(12));
        box.setBackground(new Background(
                new BackgroundFill(PANEL, new CornerRadii(18), Insets.EMPTY)
        ));
        box.setBorder(new Border(new BorderStroke(
                EDGE, BorderStrokeStyle.SOLID, new CornerRadii(18), new BorderWidths(1)
        )));

        Label title = new Label("AUDIO CONTROL");
        title.setFont(lcarsFontOrDefault(20, true));
        title.setTextFill(AMBER);

        // Master Volume Slider
        Label volLabel = new Label("MASTER VOLUME");
        volLabel.setFont(lcarsFontOrDefault(14, true));
        volLabel.setTextFill(PEACH);

        Slider volSlider = new Slider(0, 1, 0.5);
        volSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double v = newVal.doubleValue();
            setSystemVolume(v);
        });

        volSlider.setPrefWidth(260);
        volSlider.valueProperty().addListener((o, oldV, newV) ->
                setSystemVolume(newV.doubleValue())
        );

        // Mute Button
        Button muteBtn = lcarsButton("TOGGLE MUTE", SALMON);
        muteBtn.setOnAction(e -> toggleSystemMute());

        // Device Selector
        Label devLabel = new Label("OUTPUT DEVICE");
        devLabel.setFont(lcarsFontOrDefault(14, true));
        devLabel.setTextFill(PEACH);

        ComboBox<String> deviceSelector = new ComboBox<>();
        deviceSelector.setPrefWidth(260);

        Button applyDevice = lcarsButton("SWITCH DEVICE", BLUE);
        applyDevice.setOnAction(e -> {
            if (deviceSelector.getValue() != null)
                setOutputDevice(deviceSelector.getValue());
        });

        // Populate devices
        new Thread(() -> {
            List<String> devices = listOutputDevices();
            Platform.runLater(() -> deviceSelector.getItems().addAll(devices));
        }).start();

        // Live sync with system volume
        startAudioMonitor(volSlider);

        box.getChildren().addAll(
                title,
                volLabel, volSlider, muteBtn,
                devLabel, deviceSelector, applyDevice
        );

        return box;
    }

    private void toggleSystemMute() {
        try {
            Robot robot = new Robot();
            robot.keyPress(0x10000 | 0x20);
            robot.keyRelease(0x10000 | 0x20);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    private void setOutputDevice(String namePart) {
        try {
            String cmd = POWERSHELL + " -Command \""
                    + "Set-AudioDevice -Playback ("
                    + "Get-AudioDevice -List | "
                    + "Where-Object { $_.Type -eq 'Playback' -and $_.Name -like '*" + namePart + "*' })\"";

            Runtime.getRuntime().exec(cmd);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private List<String> listOutputDevices() {

        List<String> result = new ArrayList<>();

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    POWERSHELL, "-Command",
                    "Get-AudioDevice -List | "
                    + "Where-Object { $_.Type -eq 'Playback' } | "
                    + "Select-Object -ExpandProperty Name"
            );

            pb.redirectErrorStream(true);
            Process p = pb.start();

            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;

            while ((line = r.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    result.add(line.trim());
                }
            }

        } catch (Exception ignored) {}

        return result;
    }

    private void startAudioMonitor(Slider slider) {

        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            new Thread(() -> {
                double vol = getSystemVolume();
                Platform.runLater(() -> {
                    if (!slider.isValueChanging()) {
                        slider.setValue(vol);
                    }
                });
            }).start();
        }));

        tl.setCycleCount(Animation.INDEFINITE);
        tl.play();
    }

    private double getSystemVolume() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    POWERSHELL, "-Command",
                    "(Get-AudioDevice -Playback).Volume / 100"
            );

            pb.redirectErrorStream(true);
            Process p = pb.start();

            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();

            if (line != null) {
                return Double.parseDouble(line.trim());
            }

        } catch (Exception ignored) {}

        return 0.5;
    }


    private void scanWifiNetworks(TextArea wifiArea) {
        wifiArea.setText("Scanning...\n");

        new Thread(() -> {
            List<String> out = new ArrayList<>();
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "netsh", "wlan", "show", "networks", "mode=Bssid"
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();

                BufferedReader br = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)
                );

                String line;
                while ((line = br.readLine()) != null)
                    out.add(line);

            } catch (Exception ex) {
                out.add("ERROR: " + ex.getMessage());
            }

            Platform.runLater(() -> {
                wifiArea.clear();
                out.forEach(s -> wifiArea.appendText(s + "\n"));
            });
        }).start();
    }
    private void refreshBluetoothDevices(TextArea btArea) {
        btArea.setText("Querying Bluetooth...\n");

        new Thread(() -> {
            List<String> out = new ArrayList<>();

            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "powershell", "-Command",
                        "Get-PnpDevice -Class Bluetooth | Select-Object Name,Status"
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();

                BufferedReader br = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)
                );

                String line;
                while ((line = br.readLine()) != null)
                    out.add(line);

            } catch (Exception ex) {
                out.add("ERROR: " + ex.getMessage());
            }

            Platform.runLater(() -> {
                btArea.clear();
                out.forEach(s -> btArea.appendText(s + "\n"));
            });
        }).start();
    }

    private void setSystemVolume(double sliderValue) {
        try {
            Robot robot = new Robot();

            // Convert slider (0.0 – 1.0) into Windows' approx. 50 volume steps
            int targetSteps = (int)(sliderValue * 50);
            int diff = targetSteps - lastVolumeSteps;

            if (diff > 0) {
                for (int i = 0; i < diff; i++) {
                	robot.keyPress(0x10000 | 0x30);
                	robot.keyRelease(0x10000 | 0x30);


                }
            } else if (diff < 0) {
                for (int i = 0; i < Math.abs(diff); i++) {
                	robot.keyPress(0x10000 | 0x2E);
                	robot.keyRelease(0x10000 | 0x2E);

                }
            }

            lastVolumeSteps = targetSteps;

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }




    // Swap right-hand desktop pane content
    private void showInDesktopPane(Node page) {
        if (desktopContentHolder == null) return;
        desktopContentHolder.getChildren().setAll(page);
    }

    // Restore original desktop canvas + holo drive
    private void returnToDesktop() {
        if (desktopContentHolder == null || desktopDefaultContent == null) return;
        desktopContentHolder.getChildren().setAll(desktopDefaultContent);
    }

    // ===== Embedded CMD Pane (no new Stage) =====
    private void openCMDPane(Stage owner) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.setBackground(new Background(
                new BackgroundFill(BG, CornerRadii.EMPTY, Insets.EMPTY)
        ));

        TextArea output = new TextArea();
        output.setEditable(false);
        output.setWrapText(true);
        output.setStyle("""
            -fx-control-inner-background: black;
            -fx-font-family: Consolas;
            -fx-highlight-fill: #ffcc66;
            -fx-highlight-text-fill: black;
            -fx-text-fill: #ffcc66;
            -fx-font-size: 14px;
            """);

        HBox inputRow = new HBox(10);
        TextField input = new TextField();
        input.setStyle("""
            -fx-background-color: #1a1a1a;
            -fx-text-fill: #ffcc66;
            -fx-border-color: #333333;
            -fx-font-family: Consolas;
            -fx-font-size: 14px;
            """);
        input.setPromptText("Enter command...");

        Button runBtn = lcarsButton("EXECUTE", SALMON);

        inputRow.getChildren().addAll(input, runBtn);
        HBox.setHgrow(input, Priority.ALWAYS);

        Button closeBtn = lcarsButton("CLOSE", SALMON);
        closeBtn.setOnAction(e -> returnToDesktop());
        HBox topRow = new HBox(closeBtn);

        Runnable exec = () -> {
            String cmd = input.getText().trim();
            if (cmd.isEmpty()) return;

            output.appendText("\n> " + cmd + "\n");
            input.clear();

            new Thread(() -> {
                try {
                    ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", cmd);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();

                    BufferedReader r = new BufferedReader(
                            new InputStreamReader(p.getInputStream())
                    );

                    String line;
                    while ((line = r.readLine()) != null) {
                        String outLine = line;
                        Platform.runLater(() -> {
                            output.appendText(outLine + "\n");
                            output.setScrollTop(Double.MAX_VALUE);
                        });
                    }
                } catch (Exception ex) {
                    String msg = ex.getMessage();
                    Platform.runLater(() -> output.appendText("ERROR: " + msg + "\n"));
                }
            }, "cmd-runner").start();
        };

        input.setOnAction(e -> exec.run());
        runBtn.setOnAction(e -> exec.run());

        root.setTop(topRow);
        root.setCenter(output);
        root.setBottom(inputRow);

        showInDesktopPane(root);
        Platform.runLater(input::requestFocus);
    }

    // === HEADER: small username box sitting ON the amber bar at the right end ===
    private VBox buildHeaderBarsWithUserPill() {
		double barh1 = 90, barH2 = 20;

		// === Time Clock ===
		StackPane r1 = new StackPane();
		r1.setMinHeight(barh1);
		r1.setPrefHeight(barh1);
		Label timeClockLabel = new Label();
		timeClockLabel.setTextFill(Color.web("#f89d00"));
	    timeClockLabel.setFont(lcarsFontOrDefault(78, true)); // add label to the top bar
	    StackPane.setAlignment(timeClockLabel, Pos.CENTER_LEFT);
		
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
		clock = new Timeline(
				new KeyFrame(Duration.ZERO, e -> {
					String Time = LocalTime.now().format(formatter);
					timeClockLabel.setText(Time);
				}),new KeyFrame(Duration.seconds(1))
				);

		clock.setCycleCount(Animation.INDEFINITE);
		clock.play();
		
		//===curve Button==
		SVGPath curve = new SVGPath();
	    curve.setContent("M 0 0 L 200 0 L 200 82 C 200 100 220 100 235 100 L 235 123 L 50 123 C 23 123 0 120 0 90 Z"); 
	    curve.setFill(AMBER); 
	    curve.setPickOnBounds(true);
	    
	    curve.setOnMouseClicked(e -> {
		    System.out.println("Rectangle button clicked!");
		});

	    
	    HBox leftBox = new HBox(16); // spacing 8px
	    leftBox.getChildren().addAll(curve, timeClockLabel);
	    leftBox.setAlignment(Pos.TOP_LEFT);
	    HBox.setMargin(timeClockLabel, new Insets(0, 0, 0, 4)); // small gap if needed

	    r1.getChildren().add(leftBox);
	    StackPane.setAlignment(leftBox, Pos.TOP_LEFT);


		// === Bars ===
		StackPane amberRow = new StackPane();
		Region r2 = new Region();
		r2.setMinHeight(barH2);
		r2.setPrefHeight(barH2);
		r2.setBackground(new Background(new BackgroundFill(AMBER, null, null)));
		amberRow.setMargin(r2,new Insets(0,0,0,100));
		amberRow.getChildren().add(r2);
		
		// === Username box ===
		String username = System.getProperty("user.name", "USER").toUpperCase(Locale.ROOT);
		HBox box = new HBox(8);
		box.setPadding(new Insets(2, 8, 2, 8));
		box.setBackground(new Background(new BackgroundFill(PANEL, CornerRadii.EMPTY, Insets.EMPTY)));
		box.setBorder(new Border(new BorderStroke(EDGE, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(1))));
		box.setMouseTransparent(true);

		// prevent stretching
		box.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
		box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

		Label nameLabel = new Label(username);
		nameLabel.setTextFill(AMBER.brighter());
		nameLabel.setFont(lcarsFontOrDefault(12, true));
		box.getChildren().add(nameLabel);

		amberRow.getChildren().add(box);
		StackPane.setAlignment(box, Pos.CENTER_RIGHT);
		StackPane.setMargin(box, new Insets(0, 18, 0, 0));


		return new VBox(10, r1, amberRow);
	}

    // === HOLOGRAPHIC HARD-DRIVE (bigger hub) + compact storage bar + drive name ===
    private Node buildHoloDriveWithStorage() {
        File systemDrive = getSystemDriveRoot();
        String driveName = (systemDrive != null) ? systemDrive.getPath() : "SYSTEM";
        long total = (systemDrive != null) ? systemDrive.getTotalSpace() : 0L;
        long free = (systemDrive != null) ? systemDrive.getFreeSpace() : 0L;
        long used = Math.max(0L, total - free);

        Node disk = buildHoloDriveVisual(120, 26); // R=120, hub=26 (bigger red circle)

        Label driveLabel = new Label(driveName);
        driveLabel.setFont(lcarsFontOrDefault(12, true));
        driveLabel.setTextFill(AMBER);

        VBox storageBox = buildStorageGraph(used, total); // compact

        VBox wrap = new VBox(disk, driveLabel, storageBox);
        wrap.setAlignment(Pos.TOP_CENTER);
        wrap.setSpacing(6);
        wrap.setOpacity(0.95);
        wrap.setPickOnBounds(false);
        wrap.setMouseTransparent(true);

        return wrap;
    }

    // Visual-only disk; R = outer radius, hubR = center red circle radius
    private Node buildHoloDriveVisual(double R, double hubR) {
        StackPane disk = new StackPane();
        disk.setPickOnBounds(false);

        Circle outer = new Circle(R);
        outer.setFill(Color.color(0.1, 0.7, 1.0, 0.18));
        outer.setStroke(Color.web("#66CCFF"));
        outer.setStrokeWidth(2);

        Circle ring1 = new Circle(R - 10);
        ring1.setFill(Color.TRANSPARENT);
        ring1.setStroke(Color.web("#60C8C8"));
        ring1.setStrokeWidth(2);

        Circle ring2 = new Circle(R - 26);
        ring2.setFill(Color.TRANSPARENT);
        ring2.setStroke(Color.web("#FFCC66"));
        ring2.setStrokeWidth(2);

        // Bigger red (salmon) hub
        Circle hub = new Circle(hubR);
        hub.setFill(SALMON);
        hub.setStroke(Color.web("#333333"));
        hub.setStrokeWidth(2);

        Arc a1 = new Arc(0, 0, R - 18, R - 18, 0, 60);
        Arc a2 = new Arc(0, 0, R - 18, R - 18, 120, 60);
        Arc a3 = new Arc(0, 0, R - 18, R - 18, 240, 60);
        for (Arc a : new Arc[] { a1, a2, a3 }) {
            a.setFill(Color.TRANSPARENT);
            a.setStroke(PEACH);
            a.setStrokeWidth(5);
            a.setType(ArcType.OPEN);
            a.setStrokeLineCap(StrokeLineCap.ROUND);
            a.setOpacity(0.85);
        }

        Circle sweep = new Circle(R - 8);
        sweep.setFill(Color.TRANSPARENT);
        sweep.setStroke(Color.web("#FFFFFF"));
        sweep.setStrokeWidth(1.2);
        sweep.getStrokeDashArray().setAll(10.0, 32.0);
        sweep.setOpacity(0.6);

        DropShadow glow = new DropShadow();
        glow.setRadius(30);
        glow.setSpread(0.35);
        glow.setColor(Color.web("#66CCFFFF"));
        glow.setOffsetX(0);
        glow.setOffsetY(0);
        outer.setEffect(glow);

        disk.getChildren().addAll(outer, ring1, ring2, a1, a2, a3, sweep, hub);

        RotateTransition spin = new RotateTransition(Duration.seconds(6), disk);
        spin.setByAngle(360);
        spin.setCycleCount(Animation.INDEFINITE);
        spin.setInterpolator(Interpolator.LINEAR);
        spin.play();

        RotateTransition sweepSpin = new RotateTransition(Duration.seconds(3.2), sweep);
        sweepSpin.setByAngle(-360);
        sweepSpin.setCycleCount(Animation.INDEFINITE);
        sweepSpin.setInterpolator(Interpolator.LINEAR);
        sweepSpin.play();

        Timeline breathe = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(outer.opacityProperty(), 0.22)),
                new KeyFrame(Duration.seconds(1.4), new KeyValue(outer.opacityProperty(), 0.40)),
                new KeyFrame(Duration.seconds(2.8), new KeyValue(outer.opacityProperty(), 0.22))
        );
        breathe.setAutoReverse(false);
        breathe.setCycleCount(Animation.INDEFINITE);
        breathe.play();

        return disk;
    }

    // Much smaller storage bar
    private VBox buildStorageGraph(long used, long total) {

        double height = 14;

        Region bgBar = new Region();
        bgBar.setMinHeight(height);
        bgBar.setPrefHeight(height);
        bgBar.setBackground(new Background(
                new BackgroundFill(Color.web("#1a1a1a"), new CornerRadii(height), Insets.EMPTY)
        ));
        bgBar.setBorder(new Border(new BorderStroke(
                EDGE, BorderStrokeStyle.SOLID, new CornerRadii(height), new BorderWidths(1)
        )));

        double frac = (total > 0) ? Math.min(1.0, Math.max(0.0, (double) used / total)) : 0.0;

        Region usedBar = new Region();
        usedBar.setMinHeight(height);
        usedBar.setPrefHeight(height);
        usedBar.setBackground(new Background(
                new BackgroundFill(AMBER, new CornerRadii(height), Insets.EMPTY)
        ));

        usedBar.maxWidthProperty().bind(
                bgBar.widthProperty().multiply(frac)
        );

        StackPane barStack = new StackPane(bgBar);
        StackPane.setAlignment(usedBar, Pos.CENTER_LEFT);
        barStack.getChildren().add(usedBar);

        Label cap = new Label((total > 0)
                ? humanSize(used) + " / " + humanSize(total)
                : "Storage unavailable");
        cap.setFont(lcarsFontOrDefault(11, true));
        cap.setTextFill(PEACH);

        VBox box = new VBox(4, barStack, cap);
        box.setAlignment(Pos.TOP_CENTER);
        box.setFillWidth(true);

        return box;
    }

    private File getSystemDriveRoot() {
        try {
            Path home = Paths.get(System.getProperty("user.home", "."));
            Path root = home.getRoot();
            if (root != null)
                return root.toFile();
        } catch (Exception ignored) {
        }
        File[] roots = File.listRoots();
        return (roots != null && roots.length > 0) ? roots[0] : null;
    }

    private void styleCheckbox(CheckBox cb) {
        cb.setTextFill(Color.web("#FFCC66"));
        cb.setFont(lcarsFontOrDefault(14, false));
    }

    private void refreshUI(Stage stage) {
        if (stage == null || stage.getScene() == null) return;

        Parent root = stage.getScene().getRoot();

        // Update fonts everywhere
        updateFonts(root);

        // Update glow
        applyGlowSetting(stage.getScene());
        refreshGlow(stage.getScene().getRoot());

        // Update stage transparency
        stage.setOpacity(SETTINGS.transparentUI ? 0.92 : 1.0);

        // Update background if the root is a Region
        if (root instanceof Region reg) {
            if (SETTINGS.transparentUI) {
                reg.setBackground(Background.EMPTY);
                reg.setStyle("-fx-background-color: transparent;");
            } else {
                reg.setBackground(new Background(new BackgroundFill(BG, CornerRadii.EMPTY, Insets.EMPTY)));
                reg.setStyle("");
            }
        }
    }

    private void updateFonts(Node node) {
        if (node instanceof Label lbl) {
            Font f = lbl.getFont();
            boolean bold = f.getStyle().contains("Bold");
            lbl.setFont(lcarsFontOrDefault(f.getSize(), bold));
        }
        else if (node instanceof Button btn) {
            Font f = btn.getFont();
            boolean bold = f.getStyle().contains("Bold");
            btn.setFont(lcarsFontOrDefault(f.getSize(), bold));
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                updateFonts(child);
            }
        }
    }

    // Settings window (embedded in desktop pane)
    private void openSettingsWindow(Stage owner) {
        VBox root = new VBox(16);
        root.setPadding(new Insets(22));
        root.setBackground(
                SETTINGS.transparentUI
                        ? Background.EMPTY
                        : new Background(new BackgroundFill(BG, CornerRadii.EMPTY, Insets.EMPTY))
        );

        Label title = new Label("SYSTEM SETTINGS");
        title.setFont(lcarsFontOrDefault(22, true));
        title.setTextFill(AMBER);
        if (SETTINGS.glowEnabled) title.setEffect(createGlow(AMBER));

        CheckBox soundToggle = new CheckBox("Enable Ambient Sounds");
        styleCheckbox(soundToggle);
        soundToggle.setSelected(SETTINGS.ambientSound);

        CheckBox clickToggle = new CheckBox("Enable UI Click Sounds");
        styleCheckbox(clickToggle);
        clickToggle.setSelected(SETTINGS.clickSound);

        CheckBox glowToggle = new CheckBox("Enable LCARS Glow Effects");
        styleCheckbox(glowToggle);
        glowToggle.setSelected(SETTINGS.glowEnabled);

        CheckBox fontToggle = new CheckBox("Use LCARS Font (requires lcars.ttf)");
        styleCheckbox(fontToggle);
        fontToggle.setSelected(SETTINGS.useLcarsFont);

        soundToggle.setOnAction(e -> {
            SETTINGS.ambientSound = soundToggle.isSelected();
            saveSettings();
            if (SETTINGS.ambientSound) playBackgroundSounds();
            else if (bgm != null) bgm.stop();
        });
        
        Label themeLabel = new Label("Theme Color");
        themeLabel.setFont(lcarsFontOrDefault(14, true));
        themeLabel.setTextFill(AMBER);

        ComboBox<String> themeSelector = new ComboBox<>();
        themeSelector.getItems().addAll(
                "TNG PEACH",
                "VOYAGER GOLD",
                "SOVEREIGN BLUE",
                "ALTERNATE DARK"
        );

        themeSelector.setValue(SETTINGS.theme);
        themeSelector.setPrefWidth(240);

        Button applyThemeBtn = lcarsButton("APPLY THEME", TEAL);

        applyThemeBtn.setOnAction(e -> applyTheme(themeSelector.getValue()));



        clickToggle.setOnAction(e -> {
            SETTINGS.clickSound = clickToggle.isSelected();
            saveSettings();
        });

        glowToggle.setOnAction(e -> {
            SETTINGS.glowEnabled = glowToggle.isSelected();
            saveSettings();
            if (primaryStage != null) {
                applyGlowSetting(primaryStage.getScene());
                refreshGlow(primaryStage.getScene().getRoot());
            }
        });

        fontToggle.setOnAction(e -> {
            SETTINGS.useLcarsFont = fontToggle.isSelected();
            saveSettings();
            if (primaryStage != null) refreshUI(primaryStage);
        });

        Label scaleLabel = new Label("UI Scale");
        scaleLabel.setTextFill(AMBER);
        scaleLabel.setFont(lcarsFontOrDefault(14, true));

        Button smaller = lcarsButton("SMALLER", PEACH);
        Button larger = lcarsButton("LARGER", SALMON);

        smaller.setOnAction(e -> {
            SETTINGS.uiScale = 0.9;
            saveSettings();
            if (primaryStage != null) refreshUI(primaryStage);
        });

        larger.setOnAction(e -> {
            SETTINGS.uiScale = 1.1;
            saveSettings();
            if (primaryStage != null) refreshUI(primaryStage);
        });

        HBox scaleRow = new HBox(8, smaller, larger);
        Button close = lcarsButton("CLOSE", SALMON);
        close.setOnAction(e -> returnToDesktop());

        root.getChildren().addAll(
        	    title,
        	    soundToggle,
        	    clickToggle,
        	    glowToggle,
        	    fontToggle,
        	    themeLabel,
        	    themeSelector,
        	    applyThemeBtn,
        	    scaleLabel,
        	    scaleRow,
        	    spacer(12),
        	    close
        	);

        showInDesktopPane(root);
    }

    // ===== Explorer (embedded in desktop pane) =====
    private void openExplorerWindow(Window owner) {
        File start;
        boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWin && new File("C:\\").exists())
            start = new File("C:\\");
        else {
            File[] roots = File.listRoots();
            start = (roots != null && roots.length > 0) ? roots[0] : new File("/");
        }

        ExplorerView view = buildExplorerView(start);

        StackPane wrap = new StackPane(view.root);
        attachConnectivityMonitor(wrap);

        Button close = lcarsButton("CLOSE", SALMON);
        close.setOnAction(e -> returnToDesktop());

        VBox container = new VBox(10, close, wrap);
        container.setPadding(new Insets(10));

        showInDesktopPane(container);
    }

    private ExplorerView buildExplorerView(File startDir) {
        VBox bars = new VBox(6, lcarsBar(SALMON, 34), lcarsBar(AMBER, 20), lcarsBar(BLUE, 12));

        Button btnBack = lcarsButton("BACK", SALMON);
        Button btnFwd = lcarsButton("FWD", TEAL);
        Button btnUp = lcarsButton("UP", BLUE);
        Button btnHome = lcarsButton("HOME", AMBER);
        Button btnRef = lcarsButton("REFRESH", PEACH);

        Button btnCreate = lcarsButton("CREATE FILE", PEACH);
        Button btnDelete = lcarsButton("DELETE SELECTED", SALMON);
        Button btnMove = lcarsButton("MOVE TO DESKTOP", AMBER);

        Button btnCopy = lcarsButton("COPY", TEAL);
        Button btnCut = lcarsButton("CUT", AMBER);
        Button btnPaste = lcarsButton("PASTE", BLUE);

        Region spacerGrow = new Region();
        HBox toolbar = new HBox(10,
                btnBack, btnFwd, btnUp, btnHome, btnRef,
                btnCopy, btnCut, btnPaste, spacerGrow,
                btnCreate, btnDelete, btnMove
        );
        HBox.setHgrow(spacerGrow, Priority.ALWAYS);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button qDesktop = lcarsButton("DESKTOP", PEACH);
        Button qDocs = lcarsButton("DOCUMENTS", SALMON);
        Button qDown = lcarsButton("DOWNLOADS", TEAL);
        Button qPics = lcarsButton("PICTURES", BLUE);
        Button qMusic = lcarsButton("MUSIC", AMBER);
        Button qVid = lcarsButton("VIDEOS", PEACH);
        HBox quick = new HBox(8, qDesktop, qDocs, qDown, qPics, qMusic, qVid);
        quick.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(bars, spacer(6), toolbar, spacer(4), quick);

        FlowPane breadcrumbs = new FlowPane(6, 6);
        breadcrumbs.setRowValignment(VPos.CENTER);
        BorderPane.setMargin(breadcrumbs, new Insets(6, 6, 6, 6));

        ListView<File> list = new ListView<>();
        list.setFocusTraversable(false);
        list.setCellFactory(v -> new LcarsFileCell());
        list.setStyle("""
            -fx-background-color: transparent;
            -fx-control-inner-background: #000000;
            -fx-control-inner-background-alt: #000000;
            -fx-cell-hover-color: #141414;
            -fx-background-insets: 0;
            -fx-padding: 0;
            """);
        Region listCard = roundedCard(list, PANEL);

        VBox rightProps = new VBox(10);
        rightProps.setPadding(new Insets(16));
        Label rpTitle = lcarsCaption("PROPERTIES");
        ImageView rightPreview = new ImageView();
        rightPreview.setFitWidth(360);
        rightPreview.setFitHeight(260);
        rightPreview.setPreserveRatio(true);

        ScrollPane rpScroll = new ScrollPane(rightProps);
        rpScroll.setFitToWidth(true);
        rpScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox rightPane = new VBox(10, new VBox(rpTitle, lcarsBar(PEACH, 6)), rpScroll, rightPreview);
        Region rightCard = roundedCard(rightPane, PANEL);
        rightCard.setPrefWidth(460);

        Region rightFramed = frameRightWithLeftLFlush(rightCard);

        Region gap = new Region();
        gap.setPrefWidth(48);
        HBox mid = new HBox(16, listCard, gap, rightFramed);
        HBox.setHgrow(listCard, Priority.ALWAYS);

        BorderPane center = new BorderPane();
        center.setTop(breadcrumbs);
        center.setCenter(mid);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.setBackground(new Background(new BackgroundFill(BG, CornerRadii.EMPTY, Insets.EMPTY)));
        root.setTop(header);
        root.setCenter(center);

        Deque<File> back = new ArrayDeque<>();
        Deque<File> fwd = new ArrayDeque<>();
        File[] current = new File[] { startDir };

        Runnable updateNavButtons = () -> {
            btnBack.setDisable(back.isEmpty());
            btnFwd.setDisable(fwd.isEmpty());
            btnUp.setDisable(current[0].getParentFile() == null);
        };

        btnBack.setOnAction(e -> {
            if (!back.isEmpty()) {
                fwd.push(current[0]);
                File prev = back.pop();
                navigateTo(current, prev, false, back, fwd, list, breadcrumbs, rightProps, rightPreview,
                        updateNavButtons);
            }
        });
        btnFwd.setOnAction(e -> {
            if (!fwd.isEmpty()) {
                back.push(current[0]);
                File nxt = fwd.pop();
                navigateTo(current, nxt, false, back, fwd, list, breadcrumbs, rightProps, rightPreview,
                        updateNavButtons);
            }
        });
        btnUp.setOnAction(e -> {
            File p = current[0].getParentFile();
            if (p != null)
                navigateTo(current, p, true, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons);
        });
        btnHome.setOnAction(e -> navigateTo(current, startDir, true, back, fwd, list, breadcrumbs, rightProps,
                rightPreview, updateNavButtons));
        btnRef.setOnAction(e -> navigateTo(current, current[0], false, back, fwd, list, breadcrumbs, rightProps,
                rightPreview, updateNavButtons));

        qDesktop.setOnAction(e -> navKnown("Desktop", current, back, fwd, list, breadcrumbs, rightProps, rightPreview,
                updateNavButtons));
        qDocs.setOnAction(e -> navKnown("Documents", current, back, fwd, list, breadcrumbs, rightProps, rightPreview,
                updateNavButtons));
        qDown.setOnAction(e -> navKnown("Downloads", current, back, fwd, list, breadcrumbs, rightProps, rightPreview,
                updateNavButtons));
        qPics.setOnAction(e -> navKnown("Pictures", current, back, fwd, list, breadcrumbs, rightProps, rightPreview,
                updateNavButtons));
        qMusic.setOnAction(e -> navKnown("Music", current, back, fwd, list, breadcrumbs, rightProps, rightPreview,
                updateNavButtons));
        qVid.setOnAction(e -> navKnown("Videos", current, back, fwd, list, breadcrumbs, rightProps, rightPreview,
                updateNavButtons));

        list.setOnMouseClicked(ev -> {
            File sel = list.getSelectionModel().getSelectedItem();
            if (sel == null)
                return;
            if (ev.getClickCount() == 2) {
                if (sel.isDirectory())
                    navigateTo(current, sel, true, back, fwd, list, breadcrumbs, rightProps, rightPreview,
                            updateNavButtons);
                else
                    openWithDesktop(sel);
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null)
                populatePropertiesFX(rightProps, rightPreview, sel);
        });

        final File[] clipboard = new File[1];
        final boolean[] isCut = new boolean[1];

        btnCopy.setOnAction(e -> {
            File sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) {
                showAlert("Copy", "Select a file or folder first.");
                return;
            }
            clipboard[0] = sel;
            isCut[0] = false;
            showAlert("Copy", "Copied: " + sel.getName());
        });
        btnCut.setOnAction(e -> {
            File sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) {
                showAlert("Cut", "Select a file or folder first.");
                return;
            }
            clipboard[0] = sel;
            isCut[0] = true;
            showAlert("Cut", "Cut: " + sel.getName());
        });
        btnPaste.setOnAction(e -> {
            if (clipboard[0] == null) {
                showAlert("Paste", "Clipboard is empty. Use COPY or CUT first.");
                return;
            }
            File src = clipboard[0];
            File dst = uniqueName(new File(current[0], src.getName()));

            try {
                if (isCut[0]) {
                    try {
                        Files.move(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException moveEx) {
                        if (src.isDirectory()) {
                            copyRecursive(src.toPath(), dst.toPath());
                            deleteRecursive(src.toPath());
                        } else {
                            Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            Files.deleteIfExists(src.toPath());
                        }
                    }
                    if (DESKTOP_CANVAS != null) {
                        Platform.runLater(() -> {
                            DESKTOP_CANVAS.onFileMoved(src, dst);
                            DESKTOP_CANVAS.saveState();
                        });
                    }
                    clipboard[0] = null;
                    isCut[0] = false;
                } else {
                    if (src.isDirectory())
                        copyRecursive(src.toPath(), dst.toPath());
                    else
                        Files.copy(src.toPath(), dst.toPath());
                    if (DESKTOP_CANVAS != null && isInDesktop(dst)) {
                        Platform.runLater(() -> {
                            DESKTOP_CANVAS.addIcon(dst, src.getParentFile());
                            DESKTOP_CANVAS.saveState();
                        });
                    }
                }

                navigateTo(current, current[0], false, back, fwd, list, breadcrumbs, rightProps, rightPreview,
                        updateNavButtons);
            } catch (IOException ex) {
                showAlert("Paste", ex.getMessage());
            }
        });

        btnCreate.setOnAction(e -> {
            TextInputDialog t = new TextInputDialog("NewFile.txt");
            t.setTitle("Create File");
            t.setHeaderText("Enter a file name:");
            t.setContentText("Name:");
            Optional<String> r = t.showAndWait();
            if (r.isPresent() && !r.get().trim().isEmpty()) {
                File nf = new File(current[0], r.get().trim());
                try {
                    if (nf.createNewFile())
                        navigateTo(current, current[0], false, back, fwd, list, breadcrumbs, rightProps, rightPreview,
                                updateNavButtons);
                    else
                        showAlert("Create File", "File already exists.");
                } catch (IOException ex) {
                    showAlert("Create File", ex.getMessage());
                }
            }
        });

        btnDelete.setOnAction(e -> {
            File sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) {
                showAlert("Delete", "Select a file or folder first.");
                return;
            }
            Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete \"" + sel.getName() + "\"?", ButtonType.YES, ButtonType.NO);
            conf.setHeaderText("Confirm Delete");
            if (conf.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                try {
                    if (sel.isDirectory())
                        deleteRecursive(sel.toPath());
                    else
                        Files.deleteIfExists(sel.toPath());

                    if (DESKTOP_CANVAS != null && isInDesktop(sel)) {
                        Platform.runLater(() -> {
                            DESKTOP_CANVAS.removeTileFor(sel);
                            DESKTOP_CANVAS.saveState();
                        });
                    }
                    navigateTo(current, current[0], false, back, fwd, list, breadcrumbs, rightProps, rightPreview,
                            updateNavButtons);
                } catch (IOException ex) {
                    showAlert("Delete", ex.getMessage());
                }
            }
        });

        btnMove.setOnAction(e -> {
            File sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) {
                showAlert("Move to Desktop", "Select a file or folder first.");
                return;
            }
            File desktop = knownFolder("Desktop");
            if (desktop == null) {
                showAlert("Move to Desktop", "Desktop folder not found.");
                return;
            }
            File originDir = sel.getParentFile();
            File target = uniqueName(new File(desktop, sel.getName()));
            try {
                try {
                    Files.move(sel.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveEx) {
                    if (sel.isDirectory())
                        copyRecursive(sel.toPath(), target.toPath());
                    else
                        Files.copy(sel.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    deleteRecursive(sel.toPath());
                }
                navigateTo(current, current[0], false, back, fwd, list, breadcrumbs, rightProps, rightPreview,
                        updateNavButtons);
                if (DESKTOP_CANVAS != null)
                    Platform.runLater(() -> {
                        DESKTOP_CANVAS.addIcon(target, originDir);
                        DESKTOP_CANVAS.saveState();
                    });
            } catch (IOException ex) {
                showAlert("Move to Desktop", ex.getMessage());
            }
        });

        navigateTo(current, startDir, false, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons);

        qDesktop.setDisable(knownFolder("Desktop") == null);
        qDocs.setDisable(knownFolder("Documents") == null);
        qDown.setDisable(knownFolder("Downloads") == null);
        qPics.setDisable(knownFolder("Pictures") == null);
        qMusic.setDisable(knownFolder("Music") == null);
        qVid.setDisable(knownFolder("Videos") == null);

        return new ExplorerView(root, () -> new HashMap<Button, String>() {{
            put(btnBack, "Alt+LEFT");
            put(btnFwd, "Alt+RIGHT");
            put(btnUp, "Alt+UP");
            put(btnRef, "F5");
        }});
    }

    private static class ExplorerView {
        final Parent root;
        final ShortcutSupplier shortcuts;

        ExplorerView(Parent root, ShortcutSupplier shortcuts) {
            this.root = root;
            this.shortcuts = shortcuts;
        }

        void registerShortcuts(Scene scene) {
            Map<Button, String> map = shortcuts.get();
            map.forEach((btn, combo) -> scene.getAccelerators().put(
                    KeyCombination.keyCombination(combo), btn::fire));
        }
    }

    @FunctionalInterface
    private interface ShortcutSupplier {
        Map<Button, String> get();
    }

    // ===== Navigation & properties =====
    private void navigateTo(File[] current, File dir, boolean pushHistory, Deque<File> back, Deque<File> fwd,
                            ListView<File> list, FlowPane breadcrumbs, VBox rightProps, ImageView rightPreview,
                            Runnable updateNavButtons) {
        if (dir == null || !dir.exists() || !dir.isDirectory())
            return;
        if (pushHistory) {
            back.push(current[0]);
            fwd.clear();
        }
        current[0] = dir;

        breadcrumbs.getChildren().clear();
        List<File> segs = new ArrayList<>();
        try {
            File f = dir.getCanonicalFile();
            while (f != null) {
                segs.add(0, f);
                f = f.getParentFile();
            }
        } catch (IOException e) {
            File f = dir;
            while (f != null) {
                segs.add(0, f);
                f = f.getParentFile();
            }
        }
        for (int i = 0; i < segs.size(); i++) {
            File seg = segs.get(i);
            Button b = lcarsButton(seg.getName().isEmpty() ? seg.getPath() : seg.getName(), PEACH);
            b.setOnAction(e -> navigateTo(current, seg, true, back, fwd, list, breadcrumbs, rightProps, rightPreview,
                    updateNavButtons));
            breadcrumbs.getChildren().add(b);
            if (i < segs.size() - 1) {
                Label arrow = new Label("›");
                arrow.setTextFill(TEXT);
                breadcrumbs.getChildren().add(arrow);
            }
        }

        File[] files = dir.listFiles();
        list.getItems().clear();
        if (files != null) {
            Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory())
                    return -1;
                if (!a.isDirectory() && b.isDirectory())
                    return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            list.getItems().addAll(files);
        }
        rightProps.getChildren().setAll();
        rightPreview.setImage(null);
        updateNavButtons.run();
    }

    private void navKnown(String kind, File[] current, Deque<File> back, Deque<File> fwd, ListView<File> list,
                          FlowPane breadcrumbs, VBox rightProps, ImageView rightPreview, Runnable updateNavButtons) {
        File target = knownFolder(kind);
        if (target != null)
            navigateTo(current, target, true, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons);
    }

    private void populatePropertiesFX(VBox propsContent, ImageView preview, File file) {
        propsContent.getChildren().setAll(
                propRow("Name:", file.getName()),
                propRow("Path:", file.getAbsolutePath()),
                propRow("Readable:", String.valueOf(file.canRead())),
                propRow("Writable:", String.valueOf(file.canWrite())),
                propRow("Hidden:", String.valueOf(file.isHidden())),
                propRow("Directory:", String.valueOf(file.isDirectory()))
        );
        Path p = file.toPath();
        try {
            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
            propsContent.getChildren().addAll(
                    propRow("Type:", probeTypeSafe(p)),
                    propRow("Size:",
                            file.isDirectory()
                                    ? "<dir>"
                                    : humanSize(attrs.size()) + " (" + attrs.size() + " bytes)"),
                    propRow("Created:", formatFileTime(attrs.creationTime().toMillis())),
                    propRow("Modified:", formatFileTime(attrs.lastModifiedTime().toMillis())),
                    propRow("Accessed:", formatFileTime(attrs.lastAccessTime().toMillis()))
            );
        } catch (IOException ex) {
            propsContent.getChildren().add(propRow("Error:", ex.getMessage()));
        }
        if (file.isFile() && isImageFile(file))
            preview.setImage(new Image(file.toURI().toString(), 360, 260, true, true));
        else
            preview.setImage(null);
    }

    private HBox propRow(String key, String value) {
        Label k = new Label(key);
        k.setTextFill(PEACH);
        k.setFont(lcarsFontOrDefault(12, true));
        TextField v = new TextField(value == null ? "" : value);
        v.setEditable(false);
        v.setFocusTraversable(false);
        v.setStyle("""
            -fx-background-color: #1a1a1a;
            -fx-text-fill: #f0f0f0;
            -fx-border-color: #333333;
            -fx-border-width: 1;
            -fx-background-insets: 0;
            -fx-padding: 6 8 6 8;
            """);
        HBox row = new HBox(8, k, v);
        HBox.setHgrow(v, Priority.ALWAYS);
        return row;
    }

    // ===== UI helpers =====
    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setTextFill(PEACH);
        l.setFont(lcarsFontOrDefault(14, true));
        return l;
    }

    private Label lcarsCaption(String text) {
        Label l = new Label(text);
        l.setTextFill(AMBER);
        l.setFont(lcarsFontOrDefault(15, true));
        return l;
    }

    private Region lcarsBar(Color color, int h) {
        Region r = new Region();
        r.setMinHeight(h);
        r.setPrefHeight(h);
        r.setBackground(new Background(new BackgroundFill(color, new CornerRadii(h), Insets.EMPTY)));
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private Region roundedCard(Region content, Color bg) {
        StackPane wrap = new StackPane(content);
        wrap.setBackground(new Background(new BackgroundFill(bg, new CornerRadii(24), Insets.EMPTY)));
        wrap.setBorder(
                new Border(new BorderStroke(EDGE, BorderStrokeStyle.SOLID, new CornerRadii(24), new BorderWidths(1))));
        return wrap;
    }

    private Button lcarsButton(String text, Color color) {
        Button b = new Button(text);
        b.setFont(lcarsFontOrDefault(12, true));
        b.setTextFill(Color.BLACK);
        b.setBackground(new Background(new BackgroundFill(color, new CornerRadii(999), Insets.EMPTY)));
        b.setBorder(new Border(new BorderStroke(EDGE, BorderStrokeStyle.SOLID, new CornerRadii(999), new BorderWidths(1))));
        b.setPadding(new Insets(10, 16, 10, 16));
        b.setFocusTraversable(false);

        if (SETTINGS.glowEnabled) {
            b.setEffect(createGlow(color));
        }

        b.setUserData(color);

        b.setOnMouseEntered(e -> {
            Color baseColor = (Color) b.getUserData();
            Color brighter = baseColor.brighter().brighter();
            b.setBackground(new Background(new BackgroundFill(brighter, new CornerRadii(999), Insets.EMPTY)));
            if (SETTINGS.glowEnabled) {
                b.setEffect(createGlow(brighter));
            }
        });

        b.setOnMouseExited(e -> {
            Color baseColor = (Color) b.getUserData();
            b.setBackground(new Background(new BackgroundFill(baseColor, new CornerRadii(999), Insets.EMPTY)));
            if (SETTINGS.glowEnabled) {
                b.setEffect(createGlow(baseColor));
            }
        });

        return b;
    }

    private Region spacer(double h) {
        Region r = new Region();
        r.setMinHeight(h);
        r.setPrefHeight(h);
        return r;
    }

    private Region frameRightWithLeftLFlush(Region content) {
        StackPane wrap = new StackPane();

        Region leftCol = new Region();
        leftCol.setPrefWidth(16);
        leftCol.setMaxWidth(16);
        leftCol.setBackground(new Background(new BackgroundFill(TEAL, new CornerRadii(16), Insets.EMPTY)));
        leftCol.setBorder(
                new Border(new BorderStroke(EDGE, BorderStrokeStyle.SOLID, new CornerRadii(16), new BorderWidths(1))));
        StackPane.setAlignment(leftCol, Pos.CENTER_LEFT);
        StackPane.setMargin(leftCol, new Insets(16, 0, 16, 0));

        Region topCap = new Region();
        topCap.setPrefSize(140, 24);
        topCap.setBackground(
                new Background(new BackgroundFill(AMBER, new CornerRadii(24, 24, 0, 0, false), Insets.EMPTY)));
        topCap.setBorder(new Border(new BorderStroke(EDGE, BorderStrokeStyle.SOLID,
                new CornerRadii(24, 24, 0, 0, false), new BorderWidths(1))));
        StackPane.setAlignment(topCap, Pos.TOP_LEFT);
        StackPane.setMargin(topCap, new Insets(-8, 0, 0, 8));

        Region rightColumn = new Region();
        rightColumn.setPrefWidth(12);
        rightColumn.setMaxWidth(12);
        rightColumn.setBackground(new Background(new BackgroundFill(SALMON, new CornerRadii(12), Insets.EMPTY)));
        rightColumn.setBorder(
                new Border(new BorderStroke(EDGE, BorderStrokeStyle.SOLID, new CornerRadii(12), new BorderWidths(1))));
        StackPane.setAlignment(rightColumn, Pos.CENTER_RIGHT);
        StackPane.setMargin(rightColumn, new Insets(16, 0, 16, 0));

        content.setTranslateX(8);
        wrap.getChildren().addAll(leftCol, topCap, rightColumn, content);
        return wrap;
    }

    // ===== Intro overlay & connectivity (unchanged) =====
    private void playIntroOverlay(StackPane host) {
        Pane overlay = new Pane();
        overlay.setPickOnBounds(true);
        overlay.setBackground(new Background(new BackgroundFill(BG, CornerRadii.EMPTY, Insets.EMPTY)));

        double barH1 = 34, barH2 = 22, barH3 = 14;

        Rectangle r1 = new Rectangle();
        r1.setHeight(barH1);
        r1.setFill(SALMON);
        r1.setArcHeight(barH1);
        r1.setArcWidth(barH1);
        Rectangle r2 = new Rectangle();
        r2.setHeight(barH2);
        r2.setFill(AMBER);
        r2.setArcHeight(barH2);
        r2.setArcWidth(barH2);
        Rectangle r3 = new Rectangle();
        r3.setHeight(barH3);
        r3.setFill(BLUE);
        r3.setArcHeight(barH3);
        r3.setArcWidth(barH3);

        Label title = new Label("LCARS INTERFACE");
        title.setTextFill(AMBER);
        title.setFont(lcarsFontOrDefault(28, true));

        overlay.widthProperty().addListener((o, ov, nv) -> {
            double w = nv.doubleValue();
            r1.setWidth(w);
            r2.setWidth(w);
            r3.setWidth(w);
            double baseY = 100;
            r1.setLayoutY(baseY);
            r2.setLayoutY(baseY + barH1 + 8);
            r3.setLayoutY(baseY + barH1 + 8 + barH2 + 8);
            title.setLayoutX(w - 360);
            title.setLayoutY(baseY + barH1 + 40);
        });

        overlay.getChildren().addAll(r1, r2, r3, title);
        host.getChildren().add(overlay);

        r1.setTranslateX(-1600);
        r2.setTranslateX(1600);
        r3.setTranslateX(-1600);
        title.setOpacity(0);

        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(r1.translateXProperty(), -1600),
                        new KeyValue(r2.translateXProperty(), 1600),
                        new KeyValue(r3.translateXProperty(), -1600),
                        new KeyValue(title.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(450), new KeyValue(r1.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(750), new KeyValue(r2.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(1050),
                        new KeyValue(r3.translateXProperty(), 0),
                        new KeyValue(title.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(1700), new KeyValue(overlay.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(2100), new KeyValue(overlay.opacityProperty(), 0))
        );
        tl.setOnFinished(e -> host.getChildren().remove(overlay));
        tl.play();
    }

    private void attachConnectivityMonitor(StackPane host) {
        StackPane overlay = new StackPane();
        overlay.setMouseTransparent(true);
        overlay.setVisible(false);

        Rectangle pulse = new Rectangle();
        pulse.setArcWidth(24);
        pulse.setArcHeight(24);
        pulse.widthProperty().bind(host.widthProperty().multiply(0.6));
        pulse.heightProperty().bind(host.heightProperty().multiply(0.22));
        pulse.setFill(Color.color(1, 0, 0, 0.18));
        pulse.setStroke(Color.RED);
        pulse.setStrokeWidth(3);

        Label alert = new Label("COMMUNICATIONS SYSTEMS ERROR");
        alert.setTextFill(Color.RED);
        alert.setFont(lcarsFontOrDefault(44, true));

        overlay.getChildren().addAll(pulse, alert);
        host.getChildren().add(overlay);

        Timeline blink = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(overlay.opacityProperty(), 0.35)),
                new KeyFrame(Duration.seconds(0.45), new KeyValue(overlay.opacityProperty(), 1.0))
        );
        blink.setAutoReverse(true);
        blink.setCycleCount(Animation.INDEFINITE);

        Runnable playBeep = () -> new Thread(() -> {
            try {
                playAlarmTone(800, 350);
                Thread.sleep(200);
                playAlarmTone(600, 350);
            } catch (InterruptedException ignored) {
            }
        }, "alarm-tone").start();

        final boolean[] wasOnline = { true };
        Timeline poll = new Timeline(
                new KeyFrame(Duration.seconds(0.1),
                        e -> checkAndSet(overlay, blink, wasOnline, playBeep)),
                new KeyFrame(Duration.seconds(7))
        );
        poll.setCycleCount(Animation.INDEFINITE);
        poll.play();
    }

    private void checkAndSet(StackPane overlay, Timeline blink, boolean[] wasOnline, Runnable playBeep) {
        new Thread(() -> {
            boolean online = isInternetAvailable();
            Platform.runLater(() -> {
                if (!online) {
                    overlay.setVisible(true);
                    if (blink.getStatus() != Animation.Status.RUNNING)
                        blink.playFromStart();
                    if (wasOnline[0])
                        playBeep.run();
                    wasOnline[0] = false;
                } else {
                    overlay.setVisible(false);
                    blink.stop();
                    wasOnline[0] = true;
                }
            });
        }, "net-check").start();
    }

    private boolean isInternetAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("8.8.8.8", 53), 1500);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private void playAlarmTone(int hz, int millis) {
        float sampleRate = 44100f;
        int samples = (int) (millis * sampleRate / 1000);
        byte[] data = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            double angle = 2.0 * Math.PI * i * hz / sampleRate;
            short val = (short) (Math.sin(angle) * 32767);
            data[2 * i] = (byte) (val & 0xff);
            data[2 * i + 1] = (byte) ((val >> 8) & 0xff);
        }
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(data), format, samples)) {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(format);
                line.start();
                byte[] buf = new byte[2048];
                int r;
                while ((r = ais.read(buf)) != -1)
                    line.write(buf, 0, r);
                line.drain();
                line.stop();
            }
        } catch (Exception ignored) {
        }
    }

    // ===== Alerts =====
    private void showAlert(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.initStyle(StageStyle.UTILITY);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    // ===== File helpers =====
    private static void openWithDesktop(File f) {
        if (!Desktop.isDesktopSupported())
            return;
        try {
            Desktop.getDesktop().open(f);
        } catch (IOException ignored) {
        }
    }

    private static File knownFolder(String kind) {
        String home = System.getProperty("user.home");
        boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
        List<File> c = new ArrayList<>();
        switch (kind) {
            case "Desktop" -> {
                c.add(new File(home, "Desktop"));
                if (isWin)
                    c.add(new File(home, "OneDrive/Desktop"));
            }
            case "Documents" -> {
                c.add(new File(home, "Documents"));
                if (isWin) {
                    c.add(new File(home, "OneDrive/Documents"));
                    c.add(new File(home, "Documents/My Documents"));
                }
            }
            case "Downloads" -> {
                c.add(new File(home, "Downloads"));
                if (isWin)
                    c.add(new File(home, "OneDrive/Downloads"));
            }
            case "Pictures" -> {
                c.add(new File(home, "Pictures"));
                if (isWin)
                    c.add(new File(home, "OneDrive/Pictures"));
            }
            case "Music" -> {
                c.add(new File(home, "Music"));
                if (isWin)
                    c.add(new File(home, "OneDrive/Music"));
            }
            case "Videos" -> {
                c.add(new File(home, "Videos"));
                if (isWin)
                    c.add(new File(home, "OneDrive/Videos"));
            }
        }
        for (File f : c)
            if (f.exists() && f.isDirectory())
                return f;
        return null;
    }

    private static boolean isInDesktop(File f) {
        File d = knownFolder("Desktop");
        if (d == null)
            return false;
        try {
            String dp = d.getCanonicalPath();
            String fp = f.getCanonicalFile().getParent();
            return fp != null && fp.equalsIgnoreCase(dp);
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteRecursive(Path p) throws IOException {
        if (!Files.exists(p))
            return;
        Files.walk(p).sorted(Comparator.reverseOrder()).forEach(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        });
    }

    private static void copyRecursive(Path src, Path dst) throws IOException {
        Files.walk(src).forEach(s -> {
            try {
                Path rel = src.relativize(s);
                Path tgt = dst.resolve(rel);
                if (Files.isDirectory(s))
                    Files.createDirectories(tgt);
                else
                    Files.copy(s, tgt, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        });
    }

    private static boolean isImageFile(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif")
                || n.endsWith(".bmp") || n.endsWith(".webp");
    }

    private static String probeTypeSafe(Path p) {
        try {
            String t = Files.probeContentType(p);
            if (t != null)
                return t;
        } catch (IOException ignored) {
        }
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1)
            return name.substring(dot + 1).toUpperCase() + " file";
        return "Unknown";
    }

    private static String formatFileTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(millis));
    }

    private static String humanSize(long bytes) {
        String[] u = { "B", "KB", "MB", "GB", "TB" };
        double b = bytes;
        int i = 0;
        while (b >= 1024 && i < u.length - 1) {
            b /= 1024;
            i++;
        }
        return String.format("%.2f %s", b, u[i]);
    }

    private static File uniqueName(File desired) {
        if (!desired.exists())
            return desired;
        String name = desired.getName();
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        int i = 1;
        while (true) {
            File candidate = new File(desired.getParentFile(),
                    base + " - Copy" + (i > 1 ? " (" + i + ")" : "") + ext);
            if (!candidate.exists())
                return candidate;
            i++;
        }
    }

    // ===== Custom list cell =====
    private class LcarsFileCell extends ListCell<File> {
        private final Rectangle pill = new Rectangle(46, 18);
        private final Label name = new Label();
        private final Label meta = new Label();
        private final HBox box = new HBox(10);
        private final Color[] PILL_COLORS = new Color[] { SALMON, AMBER, BLUE, TEAL, PEACH };

        LcarsFileCell() {
            pill.setArcWidth(18);
            pill.setArcHeight(18);
            name.setTextFill(TEXT);
            name.setFont(lcarsFontOrDefault(13, true));
            meta.setTextFill(PEACH);
            meta.setFont(lcarsFontOrDefault(11, false));
            VBox text = new VBox(1, name, meta);
            box.getChildren().addAll(pill, text);
            box.setAlignment(Pos.CENTER_LEFT);
            setPadding(new Insets(6, 10, 6, 10));
        }

        @Override
        protected void updateItem(File f, boolean empty) {
            super.updateItem(f, empty);
            if (empty || f == null) {
                setGraphic(null);
                setBackground(new Background(new BackgroundFill(BG, CornerRadii.EMPTY, Insets.EMPTY)));
            } else {
                name.setText(f.getName().isEmpty() ? f.getPath() : f.getName());
                String type;
                try {
                    String t = Files.probeContentType(f.toPath());
                    type = t != null ? t : (f.isDirectory() ? "directory" : "unknown");
                } catch (IOException e) {
                    type = f.isDirectory() ? "directory" : "unknown";
                }
                String size = f.isDirectory() ? "<dir>" : humanSize(f.length());
                meta.setText(type + "  •  " + size);
                pill.setFill(PILL_COLORS[getIndex() % PILL_COLORS.length]);
                setGraphic(box);
                setBackground(isSelected()
                        ? new Background(new BackgroundFill(Color.web("#141414"), CornerRadii.EMPTY, Insets.EMPTY))
                        : new Background(new BackgroundFill(BG, CornerRadii.EMPTY, Insets.EMPTY)));
            }
        }
    }

    // ===== Desktop canvas (persistent tiles; sync with moves) =====
    private static class DesktopCanvas extends Pane {
        private final Region trash;
        private final Color[] PALETTE = { SALMON, AMBER, BLUE, TEAL, PEACH };

        private final Map<String, Tile> tiles = new HashMap<>();

        DesktopCanvas() {
            setPadding(new Insets(16));
            setStyle("-fx-background-color: transparent;");

            trash = new Region();
            trash.setPrefSize(ICON_SIZE, ICON_SIZE);
            trash.setMinSize(ICON_SIZE, ICON_SIZE);
            trash.setMaxSize(ICON_SIZE, ICON_SIZE);
            trash.setBackground(
                    new Background(new BackgroundFill(Color.TRANSPARENT,
                            new CornerRadii(ICON_RADIUS), Insets.EMPTY)));
            trash.setBorder(new Border(new BorderStroke(EDGE, BorderStrokeStyle.SOLID,
                    new CornerRadii(ICON_RADIUS), new BorderWidths(3))));
            getChildren().add(trash);

            widthProperty().addListener((o, ov, nv) -> layoutTrash());
            heightProperty().addListener((o, ov, nv) -> layoutTrash());
        }

        private void layoutTrash() {
            trash.relocate(16, getHeight() - trash.getPrefHeight() - 16);
        }

        void addIcon(File fileOnDesktop, File originDir) {
            addIcon(fileOnDesktop, originDir, Double.NaN, Double.NaN);
        }

        private void addIcon(File fileOnDesktop, File originDir, double x, double y) {
            String key = safePath(fileOnDesktop);
            if (tiles.containsKey(key))
                return;
            Tile t = new Tile(fileOnDesktop, originDir);
            if (Double.isNaN(x) || Double.isNaN(y)) {
                x = Math.max(16, Math.min(getWidth() - ICON_SIZE - 20, 220 + Math.random() * 300));
                y = Math.max(16, Math.min(getHeight() - ICON_SIZE - 60, 40 + Math.random() * 220));
            } else {
                x = Math.max(0, Math.min(x, Math.max(0, getWidth() - ICON_SIZE)));
                y = Math.max(0, Math.min(y, Math.max(0, getHeight() - ICON_SIZE)));
            }
            t.relocate(x, y);
            getChildren().add(t);
            tiles.put(key, t);
        }

        void removeTileFor(File f) {
            String key = safePath(f);
            Tile t = tiles.remove(key);
            if (t != null)
                getChildren().remove(t);
        }

        void onFileMoved(File src, File dst) {
            if (isInDesktop(src))
                removeTileFor(src);
            if (isInDesktop(dst))
                addIcon(dst, src.getParentFile());
            saveState();
        }

        private String safePath(File f) {
            try {
                return f.getCanonicalPath();
            } catch (IOException e) {
                return f.getAbsolutePath();
            }
        }

        void saveState() {
            try (BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(DESKTOP_STATE_FILE, false),
                            StandardCharsets.UTF_8))) {
                for (Tile t : tiles.values()) {
                    String filePath = t.file.getAbsolutePath().replace("|", "%7C");
                    String originPath = t.originDir != null
                            ? t.originDir.getAbsolutePath().replace("|", "%7C")
                            : "";
                    double x = t.getLayoutX();
                    double y = t.getLayoutY();
                    w.write(filePath + "|" + originPath + "|" + x + "|" + y);
                    w.newLine();
                }
            } catch (IOException ignored) {
            }
        }

        void loadState() {
            if (!DESKTOP_STATE_FILE.exists())
                return;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(DESKTOP_STATE_FILE), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] parts = line.split("\\|", -1);
                    if (parts.length < 4)
                        continue;
                    String filePath = parts[0].replace("%7C", "|");
                    String originPath = parts[1].isEmpty() ? null : parts[1].replace("%7C", "|");
                    double x, y;
                    try {
                        x = Double.parseDouble(parts[2]);
                        y = Double.parseDouble(parts[3]);
                    } catch (NumberFormatException nfe) {
                        continue;
                    }
                    File f = new File(filePath);
                    if (f.exists() && isInDesktop(f)) {
                        File origin = originPath == null ? null : new File(originPath);
                        addIcon(f, origin, x, y);
                    }
                }
            } catch (IOException ignored) {
            }
        }

        private class Tile extends StackPane {
            final File file;
            final File originDir;
            final Region square;
            final Label name;
            double dragDX, dragDY;

            Tile(File f, File origin) {
                this.file = f;
                this.originDir = origin;

                setPickOnBounds(false);

                int idx = Math.abs(file.getAbsolutePath().hashCode()) % PALETTE.length;
                Color fill = PALETTE[idx];
                Color border = PALETTE[(idx + 2) % PALETTE.length];

                square = new Region();
                square.setPrefSize(ICON_SIZE, ICON_SIZE);
                square.setMinSize(ICON_SIZE, ICON_SIZE);
                square.setMaxSize(ICON_SIZE, ICON_SIZE);
                square.setBackground(
                        new Background(new BackgroundFill(fill, new CornerRadii(ICON_RADIUS), Insets.EMPTY)));
                square.setBorder(new Border(new BorderStroke(border, BorderStrokeStyle.SOLID,
                        new CornerRadii(ICON_RADIUS), new BorderWidths(3))));

                name = new Label(f.getName());
                name.setTextFill(Color.BLACK);
                name.setFont(lcarsFontOrDefault(12, true));
                name.setWrapText(true);
                name.setMaxWidth(ICON_SIZE - 12);
                name.setAlignment(Pos.CENTER);

                StackPane.setAlignment(name, Pos.CENTER);
                getChildren().addAll(square, name);

                setOnMousePressed(e -> {
                    if (e.getButton() != MouseButton.PRIMARY)
                        return;
                    dragDX = e.getSceneX() - getLayoutX();
                    dragDY = e.getSceneY() - getLayoutY();
                });
                setOnMouseDragged(e -> {
                    if (e.getButton() != MouseButton.PRIMARY)
                        return;
                    double nx = e.getSceneX() - dragDX;
                    double ny = e.getSceneY() - dragDY;
                    nx = Math.max(0, Math.min(nx, getParent().getLayoutBounds().getWidth() - getWidth()));
                    ny = Math.max(0, Math.min(ny, getParent().getLayoutBounds().getHeight() - getHeight()));
                    relocate(nx, ny);
                });
                setOnMouseReleased(e -> {
                    Bounds squareScene = square.localToScene(square.getBoundsInLocal());
                    Bounds trashScene = trash.localToScene(trash.getBoundsInLocal());
                    boolean overlaps = squareScene.intersects(trashScene);

                    if (overlaps) {
                        ((Pane) getParent()).getChildren().remove(this);
                        tiles.remove(safePath(file));
                        if (originDir != null && originDir.exists() && originDir.isDirectory()) {
                            File target = uniqueName(new File(originDir, file.getName()));
                            try {
                                if (file.isDirectory()) {
                                    try {
                                        Files.move(file.toPath(), target.toPath(),
                                                StandardCopyOption.REPLACE_EXISTING);
                                    } catch (IOException moveEx) {
                                        copyRecursive(file.toPath(), target.toPath());
                                        deleteRecursive(file.toPath());
                                    }
                                } else {
                                    try {
                                        Files.move(file.toPath(), target.toPath(),
                                                StandardCopyOption.REPLACE_EXISTING);
                                    } catch (IOException moveEx) {
                                        Files.copy(file.toPath(), target.toPath(),
                                                StandardCopyOption.REPLACE_EXISTING);
                                        Files.deleteIfExists(file.toPath());
                                    }
                                }
                            } catch (IOException ignored) {
                            }
                        }
                        saveState();
                    } else {
                        saveState();
                    }
                });

                setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2)
                        openWithDesktop(file);
                });
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
