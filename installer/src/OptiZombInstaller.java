import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.sigpipe.jbsdiff.Patch;

/**
 * OptiZomb Lite Installer — cross-platform bsdiff patch-based approach.
 *
 * Supports Linux (loose .class files), Windows (projectzomboid.jar or loose),
 * and macOS. Auto-detects Steam installation via common paths and
 * libraryfolders.vdf parsing.
 *
 * Install: applies bsdiff patches against vanilla classes to build optizomb.jar,
 *          installs shaders + config, adds classpath entry to all launcher JSONs.
 * Uninstall: removes optizomb.jar + shaders + config, restores original JSONs.
 */
public class OptiZombInstaller extends JFrame {

    private static final String VERSION = "0.1.0"; // injected by build-installer.sh from version.txt
    private static final String TITLE = "OptiZomb Lite " + VERSION;
    private static final String JAR_NAME = "optizomb.jar";
    private static final String VANILLA_JAR_NAME = "projectzomboid.jar";
    private static final String SHADER_MANIFEST = ".optizomb-shaders.txt";

    private JTextArea logArea;
    private JButton installButton;
    private JButton uninstallButton;
    private JButton browseButton;
    private JTextField pathField;

    public OptiZombInstaller() {
        super(TITLE);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        buildUI();
        pack();
        setLocationRelativeTo(null);

        String detected = detectPZDirectory();
        if (detected != null) {
            pathField.setText(detected);
            log("Auto-detected PZ installation: " + detected);
            log("Platform: " + detectPlatformName());
        } else {
            log("Could not auto-detect Project Zomboid installation.");
            log("Please browse to your ProjectZomboid directory.");
        }
    }

    // Private constructor for CLI mode — no GUI
    private OptiZombInstaller(boolean headless) {
        super();
        // No GUI construction
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel header = new JLabel(TITLE + " — Client-Side Rendering Optimizer");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        header.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel subtitle = new JLabel("<html><center>"
            + "GPU optimizations only — safe on any vanilla server.<br>"
            + "No gameplay or simulation changes.</center></html>");
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel headerPanel = new JPanel(new BorderLayout(0, 5));
        headerPanel.add(header, BorderLayout.NORTH);
        headerPanel.add(subtitle, BorderLayout.CENTER);

        JPanel pathPanel = new JPanel(new BorderLayout(5, 0));
        pathPanel.setBorder(BorderFactory.createTitledBorder("Project Zomboid Directory"));
        pathField = new JTextField(40);
        browseButton = new JButton("Browse...");
        browseButton.addActionListener(this::onBrowse);
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(browseButton, BorderLayout.EAST);

        installButton = new JButton("Install");
        uninstallButton = new JButton("Uninstall");
        installButton.setFont(installButton.getFont().deriveFont(Font.BOLD, 14f));
        installButton.addActionListener(this::onInstall);
        uninstallButton.addActionListener(this::onUninstall);

        JButton exitButton = new JButton("Exit");
        exitButton.addActionListener(e -> System.exit(0));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        buttonPanel.add(installButton);
        buttonPanel.add(uninstallButton);
        buttonPanel.add(exitButton);

        logArea = new JTextArea(14, 55);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Log"));

        JPanel changesPanel = new JPanel(new BorderLayout());
        changesPanel.setBorder(BorderFactory.createTitledBorder("What This Changes"));
        JTextArea changesText = new JTextArea(
            "  - glGetError removal (eliminates GPU pipeline stalls)\n"
            + "  - GL state mask narrowing (fewer GPU state saves/restores)\n"
            + "  - Redundant glTexEnvi calls removed (vehicle rendering)\n"
            + "  - Blend function cleanup (fixes puddle diamond artifacts)\n"
            + "  - Zombie render cap raised (510 -> 4096)\n"
            + "  - Scene culling + distance LOD throttling\n"
            + "  - Performance diagnostics (ZombPerf logging in debug mode)"
        );
        changesText.setEditable(false);
        changesText.setBackground(root.getBackground());
        changesText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        changesPanel.add(changesText, BorderLayout.CENTER);

        JPanel topPanel = new JPanel(new BorderLayout(0, 10));
        topPanel.add(headerPanel, BorderLayout.NORTH);
        topPanel.add(pathPanel, BorderLayout.CENTER);
        topPanel.add(changesPanel, BorderLayout.SOUTH);

        root.add(topPanel, BorderLayout.NORTH);
        root.add(scrollPane, BorderLayout.CENTER);
        root.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void log(String msg) {
        if (logArea == null) {
            System.out.println(msg);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void setButtonsEnabled(boolean enabled) {
        if (installButton == null) return;
        installButton.setEnabled(enabled);
        uninstallButton.setEnabled(enabled);
        browseButton.setEnabled(enabled);
    }

    private void onBrowse(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Project Zomboid Directory");
        String current = pathField.getText().trim();
        if (!current.isEmpty()) {
            chooser.setCurrentDirectory(new File(current));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    // ---- Platform detection ----

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean isMacOS() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private static String detectPlatformName() {
        if (isWindows()) return "Windows";
        if (isMacOS()) return "macOS";
        return "Linux";
    }

    // ---- Find the PZ working directory ----

    private static boolean isPZWorkDir(Path dir) {
        boolean hasClasses = Files.isDirectory(dir.resolve("zombie"));
        boolean hasJar = Files.isRegularFile(dir.resolve(VANILLA_JAR_NAME));
        if (!hasClasses && !hasJar) return false;

        try (var stream = Files.list(dir)) {
            return stream.anyMatch(p -> {
                String name = p.getFileName().toString();
                return name.startsWith("ProjectZomboid") && name.endsWith(".json");
            });
        } catch (IOException e) {
            return false;
        }
    }

    private static Path findPZWorkDir(Path dir, int maxDepth) {
        if (!Files.isDirectory(dir)) return null;
        if (isPZWorkDir(dir)) return dir;
        try (var stream = Files.walk(dir, maxDepth)) {
            return stream
                .filter(Files::isDirectory)
                .filter(OptiZombInstaller::isPZWorkDir)
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    // ---- Vanilla class reading ----

    private enum VanillaSource { LOOSE, JAR }

    private VanillaSource detectVanillaSource(Path workDir) {
        if (Files.isDirectory(workDir.resolve("zombie"))) return VanillaSource.LOOSE;
        if (Files.isRegularFile(workDir.resolve(VANILLA_JAR_NAME))) return VanillaSource.JAR;
        return null;
    }

    private byte[] readVanillaClass(Path workDir, VanillaSource source, String classPath,
                                     JarFile vanillaJar) throws IOException {
        if (source == VanillaSource.LOOSE) {
            Path file = workDir.resolve(classPath);
            if (!Files.isRegularFile(file)) return null;
            return Files.readAllBytes(file);
        } else {
            JarEntry entry = vanillaJar.getJarEntry(classPath);
            if (entry == null) return null;
            try (InputStream is = vanillaJar.getInputStream(entry)) {
                return is.readAllBytes();
            }
        }
    }

    // ---- Launcher JSON handling ----

    private List<Path> findLauncherJsons(Path workDir) throws IOException {
        List<Path> jsons = new ArrayList<>();
        try (var stream = Files.list(workDir)) {
            stream.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith("ProjectZomboid") && name.endsWith(".json");
            }).forEach(jsons::add);
        }
        return jsons;
    }

    // ---- Install ----

    private void onInstall(ActionEvent e) {
        String pzDir = pathField.getText().trim();
        if (pzDir.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select your Project Zomboid directory.",
                "No Directory", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path workDir = findPZWorkDir(Path.of(pzDir), 3);
        if (workDir == null) {
            JOptionPane.showMessageDialog(this,
                "Project Zomboid installation not found in:\n" + pzDir
                + "\n\nLooking for a directory containing zombie/ (or projectzomboid.jar)\n"
                + "and ProjectZomboid*.json launcher configs.",
                "PZ Not Found", JOptionPane.ERROR_MESSAGE);
            return;
        }

        setButtonsEnabled(false);
        new Thread(() -> {
            try {
                doInstall(workDir);
            } catch (Exception ex) {
                log("ERROR: " + ex.getMessage());
                ex.printStackTrace();
            } finally {
                SwingUtilities.invokeLater(() -> setButtonsEnabled(true));
            }
        }).start();
    }

    private void doInstall(Path workDir) throws Exception {
        log("PZ working directory: " + workDir);

        VanillaSource source = detectVanillaSource(workDir);
        if (source == null) {
            log("ERROR: Cannot find vanilla classes (no zombie/ dir or " + VANILLA_JAR_NAME + ")");
            return;
        }
        log("Vanilla class source: " + (source == VanillaSource.LOOSE ? "loose .class files" : VANILLA_JAR_NAME));

        // Step 1: Build optizomb.jar
        log("");
        log("Building optizomb.jar from bsdiff patches...");
        byte[] jarBytes = buildOptiZombJar(workDir, source);
        if (jarBytes == null) return;
        Path jarTarget = workDir.resolve(JAR_NAME);
        Files.write(jarTarget, jarBytes);
        log("  Wrote " + JAR_NAME + " (" + jarBytes.length / 1024 + " KB)");

        // Step 2: Add to all launcher JSON classpaths
        addToClasspaths(workDir);

        // Step 3: Optimize JVM memory settings
        optimizeMemorySettings(workDir);

        // Step 4: Install shader files (with backup + manifest)
        installShaders(workDir);

        // Step 5: Install default config
        installConfig(workDir);

        // Step 6: Clean up stale artifacts from old installer
        Path oldBackup = workDir.getParent().resolve(".optizomb-backup");
        if (Files.isDirectory(oldBackup)) {
            deleteDirectoryRecursive(oldBackup);
            log("  Cleaned up old backup directory");
        }

        log("");
        log("=== Installation Complete ===");
        log("To uninstall, click the Uninstall button.");
    }

    /**
     * Build optizomb.jar by applying bsdiff patches against vanilla classes.
     */
    private byte[] buildOptiZombJar(Path workDir, VanillaSource source) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        var jarUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
        int patchCount = 0;
        int newCount = 0;
        int errorCount = 0;

        JarFile vanillaJar = null;
        if (source == VanillaSource.JAR) {
            vanillaJar = new JarFile(workDir.resolve(VANILLA_JAR_NAME).toFile());
        }

        try (JarFile self = new JarFile(new File(jarUrl.toURI()));
             JarOutputStream jos = new JarOutputStream(baos)) {

            Enumeration<JarEntry> entries = self.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!name.startsWith("patches/") || entry.isDirectory()) continue;

                if (name.endsWith(".class.patch")) {
                    String classPath = name.substring("patches/".length(),
                                                      name.length() - ".patch".length());

                    byte[] oldBytes = readVanillaClass(workDir, source, classPath, vanillaJar);
                    if (oldBytes == null) {
                        log("  WARN: vanilla class not found: " + classPath);
                        errorCount++;
                        continue;
                    }

                    byte[] patchBytes;
                    try (InputStream is = self.getInputStream(entry)) {
                        patchBytes = is.readAllBytes();
                    }

                    ByteArrayOutputStream patched = new ByteArrayOutputStream();
                    try {
                        Patch.patch(oldBytes, patchBytes, patched);
                    } catch (Exception ex) {
                        log("  WARN: patch failed for " + classPath + ": " + ex.getMessage());
                        errorCount++;
                        continue;
                    }

                    jos.putNextEntry(new JarEntry(classPath));
                    jos.write(patched.toByteArray());
                    jos.closeEntry();
                    patchCount++;

                } else if (name.endsWith(".class")) {
                    String classPath = name.substring("patches/".length());
                    jos.putNextEntry(new JarEntry(classPath));
                    try (InputStream is = self.getInputStream(entry)) {
                        is.transferTo(jos);
                    }
                    jos.closeEntry();
                    newCount++;
                }
            }
        } finally {
            if (vanillaJar != null) vanillaJar.close();
        }

        log("  Patched " + patchCount + " classes, " + newCount + " new classes");
        if (errorCount > 0) {
            log("  WARNING: " + errorCount + " classes failed to patch");
            if (patchCount == 0) {
                log("  ERROR: No classes were patched. Installation aborted.");
                return null;
            }
        }
        return baos.toByteArray();
    }

    /**
     * Add optizomb.jar as first classpath entry in ALL launcher JSON files.
     */
    private void addToClasspaths(Path workDir) throws IOException {
        List<Path> jsons = findLauncherJsons(workDir);
        if (jsons.isEmpty()) {
            log("  WARNING: No ProjectZomboid*.json files found");
            return;
        }

        for (Path jsonPath : jsons) {
            String fileName = jsonPath.getFileName().toString();
            String content = Files.readString(jsonPath);

            if (content.contains("\"" + JAR_NAME + "\"")) {
                log("  " + fileName + ": classpath already has " + JAR_NAME);
                continue;
            }

            // Back up original
            Path backup = jsonPath.resolveSibling(fileName + ".optizomb-backup");
            if (!Files.exists(backup)) {
                Files.copy(jsonPath, backup);
            }

            // Insert optizomb.jar as first classpath entry
            String patched = content.replace(
                "\"classpath\": [",
                "\"classpath\": [\n\t\t\"" + JAR_NAME + "\","
            );
            if (patched.equals(content)) {
                patched = content.replace(
                    "\"classpath\":[",
                    "\"classpath\":[\n\t\t\"" + JAR_NAME + "\","
                );
            }

            if (!patched.equals(content)) {
                Files.writeString(jsonPath, patched);
                log("  " + fileName + ": added " + JAR_NAME + " to classpath");
            } else {
                log("  WARNING: Could not patch " + fileName + " — unknown format");
            }
        }
    }

    /**
     * Detect system RAM and optimize -Xmx in all launcher JSONs.
     * Uses 50% of physical RAM, clamped to [3072, 8192] MB.
     * Only bumps up — never reduces a user's existing setting.
     */
    private void optimizeMemorySettings(Path workDir) throws IOException {
        long totalRamMB = detectSystemRamMB();
        if (totalRamMB <= 0) {
            log("  Could not detect system RAM — skipping memory optimization");
            return;
        }

        long recommendedMB = Math.max(3072, Math.min(totalRamMB / 2, 16384));
        log("");
        log("Optimizing JVM memory settings...");
        log("  System RAM: " + totalRamMB + " MB → recommended heap: " + recommendedMB + " MB");

        List<Path> jsons = findLauncherJsons(workDir);
        Pattern xmxPattern = Pattern.compile("\"-Xmx(\\d+)([mMgG])\"");

        for (Path jsonPath : jsons) {
            String content = Files.readString(jsonPath);
            Matcher m = xmxPattern.matcher(content);
            if (m.find()) {
                long currentMB = Long.parseLong(m.group(1));
                String unit = m.group(2).toLowerCase();
                if ("g".equals(unit)) currentMB *= 1024;

                if (currentMB < recommendedMB) {
                    String patched = content.replace(m.group(), "\"-Xmx" + recommendedMB + "m\"");
                    Files.writeString(jsonPath, patched);
                    log("  " + jsonPath.getFileName() + ": -Xmx " + currentMB + "m → " + recommendedMB + "m");
                } else {
                    log("  " + jsonPath.getFileName() + ": -Xmx " + currentMB + "m (already sufficient)");
                }
            }
        }
    }

    /**
     * Detect total physical RAM in MB using JMX, with /proc/meminfo fallback.
     */
    private static long detectSystemRamMB() {
        // Try JMX (works on all platforms with standard JDK)
        try {
            var os = ManagementFactory.getOperatingSystemMXBean();
            var method = os.getClass().getMethod("getTotalMemorySize");
            method.setAccessible(true);
            long bytes = (Long) method.invoke(os);
            if (bytes > 0) return bytes / (1024 * 1024);
        } catch (Exception ignored) {}

        // Fallback: getTotalPhysicalMemorySize (older JDKs)
        try {
            var os = ManagementFactory.getOperatingSystemMXBean();
            var method = os.getClass().getMethod("getTotalPhysicalMemorySize");
            method.setAccessible(true);
            long bytes = (Long) method.invoke(os);
            if (bytes > 0) return bytes / (1024 * 1024);
        } catch (Exception ignored) {}

        // Fallback: /proc/meminfo (Linux)
        try {
            Path meminfo = Path.of("/proc/meminfo");
            if (Files.exists(meminfo)) {
                for (String line : Files.readAllLines(meminfo)) {
                    if (line.startsWith("MemTotal:")) {
                        String kb = line.replaceAll("[^0-9]", "");
                        return Long.parseLong(kb) / 1024;
                    }
                }
            }
        } catch (Exception ignored) {}

        return -1;
    }

    /**
     * Remove optizomb.jar from ALL launcher JSON files.
     * Restores from backup if available, otherwise does string removal.
     */
    private void removeFromClasspaths(Path workDir) throws IOException {
        List<Path> jsons = findLauncherJsons(workDir);

        // Also check for backup files whose originals might have been removed
        try (var stream = Files.list(workDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".optizomb-backup"))
                  .forEach(backup -> {
                      String origName = backup.getFileName().toString()
                          .replace(".optizomb-backup", "");
                      Path orig = backup.resolveSibling(origName);
                      if (!jsons.contains(orig)) jsons.add(orig);
                  });
        }

        for (Path jsonPath : jsons) {
            String fileName = jsonPath.getFileName().toString();

            // Try restoring from backup first
            Path backup = jsonPath.resolveSibling(fileName + ".optizomb-backup");
            if (Files.isRegularFile(backup)) {
                Files.copy(backup, jsonPath, StandardCopyOption.REPLACE_EXISTING);
                Files.delete(backup);
                log("  " + fileName + ": restored from backup");
                continue;
            }

            if (!Files.isRegularFile(jsonPath)) continue;
            String content = Files.readString(jsonPath);
            if (!content.contains("\"" + JAR_NAME + "\"")) continue;

            // Fallback: string removal (handles \r\n and \n)
            StringBuilder sb = new StringBuilder();
            for (String line : content.split("\\r?\\n")) {
                if (!line.contains("\"" + JAR_NAME + "\"")) {
                    sb.append(line).append(System.lineSeparator());
                }
            }
            String cleaned = sb.toString().replaceAll(",\\s*" + System.lineSeparator() + "(\\s*])",
                System.lineSeparator() + "$1");
            Files.writeString(jsonPath, cleaned);
            log("  " + fileName + ": removed " + JAR_NAME + " from classpath");
        }
    }

    /**
     * Install shaders with backup of any vanilla originals + write manifest.
     */
    private void installShaders(Path workDir) throws Exception {
        Map<String, byte[]> shaders = new LinkedHashMap<>();
        var jarUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
        try (JarFile self = new JarFile(new File(jarUrl.toURI()))) {
            Enumeration<JarEntry> entries = self.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("shaders/") && !entry.isDirectory()) {
                    try (InputStream is = self.getInputStream(entry)) {
                        shaders.put(name.substring("shaders/".length()), is.readAllBytes());
                    }
                }
            }
        }

        if (shaders.isEmpty()) return;

        Path shaderDir = workDir.resolve("media").resolve("shaders");
        Files.createDirectories(shaderDir);
        List<String> installedNames = new ArrayList<>();

        for (var entry : shaders.entrySet()) {
            Path target = shaderDir.resolve(entry.getKey());

            // Back up existing vanilla shader if it exists and hasn't been backed up yet
            Path backup = shaderDir.resolve(entry.getKey() + ".optizomb-backup");
            if (Files.isRegularFile(target) && !Files.exists(backup)) {
                Files.copy(target, backup);
            }

            Files.write(target, entry.getValue());
            installedNames.add(entry.getKey());
        }

        // Write manifest of installed shaders for clean uninstall
        Path manifest = workDir.resolve(SHADER_MANIFEST);
        Files.write(manifest, installedNames);

        log("  Installed " + shaders.size() + " shader files");
    }

    private void installConfig(Path workDir) throws Exception {
        var jarUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
        try (JarFile self = new JarFile(new File(jarUrl.toURI()))) {
            JarEntry configEntry = self.getJarEntry("optizomb.properties.default");
            if (configEntry != null) {
                Path configFile = workDir.resolve("optizomb.properties");
                if (!Files.exists(configFile)) {
                    try (InputStream is = self.getInputStream(configEntry)) {
                        Files.write(configFile, is.readAllBytes());
                    }
                    log("  Installed default optizomb.properties");
                } else {
                    log("  optizomb.properties already exists (kept)");
                }
            }
        }
    }

    // ---- Uninstall ----

    private void onUninstall(ActionEvent e) {
        String pzDir = pathField.getText().trim();
        if (pzDir.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select your Project Zomboid directory.",
                "No Directory", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path workDir = findPZWorkDir(Path.of(pzDir), 3);
        if (workDir == null) {
            JOptionPane.showMessageDialog(this,
                "Project Zomboid installation not found in:\n" + pzDir,
                "Not Found", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path jarFile = workDir.resolve(JAR_NAME);
        if (!Files.exists(jarFile)) {
            JOptionPane.showMessageDialog(this,
                "OptiZomb does not appear to be installed.\n" + JAR_NAME + " not found in:\n" + workDir,
                "Not Installed", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setButtonsEnabled(false);
        new Thread(() -> {
            try {
                doUninstall(workDir);
            } catch (Exception ex) {
                log("ERROR: " + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> setButtonsEnabled(true));
            }
        }).start();
    }

    private void doUninstall(Path workDir) throws Exception {
        log("Uninstalling from: " + workDir);

        // Remove optizomb.jar
        Path jarFile = workDir.resolve(JAR_NAME);
        if (Files.exists(jarFile)) {
            Files.delete(jarFile);
            log("  Removed " + JAR_NAME);
        }

        // Restore all launcher JSONs
        removeFromClasspaths(workDir);

        // Remove shaders using manifest (dynamic, not hardcoded)
        uninstallShaders(workDir);

        // Remove config
        Path configFile = workDir.resolve("optizomb.properties");
        if (Files.exists(configFile)) {
            Files.delete(configFile);
            log("  Removed optizomb.properties");
        }

        // Remove shader manifest
        Path manifest = workDir.resolve(SHADER_MANIFEST);
        if (Files.exists(manifest)) Files.delete(manifest);

        // Clean up old backup directory
        Path oldBackup = workDir.getParent().resolve(".optizomb-backup");
        if (Files.isDirectory(oldBackup)) {
            deleteDirectoryRecursive(oldBackup);
            log("  Cleaned up old backup directory");
        }

        log("");
        log("=== Uninstall Complete ===");
        log("Game restored to vanilla state.");
    }

    /**
     * Remove installed shaders using manifest. Restore vanilla backups.
     */
    private void uninstallShaders(Path workDir) throws IOException {
        Path shaderDir = workDir.resolve("media").resolve("shaders");
        Path manifest = workDir.resolve(SHADER_MANIFEST);

        List<String> shaderNames;
        if (Files.isRegularFile(manifest)) {
            shaderNames = Files.readAllLines(manifest);
        } else {
            // Fallback: hardcoded list for installations done before manifest was added
            shaderNames = List.of(
                "spriteInstanced.vert", "spriteInstanced.frag",
                "floorTileInstanced.vert", "floorTileInstanced.frag",
                "basicEffect_tbo.vert", "basicEffect_tbo.frag",
                "basicEffect_static.vert", "basicEffect_tbo_static.vert",
                "basicEffect.vert", "basicEffect.frag",
                "floorTile.vert", "floorTile.frag"
            );
        }

        int removed = 0;
        int restored = 0;
        for (String name : shaderNames) {
            Path sf = shaderDir.resolve(name);
            Path backup = shaderDir.resolve(name + ".optizomb-backup");

            if (Files.isRegularFile(backup)) {
                // Restore vanilla original
                Files.copy(backup, sf, StandardCopyOption.REPLACE_EXISTING);
                Files.delete(backup);
                restored++;
            } else if (Files.exists(sf)) {
                Files.delete(sf);
                removed++;
            }
        }

        if (removed > 0) log("  Removed " + removed + " shader files");
        if (restored > 0) log("  Restored " + restored + " vanilla shader files");
    }

    // ---- Auto-detect ----

    private static String detectPZDirectory() {
        String home = System.getProperty("user.home");
        List<String> candidates = new ArrayList<>();

        if (isWindows()) {
            candidates.add("C:\\Program Files (x86)\\Steam\\steamapps\\common\\ProjectZomboid");
            candidates.add("C:\\Program Files\\Steam\\steamapps\\common\\ProjectZomboid");
            candidates.add("D:\\SteamLibrary\\steamapps\\common\\ProjectZomboid");
            candidates.add("E:\\SteamLibrary\\steamapps\\common\\ProjectZomboid");
        } else if (isMacOS()) {
            candidates.add(home + "/Library/Application Support/Steam/steamapps/common/ProjectZomboid");
        } else {
            // Linux (native, Flatpak, Snap)
            candidates.add(home + "/.steam/steam/steamapps/common/ProjectZomboid");
            candidates.add(home + "/.local/share/Steam/steamapps/common/ProjectZomboid");
            candidates.add(home + "/.var/app/com.valvesoftware.Steam/.steam/steam/steamapps/common/ProjectZomboid");
            candidates.add(home + "/.var/app/com.valvesoftware.Steam/.local/share/Steam/steamapps/common/ProjectZomboid");
            candidates.add(home + "/snap/steam/common/.steam/steam/steamapps/common/ProjectZomboid");
        }

        for (String path : candidates) {
            Path workDir = findPZWorkDir(Path.of(path), 3);
            if (workDir != null) return workDir.toString();
        }

        // Parse libraryfolders.vdf for additional Steam library locations
        List<String> vdfPaths = new ArrayList<>();
        if (isWindows()) {
            vdfPaths.add("C:\\Program Files (x86)\\Steam\\steamapps\\libraryfolders.vdf");
            vdfPaths.add("C:\\Program Files\\Steam\\steamapps\\libraryfolders.vdf");
        } else if (isMacOS()) {
            vdfPaths.add(home + "/Library/Application Support/Steam/steamapps/libraryfolders.vdf");
        } else {
            vdfPaths.add(home + "/.steam/steam/steamapps/libraryfolders.vdf");
            vdfPaths.add(home + "/.local/share/Steam/steamapps/libraryfolders.vdf");
            vdfPaths.add(home + "/.var/app/com.valvesoftware.Steam/.steam/steam/steamapps/libraryfolders.vdf");
            vdfPaths.add(home + "/snap/steam/common/.steam/steam/steamapps/libraryfolders.vdf");
        }

        for (String vdfPath : vdfPaths) {
            Path vdf = Path.of(vdfPath);
            if (!Files.exists(vdf)) continue;
            try {
                for (String line : Files.readAllLines(vdf)) {
                    line = line.trim();
                    if (line.startsWith("\"path\"")) {
                        String libPath = line.replaceAll(".*\"path\"\\s+\"(.+)\".*", "$1");
                        libPath = libPath.replace("\\\\", "\\");
                        Path pzDir = Path.of(libPath, "steamapps", "common", "ProjectZomboid");
                        Path workDir = findPZWorkDir(pzDir, 3);
                        if (workDir != null) return workDir.toString();
                    }
                }
            } catch (IOException ignored) {}
        }

        return null;
    }

    // ---- Utilities ----

    private static void deleteDirectoryRecursive(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        }
    }

    // ---- Entry point ----

    public static void main(String[] args) {
        if (args.length > 0 && "--help".equals(args[0])) {
            System.out.println("OptiZomb Lite Installer " + VERSION);
            System.out.println("Usage: java -jar OptiZomb-Lite-Installer.jar");
            System.out.println("       java -jar OptiZomb-Lite-Installer.jar --install <path>");
            System.out.println("       java -jar OptiZomb-Lite-Installer.jar --uninstall <path>");
            return;
        }

        // CLI mode — no GUI, works headless
        if (args.length >= 2 && ("--install".equals(args[0]) || "--uninstall".equals(args[0]))) {
            runCLI(args[0], args[1]);
            return;
        }

        // GUI mode
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            new OptiZombInstaller().setVisible(true);
        });
    }

    /**
     * Headless CLI mode — no Swing, no display required.
     */
    private static void runCLI(String command, String path) {
        Path dir = Path.of(path);
        Path workDir = findPZWorkDir(dir, 3);
        if (workDir == null) {
            System.err.println("ERROR: PZ installation not found at: " + path);
            System.err.println("Looking for zombie/ (or projectzomboid.jar) + ProjectZomboid*.json");
            System.exit(1);
        }

        // Create headless installer (no JFrame, no GUI)
        OptiZombInstaller installer = new OptiZombInstaller(true);

        try {
            if ("--install".equals(command)) {
                installer.doInstall(workDir);
            } else {
                installer.doUninstall(workDir);
            }
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
}
