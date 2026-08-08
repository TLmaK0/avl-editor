/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.avl.connectivity;

import com.abajar.avleditor.UnitConversor;
import com.abajar.avleditor.avl.AVL;
import com.abajar.avleditor.avl.AVLS;
import com.abajar.avleditor.avl.runcase.Configuration;
import com.abajar.avleditor.avl.runcase.AlphaSweepPoint;
import com.abajar.avleditor.avl.runcase.AvlCalculation;
import com.abajar.avleditor.avl.runcase.AvlEigenvalue;
import com.abajar.avleditor.avl.runcase.StabilityDerivatives;
import com.abajar.avleditor.avl.runcase.StripForce;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author hfreire
 */
public class AvlRunner {
    OutputStream stdin;
    InputStream stderr;
    InputStream stdout;
    Process process;
    final String avlPath;
    final Path avlFileName;
    final Path executionPath;
    final AVL avl;
    private AvlCalculation result;
    private Path geometryPlotPath;
    private Path trefftzPlotPath;
    private float viewAzimuth = 45.0f;   // Default view angle
    private float viewElevation = 20.0f; // Default view angle
    private volatile boolean trimConvergenceFailed;
    private volatile boolean noFlowSolution;
    private volatile boolean stabilityCommandRejected;

    final static Logger logger = Logger.getLogger(AvlRunner.class.getName());
    private final String avlFileBase;
    private static final String NUMBER_PATTERN = "[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[Ee][+-]?\\d+)?";
    private static final Pattern MODE_HEADER_PATTERN = Pattern.compile("^\\s*mode\\s+(\\d+)\\s*:\\s*(" + NUMBER_PATTERN + ")\\s+(" + NUMBER_PATTERN + ").*$");
    private static final Pattern MODE_STATE_PATTERN = Pattern.compile("([A-Za-z']+)\\s*:\\s*(" + NUMBER_PATTERN + ")\\s+(" + NUMBER_PATTERN + ")");
    private static final Pattern TRIM_CONTROL_VALUE_PATTERN = Pattern.compile("^\\s*([A-Za-z0-9_\\-\\.]+)\\s*=\\s*(" + NUMBER_PATTERN + ")\\s*$");

    private static class ModeStateAmplitudes {
        private float u = Float.NaN;
        private float v = Float.NaN;
        private float w = Float.NaN;
        private float p = Float.NaN;
        private float q = Float.NaN;
        private float r = Float.NaN;
        private float the = Float.NaN;
        private float phi = Float.NaN;
        private float psi = Float.NaN;

        void setAmplitude(String state, float amplitude) {
            if ("u".equals(state)) u = amplitude;
            else if ("v".equals(state)) v = amplitude;
            else if ("w".equals(state)) w = amplitude;
            else if ("p".equals(state)) p = amplitude;
            else if ("q".equals(state)) q = amplitude;
            else if ("r".equals(state)) r = amplitude;
            else if ("the".equals(state)) the = amplitude;
            else if ("phi".equals(state)) phi = amplitude;
            else if ("psi".equals(state)) psi = amplitude;
        }

        void applyTo(AvlEigenvalue eigenvalue) {
            if (isFinite(u)) eigenvalue.setModeStateAmplitude("u", u);
            if (isFinite(v)) eigenvalue.setModeStateAmplitude("v", v);
            if (isFinite(w)) eigenvalue.setModeStateAmplitude("w", w);
            if (isFinite(p)) eigenvalue.setModeStateAmplitude("p", p);
            if (isFinite(q)) eigenvalue.setModeStateAmplitude("q", q);
            if (isFinite(r)) eigenvalue.setModeStateAmplitude("r", r);
            if (isFinite(the)) eigenvalue.setModeStateAmplitude("the", the);
            if (isFinite(phi)) eigenvalue.setModeStateAmplitude("phi", phi);
            if (isFinite(psi)) eigenvalue.setModeStateAmplitude("psi", psi);
        }

        private boolean isFinite(float value) {
            return !Float.isNaN(value) && !Float.isInfinite(value);
        }
    }

    public AvlRunner(String avlPath, AVL avl, Path originPath) throws IOException, InterruptedException, Exception {
        this(avlPath, avl, originPath, 45.0f, 20.0f);
    }

    public AvlRunner(String avlPath, AVL avl, Path originPath, float azimuth, float elevation) throws IOException, InterruptedException, Exception{
        this.viewAzimuth = azimuth;
        this.viewElevation = elevation;
        this.avl = avl;
        this.avlPath = avlPath;
        this.executionPath = Files.createTempDirectory("chrrcsim_");
        this.avlFileBase = this.executionPath.toString() + "/crrcsim_tmp";
        this.avlFileName = Paths.get(this.avlFileBase + ".avl");

        logger.log(Level.INFO, "Writing AVL file to: " + this.avlFileName);
        AVLS.avlToFile(avl, avlFileName, originPath);

        logger.log(Level.INFO, "Starting AVL process: " + avlPath);
        ProcessBuilder pb = new ProcessBuilder(avlPath, this.avlFileName.toString());
        pb.directory(executionPath.toFile().getAbsoluteFile());

        pb.redirectErrorStream(true);

        process = pb.start();
        stdin = process.getOutputStream ();
        stdout = process.getInputStream ();

        // Start a thread to capture AVL output in real-time
        Thread outputReader = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.log(Level.INFO, "[AVL] " + line);
                    registerMainPassStatus(line);
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error reading AVL output: " + e.getMessage());
            }
        });
        outputReader.setDaemon(true);
        outputReader.start();

        this.run(avl.getElevatorPosition(), avl.getRudderPosition(), avl.getAileronPosition());

        stdin.close();
        //this.removeDirectory(this.executionPath);
    }

    private void removeDirectory(Path directory) throws IOException{
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
	   @Override
	   public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
		   Files.delete(file);
		   return FileVisitResult.CONTINUE;
	   }

	   @Override
	   public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
		   Files.delete(dir);
		   return FileVisitResult.CONTINUE;
	   }

        });
    }

    /**
     * The lift coefficient every pass trims at, taken from {@link AVL#analysisLiftCoefficient()} — the one
     * derivation there is, so the stability run, the eigenvalue pass and the plots cannot end up describing
     * different aircraft.
     *
     * It throws rather than substituting anything. An operating point that cannot be derived means the
     * weight, the speed, the air density or the reference area is missing, which the requirements refuse
     * before a run is started; reaching here without one is a bug, and a silent 0 is what this whole change
     * exists to remove.
     */
    private float analysisLiftCoefficient() {
        Float cl = avl.analysisLiftCoefficient();
        if (cl == null) {
            throw new IllegalStateException(
                "The AVL operating point cannot be derived: it needs the aircraft's weight, its speed, the "
                + "air density and the reference area. Speed " + avl.analysisVelocityMetresPerSecond()
                + " m/s, density "
                + avl.getAirDensity() + " kg/m3, weight " + avl.getAnalysisWeightKg() + " kg, Sref "
                + (avl.getGeometry() == null ? 0f : avl.getGeometry().getSref()) + " m2.");
        }
        return cl;
    }

    private void run(int elevatorPosition, int rudderPosition, int aileronPosition) throws IOException, InterruptedException, Exception{
        String resultFile = this.avlFileName.toString().replace(".avl", ".st");
        String eigenFile = this.avlFileName.toString().replace(".avl", ".eig");
        trimConvergenceFailed = false;
        noFlowSolution = false;
        stabilityCommandRejected = false;

        sendCommand("oper\n");
        //sendCommand("g\n\n");

        //setting pitch moment 0
        if (elevatorPosition != -1) sendCommand("d" + (elevatorPosition + 1) + " pm 0\n");
        
        //setting velocity
        sendCommand("c1\n");
        sendCommand("v\n");

        // In m/s, which is what AVL's run case is stated in whatever Lunit the mass file declares.
        sendCommand(avl.analysisVelocityMetresPerSecond() + "\n\n");        //setting velocity
        sendCommand("a c " + analysisLiftCoefficient() + "\n");
        //execute run case
        sendCommand("x\n");

        sendCommand("st\n");
        sendCommand(resultFile + "\n");
        sendCommand("c1\n\n");
        sendCommand("\nq\n");
        stdin.flush();

        // Wait for AVL process to finish
        boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            logger.log(Level.WARNING, "AVL process timed out after 30 seconds, destroying...");
            process.destroyForcibly();
            throw new Exception("AVL process timed out");
        }

        logger.log(Level.INFO, "AVL process finished with exit code: " + process.exitValue());

        File stabilityFile = new File(resultFile);
        if (!stabilityFile.exists()) {
            throw new IOException(buildMissingStabilityMessage(resultFile));
        }

        // Run a second AVL pass for modal eigenvalues to avoid interfering with .st generation.
        List<ModeStateAmplitudes> modeStates = runEigenvalueAnalysis(eigenFile, elevatorPosition);

        InputStream fis = new FileInputStream(stabilityFile);
        Scanner scanner = new Scanner(fis);

        AvlCalculation runCase = new AvlCalculation(elevatorPosition, rudderPosition, aileronPosition);

        // Extract unique control names from geometry
        java.util.Set<String> uniqueNames = new java.util.LinkedHashSet<>();
        if (avl.getGeometry() != null) {
            for (com.abajar.avleditor.avl.geometry.Surface surface : avl.getGeometry().getSurfaces()) {
                for (com.abajar.avleditor.avl.geometry.Section section : surface.getSections()) {
                    for (com.abajar.avleditor.avl.geometry.Control control : section.getControls()) {
                        uniqueNames.add(control.getName());
                    }
                }
            }
        }
        String[] controlNames = uniqueNames.toArray(new String[0]);
        runCase.setControlNames(controlNames);
        float[] controlGains = extractControlGains(controlNames);
        runCase.setControlGains(controlGains);
        runCase.setControlMaxDeflections(extractControlMaxDeflections(controlNames));
        runCase.setFlightPhase(avl.getFlightPhase());
        float[] trimControlValues = readTrimControlValues(stabilityFile, controlNames);
        runCase.setTrimControlValues(trimControlValues);
        runCase.setTrimControlDeflections(calculateTrimControlDeflections(trimControlValues, controlGains));

        Configuration config = runCase.getConfiguration();

        config.setVelocity(avl.getVelocity());

        config.setSref(readFloat("Sref =", scanner));
        config.setCref(readFloat("Cref =", scanner));
        config.setBref(readFloat("Bref =", scanner));
        // Written beside Bref, from the same run: the flying-qualities criteria scale with how big the
        // aircraft physically is, and Bref alone does not say whether it is 1.5 of something or 60.
        config.setMetresPerLengthUnit(avl.units().metresPerLengthUnit());
        config.setSecondsPerTimeUnit(avl.units().secondsPerTimeUnit());
        config.setAnalysisInertias(avl.getAnalysisWeightKg(), avl.getAnalysisIxx(), avl.getAnalysisIzz(),
                avl.getAnalysisIxz(), avl.getAirDensity());
        // What AVL says the air adds, read from its own modal pass above.
        if (this.apparentInertias != null) {
            config.setApparentInertias(this.apparentInertias[0], this.apparentInertias[1],
                    this.apparentInertias[2], this.apparentInertias[3]);
        }

        config.setAlpha(readFloat("Alpha =", scanner));

        config.setCmtot(readFloat("Cmtot =", scanner));
        config.setCLtot(readFloat("CLtot =", scanner));
        config.setCDvis(readFloat("CDvis =", scanner));
        config.setE(readFloat("e =", scanner));

        StabilityDerivatives std = runCase.getStabilityDerivatives();
        int numControls = controlNames.length;
        std.initControls(numControls);

        std.setCLa(readFloat("CLa = ", scanner));
        std.setCYb(readFloat("CYb = ", scanner));
        std.setClb(readFloat("Clb = ", scanner));
        std.setCma(readFloat("Cma = ", scanner));
        std.setCnb(readFloat("Cnb = ", scanner));
        std.setCLq(readFloat("CLq = ", scanner));
        std.setCYp(readFloat("CYp = ", scanner));
        std.setCYr(readFloat("CYr = ", scanner));
        std.setClp(readFloat("Clp = ", scanner));
        std.setClr(readFloat("Clr = ", scanner));
        std.setCmq(readFloat("Cmq = ", scanner));
        std.setCnp(readFloat("Cnp = ", scanner));
        std.setCnr(readFloat("Cnr = ", scanner));

        // Read control derivatives for all controls
        for (int i = 0; i < numControls; i++) {
            String suffix = String.format("%02d", i + 1);
            std.getCLd()[i] = readFloat("CLd" + suffix + " =", scanner);
        }
        for (int i = 0; i < numControls; i++) {
            String suffix = String.format("%02d", i + 1);
            std.getCYd()[i] = readFloat("CYd" + suffix + " =", scanner);
        }
        for (int i = 0; i < numControls; i++) {
            String suffix = String.format("%02d", i + 1);
            std.getCld()[i] = readFloat("Cld" + suffix + " =", scanner);
        }
        for (int i = 0; i < numControls; i++) {
            String suffix = String.format("%02d", i + 1);
            std.getCmd()[i] = readFloat("Cmd" + suffix + " =", scanner);
        }
        for (int i = 0; i < numControls; i++) {
            String suffix = String.format("%02d", i + 1);
            std.getCnd()[i] = readFloat("Cnd" + suffix + " =", scanner);
        }

        scanner.close();

        // The curve, measured after the single point above: a separate pass, so a failure in it cannot
        // spoil the stability file the whole analysis rests on.
        try {
            runCase.setAlphaSweep(runAlphaSweep());
        } catch (Exception ex) {
            logger.log(Level.WARNING, "The alpha sweep did not complete: " + ex.getMessage(), ex);
        }

        List<AvlEigenvalue> eigenvalues = readEigenvalues(eigenFile);
        applyModeStates(eigenvalues, modeStates);
        runCase.setEigenvalues(eigenvalues);
        this.result = runCase;

        try {
            runPlotGeneration(elevatorPosition);
            convertPlotsToImages();
        } catch (Exception ex) {
            geometryPlotPath = null;
            trefftzPlotPath = null;
            logger.log(Level.WARNING, "Unable to generate AVL plots in dedicated pass", ex);
        }
    }

    /**
     * The attitudes the sweep measures, in degrees. From well below level flight — a model flying faster
     * than it needs sits nose-down, the eurofighter at -4.2 deg — to beyond where a real wing would have
     * stopped lifting, so the curve covers the range JSBSim will actually ask about.
     *
     * Thirteen points is not a cost: opening AVL is the slow part and solving is milliseconds, so the sweep
     * costs about what the single measurement it replaces cost.
     */
    static final float[] SWEEP_ANGLES_DEG = {
        -10f, -7.5f, -5f, -2.5f, 0f, 2.5f, 5f, 7.5f, 10f, 12.5f, 15f, 17.5f, 20f
    };

    /**
     * What is typed at AVL to measure the aircraft across attitudes, as a list so it can be read and checked
     * without running AVL.
     *
     * Two things here are the point of the whole pass. The attitude is <b>imposed</b> ({@code a a}) instead
     * of asking for a lift coefficient and letting AVL find the attitude, because the curve's independent
     * variable has to be the one we chose. And the controls are left at <b>neutral</b> — no {@code d1 pm 0}
     * — because JSBSim adds the elevator's effect itself through Cmde x elevator, so measuring with the
     * elevator trimmed would carry that trim inside Cm and count the elevator twice.
     *
     * Each attitude also writes the <b>spanwise loading</b> ({@code fs}), because the same solve that
     * produces the totals already contains it and a second AVL session to fetch it would cost far more
     * than the file does. It is what the stall is found from: AVL is inviscid and has no opinion about
     * where a wing stops lifting, but it does say which strip is working hardest, and XFOIL says how hard
     * that strip's aerofoil can work. See {@code WingMaximumLift}.
     *
     * It ends the way the plot pass ends, and for the same reason: a blank line out of OPER and {@code quit},
     * never {@code q}, which OPER does not know.
     */
    static List<String> sweepCommands(float velocity, float[] anglesDeg, String stemPath) {
        List<String> commands = new ArrayList<String>();
        commands.add("oper");
        commands.add("c1");
        commands.add("v");
        commands.add(velocity + "\n");
        for (int i = 0; i < anglesDeg.length; i++) {
            commands.add("a a " + anglesDeg[i]);
            commands.add("x");
            commands.add("st");
            commands.add(sweepFileName(stemPath, i));
            commands.add("fs");
            commands.add(stripFileName(stemPath, i));
        }
        commands.add("");
        commands.add("quit");
        return commands;
    }

    static String sweepFileName(String stemPath, int index) {
        return stemPath + "_a" + index + ".st";
    }

    static String stripFileName(String stemPath, int index) {
        return stemPath + "_a" + index + ".fs";
    }

    /**
     * Measures the aircraft at every attitude in {@link #SWEEP_ANGLES_DEG}, in one AVL session.
     *
     * An attitude AVL does not answer is dropped and said so, rather than filled in: the curve is then built
     * from the points that exist, and the export refuses if too few came back to be a curve at all. A run of
     * its own, like the modal and plot passes, so a failure here cannot spoil the stability file.
     */
    private List<AlphaSweepPoint> runAlphaSweep() throws IOException, InterruptedException {
        logger.log(Level.INFO, "Starting alpha sweep over " + SWEEP_ANGLES_DEG.length + " attitudes...");
        ProcessBuilder pb = new ProcessBuilder(avlPath, this.avlFileName.toString());
        pb.directory(executionPath.toFile().getAbsoluteFile());
        pb.redirectErrorStream(true);

        Process sweepProcess = pb.start();
        try (OutputStream sweepIn = sweepProcess.getOutputStream();
             BufferedReader sweepOut = new BufferedReader(new InputStreamReader(sweepProcess.getInputStream()))) {

            for (String command : sweepCommands(avl.analysisVelocityMetresPerSecond(), SWEEP_ANGLES_DEG,
                    avlFileBase)) {
                writeModeCommand(sweepIn, command + "\n");
            }
            sweepIn.flush();
            sweepIn.close();

            String line;
            while ((line = sweepOut.readLine()) != null) {
                logger.log(Level.FINE, "[AVL-SWEEP] " + line);
            }
        }

        if (!sweepProcess.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
            sweepProcess.destroyForcibly();
            throw new IOException("The AVL alpha sweep timed out");
        }

        List<AlphaSweepPoint> points = new ArrayList<AlphaSweepPoint>();
        for (int i = 0; i < SWEEP_ANGLES_DEG.length; i++) {
            File file = new File(sweepFileName(avlFileBase, i));
            AlphaSweepPoint point = file.exists() ? parseSweepPoint(file, SWEEP_ANGLES_DEG[i]) : null;
            if (point == null) {
                logger.log(Level.WARNING, String.format(java.util.Locale.ENGLISH,
                    "AVL did not answer at %.1f deg of attitude; that point is left out of the curve.",
                    SWEEP_ANGLES_DEG[i]));
            } else {
                File stripFile = new File(stripFileName(avlFileBase, i));
                if (stripFile.exists()) point.setStrips(parseStripForces(stripFile));
                points.add(point);
            }
        }
        int withStrips = 0;
        for (AlphaSweepPoint point : points) if (!point.getStrips().isEmpty()) withStrips++;
        logger.log(Level.INFO, "Spanwise loading: " + withStrips + " of " + points.size()
            + " attitudes carry strip forces"
            + (points.isEmpty() || points.get(0).getStrips().isEmpty()
               ? "" : ", " + points.get(0).getStrips().size() + " strips each"));
        logger.log(Level.INFO, "Alpha sweep: " + points.size() + " of " + SWEEP_ANGLES_DEG.length
            + " attitudes measured");
        for (AlphaSweepPoint point : points) logger.log(Level.INFO, "  " + point);
        // Why the rest of the derivatives are exported as one number each, measured rather than assumed.
        for (String line : AlphaSweepPoint.constantsReport(points)) logger.log(Level.INFO, line);
        return points;
    }

    /**
     * One attitude out of a stability file, by label.
     *
     * By label and <b>case-sensitively</b>, not by position: the file states {@code CLa} (lift with
     * attitude) and {@code Cla} (roll with attitude) on different lines, distinguished by nothing but the
     * capital, so a case-blind match reads the wrong number. Returns null if any of them is absent, which is
     * how an attitude AVL failed at ends up dropped instead of half-read.
     */
    private AlphaSweepPoint parseSweepPoint(File file, float angleDeg) throws IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
        }
        String content = text.toString();

        Float cl = labelled(content, "CLtot");
        Float cd = labelled(content, "CDtot");
        Float cm = labelled(content, "Cmtot");
        Float alpha = labelled(content, "Alpha");
        Float cla = labelled(content, "CLa");
        Float cma = labelled(content, "Cma");
        Float cnb = labelled(content, "Cnb");
        Float clb = labelled(content, "Clb");
        if (cl == null || cd == null || cm == null || alpha == null) return null;

        // AVL's own alpha, not the one we asked for: if the two disagree the file is not the case we set.
        if (Math.abs(alpha - angleDeg) > 0.05f) {
            logger.log(Level.WARNING, String.format(java.util.Locale.ENGLISH,
                "Stability file for %.1f deg reports Alpha = %.3f; ignoring it.", angleDeg, alpha));
            return null;
        }
        return new AlphaSweepPoint(angleDeg, cl, cd, cm,
            cla == null ? Float.NaN : cla, cma == null ? Float.NaN : cma,
            cnb == null ? Float.NaN : cnb, clb == null ? Float.NaN : clb);
    }

    /** {@code Surface # 3     Fin (YDUP)} — the number, then the name, then AVL's mirror marker. */
    private static final Pattern STRIP_SURFACE_PATTERN =
        Pattern.compile("^\\s*Surface\\s*#\\s*\\d+\\s+(.*?)\\s*$");

    /** The header line above the strip table, which is what says the numbers that follow are strips. */
    private static final String STRIP_TABLE_HEADER = "j";

    /**
     * The spanwise loading out of one {@code fs} file.
     *
     * The file is a sequence of surface blocks, each ending in a table of one row per strip:
     *
     * <pre>
     *   Surface # 1     Wing
     *   ...
     *  Strip Forces referred to Strip Area, Chord
     *     j   Xle   Yle   Zle   Chord   Area   c_cl   ai   cl_norm   cl   cd   cdv   cm_c/4   cm_LE   C.P.x/c
     *     1  0.0000 0.0008 0.0000 0.1998 0.0006 0.1587 0.1099 0.8024 0.8004 -0.0166 0.0000 -0.0908 -0.2894 0.363
     * </pre>
     *
     * Read by <b>column position within the table</b> rather than by label, because the rows carry no
     * labels at all — but the table is entered only through its header line, so a stray row of numbers
     * elsewhere in the file cannot be mistaken for a strip. A mirrored surface arrives as its own block
     * named {@code ... (YDUP)}, and both halves are kept: they are separate strips of the real aircraft,
     * and the analysis that uses them asks which single strip is closest to stalling.
     */
    private List<StripForce> parseStripForces(File file) throws IOException {
        List<StripForce> strips = new ArrayList<StripForce>();
        String surfaceName = null;
        boolean mirrored = false;
        boolean inTable = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                Matcher surface = STRIP_SURFACE_PATTERN.matcher(line);
                if (surface.matches()) {
                    String name = surface.group(1).trim();
                    mirrored = name.endsWith("(YDUP)");
                    if (mirrored) name = name.substring(0, name.length() - "(YDUP)".length()).trim();
                    surfaceName = name;
                    inTable = false;
                    continue;
                }
                if (trimmed.startsWith(STRIP_TABLE_HEADER) && trimmed.contains("Yle") && trimmed.contains("Chord")) {
                    inTable = true;
                    continue;
                }
                if (!inTable) continue;

                String[] columns = trimmed.split("\\s+");
                // j Xle Yle Zle Chord Area c_cl ai cl_norm cl ... — ten columns are needed to reach cl.
                if (columns.length < 10) { inTable = false; continue; }
                try {
                    strips.add(new StripForce(
                        surfaceName == null ? "" : surfaceName, mirrored,
                        Integer.parseInt(columns[0]),
                        Float.parseFloat(columns[2]),   // Yle
                        Float.parseFloat(columns[4]),   // Chord
                        Float.parseFloat(columns[5]),   // Area
                        Float.parseFloat(columns[9]))); // cl, referred to the strip's own area and chord
                } catch (NumberFormatException ex) {
                    inTable = false;
                }
            }
        }
        return strips;
    }

    private static Float labelled(String content, String label) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(label) + "\\s*=\\s*(" + NUMBER_PATTERN + ")")
            .matcher(content);
        return matcher.find() ? Float.valueOf(matcher.group(1)) : null;
    }

    private void registerMainPassStatus(String line) {
        if (line == null) return;
        String normalized = line.toLowerCase();
        if (normalized.contains("trim convergence failed")) {
            trimConvergenceFailed = true;
        }
        if (normalized.contains("no flow solution")) {
            noFlowSolution = true;
        }
        if (normalized.contains("st   command not recognized") || normalized.contains("st command not recognized")) {
            stabilityCommandRejected = true;
        }
    }

    private String buildMissingStabilityMessage(String resultFile) {
        if (trimConvergenceFailed) {
            return String.format(
                "AVL trim convergence failed at V=%.3f m/s and CL=%.6f; no stability file generated: %s",
                avl.analysisVelocityMetresPerSecond(), analysisLiftCoefficient(), resultFile
            );
        }
        if (noFlowSolution) {
            return "AVL reported no flow solution; no stability file generated: " + resultFile;
        }
        if (stabilityCommandRejected) {
            return "AVL rejected ST command during run; no stability file generated: " + resultFile;
        }
        return "AVL did not generate stability file: " + resultFile;
    }

    private float[] extractControlGains(String[] controlNames) {
        float[] gains = new float[controlNames.length];
        Arrays.fill(gains, 1f);
        if (controlNames.length == 0 || avl.getGeometry() == null) {
            return gains;
        }

        Map<String, Float> gainByName = new HashMap<String, Float>();
        for (com.abajar.avleditor.avl.geometry.Surface surface : avl.getGeometry().getSurfaces()) {
            for (com.abajar.avleditor.avl.geometry.Section section : surface.getSections()) {
                for (com.abajar.avleditor.avl.geometry.Control control : section.getControls()) {
                    String name = control.getName();
                    if (!gainByName.containsKey(name)) {
                        gainByName.put(name, control.getGain());
                    }
                }
            }
        }

        for (int i = 0; i < controlNames.length; i++) {
            String name = controlNames[i];
            if (gainByName.containsKey(name)) {
                gains[i] = gainByName.get(name);
            }
        }
        return gains;
    }

    /**
     * How far each control can actually move, in degrees.
     *
     * Roll performance (MIL-F-8785C 3.3.4, TABLE IXa) is how long the aircraft takes to bank with the stick
     * hard over, so it needs the deflection the surface stops at — a derivative alone cannot say how much
     * roll is available, only how much per degree.
     */
    private float[] extractControlMaxDeflections(String[] controlNames) {
        float[] deflections = new float[controlNames.length];
        Arrays.fill(deflections, Float.NaN);
        if (controlNames.length == 0 || avl.getGeometry() == null) {
            return deflections;
        }

        Map<String, Float> byName = new HashMap<String, Float>();
        for (com.abajar.avleditor.avl.geometry.Surface surface : avl.getGeometry().getSurfaces()) {
            for (com.abajar.avleditor.avl.geometry.Section section : surface.getSections()) {
                for (com.abajar.avleditor.avl.geometry.Control control : section.getControls()) {
                    String name = control.getName();
                    if (!byName.containsKey(name)) {
                        byName.put(name, control.getMaxDeflection());
                    }
                }
            }
        }

        for (int i = 0; i < controlNames.length; i++) {
            if (byName.containsKey(controlNames[i])) {
                deflections[i] = byName.get(controlNames[i]);
            }
        }
        return deflections;
    }

    private float[] readTrimControlValues(File stabilityFile, String[] controlNames) {
        float[] values = new float[controlNames.length];
        Arrays.fill(values, Float.NaN);
        if (controlNames.length == 0) {
            return values;
        }

        Map<String, Integer> indexByName = new HashMap<String, Integer>();
        for (int i = 0; i < controlNames.length; i++) {
            indexByName.put(controlNames[i], i);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(stabilityFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = TRIM_CONTROL_VALUE_PATTERN.matcher(line);
                if (!matcher.matches()) continue;
                String name = matcher.group(1);
                Integer index = indexByName.get(name);
                if (index == null) continue;
                try {
                    values[index] = Float.parseFloat(matcher.group(2));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to read trim control values from stability file", ex);
        }

        return values;
    }

    private float[] calculateTrimControlDeflections(float[] values, float[] gains) {
        int size = Math.min(values.length, gains.length);
        float[] deflections = new float[size];
        Arrays.fill(deflections, Float.NaN);

        for (int i = 0; i < size; i++) {
            if (!isFinite(values[i]) || !isFinite(gains[i])) continue;
            deflections[i] = values[i] * gains[i];
        }

        return deflections;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    /** What AVL said the air adds: [Ixx, Izz, Ixz, m_y]. Null until the modal pass has read it. */
    private float[] apparentInertias;

    private static final Pattern INERTIA_NUMBER_PATTERN =
            Pattern.compile("[-+]?\\d*\\.\\d+(?:[EeDd][-+]?\\d+)?");

    /** Every number on a line of AVL's inertia tensor, in the order it prints them. */
    private static float[] numbersIn(String line) {
        Matcher matcher = INERTIA_NUMBER_PATTERN.matcher(line);
        List<Float> found = new ArrayList<Float>();
        while (matcher.find()) {
            try {
                found.add(Float.parseFloat(matcher.group().replace('D', 'E').replace('d', 'E')));
            } catch (NumberFormatException ignored) {
            }
        }
        float[] result = new float[found.size()];
        for (int i = 0; i < found.size(); i++) result[i] = found.get(i);
        return result;
    }

    private List<ModeStateAmplitudes> runEigenvalueAnalysis(String eigenFile, int elevatorPosition) throws IOException, InterruptedException {
        logger.log(Level.INFO, "Starting modal pass to generate eigenvalues...");
        List<ModeStateAmplitudes> modeStates = new ArrayList<ModeStateAmplitudes>();
        ProcessBuilder pb = new ProcessBuilder(avlPath, this.avlFileName.toString());
        pb.directory(executionPath.toFile().getAbsoluteFile());
        pb.redirectErrorStream(true);

        Process modeProcess = pb.start();
        try (OutputStream modeIn = modeProcess.getOutputStream();
             BufferedReader modeOut = new BufferedReader(new InputStreamReader(modeProcess.getInputStream()))) {

            writeModeCommand(modeIn, "mset 0\n");
            writeModeCommand(modeIn, "oper\n");

            if (elevatorPosition != -1) {
                writeModeCommand(modeIn, "d" + (elevatorPosition + 1) + " pm 0\n");
            }

            writeModeCommand(modeIn, "c1\n");
            writeModeCommand(modeIn, "v\n");
            writeModeCommand(modeIn, avl.analysisVelocityMetresPerSecond() + "\n\n");
            writeModeCommand(modeIn, "a c " + analysisLiftCoefficient() + "\n");
            writeModeCommand(modeIn, "x\n");
            writeModeCommand(modeIn, "\n");
            writeModeCommand(modeIn, "mode\n");
            writeModeCommand(modeIn, "n\n");
            writeModeCommand(modeIn, "w\n");
            writeModeCommand(modeIn, eigenFile + "\n");
            writeModeCommand(modeIn, "\n");
            // Out of the mode menu, out of OPER, then quit: see runPlotGeneration for why 'q' is not it.
            writeModeCommand(modeIn, "\n");
            writeModeCommand(modeIn, "quit\n");
            modeIn.flush();
            modeIn.close();

            String line;
            ModeStateAmplitudes currentMode = null;
            // AVL prints the airframe's inertia, then the "Apparent mass, inertia" the air adds, and solves
            // the modes with the sum. On a model that second tensor is not small — 15.6 % of the roll
            // inertia on the check aircraft — so it is read here rather than left out or estimated.
            int apparentCountdown = -1;
            float[] apparentMassRow = null;
            float[] apparentInertiaRow1 = null;
            float[] apparentInertiaRow2 = null;
            while ((line = modeOut.readLine()) != null) {
                logger.log(Level.INFO, "[AVL-MODE] " + line);
                if (line.contains("Apparent mass, inertia")) {
                    apparentCountdown = 12;
                    apparentMassRow = null;
                    apparentInertiaRow1 = null;
                    continue;
                }
                if (apparentCountdown > 0) {
                    apparentCountdown--;
                    float[] numbers = numbersIn(line);
                    if (line.contains("myy") && numbers.length >= 1) {
                        apparentMassRow = numbers;
                    } else if (line.contains("Ixx") && numbers.length >= 3) {
                        apparentInertiaRow1 = numbers;
                    } else if (apparentInertiaRow1 != null && line.contains("Iyy") && numbers.length >= 1) {
                        apparentInertiaRow2 = numbers;
                    } else if (apparentInertiaRow1 != null && line.contains("Izz") && numbers.length >= 1) {
                        // Row one is [Ixx, -Ixy, -Ixz], so the product of inertia comes back negated.
                        this.apparentInertias = new float[]{
                                apparentInertiaRow1[0], numbers[0], -apparentInertiaRow1[2],
                                apparentMassRow == null ? 0f : apparentMassRow[0]};
                        apparentCountdown = -1;
                    }
                    continue;
                }
                Matcher modeMatcher = MODE_HEADER_PATTERN.matcher(line);
                if (modeMatcher.matches()) {
                    int modeNumber = Integer.parseInt(modeMatcher.group(1));
                    currentMode = getOrCreateModeState(modeStates, modeNumber);
                    continue;
                }
                if (currentMode != null) {
                    Matcher stateMatcher = MODE_STATE_PATTERN.matcher(line);
                    while (stateMatcher.find()) {
                        String state = normalizeStateToken(stateMatcher.group(1));
                        if (state == null) continue;
                        try {
                            float real = Float.parseFloat(stateMatcher.group(2));
                            float imag = Float.parseFloat(stateMatcher.group(3));
                            float amplitude = (float) Math.hypot(real, imag);
                            currentMode.setAmplitude(state, amplitude);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }

        boolean finished = modeProcess.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            modeProcess.destroyForcibly();
            logger.log(Level.WARNING, "Modal pass timed out while generating eigenvalues");
            return Collections.emptyList();
        }
        logger.log(Level.INFO, "Modal pass finished with exit code: " + modeProcess.exitValue());
        return modeStates;
    }

    /**
     * What is typed at AVL to get the geometry and Trefftz hardcopies, as a list so it can be read and
     * checked without running AVL.
     *
     * The ending is the part that matters. Xplot11 finishes a page — 'showpage' and the PostScript
     * trailer — when the plot file is closed, and that happens when AVL exits. AVL exits on 'quit' at
     * its top-level prompt, which is reached with a blank line from the plot menu and another from OPER.
     * Sending 'q' instead printed 'Option not recognized' and left AVL to die on end-of-input, so the
     * Trefftz page stayed half written and Ghostscript produced nothing from it; the geometry page
     * survived only because opening the second hardcopy had flushed it.
     */
    static List<String> plotCommands(int elevatorPosition, float velocity, float liftCoefficient,
                                     float viewAzimuth, float viewElevation) {
        List<String> commands = new ArrayList<String>();
        commands.add("oper");
        if (elevatorPosition != -1) {
            commands.add("d" + (elevatorPosition + 1) + " pm 0");
        }
        commands.add("c1");
        commands.add("v");
        commands.add(velocity + "\n");
        commands.add("a c " + liftCoefficient);
        commands.add("x");

        // Plotting options: no interactive window, colour PostScript, one appended file.
        commands.add("plop");
        commands.add("g");
        commands.add("c");
        commands.add("i");
        commands.add("");

        commands.add("g");
        commands.add("v");
        commands.add(String.format("%.1f %.1f", viewAzimuth, viewElevation));
        commands.add("h");
        commands.add("");

        commands.add("t");
        commands.add("h");
        commands.add("");

        commands.add("");
        commands.add("quit");
        return commands;
    }

    private void runPlotGeneration(int elevatorPosition) throws IOException, InterruptedException {
        logger.log(Level.INFO, "Starting plot pass for geometry and Trefftz images...");
        ProcessBuilder pb = new ProcessBuilder(avlPath, this.avlFileName.toString());
        pb.directory(executionPath.toFile().getAbsoluteFile());
        pb.redirectErrorStream(true);

        Process plotProcess = pb.start();
        try (OutputStream plotIn = plotProcess.getOutputStream();
             BufferedReader plotOut = new BufferedReader(new InputStreamReader(plotProcess.getInputStream()))) {

            for (String command : plotCommands(elevatorPosition, avl.analysisVelocityMetresPerSecond(),
                    analysisLiftCoefficient(), viewAzimuth, viewElevation)) {
                writeModeCommand(plotIn, command + "\n");
            }
            plotIn.flush();
            plotIn.close();

            String line;
            while ((line = plotOut.readLine()) != null) {
                logger.log(Level.INFO, "[AVL-PLOT] " + line);
            }
        }

        boolean finished = plotProcess.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            plotProcess.destroyForcibly();
            throw new IOException("AVL plot pass timed out");
        }
        logger.log(Level.INFO, "Plot pass finished with exit code: " + plotProcess.exitValue());
    }

    private ModeStateAmplitudes getOrCreateModeState(List<ModeStateAmplitudes> modeStates, int modeNumber) {
        while (modeStates.size() < modeNumber) {
            modeStates.add(new ModeStateAmplitudes());
        }
        return modeStates.get(modeNumber - 1);
    }

    private String normalizeStateToken(String token) {
        if (token == null) return null;
        String normalized = token.trim().toLowerCase().replace("'", "");
        if ("theta".equals(normalized)) return "the";
        if ("u".equals(normalized) || "v".equals(normalized) || "w".equals(normalized)
                || "p".equals(normalized) || "q".equals(normalized) || "r".equals(normalized)
                || "the".equals(normalized) || "phi".equals(normalized) || "psi".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private void writeModeCommand(OutputStream modeIn, String command) throws IOException {
        modeIn.write(command.getBytes());
        modeIn.flush();
    }

    public AvlCalculation getCalculation(){
        return this.result;
    }

    private List<AvlEigenvalue> readEigenvalues(String eigenFile) {
        List<AvlEigenvalue> eigenvalues = new ArrayList<AvlEigenvalue>();
        File file = new File(eigenFile);
        if (!file.exists()) {
            logger.log(Level.INFO, "Eigenvalue file not found: " + eigenFile);
            return eigenvalues;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length < 3) {
                    continue;
                }
                try {
                    float sigma = Float.parseFloat(parts[1]);
                    float omega = Float.parseFloat(parts[2]);
                    eigenvalues.add(new AvlEigenvalue(sigma, omega));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to read eigenvalues file: " + eigenFile, e);
        }
        return eigenvalues;
    }

    private void applyModeStates(List<AvlEigenvalue> eigenvalues, List<ModeStateAmplitudes> modeStates) {
        int modesToApply = Math.min(eigenvalues.size(), modeStates.size());
        for (int i = 0; i < modesToApply; i++) {
            ModeStateAmplitudes states = modeStates.get(i);
            if (states != null) {
                states.applyTo(eigenvalues.get(i));
            }
        }
    }

    private void sendCommand(String command) throws IOException{
        stdin.write(command.getBytes());
        stdin.flush();
        logger.log(Level.FINE, "Sending command: {0}", command);
    }

    
    private Float readFloat(String pattern, Scanner scanner){
        scanner.findWithinHorizon(pattern, 0);
        String value = scanner.next();
        Float realValue = Float.parseFloat(value);
        logger.log(Level.FINE, "{0} {1}", new Object[]{pattern, realValue});
        return realValue;
    }

    private void convertPlotsToImages() {
        Path plotFile = executionPath.resolve("plot.ps");

        geometryPlotPath = executionPath.resolve("geometry.png");
        trefftzPlotPath = executionPath.resolve("trefftz.png");

        if (Files.exists(plotFile)) {
            // Convert multi-page PS to individual PNGs
            // Page 0 = geometry, Page 1 = trefftz
            convertPsToPng(plotFile, geometryPlotPath, 0);
            convertPsToPng(plotFile, trefftzPlotPath, 1);
        } else {
            logger.log(Level.WARNING, "Plot file not found: " + plotFile);
            geometryPlotPath = null;
            trefftzPlotPath = null;
        }
    }

    private void convertPsToPng(Path psFile, Path pngFile, int pageNumber) {
        try {
            // Use Ghostscript to extract specific page
            ProcessBuilder pb = new ProcessBuilder(
                "gs",
                "-dSAFER",
                "-dBATCH",
                "-dNOPAUSE",
                "-dFirstPage=" + (pageNumber + 1),
                "-dLastPage=" + (pageNumber + 1),
                "-sDEVICE=png16m",
                "-r150",
                "-sOutputFile=" + pngFile.toString(),
                psFile.toString()
            );
            pb.directory(executionPath.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();

            // Read output to prevent blocking
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                logger.log(Level.FINE, "[GS] " + line);
            }

            boolean finished = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            if (finished && p.exitValue() == 0 && Files.exists(pngFile)) {
                logger.log(Level.INFO, "Converted page " + pageNumber + " of " + psFile + " to " + pngFile);
                // Rotate image 90 degrees clockwise
                rotateImage(pngFile);
            } else {
                logger.log(Level.WARNING, "Ghostscript conversion failed for page " + pageNumber);
                if (pageNumber == 0) geometryPlotPath = null;
                if (pageNumber == 1) trefftzPlotPath = null;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error converting PostScript to PNG: " + e.getMessage());
            if (pageNumber == 0) geometryPlotPath = null;
            if (pageNumber == 1) trefftzPlotPath = null;
        }
    }

    private void rotateImage(Path pngFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "convert",
                pngFile.toString(),
                "-rotate", "90",
                pngFile.toString()
            );
            pb.directory(executionPath.toFile());
            Process p = pb.start();
            boolean finished = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && p.exitValue() == 0) {
                logger.log(Level.INFO, "Rotated image: " + pngFile);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not rotate image: " + e.getMessage());
        }
    }

    public Path getGeometryPlotPath() {
        return geometryPlotPath;
    }

    public Path getTrefftzPlotPath() {
        return trefftzPlotPath;
    }
}
