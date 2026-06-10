package steg;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class StegoApp extends Application {

    private Stage primaryStage;
    private BufferedImage loadedImage = null;
    private final ImageView imageView = new ImageView();

    // Embed controls
    private TextArea messageArea;
    private File secretFile = null;
    private Label secretFileLabel;
    private Label capacityLabel;
    private CheckBox usePasswordCheck;
    private PasswordField passwordField;

    // Extract controls
    private PasswordField extractPasswordField;

    // Info area
    private Label infoLabel;

    // Undo storage
    private String lastTextMessage = null;
    private File lastSecretFile = null;
    private String lastPassword = null;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("Secure Image Steganography System");

        Scene scene = new Scene(createMainScreen(), 1100, 550);

scene.getStylesheets().add(
    getClass().getResource("stegui.css").toExternalForm()
);

primaryStage.setScene(scene);
        primaryStage.show();
    }

    /** Preserve size when switching scenes, clear image/data */
    private void refreshScene(Pane newRoot) {
    loadedImage = null;
    imageView.setImage(null);
    secretFile = null;

    double w = primaryStage.getWidth();
    double h = primaryStage.getHeight();

    Scene newScene = new Scene(newRoot, w, h);

    newScene.getStylesheets().add(
        getClass().getResource("stegui.css").toExternalForm()
    );

    primaryStage.setScene(newScene);
}

  private Pane createMainScreen() {
    VBox box = new VBox(20);
    box.setAlignment(Pos.CENTER);
    box.setPadding(new Insets(20));

    Label title = new Label("Secure Image Steganography System");
    title.setStyle("-fx-font-size: 30px; -fx-font-weight: bold;");

    Label subtitle = new Label(
            "Hide and extract secret messages and files using AES encryption");
    subtitle.setStyle("-fx-font-size: 15px;");

    Button embedBtn = new Button("Go to Embed Mode");

    embedBtn.setOnAction(e -> {
        try {
            System.out.println("EMBED BUTTON CLICKED");

            Pane pane = createEmbedPane();

            System.out.println("EMBED PANE CREATED");

            refreshScene(pane);

            System.out.println("SCENE CHANGED");

        } catch (Exception ex) {
            ex.printStackTrace();

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Embed Screen Error");
            alert.setContentText(ex.toString());
            alert.showAndWait();
        }
    });

    Button extractBtn = new Button("Go to Extract Mode");

    extractBtn.setOnAction(e -> {
        try {
            refreshScene(createExtractPane());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    });

    box.getChildren().addAll(
            title,
            subtitle,
            embedBtn,
            extractBtn
    );

    return box;
}
    private Pane createEmbedPane() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        VBox leftBox = new VBox(10);
        leftBox.setAlignment(Pos.CENTER);
        imageView.setFitWidth(420);
        imageView.setFitHeight(340);
        imageView.setPreserveRatio(true);
        leftBox.getChildren().add(imageView);
        root.setCenter(leftBox);

        // Drag Over
        leftBox.setOnDragOver(new javafx.event.EventHandler<javafx.scene.input.DragEvent>() {
            public void handle(javafx.scene.input.DragEvent event) {
                if (event.getGestureSource() != leftBox && event.getDragboard().hasFiles()) {
                    File f = event.getDragboard().getFiles().get(0);
                    if (f.getName().toLowerCase().endsWith(".png")) {
                        event.acceptTransferModes(TransferMode.COPY);
                    }
                }
                event.consume();
            }
        });

        // Drag Dropped
        leftBox.setOnDragDropped(new javafx.event.EventHandler<javafx.scene.input.DragEvent>() {
            public void handle(javafx.scene.input.DragEvent event) {
                boolean success = false;
                if (event.getDragboard().hasFiles()) {
                    File f = event.getDragboard().getFiles().get(0);
                    if (f.getName().toLowerCase().endsWith(".png")) {
                        loadImageFromFile(f);
                        success = true;
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Invalid file", "Only PNG images are supported.");
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            }
        });

        VBox controls = new VBox(10);
        controls.setPadding(new Insets(8));
        controls.setPrefWidth(420);

        HBox topBar = new HBox(10);

        Button toMain = new Button("Back");
        toMain.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                refreshScene(createMainScreen());
            }
        });

        Button toExtract = new Button("Switch to Extract Mode");
        toExtract.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                refreshScene(createExtractPane());
            }
        });

        Button undoBtn = new Button("Undo");
        undoBtn.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                onUndo();
            }
        });

        topBar.getChildren().addAll(toMain, toExtract, undoBtn);

        Button loadImageBtn = new Button("Load Cover Image (PNG)");
        loadImageBtn.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                onLoadImage();
            }
        });

        capacityLabel = new Label("Capacity: - bytes");
        infoLabel = new Label("Image info: -");

        ToggleGroup tg = new ToggleGroup();
        RadioButton textRadio = new RadioButton("Text message");
        textRadio.setToggleGroup(tg);
        textRadio.setSelected(true);

        RadioButton fileRadio = new RadioButton("Hide file");
        fileRadio.setToggleGroup(tg);

        RadioButton audioRadio = new RadioButton("Hide audio");
        audioRadio.setToggleGroup(tg);

        Button chooseFileBtn = new Button("Choose file/audio…");
        chooseFileBtn.setDisable(true);
        chooseFileBtn.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                chooseSecretFile();
            }
        });

        messageArea = new TextArea();
        messageArea.setPromptText("Enter message to embed...");
        messageArea.setPrefRowCount(6);

        textRadio.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                messageArea.setDisable(false);
                chooseFileBtn.setDisable(true);
            }
        });

        fileRadio.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                messageArea.setDisable(true);
                chooseFileBtn.setDisable(false);
            }
        });

        audioRadio.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
    public void handle(javafx.event.ActionEvent e) {
        messageArea.setDisable(true);
        chooseFileBtn.setDisable(false);
    }
});

secretFileLabel = new Label("No file selected");

usePasswordCheck = new CheckBox("Use password (AES)");
passwordField = new PasswordField();
passwordField.setDisable(true);

        usePasswordCheck.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                passwordField.setDisable(!usePasswordCheck.isSelected());
            }
        });

        Button embedBtn = new Button("Embed → Save stego.png");
        embedBtn.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                onEmbed(textRadio.isSelected(), false);
            }
        });

        Button embedZipBtn = new Button("Embed → Save & Package (ZIP)");
        embedZipBtn.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                onEmbed(textRadio.isSelected(), true);
            }
        });

        controls.getChildren().addAll(topBar, loadImageBtn, capacityLabel, infoLabel,
                textRadio, fileRadio, audioRadio,
                messageArea, chooseFileBtn, secretFileLabel,
                usePasswordCheck, passwordField, embedBtn, embedZipBtn);

        root.setRight(controls);
        return root;
    }

    private Pane createExtractPane() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        VBox leftBox = new VBox(10);
        leftBox.setAlignment(Pos.CENTER);
        imageView.setFitWidth(420);
        imageView.setFitHeight(340);
        imageView.setPreserveRatio(true);
        leftBox.getChildren().add(imageView);
        root.setCenter(leftBox);

        // Drag Over
        leftBox.setOnDragOver(new javafx.event.EventHandler<javafx.scene.input.DragEvent>() {
            public void handle(javafx.scene.input.DragEvent event) {
                if (event.getGestureSource() != leftBox && event.getDragboard().hasFiles()) {
                    File f = event.getDragboard().getFiles().get(0);
                    if (f.getName().toLowerCase().endsWith(".png")) {
                        event.acceptTransferModes(TransferMode.COPY);
                    }
                }
                event.consume();
            }
        });

        // Drag Dropped
        leftBox.setOnDragDropped(new javafx.event.EventHandler<javafx.scene.input.DragEvent>() {
            public void handle(javafx.scene.input.DragEvent event) {
                boolean success = false;
                if (event.getDragboard().hasFiles()) {
                    File f = event.getDragboard().getFiles().get(0);
                    if (f.getName().toLowerCase().endsWith(".png")) {
                        loadImageFromFile(f);
                        success = true;
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Invalid file", "Only PNG images are supported.");
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            }
        });

        VBox controls = new VBox(10);
        controls.setPadding(new Insets(8));
        controls.setPrefWidth(420);

        HBox topBar = new HBox(10);

        Button toMain = new Button("Back");
        toMain.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                refreshScene(createMainScreen());
            }
        });

        Button toEmbed = new Button("Switch to Embed Mode");
        toEmbed.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                refreshScene(createEmbedPane());
            }
        });

        Button undoBtn = new Button("Undo");
        undoBtn.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                onUndo();
            }
        });

        topBar.getChildren().addAll(toMain, toEmbed, undoBtn);

        Button loadImageBtn = new Button("Load Stego Image (PNG)");
        loadImageBtn.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                onLoadImage();
            }
        });

        Label passLabel = new Label("Password (if encrypted):");
        extractPasswordField = new PasswordField();

        Button extractBtn = new Button("Extract from Image");
        extractBtn.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                onExtract();
            }
        });

        infoLabel = new Label("Image info: -");

        controls.getChildren().addAll(topBar, loadImageBtn, passLabel, extractPasswordField, extractBtn, infoLabel);
        root.setRight(controls);

        return root;
    }

    // ===================== ACTION METHODS =====================

    private void onLoadImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open PNG image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG images", "*.png"));
        File f = chooser.showOpenDialog(primaryStage);
        if (f != null) loadImageFromFile(f);
    }

    private void loadImageFromFile(File f) {
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) {
                showAlert(Alert.AlertType.ERROR, "Load error", "Selected file is not a readable image.");
                return;
            }
            loadedImage = toARGB(img);
            imageView.setImage(SwingFXUtils.toFXImage(loadedImage, null));

            long cap = Steganography.getMaxPayloadBytes(loadedImage);
            capacityLabel.setText("Capacity: " + cap + " bytes (" + formatSize(cap) + ")");

            infoLabel.setText("File: " + f.getName() + "\nSize: " + formatSize(f.length())
                    + "\nDimensions: " + loadedImage.getWidth() + " x " + loadedImage.getHeight()
                    + "\nCapacity: " + cap + " bytes (" + formatSize(cap) + ")");
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "I/O error", "Could not read image: " + ex.getMessage());
        }
    }

    private void chooseSecretFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose file or audio to hide");
        File f = chooser.showOpenDialog(primaryStage);
        if (f == null) return;
        secretFile = f;
        secretFileLabel.setText("Chosen: " + f.getName() + " (" + formatSize(f.length()) + ")");
    }

    private void onEmbed(boolean isText, boolean packageZip) {
        if (loadedImage == null) {
            showAlert(Alert.AlertType.WARNING, "No image", "Load a PNG cover image first.");
            return;
        }
        try {
            byte[] secretBytes;
            String hiddenFilename = null;

            if (isText) {
                String msg = messageArea.getText();
                if (msg == null || msg.isEmpty()) {
                    showAlert(Alert.AlertType.WARNING, "No message", "Enter a text message to hide.");
                    return;
                }
                secretBytes = msg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                lastTextMessage = msg;
            } else {
                if (secretFile == null) {
                    showAlert(Alert.AlertType.WARNING, "No file", "Choose a file to hide.");
                    return;
                }
                secretBytes = Files.readAllBytes(secretFile.toPath());
                hiddenFilename = secretFile.getName();
                lastSecretFile = secretFile;
            }

            boolean usePassword = usePasswordCheck.isSelected();
            String password = (usePassword ? passwordField.getText() : null);
            if (usePassword && (password == null || password.isEmpty())) {
                showAlert(Alert.AlertType.WARNING, "Password missing", "Please enter a password or uncheck 'Use password'.");
                return;
            }
            if (usePassword) lastPassword = password;

            long capacity = Steganography.getMaxPayloadBytes(loadedImage);
            long estimated = secretBytes.length + 4096;
            if (estimated > capacity) {
                boolean cont = confirmYesNo("Capacity warning",
                        "Secret may be too large for this cover image.\n" +
                                "Secret size (approx): " + formatSize(secretBytes.length) +
                                "\nCapacity: " + capacity + " bytes (" + formatSize(capacity) + ")\nContinue?");
                if (!cont) return;
            }

            BufferedImage stego = Steganography.embed(loadedImage, secretBytes, (isText ? null : hiddenFilename), usePassword, password);

            FileChooser saveChooser = new FileChooser();
            saveChooser.setTitle("Save stego PNG (enter filename)");
            saveChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG image", "*.png"));
            File out = saveChooser.showSaveDialog(primaryStage);
            if (out == null) return;
            if (!out.getName().toLowerCase().endsWith(".png")) {
                out = new File(out.getAbsolutePath() + ".png");
            }

            ImageIO.write(stego, "PNG", out);

            if (packageZip) {
                File zipTarget = new File(out.getParentFile(), out.getName().replaceAll("\\.png$", "") + ".zip");
                createZipWithSingleFile(out, zipTarget);
                showAlert(Alert.AlertType.INFORMATION, "Saved & Packaged",
                        "Stego image saved: " + out.getAbsolutePath() +
                                "\nPackage created: " + zipTarget.getAbsolutePath() +
                                "\n\nSend the ZIP via messaging apps as Document (do NOT send as a photo).");
            } else {
                showAlert(Alert.AlertType.INFORMATION, "Saved", "Stego image saved: " + out.getAbsolutePath()
                        + "\n\nTip: To safely send via WhatsApp, send as Document or ZIP the file.");
            }

        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Error", "Embedding failed: " + ex.getMessage());
        }
    }

    private void createZipWithSingleFile(File sourceFile, File zipFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            ZipEntry entry = new ZipEntry(sourceFile.getName());
            zos.putNextEntry(entry);
            byte[] bytes = Files.readAllBytes(sourceFile.toPath());
            zos.write(bytes, 0, bytes.length);
            zos.closeEntry();
        }
    }

    private void onExtract() {
        if (loadedImage == null) {
            showAlert(Alert.AlertType.WARNING, "No image", "Load a stego PNG first.");
            return;
        }
        String password = extractPasswordField.getText();
        if (password != null && password.isEmpty()) password = null;

        try {
            Steganography.ExtractionResult res = Steganography.extract(loadedImage, password);
            if (res == null) {
                showAlert(Alert.AlertType.ERROR, "Failed", "Could not extract data.");
                return;
            }

            if (res.isFile) {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Save extracted file");
                chooser.setInitialFileName(res.filename == null ? "extracted.bin" : res.filename);
                File out = chooser.showSaveDialog(primaryStage);
                if (out == null) return;
                Files.write(out.toPath(), res.data);
                lastSecretFile = out;
                showAlert(Alert.AlertType.INFORMATION, "Saved", "File extracted to: " + out.getAbsolutePath());
            } else {
                TextArea ta = new TextArea(new String(res.data, java.nio.charset.StandardCharsets.UTF_8));
                ta.setWrapText(true);
                ta.setEditable(false);
                ta.setPrefSize(500, 400);
                Stage s = new Stage();
                s.setTitle("Extracted Message");
                s.setScene(new Scene(new VBox(ta), 500, 400));
                s.show();
            }

        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Error", "Extraction failed: " + ex.getMessage());
        }
    }

    private void onUndo() {
        if (lastTextMessage != null) messageArea.setText(lastTextMessage);
        if (lastSecretFile != null) {
            secretFile = lastSecretFile;
            secretFileLabel.setText("Chosen: " + secretFile.getName() + " (" + formatSize(secretFile.length()) + ")");
        }
        if (lastPassword != null) {
            passwordField.setText(lastPassword);
            extractPasswordField.setText(lastPassword);
        }
    }

    // ===================== UTILITIES =====================

    private static BufferedImage toARGB(BufferedImage img) {
        BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        argb.getGraphics().drawImage(img, 0, 0, null);
        return argb;
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private boolean confirmYesNo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        return a.showAndWait().filter(response -> response == ButtonType.OK).isPresent();
    }

    private static String formatSize(long bytes) {
        DecimalFormat df = new DecimalFormat("0.##");
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return df.format(kb) + " KB";
        double mb = kb / 1024.0;
        return df.format(mb) + " MB";
    }

    public static void main(String[] args) {
        launch(args);
    }
}
