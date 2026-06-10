package steg;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SteganographyUI extends Application {

    private Stage primaryStage;

    private ImageView imageViewEmbed = new ImageView();
    private ImageView imageViewExtract = new ImageView();

    private BufferedImage loadedImageEmbed;
    private BufferedImage loadedImageExtract;

    private TextArea messageArea;
    private File secretFile;
    private Label secretFileLabel;
    private Label capacityLabel;
    private CheckBox usePasswordCheck;
    private PasswordField passwordField;
    private Label warningLabelEmbed;
    private ProgressIndicator progressEmbed;
    private Label infoLabelEmbed;

    private PasswordField extractPasswordField;
    private Label warningLabelExtract;
    private ProgressIndicator progressExtract;
    private Label infoLabelExtract;

    private String lastTextMessage = null;
    private File lastSecretFile = null;
    private String lastPassword = null;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("Steganography Project (PNG only)");

        Scene scene = new Scene(createMainScreen(), 1150, 600);
        scene.getStylesheets().add(getClass().getResource("stegui.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
 // === Calculate true pixel-based embedding capacity (in bytes) ===


    /** ---------------- MAIN SCREEN ---------------- **/
    private BorderPane createMainScreen() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));

        VBox box = new VBox(20);
        box.setAlignment(Pos.CENTER);
        Label title = new Label("Steganography Project (PNG)");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        Button embedBtn = new Button("Go to Embed Mode");
        embedBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                switchScene(createEmbedPane());
            }
        });

        Button extractBtn = new Button("Go to Extract Mode");
        extractBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                switchScene(createExtractPane());
            }
        });

        box.getChildren().addAll(title, embedBtn, extractBtn);
        root.setCenter(box);
        return root;
    }
    

    private void switchScene(Pane pane) {
        Scene scene = new Scene(pane, primaryStage.getWidth(), primaryStage.getHeight());
        scene.getStylesheets().add(getClass().getResource("stegui.css").toExternalForm());
        primaryStage.setScene(scene);
    }
    
 // --- Calculate embedding capacity purely based on pixel count ---
    private static long getPixelBasedCapacity(BufferedImage img) {
        if (img == null) return 0;
        long totalPixels = (long) img.getWidth() * img.getHeight();
        // Each pixel has 3 channels (R,G,B) → 3 bits per pixel → 3/8 bytes per pixel
        return (totalPixels * 3L) / 8L;  // bytes
    }


  
    private BorderPane createEmbedPane() {
        clearAll(); 

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        // ===== LEFT IMAGE PREVIEW =====
        VBox left = new VBox(10);
        left.setAlignment(Pos.CENTER);
        imageViewEmbed.setFitWidth(500);
        imageViewEmbed.setFitHeight(400);
        imageViewEmbed.setPreserveRatio(true);
        left.getChildren().add(imageViewEmbed);
        root.setCenter(left);

        
        left.setOnDragOver(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                if (event.getGestureSource() != left && event.getDragboard().hasFiles())
                    event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            }
        });

        left.setOnDragDropped(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                Dragboard db = event.getDragboard();
                if (db.hasFiles()) {
                    File file = db.getFiles().get(0);
                    loadImageFromFile(file, true);
                    event.setDropCompleted(true);
                }
                event.consume();
            }
        });

        // ===== RIGHT CONTROL PANEL =====
        VBox controls = new VBox(10);
        controls.setPadding(new Insets(10));
        controls.setPrefWidth(450);
        controls.getStyleClass().add("control-pane");

        // ===== TOP BAR =====
        HBox topBar = new HBox(10);
        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("btn-orange"); 
        backBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                clearAll();
                switchScene(createMainScreen());
            }
        });

        Button switchBtn = new Button("Switch to Extract Mode");
        switchBtn.getStyleClass().add("btn-orange"); 
        switchBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                clearAll();
                switchScene(createExtractPane());
            }
        });

        Button undoBtn = new Button("Undo");
        undoBtn.getStyleClass().add("btn-orange"); 
        undoBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                onUndo();
            }
        });
        topBar.getChildren().addAll(backBtn, switchBtn, undoBtn);

        // ===== LABELS AND INFO =====
        Label title = new Label("Embed Secret");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Button loadImageBtn = new Button("Load Cover Image (PNG)");
        loadImageBtn.getStyleClass().add("btn-green"); 
        loadImageBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                onLoadImage(true);
            }
        });

        capacityLabel = new Label("Capacity: - bytes");
        infoLabelEmbed = new Label("Image info: -");
        warningLabelEmbed = new Label();

     // ===== INPUT OPTIONS =====
        ToggleGroup tg = new ToggleGroup();

        // declare radios once
        RadioButton textRadio = new RadioButton("Text message");
        textRadio.getStyleClass().add("option-label");
        textRadio.setToggleGroup(tg);
        textRadio.setSelected(true);

        RadioButton fileRadio = new RadioButton("Hide file");
        fileRadio.getStyleClass().add("option-label");
        fileRadio.setToggleGroup(tg);

        RadioButton audioRadio = new RadioButton("Hide audio");
        audioRadio.getStyleClass().add("option-label");
        audioRadio.setToggleGroup(tg);

        // choose file button (disabled for text)
        Button chooseFileBtn = new Button("Choose file/audio...");
        chooseFileBtn.getStyleClass().add("btn-green");
        chooseFileBtn.setDisable(true);
        chooseFileBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                chooseSecretFile();
            }
        });

        // message area (enabled only for text)
        messageArea = new TextArea();
        messageArea.setPromptText("Enter message to embed...");
        messageArea.setPrefRowCount(6);

        // keep radios & controls in sync: if Text selected -> message enabled, choose-file disabled.
        // otherwise -> message disabled, choose-file enabled.
        tg.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == textRadio) {
                messageArea.setDisable(false);
                chooseFileBtn.setDisable(true);
            } else {
                messageArea.setDisable(true);
                chooseFileBtn.setDisable(false);
            }
        });

        // now declare secretFileLabel (the rest of your code expects this to exist next)
        secretFileLabel = new Label("No file chosen");
        secretFileLabel.setWrapText(true);

        // password checkbox & field (declare here so earlier UI doesn't reference before init)
        usePasswordCheck = new CheckBox("Use password (AES)");
        passwordField = new PasswordField();
        passwordField.setDisable(true);
        usePasswordCheck.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                passwordField.setDisable(!usePasswordCheck.isSelected());
            }
        });


        secretFileLabel = new Label("No file chosen");
        secretFileLabel.setWrapText(true);

        usePasswordCheck = new CheckBox("Use password (AES)");
        passwordField = new PasswordField();
        passwordField.setDisable(true);
        usePasswordCheck.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                passwordField.setDisable(!usePasswordCheck.isSelected());
            }
        });

        Button embedBtn = new Button("Embed → Save stego.png");
        embedBtn.getStyleClass().add("btn-pink"); // 🔴
        embedBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                onEmbed(textRadio.isSelected(), false);
            }
        });

        Button embedAndPackageBtn = new Button("Embed → Save & Package (ZIP)");
        embedAndPackageBtn.getStyleClass().add("btn-pink"); // 🔴
        embedAndPackageBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                onEmbed(textRadio.isSelected(), true);
            }
        });

        progressEmbed = new ProgressIndicator();
        progressEmbed.setVisible(false);

        controls.getChildren().addAll(
                topBar, title, loadImageBtn,
                capacityLabel, infoLabelEmbed, warningLabelEmbed,
                textRadio, fileRadio, audioRadio,
                messageArea, chooseFileBtn, secretFileLabel,
                usePasswordCheck, passwordField,
                embedBtn, embedAndPackageBtn, progressEmbed
        );

        root.setRight(controls);
        return root;
        
        
    }

    

    /** ---------------- EXTRACT PANE ---------------- **/
    private BorderPane createExtractPane() {
        clearAll(); // ✅ Reset all fields before showing extract mode

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        // ===== LEFT IMAGE PREVIEW =====
        VBox left = new VBox(10);
        left.setAlignment(Pos.CENTER);
        imageViewExtract.setFitWidth(500);
        imageViewExtract.setFitHeight(400);
        imageViewExtract.setPreserveRatio(true);
        left.getChildren().add(imageViewExtract);
        root.setCenter(left);

        // ✅ Drag & Drop Support
        left.setOnDragOver(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                if (event.getGestureSource() != left && event.getDragboard().hasFiles())
                    event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            }
        });

        left.setOnDragDropped(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                Dragboard db = event.getDragboard();
                if (db.hasFiles()) {
                    File file = db.getFiles().get(0);
                    loadImageFromFile(file, false); // false → extract mode
                    event.setDropCompleted(true);
                }
                event.consume();
            }
        });

        // ===== RIGHT CONTROL PANEL =====
        VBox controls = new VBox(10);
        controls.setPadding(new Insets(10));
        controls.setPrefWidth(450);
        controls.getStyleClass().add("control-pane");

        // ===== TOP BAR =====
        HBox topBar = new HBox(10);
        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("btn-orange"); // 🟠
        backBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                clearAll();
                switchScene(createMainScreen());
            }
        });

        Button switchBtn = new Button("Switch to Embed Mode");
        switchBtn.getStyleClass().add("btn-orange"); // 🟠
        switchBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                clearAll();
                switchScene(createEmbedPane());
            }
        });

        Button undoBtn = new Button("Undo");
        undoBtn.getStyleClass().add("btn-orange"); // 🟠
        undoBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                onUndo();
            }
        });

        topBar.getChildren().addAll(backBtn, switchBtn, undoBtn);

        // ===== TITLE & INFO =====
        Label title = new Label("Extract Secret");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Button loadImageBtn = new Button("Load Stego Image (PNG)");
        loadImageBtn.getStyleClass().add("btn-green"); // 🟢
        loadImageBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                onLoadImage(false);
            }
        });

        Label passLabel = new Label("Password (if encrypted):");
        extractPasswordField = new PasswordField();

        Button extractBtn = new Button("Extract from Image");
        extractBtn.getStyleClass().add("btn-pink"); // 🔴
        extractBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                onExtract();
            }
        });

        infoLabelExtract = new Label("Image info: -");
        warningLabelExtract = new Label();

        progressExtract = new ProgressIndicator();
        progressExtract.setVisible(false);

        // ===== ADD ALL ELEMENTS =====
        controls.getChildren().addAll(
                topBar, title, loadImageBtn,
                passLabel, extractPasswordField,
                extractBtn, infoLabelExtract, warningLabelExtract, progressExtract
        );

        root.setRight(controls);
        return root;
    }


    /** ---------------- SHARED HELPERS ---------------- **/
    private void clearAll() {
        loadedImageEmbed = null;
        loadedImageExtract = null;
        secretFile = null;
        lastTextMessage = null;
        lastSecretFile = null;
        lastPassword = null;

        imageViewEmbed.setImage(null);
        imageViewExtract.setImage(null);
        if (messageArea != null) messageArea.clear();
        if (secretFileLabel != null) secretFileLabel.setText("No file chosen");
        if (passwordField != null) passwordField.clear();
        if (extractPasswordField != null) extractPasswordField.clear();
        if (warningLabelEmbed != null) warningLabelEmbed.setText("");
        if (warningLabelExtract != null) warningLabelExtract.setText("");
        if (capacityLabel != null) capacityLabel.setText("Capacity: - bytes");
        if (infoLabelEmbed != null) infoLabelEmbed.setText("Image info: -");
        if (infoLabelExtract != null) infoLabelExtract.setText("Image info: -");
    }

    private void onLoadImage(boolean isEmbed) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open PNG image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG images", "*.png"));
        File f = chooser.showOpenDialog(primaryStage);
        if (f != null) loadImageFromFile(f, isEmbed);
    }

    private void loadImageFromFile(File file, boolean isEmbed) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                setWarning(isEmbed, "❌ Selected file is not a valid image.");
                return;
            }

            BufferedImage convertedImg = toARGB(img);

            if (isEmbed) {
                loadedImageEmbed = convertedImg;
                imageViewEmbed.setImage(SwingFXUtils.toFXImage(loadedImageEmbed, null));
                // Pixel-based capacity
                long cap = getPixelBasedCapacity(loadedImageEmbed);
                capacityLabel.setText("Capacity: " + cap + " bytes (" + formatSize(cap) + ")");
                infoLabelEmbed.setText("File: " + file.getName() + " | Dimensions: "
                        + img.getWidth() + "×" + img.getHeight() + " | Capacity: " + formatSize(cap));
                warningLabelEmbed.setText(cap < 20000 ? "⚠ Cover image is small — limited capacity." : "");
            } else {
                loadedImageExtract = convertedImg;
                imageViewExtract.setImage(SwingFXUtils.toFXImage(loadedImageExtract, null));
                infoLabelExtract.setText("File: " + file.getName() + " | Dimensions: "
                        + img.getWidth() + "×" + img.getHeight() + " | Capacity: " + formatSize(getPixelBasedCapacity(loadedImageExtract)));
                warningLabelExtract.setText("");
            }
        } catch (Exception ex) {
            setWarning(isEmbed, "Error reading image: " + ex.getMessage());
        }
    }



    private void chooseSecretFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose file/audio to hide");
        File f = chooser.showOpenDialog(primaryStage);
        if (f == null) return;
        secretFile = f;
        secretFileLabel.setText("Chosen: " + f.getName() + " (" + formatSize(f.length()) + ")");
    }

    private void onEmbed(boolean isText, boolean packageZip) {
        if (loadedImageEmbed == null) {
            setWarning(true, "Load a PNG cover image first.");
            return;
        }

        progressEmbed.setVisible(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try {
                    byte[] secretBytes;
                    String hiddenFilename = null;

                    if (isText) {
                        String msg = messageArea.getText();
                        if (msg == null || msg.isEmpty()) {
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    setWarning(true, "Enter a text message to hide.");
                                }
                            });
                            return null;
                        }
                        secretBytes = msg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        lastTextMessage = msg;
                    } else {
                        if (secretFile == null) {
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    setWarning(true, "Choose a file to hide.");
                                }
                            });
                            return null;
                        }
                        secretBytes = Files.readAllBytes(secretFile.toPath());
                        hiddenFilename = secretFile.getAbsolutePath();
                        lastSecretFile = secretFile;
                    }

                    boolean usePassword = usePasswordCheck.isSelected();
                    String password = usePassword ? passwordField.getText() : null;
                    if (usePassword) lastPassword = password;

                    // 🔹 Perform embedding (heavy work)
                    BufferedImage stego = Steganography.embed(loadedImageEmbed, secretBytes, hiddenFilename, usePassword, password);

                    // 🔹 Switch to JavaFX thread for file chooser & saving
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                FileChooser saveChooser = new FileChooser();
                                saveChooser.setTitle("Save stego PNG");
                                saveChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG image", "*.png"));
                                File out = saveChooser.showSaveDialog(primaryStage);
                                if (out != null) {
                                    ImageIO.write(stego, "PNG", out);

                                    if (packageZip) {
                                        File zipTarget = new File(out.getParentFile(), out.getName().replaceAll("\\.png$", "") + ".zip");
                                        createZipWithSingleFile(out, zipTarget);
                                    }

                                    setWarning(true, "✅ Stego image saved successfully: " + out.getName());
                                } else {
                                    setWarning(true, "Save cancelled by user.");
                                }
                            } catch (Exception e) {
                                setWarning(true, "Error saving image: " + e.getMessage());
                            } finally {
                                progressEmbed.setVisible(false);
                            }
                        }
                    });

                } catch (Exception ex) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            progressEmbed.setVisible(false);
                            setWarning(true, "Embedding error: " + ex.getMessage());
                        }
                    });
                }
                return null;
            }
        };
        new Thread(task).start();
    }


    private void onExtract() {
        if (loadedImageExtract == null) {
            setWarning(false, "⚠ Load a PNG stego image first.");
            return;
        }

        progressExtract.setVisible(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try {
                    String password = extractPasswordField.getText();
                    if (password != null && password.isEmpty()) password = null;

                    Steganography.ExtractionResult res = Steganography.extract(loadedImageExtract, password);

                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            progressExtract.setVisible(false);
                            if (res == null) {
                                setWarning(false, "❌ No hidden data found.");
                                return;
                            }
                            try {
                                if (res.isFile) {
                                    FileChooser chooser = new FileChooser();
                                    chooser.setTitle("Save extracted file");
                                    chooser.setInitialFileName(res.filename == null ? "extracted.bin" : res.filename);
                                    File out = chooser.showSaveDialog(primaryStage);
                                    if (out != null) {
                                        Files.write(out.toPath(), res.data);
                                        setWarning(false, "✅ File extracted and saved: " + out.getName());
                                    }
                                } else {
                                    String text = new String(res.data, java.nio.charset.StandardCharsets.UTF_8);
                                    TextArea ta = new TextArea(text);
                                    ta.setWrapText(true);
                                    ta.setEditable(false);
                                    Alert a = new Alert(Alert.AlertType.INFORMATION);
                                    a.setTitle("Extracted message");
                                    a.getDialogPane().setContent(ta);
                                    a.showAndWait();
                                }
                            } catch (Exception e) {
                                setWarning(false, "❌ Error saving file: " + e.getMessage());
                            }
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            setWarning(false, "❌ Extraction error: " + ex.getMessage());
                        }
                    });
                }
                return null;
            }
        };
        new Thread(task).start();
    }

    private void onUndo() {
        messageArea.setText(lastTextMessage);
        secretFile = lastSecretFile;
        passwordField.setText(lastPassword);
        setWarning(true, "Undo restored previous input.");
    }

    private void setWarning(boolean isEmbed, String msg) {
        if (isEmbed) warningLabelEmbed.setText(msg);
        else warningLabelExtract.setText(msg);
    }

    private static void createZipWithSingleFile(File sourceFile, File zipFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            ZipEntry entry = new ZipEntry(sourceFile.getName());
            zos.putNextEntry(entry);
            byte[] bytes = Files.readAllBytes(sourceFile.toPath());
            zos.write(bytes);
            zos.closeEntry();
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return new DecimalFormat("#.##").format(kb) + " KB";
        double mb = kb / 1024.0;
        return new DecimalFormat("#.##").format(mb) + " MB";
    }

    private static BufferedImage toARGB(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage b = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = b.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return b;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
