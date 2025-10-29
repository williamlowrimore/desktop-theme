// LcarsApp.java — Adds CUT/PASTE (true move) and keeps LCARS desktop tiles in sync
// - CUT + PASTE moves (Files.move with fallback), COPY + PASTE duplicates
// - After paste, tiles update: removed if moved off Desktop, added if moved onto Desktop
// - Desktop tiles persist via ~/.lcars_desktop.txt
// - UPDATE: Top-right HOLO DRIVE w/ bigger red hub + compact storage bar
// - UPDATE: Username appears on the amber header bar in a small right-side pill

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.*;
import javafx.util.Duration;

import javax.sound.sampled.*;
import java.awt.Desktop;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class LcarsApp extends Application {

    // ===== LCARS palette (Classic TNG) =====
    public static final Color BG     = Color.web("#000000");
    public static final Color TEXT   = Color.web("#F0F0F0");
    public static final Color PEACH  = Color.web("#FFB266");
    public static final Color SALMON = Color.web("#FF8C78");
    public static final Color AMBER  = Color.web("#FFCC66");
    public static final Color BLUE   = Color.web("#6699FF");
    public static final Color TEAL   = Color.web("#60C8C8");
    public static final Color PANEL  = Color.web("#0D0D0D");
    public static final Color EDGE   = Color.web("#333333");

    // Desktop tiles / trash sizing
    private static final double ICON_SIZE = 80; // exact square size
    private static final double ICON_RADIUS = 20;

    private static final File DESKTOP_STATE_FILE =
            new File(System.getProperty("user.home"), ".lcars_desktop.txt");

    private ImageView headerLogo;
    private static DesktopCanvas DESKTOP_CANVAS;

    // ===== Font helper (optional LCARS font) =====
    private static Font lcarsFontOrDefault(double size, boolean bold) {
        try (FileInputStream in = new FileInputStream("lcars.ttf")) {
            Font f = Font.loadFont(in, size);
            if (f != null) return bold ? Font.font(f.getFamily(), FontWeight.BOLD, size) : f;
        } catch (Exception ignored) {}
        return bold ? Font.font("System", FontWeight.BOLD, size) : Font.font(size);
    }

    @Override
    public void start(Stage primary) {
        Parent content = buildMainConsole(primary);

        StackPane root = new StackPane(content);
        root.setBackground(new Background(new BackgroundFill(BG, CornerRadii.EMPTY, Insets.EMPTY)));

        attachConnectivityMonitor(root);

        Scene scene = new Scene(root, 1280, 800, BG);
        primary.setTitle("LCARS File Console — JavaFX (TNG)");
        primary.setScene(scene);
        primary.setFullScreen(true);
        primary.show();

        Platform.runLater(() -> {
            if (DESKTOP_CANVAS != null) DESKTOP_CANVAS.loadState();
        });

        playIntroOverlay(root);
    }

    // ===== Main console (right side is desktop canvas) =====
    private Parent buildMainConsole(Stage owner) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));

        // Header with username pill on amber bar
        VBox bars = buildHeaderBarsWithUserPill();
        BorderPane titleRow = new BorderPane();
        Label title = new Label("LCARS FILE CONSOLE");
        title.setTextFill(AMBER);
        title.setFont(lcarsFontOrDefault(22, true));
        titleRow.setLeft(title);
        headerLogo = new ImageView();
        headerLogo.setFitWidth(320); headerLogo.setFitHeight(96); headerLogo.setPreserveRatio(true);
        titleRow.setRight(headerLogo);
        root.setTop(new VBox(bars, spacer(8), titleRow));

        GridPane grid = new GridPane(); grid.setHgap(12); grid.setVgap(12);
        VBox controls = new VBox(12, sectionLabel("PRIMARY CONTROLS"), grid);
        controls.setPadding(new Insets(16));
        Region controlsCard = roundedCard(new StackPane(controls), PANEL);
        BorderPane.setMargin(controlsCard, new Insets(0, 12, 0, 0));

        DESKTOP_CANVAS = new DesktopCanvas();
        Region desktopCard = roundedCard(DESKTOP_CANVAS, PANEL);

        // Top-right: larger holo drive + compact storage bar
        StackPane desktopStack = new StackPane(desktopCard);
        Node holo = buildHoloDriveWithStorage();
        StackPane.setAlignment(holo, Pos.TOP_RIGHT);
        StackPane.setMargin(holo, new Insets(12, 12, 0 ,0));
        desktopStack.getChildren().add(holo);

        BorderPane body = new BorderPane();
        body.setLeft(controlsCard);
        body.setCenter(desktopStack);
        root.setCenter(body);

        Button b1 = lcarsButton("PLACEHOLDER", SALMON); b1.setDisable(true);
        Button b2 = lcarsButton("PLACEHOLDER", BLUE);   b2.setDisable(true);
        Button btnExplorer = lcarsButton("OPEN FOLDER VIEW", TEAL);
        Button b4 = lcarsButton("PLACEHOLDER", PEACH);  b4.setDisable(true);

        grid.add(b1, 0, 0); grid.add(b2, 1, 0);
        grid.add(btnExplorer, 0, 1); grid.add(b4, 1, 1);

        btnExplorer.setOnAction(e -> openExplorerWindow(owner));

        return root;
    }

 // === HEADER: small username box sitting ON the amber bar at the right end ===
    private VBox buildHeaderBarsWithUserPill() {
        double barH1 = 34, barH2 = 20, barH3 = 12;

        // === Bars ===
        Region r1 = new Region();
        r1.setMinHeight(barH1);
        r1.setPrefHeight(barH1);
        r1.setBackground(new Background(new BackgroundFill(SALMON, new CornerRadii(barH1), Insets.EMPTY)));

        StackPane amberRow = new StackPane();
        Region r2 = new Region();
        r2.setMinHeight(barH2);
        r2.setPrefHeight(barH2);
        r2.setBackground(new Background(new BackgroundFill(AMBER, new CornerRadii(barH2), Insets.EMPTY)));
        amberRow.getChildren().add(r2);

        // === Username box ===
        String username = System.getProperty("user.name", "USER").toUpperCase(Locale.ROOT);
        HBox box = new HBox();
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

        Region r3 = new Region();
        r3.setMinHeight(barH3);
        r3.setPrefHeight(barH3);
        r3.setBackground(new Background(new BackgroundFill(BLUE, new CornerRadii(barH3), Insets.EMPTY)));

        return new VBox(6, r1, amberRow, r3);
    }



    // === HOLOGRAPHIC HARD-DRIVE (bigger hub) + compact storage bar + drive name ===
    private Node buildHoloDriveWithStorage() {
        File systemDrive = getSystemDriveRoot();
        String driveName = (systemDrive != null) ? systemDrive.getPath() : "SYSTEM";
        long total = (systemDrive != null) ? systemDrive.getTotalSpace() : 0L;
        long free  = (systemDrive != null) ? systemDrive.getFreeSpace()  : 0L;
        long used  = Math.max(0L, total - free);

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

        Arc a1 = new Arc(0,0,R-18,R-18,0,60);
        Arc a2 = new Arc(0,0,R-18,R-18,120,60);
        Arc a3 = new Arc(0,0,R-18,R-18,240,60);
        for (Arc a : new Arc[]{a1,a2,a3}) {
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

        DropShadow glow = new DropShadow(40, Color.web("#66CCFF80"));
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
            new KeyFrame(Duration.ZERO,           new KeyValue(outer.opacityProperty(), 0.22)),
            new KeyFrame(Duration.seconds(1.4),   new KeyValue(outer.opacityProperty(), 0.40)),
            new KeyFrame(Duration.seconds(2.8),   new KeyValue(outer.opacityProperty(), 0.22))
        );
        breathe.setAutoReverse(false);
        breathe.setCycleCount(Animation.INDEFINITE);
        breathe.play();

        return disk;
    }

    // Much smaller storage bar
    private VBox buildStorageGraph(long used, long total) {
        double width = 140;   // was 260
        double height = 10;   // was 18

        Region bgBar = new Region();
        bgBar.setMinSize(width, height);
        bgBar.setPrefSize(width, height);
        bgBar.setBackground(new Background(new BackgroundFill(Color.web("#1a1a1a"), new CornerRadii(height), Insets.EMPTY)));
        bgBar.setBorder(new Border(new BorderStroke(EDGE, BorderStrokeStyle.SOLID, new CornerRadii(height), new BorderWidths(1))));

        double frac = (total <= 0) ? 0.0 : Math.min(1.0, Math.max(0.0, (double)used / (double)total));
        Region usedBar = new Region();
        usedBar.setMinHeight(height);
        usedBar.setPrefHeight(height);
        usedBar.setBackground(new Background(new BackgroundFill(AMBER, new CornerRadii(height), Insets.EMPTY)));
        usedBar.setMaxWidth(width * frac);
        usedBar.setPrefWidth(width * frac);

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
        return box;
    }

    private File getSystemDriveRoot() {
        try {
            Path home = Paths.get(System.getProperty("user.home", "."));
            Path root = home.getRoot();
            if (root != null) return root.toFile();
        } catch (Exception ignored) {}
        File[] roots = File.listRoots();
        return (roots != null && roots.length > 0) ? roots[0] : null;
    }

    // ===== Explorer (unchanged except for context) =====
    private void openExplorerWindow(Window owner) {
        File start;
        boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWin && new File("C:\\").exists()) start = new File("C:\\");
        else {
            File[] roots = File.listRoots();
            start = (roots != null && roots.length > 0) ? roots[0] : new File("/");
        }

        Stage stage = new Stage(StageStyle.DECORATED);
        stage.setTitle("LCARS Explorer — TNG");
        stage.initOwner(owner);
        stage.setFullScreen(true);

        ExplorerView view = buildExplorerView(start, stage);

        StackPane wrap = new StackPane(view.root);
        attachConnectivityMonitor(wrap);

        Scene scene = new Scene(wrap, 1400, 900, BG);
        stage.setScene(scene);
        stage.show();

        view.registerShortcuts(scene);
    }

    private ExplorerView buildExplorerView(File startDir, Stage stage) {
        VBox bars = new VBox(6, lcarsBar(SALMON, 34), lcarsBar(AMBER, 20), lcarsBar(BLUE, 12));

        Button btnBack = lcarsButton("BACK", SALMON);
        Button btnFwd  = lcarsButton("FWD",  TEAL);
        Button btnUp   = lcarsButton("UP",   BLUE);
        Button btnHome = lcarsButton("HOME", AMBER);
        Button btnRef  = lcarsButton("REFRESH", PEACH);

        Button btnClose = lcarsButton("CLOSE", SALMON);
        btnClose.setOnAction(e -> stage.close());

        Button btnCreate = lcarsButton("CREATE FILE", PEACH);
        Button btnDelete = lcarsButton("DELETE SELECTED", SALMON);
        Button btnMove   = lcarsButton("MOVE TO DESKTOP", AMBER);

        Button btnCopy = lcarsButton("COPY", TEAL);
        Button btnCut  = lcarsButton("CUT",  AMBER);
        Button btnPaste= lcarsButton("PASTE", BLUE);

        Region spacerGrow = new Region();
        HBox toolbar = new HBox(10,
                btnBack, btnFwd, btnUp, btnHome, btnRef,
                btnCopy, btnCut, btnPaste,
                spacerGrow, btnCreate, btnDelete, btnMove, btnClose);
        HBox.setHgrow(spacerGrow, Priority.ALWAYS);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button qDesktop = lcarsButton("DESKTOP", PEACH);
        Button qDocs    = lcarsButton("DOCUMENTS", SALMON);
        Button qDown    = lcarsButton("DOWNLOADS", TEAL);
        Button qPics    = lcarsButton("PICTURES",  BLUE);
        Button qMusic   = lcarsButton("MUSIC",     AMBER);
        Button qVid     = lcarsButton("VIDEOS",    PEACH);
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

        VBox rightProps = new VBox(10); rightProps.setPadding(new Insets(16));
        Label rpTitle = lcarsCaption("PROPERTIES");
        ImageView rightPreview = new ImageView(); rightPreview.setFitWidth(360); rightPreview.setFitHeight(260); rightPreview.setPreserveRatio(true);

        ScrollPane rpScroll = new ScrollPane(rightProps);
        rpScroll.setFitToWidth(true);
        rpScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox rightPane = new VBox(10, new VBox(rpTitle, lcarsBar(PEACH, 6)), rpScroll, rightPreview);
        Region rightCard = roundedCard(rightPane, PANEL); rightCard.setPrefWidth(460);

        Region rightFramed = frameRightWithLeftLFlush(rightCard);

        Region gap = new Region(); gap.setPrefWidth(48);
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
        Deque<File> fwd  = new ArrayDeque<>();
        File[] current = new File[]{ startDir };

        Runnable updateNavButtons = () -> {
            btnBack.setDisable(back.isEmpty());
            btnFwd.setDisable(fwd.isEmpty());
            btnUp.setDisable(current[0].getParentFile() == null);
        };

        btnBack.setOnAction(e -> { if (!back.isEmpty()) { fwd.push(current[0]); File prev = back.pop(); navigateTo(current, prev, false, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons); }});
        btnFwd .setOnAction(e -> { if (!fwd .isEmpty()) { back.push(current[0]); File nxt  = fwd .pop(); navigateTo(current, nxt , false, back, fwd , list, breadcrumbs, rightProps, rightPreview, updateNavButtons); }});
        btnUp  .setOnAction(e -> { File p = current[0].getParentFile(); if (p != null) navigateTo(current, p, true, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons); });
        btnHome.setOnAction(e -> navigateTo(current, startDir, true, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons));
        btnRef .setOnAction(e -> navigateTo(current, current[0], false, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons));

        qDesktop.setOnAction(e -> navKnown("Desktop",   current, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons));
        qDocs   .setOnAction(e -> navKnown("Documents", current, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons));
        qDown   .setOnAction(e -> navKnown("Downloads", current, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons));
        qPics   .setOnAction(e -> navKnown("Pictures",  current, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons));
        qMusic  .setOnAction(e -> navKnown("Music",     current, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons));
        qVid    .setOnAction(e -> navKnown("Videos",    current, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons));

        list.setOnMouseClicked(ev -> {
            File sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (ev.getClickCount() == 2) {
                if (sel.isDirectory()) navigateTo(current, sel, true, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons);
                else openWithDesktop(sel);
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) populatePropertiesFX(rightProps, rightPreview, sel);
        });

        final File[] clipboard = new File[1];
        final boolean[] isCut = new boolean[1];

        btnCopy.setOnAction(e -> {
            File sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) { showAlert("Copy", "Select a file or folder first."); return; }
            clipboard[0] = sel;
            isCut[0] = false;
            showAlert("Copy", "Copied: " + sel.getName());
        });
        btnCut.setOnAction(e -> {
            File sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) { showAlert("Cut", "Select a file or folder first."); return; }
            clipboard[0] = sel;
            isCut[0] = true;
            showAlert("Cut", "Cut: " + sel.getName());
        });
        btnPaste.setOnAction(e -> {
            if (clipboard[0] == null) { showAlert("Paste", "Clipboard is empty. Use COPY or CUT first."); return; }
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
                    if (src.isDirectory()) copyRecursive(src.toPath(), dst.toPath());
                    else Files.copy(src.toPath(), dst.toPath());
                    if (DESKTOP_CANVAS != null && isInDesktop(dst)) {
                        Platform.runLater(() -> {
                            DESKTOP_CANVAS.addIcon(dst, src.getParentFile());
                            DESKTOP_CANVAS.saveState();
                        });
                    }
                }

                navigateTo(current, current[0], false, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons);
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
                    if (nf.createNewFile()) navigateTo(current, current[0], false, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons);
                    else showAlert("Create File", "File already exists.");
                } catch (IOException ex) { showAlert("Create File", ex.getMessage()); }
            }
        });

        btnDelete.setOnAction(e -> {
            File sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) { showAlert("Delete", "Select a file or folder first."); return; }
            Alert conf = new Alert(Alert.AlertType.CONFIRMATION, "Delete \"" + sel.getName() + "\"?", ButtonType.YES, ButtonType.NO);
            conf.setHeaderText("Confirm Delete");
            if (conf.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                try {
                    if (sel.isDirectory()) deleteRecursive(sel.toPath());
                    else Files.deleteIfExists(sel.toPath());

                    if (DESKTOP_CANVAS != null && isInDesktop(sel)) {
                        Platform.runLater(() -> {
                            DESKTOP_CANVAS.removeTileFor(sel);
                            DESKTOP_CANVAS.saveState();
                        });
                    }
                    navigateTo(current, current[0], false, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons);
                } catch (IOException ex) { showAlert("Delete", ex.getMessage()); }
            }
        });

        btnMove.setOnAction(e -> {
            File sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) { showAlert("Move to Desktop", "Select a file or folder first."); return; }
            File desktop = knownFolder("Desktop");
            if (desktop == null) { showAlert("Move to Desktop", "Desktop folder not found."); return; }
            File originDir = sel.getParentFile();
            File target = uniqueName(new File(desktop, sel.getName()));
            try {
                try {
                    Files.move(sel.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveEx) {
                    if (sel.isDirectory()) copyRecursive(sel.toPath(), target.toPath());
                    else Files.copy(sel.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    deleteRecursive(sel.toPath());
                }
                navigateTo(current, current[0], false, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons);
                if (DESKTOP_CANVAS != null) Platform.runLater(() -> {
                    DESKTOP_CANVAS.addIcon(target, originDir);
                    DESKTOP_CANVAS.saveState();
                });
            } catch (IOException ex) { showAlert("Move to Desktop", ex.getMessage()); }
        });

        navigateTo(current, startDir, false, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons);

        qDesktop.setDisable(knownFolder("Desktop")==null);
        qDocs.setDisable(knownFolder("Documents")==null);
        qDown.setDisable(knownFolder("Downloads")==null);
        qPics.setDisable(knownFolder("Pictures")==null);
        qMusic.setDisable(knownFolder("Music")==null);
        qVid.setDisable(knownFolder("Videos")==null);

        return new ExplorerView(root, () -> new HashMap<Button, String>() {{
            put(btnBack, "Alt+LEFT");
            put(btnFwd,  "Alt+RIGHT");
            put(btnUp,   "Alt+UP");
            put(btnRef,  "F5");
        }});
    }

    private static class ExplorerView {
        final Parent root;
        final ShortcutSupplier shortcuts;
        ExplorerView(Parent root, ShortcutSupplier shortcuts) { this.root = root; this.shortcuts = shortcuts; }
        void registerShortcuts(Scene scene) {
            Map<Button,String> map = shortcuts.get();
            map.forEach((btn, combo) -> scene.getAccelerators().put(KeyCombination.keyCombination(combo), btn::fire));
        }
    }
    @FunctionalInterface private interface ShortcutSupplier { Map<Button,String> get(); }

    // ===== Navigation & properties =====
    private void navigateTo(File[] current, File dir, boolean pushHistory,
                            Deque<File> back, Deque<File> fwd,
                            ListView<File> list, FlowPane breadcrumbs, VBox rightProps, ImageView rightPreview,
                            Runnable updateNavButtons) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        if (pushHistory) { back.push(current[0]); fwd.clear(); }
        current[0] = dir;

        breadcrumbs.getChildren().clear();
        List<File> segs = new ArrayList<>();
        try { File f = dir.getCanonicalFile(); while (f != null) { segs.add(0,f); f = f.getParentFile(); } }
        catch (IOException e) { File f = dir; while (f != null) { segs.add(0,f); f = f.getParentFile(); } }
        for (int i=0;i<segs.size();i++) {
            File seg = segs.get(i);
            Button b = lcarsButton(seg.getName().isEmpty()? seg.getPath() : seg.getName(), PEACH);
            b.setOnAction(e -> navigateTo(current, seg, true, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons));
            breadcrumbs.getChildren().add(b);
            if (i<segs.size()-1) { Label arrow = new Label("›"); arrow.setTextFill(TEXT); breadcrumbs.getChildren().add(arrow); }
        }

        File[] files = dir.listFiles();
        list.getItems().clear();
        if (files != null) {
            Arrays.sort(files, (a,b)-> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            list.getItems().addAll(files);
        }
        rightProps.getChildren().setAll();
        rightPreview.setImage(null);
        updateNavButtons.run();
    }

    private void navKnown(String kind, File[] current,
                          Deque<File> back, Deque<File> fwd,
                          ListView<File> list, FlowPane breadcrumbs, VBox rightProps, ImageView rightPreview,
                          Runnable updateNavButtons) {
        File target = knownFolder(kind);
        if (target != null) navigateTo(current, target, true, back, fwd, list, breadcrumbs, rightProps, rightPreview, updateNavButtons);
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
                    propRow("Size:", file.isDirectory()? "<dir>" : humanSize(attrs.size()) + " (" + attrs.size() + " bytes)"),
                    propRow("Created:", formatFileTime(attrs.creationTime().toMillis())),
                    propRow("Modified:", formatFileTime(attrs.lastModifiedTime().toMillis())),
                    propRow("Accessed:", formatFileTime(attrs.lastAccessTime().toMillis()))
            );
        } catch (IOException ex) {
            propsContent.getChildren().add(propRow("Error:", ex.getMessage()));
        }
        if (file.isFile() && isImageFile(file)) preview.setImage(new Image(file.toURI().toString(), 360, 260, true, true));
        else preview.setImage(null);
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
        Label l = new Label(text); l.setTextFill(PEACH); l.setFont(lcarsFontOrDefault(14, true)); return l;
    }
    private Label lcarsCaption(String text) {
        Label l = new Label(text); l.setTextFill(AMBER); l.setFont(lcarsFontOrDefault(15, true)); return l;
    }
    private Region lcarsBar(Color color, int h) {
        Region r = new Region(); r.setMinHeight(h); r.setPrefHeight(h);
        r.setBackground(new Background(new BackgroundFill(color, new CornerRadii(h), Insets.EMPTY)));
        HBox.setHgrow(r, Priority.ALWAYS); return r;
    }
    private Region roundedCard(Region content, Color bg) {
        StackPane wrap = new StackPane(content);
        wrap.setBackground(new Background(new BackgroundFill(bg, new CornerRadii(24), Insets.EMPTY)));
        wrap.setBorder(new Border(new BorderStroke(EDGE, BorderStrokeStyle.SOLID, new CornerRadii(24), new BorderWidths(1))));
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
        b.setOnMouseEntered(e -> b.setBackground(new Background(new BackgroundFill(color.brighter(), new CornerRadii(999), Insets.EMPTY))));
        b.setOnMouseExited(e -> b.setBackground(new Background(new BackgroundFill(color, new CornerRadii(999), Insets.EMPTY))));
        return b;
    }
    private Region spacer(double h) { Region r = new Region(); r.setMinHeight(h); r.setPrefHeight(h); return r; }

    private Region frameRightWithLeftLFlush(Region content) {
        StackPane wrap = new StackPane();

        Region leftCol = new Region();
        leftCol.setPrefWidth(16); leftCol.setMaxWidth(16);
        leftCol.setBackground(new Background(new BackgroundFill(TEAL, new CornerRadii(16), Insets.EMPTY)));
        leftCol.setBorder(new Border(new BorderStroke(EDGE, BorderStrokeStyle.SOLID, new CornerRadii(16), new BorderWidths(1))));
        StackPane.setAlignment(leftCol, Pos.CENTER_LEFT);
        StackPane.setMargin(leftCol, new Insets(16, 0, 16, 0));

        Region topCap = new Region();
        topCap.setPrefSize(140, 24);
        topCap.setBackground(new Background(new BackgroundFill(AMBER, new CornerRadii(24, 24, 0, 0, false), Insets.EMPTY)));
        topCap.setBorder(new Border(new BorderStroke(EDGE, BorderStrokeStyle.SOLID, new CornerRadii(24, 24, 0, 0, false), new BorderWidths(1))));
        StackPane.setAlignment(topCap, Pos.TOP_LEFT);
        StackPane.setMargin(topCap, new Insets(-8, 0, 0, 8));

        Region rightColumn = new Region();
        rightColumn.setPrefWidth(12); rightColumn.setMaxWidth(12);
        rightColumn.setBackground(new Background(new BackgroundFill(SALMON, new CornerRadii(12), Insets.EMPTY)));
        rightColumn.setBorder(new Border(new BorderStroke(EDGE, BorderStrokeStyle.SOLID, new CornerRadii(12), new BorderWidths(1))));
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

        Rectangle r1 = new Rectangle(); r1.setHeight(barH1); r1.setFill(SALMON); r1.setArcHeight(barH1); r1.setArcWidth(barH1);
        Rectangle r2 = new Rectangle(); r2.setHeight(barH2); r2.setFill(AMBER ); r2.setArcHeight(barH2); r2.setArcWidth(barH2);
        Rectangle r3 = new Rectangle(); r3.setHeight(barH3); r3.setFill(BLUE  ); r3.setArcHeight(barH3); r3.setArcWidth(barH3);

        Label title = new Label("LCARS INTERFACE");
        title.setTextFill(AMBER);
        title.setFont(lcarsFontOrDefault(28, true));

        overlay.widthProperty().addListener((o,ov,nv)-> {
            double w = nv.doubleValue();
            r1.setWidth(w); r2.setWidth(w); r3.setWidth(w);
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
                        new KeyValue(r2.translateXProperty(),  1600),
                        new KeyValue(r3.translateXProperty(), -1600),
                        new KeyValue(title.opacityProperty(), 0)
                ),
                new KeyFrame(Duration.millis(450), new KeyValue(r1.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(750), new KeyValue(r2.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(1050), new KeyValue(r3.translateXProperty(), 0), new KeyValue(title.opacityProperty(), 1)),
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
        pulse.setArcWidth(24); pulse.setArcHeight(24);
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
            try { playAlarmTone(800, 350); Thread.sleep(200); playAlarmTone(600, 350); }
            catch (InterruptedException ignored) {}
        }, "alarm-tone").start();

        final boolean[] wasOnline = {true};
        Timeline poll = new Timeline(new KeyFrame(Duration.seconds(0.1), e -> checkAndSet(overlay, blink, wasOnline, playBeep)),
                                     new KeyFrame(Duration.seconds(7)));
        poll.setCycleCount(Animation.INDEFINITE);
        poll.play();
    }
    private void checkAndSet(StackPane overlay, Timeline blink, boolean[] wasOnline, Runnable playBeep) {
        new Thread(() -> {
            boolean online = isInternetAvailable();
            Platform.runLater(() -> {
                if (!online) {
                    overlay.setVisible(true);
                    if (blink.getStatus() != Animation.Status.RUNNING) blink.playFromStart();
                    if (wasOnline[0]) playBeep.run();
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
        int samples = (int)(millis * sampleRate / 1000);
        byte[] data = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            double angle = 2.0 * Math.PI * i * hz / sampleRate;
            short val = (short)(Math.sin(angle) * 32767);
            data[2*i]   = (byte)(val & 0xff);
            data[2*i+1] = (byte)((val >> 8) & 0xff);
        }
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(data), format, samples)) {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(format);
                line.start();
                byte[] buf = new byte[2048];
                int r;
                while ((r = ais.read(buf)) != -1) line.write(buf, 0, r);
                line.drain(); line.stop();
            }
        } catch (Exception ignored) {}
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
    private static void openWithDesktop(File f) { if (!Desktop.isDesktopSupported()) return; try { Desktop.getDesktop().open(f); } catch (IOException ignored) {} }
    private static File knownFolder(String kind) {
        String home = System.getProperty("user.home");
        boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
        List<File> c = new ArrayList<>();
        switch (kind) {
            case "Desktop"   -> { c.add(new File(home, "Desktop"));   if (isWin) c.add(new File(home, "OneDrive/Desktop")); }
            case "Documents" -> { c.add(new File(home, "Documents")); if (isWin) { c.add(new File(home, "OneDrive/Documents")); c.add(new File(home, "Documents/My Documents")); } }
            case "Downloads" -> { c.add(new File(home, "Downloads")); if (isWin) c.add(new File(home, "OneDrive/Downloads")); }
            case "Pictures"  -> { c.add(new File(home, "Pictures"));  if (isWin) c.add(new File(home, "OneDrive/Pictures")); }
            case "Music"     -> { c.add(new File(home, "Music"));     if (isWin) c.add(new File(home, "OneDrive/Music")); }
            case "Videos"    -> { c.add(new File(home, "Videos"));    if (isWin) c.add(new File(home, "OneDrive/Videos")); }
        }
        for (File f : c) if (f.exists() && f.isDirectory()) return f; return null;
    }
    private static boolean isInDesktop(File f) {
        File d = knownFolder("Desktop");
        if (d == null) return false;
        try {
            String dp = d.getCanonicalPath();
            String fp = f.getCanonicalFile().getParent();
            return fp != null && fp.equalsIgnoreCase(dp);
        } catch (IOException e) {
            return false;
        }
    }
    private static void deleteRecursive(Path p) throws IOException {
        if (!Files.exists(p)) return;
        Files.walk(p).sorted(Comparator.reverseOrder()).forEach(path -> {
            try { Files.deleteIfExists(path); } catch (IOException ignored) {}
        });
    }
    private static void copyRecursive(Path src, Path dst) throws IOException {
        Files.walk(src).forEach(s -> {
            try {
                Path rel = src.relativize(s);
                Path tgt = dst.resolve(rel);
                if (Files.isDirectory(s)) Files.createDirectories(tgt);
                else Files.copy(s, tgt, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {}
        });
    }
    private static boolean isImageFile(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".png")||n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".gif")||n.endsWith(".bmp")||n.endsWith(".webp");
    }
    private static String probeTypeSafe(Path p) {
        try { String t = Files.probeContentType(p); if (t != null) return t; } catch (IOException ignored) {}
        String name = p.getFileName().toString(); int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length()-1) return name.substring(dot+1).toUpperCase() + " file";
        return "Unknown";
    }
    private static String formatFileTime(long millis) { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(millis)); }
    private static String humanSize(long bytes) {
        String[] u = {"B","KB","MB","GB","TB"}; double b = bytes; int i=0; while (b>=1024 && i<u.length-1){ b/=1024; i++; }
        return String.format("%.2f %s", b, u[i]);
    }
    private static File uniqueName(File desired) {
        if (!desired.exists()) return desired;
        String name = desired.getName();
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        int i = 1;
        while (true) {
            File candidate = new File(desired.getParentFile(), base + " - Copy" + (i>1 ? " ("+i+")" : "") + ext);
            if (!candidate.exists()) return candidate;
            i++;
        }
    }

    // ===== Custom list cell =====
    private class LcarsFileCell extends ListCell<File> {
        private final Rectangle pill = new Rectangle(46, 18);
        private final Label name = new Label();
        private final Label meta = new Label();
        private final HBox box = new HBox(10);
        private final Color[] PILL_COLORS = new Color[]{ SALMON, AMBER, BLUE, TEAL, PEACH };

        LcarsFileCell() {
            pill.setArcWidth(18); pill.setArcHeight(18);
            name.setTextFill(TEXT); name.setFont(lcarsFontOrDefault(13, true));
            meta.setTextFill(PEACH); meta.setFont(lcarsFontOrDefault(11, false));
            VBox text = new VBox(1, name, meta);
            box.getChildren().addAll(pill, text);
            box.setAlignment(Pos.CENTER_LEFT);
            setPadding(new Insets(6, 10, 6, 10));
        }

        @Override protected void updateItem(File f, boolean empty) {
            super.updateItem(f, empty);
            if (empty || f == null) {
                setGraphic(null);
                setBackground(new Background(new BackgroundFill(BG, CornerRadii.EMPTY, Insets.EMPTY)));
            } else {
                name.setText(f.getName().isEmpty()? f.getPath() : f.getName());
                String type;
                try { String t = Files.probeContentType(f.toPath()); type = t != null ? t : (f.isDirectory()? "directory":"unknown"); }
                catch (IOException e) { type = f.isDirectory()? "directory":"unknown"; }
                String size = f.isDirectory()? "<dir>" : humanSize(f.length());
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
            trash.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, new CornerRadii(ICON_RADIUS), Insets.EMPTY)));
            trash.setBorder(new Border(new BorderStroke(EDGE, BorderStrokeStyle.SOLID, new CornerRadii(ICON_RADIUS), new BorderWidths(3))));
            getChildren().add(trash);

            widthProperty().addListener((o,ov,nv) -> layoutTrash());
            heightProperty().addListener((o,ov,nv) -> layoutTrash());
        }

        private void layoutTrash() {
            trash.relocate(16, getHeight() - trash.getPrefHeight() - 16);
        }

        void addIcon(File fileOnDesktop, File originDir) {
            addIcon(fileOnDesktop, originDir, Double.NaN, Double.NaN);
        }
        private void addIcon(File fileOnDesktop, File originDir, double x, double y) {
            String key = safePath(fileOnDesktop);
            if (tiles.containsKey(key)) return;
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
            if (t != null) getChildren().remove(t);
        }

        void onFileMoved(File src, File dst) {
            if (isInDesktop(src)) removeTileFor(src);
            if (isInDesktop(dst)) addIcon(dst, src.getParentFile());
            saveState();
        }

        private String safePath(File f) {
            try { return f.getCanonicalPath(); } catch (IOException e) { return f.getAbsolutePath(); }
        }

        void saveState() {
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(DESKTOP_STATE_FILE, false), StandardCharsets.UTF_8))) {
                for (Tile t : tiles.values()) {
                    String filePath = t.file.getAbsolutePath().replace("|","%7C");
                    String originPath = t.originDir != null ? t.originDir.getAbsolutePath().replace("|","%7C") : "";
                    double x = t.getLayoutX();
                    double y = t.getLayoutY();
                    w.write(filePath + "|" + originPath + "|" + x + "|" + y);
                    w.newLine();
                }
            } catch (IOException ignored) {}
        }

        void loadState() {
            if (!DESKTOP_STATE_FILE.exists()) return;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(DESKTOP_STATE_FILE), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] parts = line.split("\\|", -1);
                    if (parts.length < 4) continue;
                    String filePath = parts[0].replace("%7C","|");
                    String originPath = parts[1].isEmpty()? null : parts[1].replace("%7C","|");
                    double x, y;
                    try {
                        x = Double.parseDouble(parts[2]);
                        y = Double.parseDouble(parts[3]);
                    } catch (NumberFormatException nfe) { continue; }
                    File f = new File(filePath);
                    if (f.exists() && isInDesktop(f)) {
                        File origin = originPath == null ? null : new File(originPath);
                        addIcon(f, origin, x, y);
                    }
                }
            } catch (IOException ignored) {}
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
                Color fill   = PALETTE[idx];
                Color border = PALETTE[(idx + 2) % PALETTE.length];

                square = new Region();
                square.setPrefSize(ICON_SIZE, ICON_SIZE);
                square.setMinSize(ICON_SIZE, ICON_SIZE);
                square.setMaxSize(ICON_SIZE, ICON_SIZE);
                square.setBackground(new Background(new BackgroundFill(fill, new CornerRadii(ICON_RADIUS), Insets.EMPTY)));
                square.setBorder(new Border(new BorderStroke(border, BorderStrokeStyle.SOLID, new CornerRadii(ICON_RADIUS), new BorderWidths(3))));

                name = new Label(f.getName());
                name.setTextFill(Color.BLACK);
                name.setFont(lcarsFontOrDefault(12, true));
                name.setWrapText(true);
                name.setMaxWidth(ICON_SIZE - 12);
                name.setAlignment(Pos.CENTER);

                StackPane.setAlignment(name, Pos.CENTER);
                getChildren().addAll(square, name);

                setOnMousePressed(e -> {
                    if (e.getButton() != MouseButton.PRIMARY) return;
                    dragDX = e.getSceneX() - getLayoutX();
                    dragDY = e.getSceneY() - getLayoutY();
                });
                setOnMouseDragged(e -> {
                    if (e.getButton() != MouseButton.PRIMARY) return;
                    double nx = e.getSceneX() - dragDX;
                    double ny = e.getSceneY() - dragDY;
                    nx = Math.max(0, Math.min(nx, getParent().getLayoutBounds().getWidth()  - getWidth()));
                    ny = Math.max(0, Math.min(ny, getParent().getLayoutBounds().getHeight() - getHeight()));
                    relocate(nx, ny);
                });
                setOnMouseReleased(e -> {
                    Bounds squareScene = square.localToScene(square.getBoundsInLocal());
                    Bounds trashScene  = trash.localToScene(trash.getBoundsInLocal());
                    boolean overlaps = squareScene.intersects(trashScene);

                    if (overlaps) {
                        ((Pane)getParent()).getChildren().remove(this);
                        tiles.remove(safePath(file));
                        if (originDir != null && originDir.exists() && originDir.isDirectory()) {
                            File target = uniqueName(new File(originDir, file.getName()));
                            try {
                                if (file.isDirectory()) {
                                    try { Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
                                    catch (IOException moveEx) { copyRecursive(file.toPath(), target.toPath()); deleteRecursive(file.toPath()); }
                                } else {
                                    try { Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
                                    catch (IOException moveEx) { Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); Files.deleteIfExists(file.toPath()); }
                                }
                            } catch (IOException ignored) {}
                        }
                        saveState();
                    } else {
                        saveState();
                    }
                });

                setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) openWithDesktop(file);
                });
            }
        }
    }

    public static void main(String[] args) { launch(args); }
}
