package horizon;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.File;
import java.util.prefs.Preferences;

public class ImprovedOCRApp extends JFrame {
    private final ImagePanel imagePanel;
    private final JTextArea resultArea;
    private JComboBox<String> languageSelector;
    private JButton copyButton;
    private JButton saveButton;
    private final Preferences prefs;
    private final Tesseract tesseract;

    private static final String TESSDATA_PATH = "C:\\Users\\ufukt\\Desktop\\metin_ceviri";
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new ImprovedOCRApp();
        });
    }

    public ImprovedOCRApp() {
        super("Gelişmiş OCR Uygulaması");
        prefs = Preferences.userNodeForPackage(ImprovedOCRApp.class);
        tesseract = initializeTesseract();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        imagePanel = new ImagePanel();
        resultArea = createResultArea();
        JPanel controlPanel = createControlPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(imagePanel),
                new JScrollPane(resultArea));
        splitPane.setResizeWeight(0.7);

        add(controlPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        setSize(1200, 800);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private Tesseract initializeTesseract() {
        Tesseract tess = new Tesseract();
        File tessdataDir = new File(TESSDATA_PATH);
        if (!tessdataDir.exists()) {
            JOptionPane.showMessageDialog(this,
                    "tessdata klasörü bulunamadı: " + tessdataDir.getAbsolutePath(),
                    "Hata",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        tess.setDatapath(tessdataDir.getAbsolutePath());
        tess.setLanguage(prefs.get("last_language", "eng"));
        return tess;
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton loadButton = new JButton("Resim Yükle");
        loadButton.addActionListener(e -> loadImage());

        languageSelector = new JComboBox<>(new String[]{"eng", "tur", "deu"});
        languageSelector.setSelectedItem(prefs.get("last_language", "eng"));
        languageSelector.addActionListener(e -> {
            String selectedLang = (String) languageSelector.getSelectedItem();
            prefs.put("last_language", selectedLang);
            tesseract.setLanguage(selectedLang);
        });

        copyButton = new JButton("Metni Kopyala");
        copyButton.setEnabled(false);
        copyButton.addActionListener(e -> {
            resultArea.selectAll();
            resultArea.copy();
            resultArea.select(0, 0);
        });

        saveButton = new JButton("Metni Kaydet");
        saveButton.setEnabled(false);
        saveButton.addActionListener(e -> saveText());

        panel.add(loadButton);
        panel.add(new JLabel("Dil:"));
        panel.add(languageSelector);
        panel.add(copyButton);
        panel.add(saveButton);

        return panel;
    }

    private JTextArea createResultArea() {
        JTextArea area = new JTextArea();
        area.setWrapStyleWord(true);
        area.setLineWrap(true);
        area.setFont(new Font("Arial", Font.PLAIN, 14));
        area.setMargin(new Insets(5, 5, 5, 5));
        return area;
    }

    private void loadImage() {
        JFileChooser chooser = new JFileChooser(prefs.get("last_directory", ""));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Resim Dosyaları", "jpg", "jpeg", "png", "bmp", "gif"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                prefs.put("last_directory", file.getParent());

                BufferedImage image = ImageIO.read(file);
                imagePanel.setImage(image);
                resultArea.setText("");
                copyButton.setEnabled(false);
                saveButton.setEnabled(false);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "Resim yüklenirken hata oluştu: " + e.getMessage(),
                        "Hata", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveText() {
        JFileChooser chooser = new JFileChooser(prefs.get("last_save_directory", ""));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Metin Dosyası", "txt"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".txt")) {
                    file = new File(file.getPath() + ".txt");
                }
                prefs.put("last_save_directory", file.getParent());

                java.nio.file.Files.writeString(file.toPath(), resultArea.getText());
                JOptionPane.showMessageDialog(this,
                        "Metin başarıyla kaydedildi!",
                        "Başarılı", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "Metin kaydedilirken hata oluştu: " + e.getMessage(),
                        "Hata", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    class ImagePanel extends JPanel {
        private BufferedImage image;
        private Rectangle selection;
        private Point startPoint;
        private boolean isDragging;

        public ImagePanel() {
            selection = new Rectangle();
            startPoint = new Point();

            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    startPoint.setLocation(e.getPoint());
                    isDragging = true;
                    selection.setBounds(0, 0, 0, 0);
                    repaint();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (isDragging) {
                        updateSelection(e.getPoint());
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (isDragging) {
                        isDragging = false;
                        updateSelection(e.getPoint());
                        processSelection();
                    }
                }
            };

            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
        }

        private void updateSelection(Point currentPoint) {
            int x = Math.min(startPoint.x, currentPoint.x);
            int y = Math.min(startPoint.y, currentPoint.y);
            int width = Math.abs(currentPoint.x - startPoint.x);
            int height = Math.abs(currentPoint.y - startPoint.y);
            selection.setBounds(x, y, width, height);
        }

        public void setImage(BufferedImage image) {
            this.image = image;
            selection.setBounds(0, 0, 0, 0);
            setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
            revalidate();
            repaint();
        }

        private void processSelection() {
            if (image != null) {
                if (selection.width > 5 && selection.height > 5) {
                    try {
                        BufferedImage cropped = image.getSubimage(
                                selection.x, selection.y, selection.width, selection.height);

                        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                        String result = performOCR(cropped);
                        displayOCRResult(result);
                        setCursor(Cursor.getDefaultCursor());
                    } catch (RasterFormatException e) {
                        JOptionPane.showMessageDialog(this,
                                "Seçili alan hatalı: " + e.getMessage(),
                                "Hata", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Geçerli bir seçim yapın (minimum 5x5 boyutunda).",
                            "Hata", JOptionPane.WARNING_MESSAGE);
                }
            }
        }

        private String performOCR(BufferedImage croppedImage) {
            try {
                return tesseract.doOCR(croppedImage);
            } catch (TesseractException e) {
                JOptionPane.showMessageDialog(this,
                        "OCR işlemi sırasında hata oluştu: " + e.getMessage(),
                        "Hata", JOptionPane.ERROR_MESSAGE);
                return "";
            }
        }

        private void displayOCRResult(String result) {
            resultArea.setText(result);
            if (!result.isBlank()) {
                copyButton.setEnabled(true);
                saveButton.setEnabled(true);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image != null) {
                g.drawImage(image, 0, 0, this);
            }
            if (selection.width > 0 && selection.height > 0) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setColor(new Color(0, 0, 255, 128));
                g2d.fill(selection);
                g2d.setColor(Color.BLUE);
                g2d.draw(selection);
            }
        }
    }
}
