/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor;

import com.abajar.avleditor.avl.AVL
import com.abajar.avleditor.avl.AVLS
import com.abajar.avleditor.crrcsim.CRRCSim
import com.abajar.avleditor.crrcsim.CRRCSimFactory
import com.abajar.avleditor.crrcsim.CRRCSimRepository
import com.abajar.avleditor.crrcsim.MetersConversor
import com.abajar.avleditor.crrcsim.MetersConversorInverted
import com.abajar.avleditor.crrcsim.XRelativeToCG
import com.abajar.avleditor.crrcsim.XRelativeToCGInverted
import com.abajar.avleditor.crrcsim.MultiUnit
import com.abajar.avleditor.crrcsim.YRelativeToCG
import com.abajar.avleditor.crrcsim.YRelativeToCGInverted
import com.abajar.avleditor.crrcsim.ZRelativeToCG
import com.abajar.avleditor.crrcsim.ZRelativeToCGInverted
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.util.Properties
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger
import java.lang.reflect.Method
import java.util.ArrayList

import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException
import javax.xml.bind.Marshaller

import org.eclipse.swt.widgets.{Event, TreeItem, TableItem, Listener}
import org.eclipse.swt.events._
import org.eclipse.swt.SWT
import scala.collection.JavaConverters._

import swt.MainWindow
import swt.AvlResultsWindow
import swt.dsl.TableFieldWritable
import swt.dsl.TableFieldReadOnly
import swt.dsl.TableFieldFile
import swt.dsl.TableFieldOptions
import com.abajar.avleditor.view.annotations
import java.lang.reflect.Field
import com.abajar.avleditor.view.annotations.AvlEditorNode
import com.abajar.avleditor.view.annotations.AvlEditorField
import com.abajar.avleditor.view.annotations.AvlEditorFileField
import com.abajar.avleditor.view.annotations.AvlEditorReadOnly
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS
import com.abajar.avleditor.swt.MenuOption._
import com.abajar.avleditor.swt.dsl.TableField
import com.abajar.avleditor.avl.connectivity.AvlRunner


object AvlEditor{

    val logger = Logger.getLogger(AvlEditor.getClass.getName)

    val CONFIGURATION_ROOT = System.getProperty("user.home") + "/.avleditor"
    val CONFIGURATION_PATH = CONFIGURATION_ROOT + "/configuration.xml"

    var configuration = new Properties()

    val dir = new File(CONFIGURATION_ROOT)
    if (!dir.exists) dir.mkdir

    var crrcsim = new CRRCSimFactory().create()
    var currentFile: Option[File] = None

    try {
      configuration.loadFromXML(new FileInputStream(CONFIGURATION_PATH))
    } catch {
      case ex: IOException =>
        logger.log(Level.INFO, "Config file doesn't exists");
    }

    Runtime.getRuntime().addShutdownHook(new Thread(){
      override def run: Unit = {
        try {
          configuration.storeToXML(new FileOutputStream(CONFIGURATION_PATH), "Configuration file")
        } catch {
          case ex: Exception =>
            logger.log(Level.FINE, "Unable to store configuration file", ex)
        }
      }
    })

    val window = new MainWindow(
        handleClickButton,
        treeSourceHandler,
        handleTreeEvent,
        handleClickMenu,
        propertiesSourceHandler,
        handleClickProperties
      )

    // Set up property change callback to update 3D viewer when scale changes
    swt.dsl.Widget.setPropertyChangeCallback(() => {
      window.treeNodeSelected.foreach {
        case graphics: com.abajar.avleditor.crrcsim.Graphics =>
          window.viewer3D.setScale(graphics.getScale)
          window.viewer3D.setAxisMapping(graphics.getAvlXAxis, graphics.getAvlYAxis, graphics.getAvlZAxis)
          window.viewer3D.setShowReferenceLine(graphics.getShowReferenceLine)
          // Refresh properties table to show swapped axis values
          window.properties.clearAll()
        case section: com.abajar.avleditor.avl.geometry.Section =>
          // Update selected section in 3D viewer when properties change
          selectSectionIn3D(section)
        case control: com.abajar.avleditor.avl.geometry.Control =>
          // Sync type across all controls with the same name
          syncControlType(control)
          // Refresh properties table to show updated values (type changes hinge axis)
          loadPropertiesForTreeItem(control)
          // Update selected control in 3D viewer when properties change
          selectControlIn3D(control)
        case body: com.abajar.avleditor.avl.geometry.Body =>
          // Select and update body in 3D viewer when properties change
          selectBodyIn3D(body)
          loadAvlBodies()
        case _ => // Do nothing for other types
      }
      // Refresh the selected tree item name
      val selection = window.tree.getSelection()
      if (selection.nonEmpty) {
        val item = selection(0)
        val data = item.getData()
        if (data != null) {
          item.setText(data.toString)
        }
      }
      // Reload AVL surfaces when any property changes
      loadAvlSurfaces()
    })

    // Set up section update callback for 3D editing
    window.viewer3D.setSectionUpdateCallback((surfaceIdx: Int, sectionIdx: Int, x: Float, y: Float, z: Float, chord: Float) => {
      window.display.asyncExec(new Runnable {
        override def run(): Unit = {
          updateSectionFromViewer(surfaceIdx, sectionIdx, x, y, z, chord)
        }
      })
    })

    // Set up control update callback for 3D editing
    window.viewer3D.setControlUpdateCallback((surfaceIdx: Int, sectionIdx: Int, controlIdx: Int, xhinge: Float) => {
      window.display.asyncExec(new Runnable {
        override def run(): Unit = {
          updateControlFromViewer(surfaceIdx, sectionIdx, controlIdx, xhinge)
        }
      })
    })

    // Set up body update callback for 3D editing
    window.viewer3D.setBodyUpdateCallback((bodyIdx: Int, dX: Float, dY: Float, dZ: Float) => {
      window.display.asyncExec(new Runnable {
        override def run(): Unit = {
          updateBodyFromViewer(bodyIdx, dX, dY, dZ)
        }
      })
    })

    // Set up profile point update callback for 3D editing
    window.viewer3D.setProfilePointUpdateCallback((bodyIdx: Int, pointIdx: Int, x: Float, radius: Float) => {
      window.display.asyncExec(new Runnable {
        override def run(): Unit = {
          updateProfilePointFromViewer(bodyIdx, pointIdx, x, radius)
        }
      })
    })

    // Set up log handler to display logs in footer BEFORE logging anything
    val logFooterHandler = new LogFooterHandler(window.display, window.footerLabel)
    Logger.getLogger("").addHandler(logFooterHandler)

    // Set up log window
    val logWindow = new swt.LogWindow(window.display, logFooterHandler.logHistory)

    // Set up AVL results window
    val avlResultsWindow = new AvlResultsWindow(window.display)

    // Add click listener to footer to open log window
    window.footerLabel.addListener(SWT.MouseDown, new Listener {
      override def handleEvent(e: Event): Unit = {
        logWindow.open()
      }
    })

    // Check and ensure AVL is available on startup (now logs will be captured)
    logger.log(Level.INFO, "Application starting...")
    logger.log(Level.INFO, "Checking AVL availability...")
    AvlManager.ensureAvlAvailable(configuration)
    logger.log(Level.INFO, "Application initialized successfully")

    def apply():Unit = {
      window.disableAllButtons
      loadLastFile()
      logger.log(Level.INFO, "Ready")
      window.show
    }

    private def loadLastFile(): Unit = {
      Option(configuration.getProperty("crrcsim.lastFile")).foreach { lastFilePath =>
        val file = new File(lastFilePath)
        if (file.exists() && file.getName.endsWith(".avle")) {
          try {
            logger.log(Level.INFO, s"Loading last file: ${file.getAbsolutePath}")
            currentFile = Some(file)
            open(file)
          } catch {
            case ex: Exception =>
              logger.log(Level.WARNING, s"Failed to load last file: ${ex.getMessage}")
          }
        }
      }
    }

    private def handleClickButton(button: ENABLE_BUTTONS): Unit = {
      window.treeNodeSelected match {
        case Some(nodeSelected) => {
          // Special handling for IMPORT_BFILE - open file dialog
          if (button == ENABLE_BUTTONS.IMPORT_BFILE) {
            handleImportBfile(nodeSelected)
          } else {
            button.click(nodeSelected, window.treeNodeSelectedParent.get)
          }
          window.refreshTree
          loadAvlSurfaces()
          loadAvlBodies()
        }
        case None => throw new Exception("Button click without node selected")
      }
    }

    private def handleImportBfile(nodeSelected: Any): Unit = {
      import org.eclipse.swt.widgets.FileDialog
      import org.eclipse.swt.SWT
      import com.abajar.avleditor.avl.geometry.Body

      nodeSelected match {
        case body: Body =>
          val dialog = new FileDialog(window.getShell, SWT.OPEN)
          dialog.setText("Import Body Profile")
          dialog.setFilterExtensions(Array("*.dat", "*.*"))
          dialog.setFilterNames(Array("Body Profile (*.dat)", "All Files"))

          val filePath = dialog.open()
          if (filePath != null) {
            val parsed = parseBfile(filePath)
            if (parsed.nonEmpty) {
              body.importProfilePoints(parsed.map(t => Array(t._1, t._2)))
              body.setBFILE(new java.io.File(filePath).getName())
            }
          }
        case _ =>
          logger.log(Level.WARNING, "IMPORT_BFILE called on non-Body node")
      }
    }

    private def handleClickMenu(menuOption: MenuOption): Unit = menuOption match {
      case Save => save
      case SaveAs => saveFile
      case Open => openFile
      case ExportAsAvl => exportAsAVL
      case ExportAsCRRCSim => exportAsCRRCsim
      case RunAvl => runAvl
      case SetAvlExecutable => setAvlExecutable
      case ClearAvlConfiguration => clearAvlConfiguration
    }

    def exportAsAVL(avlFile: Path): Unit = {
      AVLS.avlToFile(this.crrcsim.getAvl(), avlFile, avlFile.getParent())
    }

    private def saveAs(file: File) = {
      new CRRCSimRepository().storeToFile(file, crrcsim)
    }

    private def open(file: File) = {
      crrcsim = new CRRCSimRepository().restoreFromFile(file)
      // Initialize parent references for sections (needed for control creation)
      initSectionParents()
      window.refreshTree
      // Auto-load 3D model if available
      load3DModel()
    }

    private def initSectionParents(): Unit = {
      import scala.collection.JavaConverters._
      val avl = crrcsim.getAvl()
      if (avl != null && avl.getGeometry() != null) {
        avl.getGeometry().getSurfaces().asScala.foreach(_.initSectionParents())
      }
    }

    private def load3DModel(): Unit = {
      val graphics = crrcsim.getGraphics
      if (graphics != null) {
        val modelPath = graphics.getModel
        window.viewer3D.setScale(graphics.getScale)
        window.viewer3D.setAxisMapping(graphics.getAvlXAxis, graphics.getAvlYAxis, graphics.getAvlZAxis)
        window.viewer3D.setShowReferenceLine(graphics.getShowReferenceLine)
        if (modelPath != null && modelPath.nonEmpty) {
          val file = new File(modelPath)
          if (file.exists) {
            val loaded = window.viewer3D.loadModel(modelPath)
            logger.info(s"Auto-loaded 3D model: $loaded")
          } else {
            window.viewer3D.clearModel()
          }
        } else {
          window.viewer3D.clearModel()
        }
      }
      // Load AVL surfaces
      loadAvlSurfaces()
    }

    private def loadAvlSurfaces(): Unit = {
      import scala.collection.JavaConverters._
      try {
        val avl = crrcsim.getAvl()
        if (avl != null && avl.getGeometry() != null) {
          val surfaces = avl.getGeometry().getSurfaces().asScala.map { surface =>
            val dX = surface.getdX()
            val dY = surface.getdY()
            val dZ = surface.getdZ()
            val symmetric = surface.isSymmetric()
            val sections = surface.getSections().asScala.map { section =>
              val naca = Option(section.getNACA()).getOrElse("0012")
              // Extract control surfaces data for each section
              val controls = section.getControls().asScala.map { control =>
                (control.getName(), control.getXhinge(), control.getGain(),
                 control.getSgnDup(), control.getType())
              }.toArray
              (section.getXle() + dX, section.getYle() + dY, section.getZle() + dZ,
               section.getChord(), section.getAinc(), naca, controls)
            }.toArray
            (sections, symmetric)
          }.toArray
          window.viewer3D.setAvlSurfaces(surfaces)
        }
      } catch {
        case e: Exception =>
          logger.log(Level.WARNING, "Error loading AVL surfaces", e)
      }
      // Also load bodies
      loadAvlBodies()
    }

    private def loadAvlBodies(): Unit = {
      import scala.collection.JavaConverters._
      import java.io.{File, BufferedReader, FileReader}
      try {
        val avl = crrcsim.getAvl()
        if (avl != null && avl.getGeometry() != null) {
          val bodies = avl.getGeometry().getBodies().asScala.map { body =>
            // Initialize parent references after deserialization
            body.initProfilePointParents()

            val profile = if (!body.getProfilePoints().isEmpty) {
              // Use inline profile points
              body.getProfilePoints().asScala.map(p => (p.getX(), p.getRadius())).toArray
            } else {
              // Fall back to BFILE parsing or default
              val bfilePath = Option(body.getBFILE()).getOrElse("")
              if (bfilePath.nonEmpty) {
                val parsed = parseBfile(bfilePath)
                if (parsed.nonEmpty) {
                  // Import parsed points into body for future editing
                  body.importProfilePoints(parsed.map(t => Array(t._1, t._2)))
                  parsed
                } else {
                  body.initDefaultProfile()
                  body.getProfilePoints().asScala.map(p => (p.getX(), p.getRadius())).toArray
                }
              } else {
                body.initDefaultProfile()
                body.getProfilePoints().asScala.map(p => (p.getX(), p.getRadius())).toArray
              }
            }
            // Enable mirroring if body is off-center (dY != 0) or Ydupl is explicitly non-zero
            // Ydupl=0 means mirror about Y=0 plane (centerline), which is the common case
            val ydupl = if (body.getYdupl() != 0 || body.getdY() != 0) Some(body.getYdupl()) else None
            (body.getName(), profile, body.getdX(), body.getdY(), body.getdZ(), body.getLength(), ydupl)
          }.toArray
          window.viewer3D.setAvlBodies(bodies)
        }
      } catch {
        case e: Exception =>
          logger.log(Level.WARNING, "Error loading AVL bodies", e)
      }
    }

    // Default body profile - a simple tapered fuselage (scaled to typical aircraft proportions)
    private def defaultBodyProfile(): Array[(Float, Float)] = {
      // Length ~1 unit, radius ~0.1 unit (1:10 aspect ratio typical for fuselage)
      Array(
        (0.00f, 0.00f),    // Nose tip
        (0.05f, 0.04f),    // Nose cone
        (0.15f, 0.08f),    // Forward section
        (0.25f, 0.10f),    // Main body start
        (0.75f, 0.10f),    // Main body end
        (0.90f, 0.06f),    // Tail taper
        (1.00f, 0.02f)     // Tail tip
      )
    }

    // Parse BFILE format: lines of "x r" where x is position along body and r is radius
    private def parseBfile(path: String): Array[(Float, Float)] = {
      import java.io.{File, BufferedReader, FileReader}
      try {
        // Try to find the file relative to the current AVL file or as absolute path
        val file = new File(path)
        val actualFile = if (file.isAbsolute && file.exists()) {
          file
        } else {
          // Try relative to current file
          currentFile.map(f => new File(f.getParentFile, path)).filter(_.exists()).getOrElse(file)
        }

        if (!actualFile.exists()) {
          logger.log(Level.WARNING, s"BFILE not found: $path")
          return Array()
        }

        val reader = new BufferedReader(new FileReader(actualFile))
        val points = new scala.collection.mutable.ArrayBuffer[(Float, Float)]()
        var line = reader.readLine()
        while (line != null) {
          val trimmed = line.trim()
          if (trimmed.nonEmpty && !trimmed.startsWith("#") && !trimmed.startsWith("!")) {
            val parts = trimmed.split("\\s+")
            if (parts.length >= 2) {
              try {
                val x = parts(0).toFloat
                val r = parts(1).toFloat
                points += ((x, r))
              } catch {
                case _: NumberFormatException => // Skip invalid lines
              }
            }
          }
          line = reader.readLine()
        }
        reader.close()
        points.toArray
      } catch {
        case e: Exception =>
          logger.log(Level.WARNING, s"Error parsing BFILE: $path", e)
          Array()
      }
    }

    private def updateSectionFromViewer(surfaceIdx: Int, sectionIdx: Int, x: Float, y: Float, z: Float, chord: Float): Unit = {
      val avl = crrcsim.getAvl()
      if (avl == null || avl.getGeometry() == null) return

      val surfaces = avl.getGeometry().getSurfaces()
      if (surfaceIdx >= surfaces.size()) return

      val surface = surfaces.get(surfaceIdx)
      val sections = surface.getSections()
      if (sectionIdx >= sections.size()) return

      val section = sections.get(sectionIdx)

      // Subtract surface offsets to get local coordinates
      val dX = surface.getdX()
      val dY = surface.getdY()
      val dZ = surface.getdZ()

      section.setXle(x - dX)
      section.setYle(y - dY)
      section.setZle(z - dZ)
      section.setChord(chord)

      // Refresh properties table if this section is selected
      window.treeNodeSelected.foreach {
        case s: com.abajar.avleditor.avl.geometry.Section if s eq section =>
          loadPropertiesForTreeItem(section)
        case _ =>
      }

      // Reload AVL surfaces to reflect the change
      loadAvlSurfaces()
    }

    private def openFile = {
      window.showOpenDialog(
        configuration.getProperty("crrcsim.save", "~/"),
        Array("AVL Editor files (*.avle)", "AVL Editor file (*.crr)"),
        Array("avle", "crr")
      ) match {
        case Some(file) =>
          configuration.setProperty("crrcsim.save", file.getAbsolutePath())
          currentFile = Some(file)
          open(file)
          if (file.getName.endsWith(".avle")) {
            setLastFile(file)
          }
        case None =>
      }
    }

    private def save = {
      currentFile match {
        case Some(file) =>
          saveAs(file)
          setLastFile(file)
          logger.log(Level.INFO, s"Saved: ${file.getAbsolutePath}")
        case None =>
          saveFile
      }
    }

    private def saveFile = {
      val path = this.configuration.getProperty("crrcsim.save", "~/")
      showSaveDialog(path, "AVL Editor file (*.avle)", "avle").foreach(file => {
        this.configuration.setProperty("crrcsim.save", file.getAbsolutePath())
        currentFile = Some(file)
        this.saveAs(file)
        setLastFile(file)
      })
    }

    private def setLastFile(file: File): Unit = {
      configuration.setProperty("crrcsim.lastFile", file.getAbsolutePath())
      try {
        configuration.storeToXML(new FileOutputStream(CONFIGURATION_PATH), "Configuration file")
      } catch {
        case ex: Exception =>
          logger.log(Level.FINE, "Unable to save configuration", ex)
      }
    }

    def exportAsAVL: Unit = {
      val path = this.configuration.getProperty("crrcsim.save", "~/")
      this.showSaveDialog(path, "AVL file (*.avl)","avl").foreach(file => {
        this.configuration.setProperty("crrcsim.save",file.getAbsolutePath())
        exportAsAVL(Paths.get(file.getPath()))
      })
    }

    def exportAsCRRCsim: Unit = {
      val path = this.configuration.getProperty("crrcsim.save", "~/")
      this.showSaveDialog(path, "CRRCsim file (*.xml)", "xml").foreach(file => {
        this.configuration.setProperty("crrcsim.save",file.getAbsolutePath())
        if (existsAvlExecutable) exportAsCRRCsim(file)
      })
    }

    def runAvl: Unit = {
      if (!existsAvlExecutable) {
        logger.log(Level.WARNING, "AVL executable not configured")
        return
      }

      val avl = this.crrcsim.getAvl()
      val geometry = avl.getGeometry()
      val errors = geometry.validate()

      if (!errors.isEmpty) {
        import scala.collection.JavaConverters._
        for (error <- errors.asScala) {
          logger.log(Level.WARNING, s"Validation error: $error")
        }
        logger.log(Level.SEVERE, "Model validation failed. Please fix the errors above before running AVL.")
        return
      }

      logger.log(Level.INFO, "Running AVL analysis...")
      val avlPath = this.configuration.getProperty("avl.path")
      val originPath = this.crrcsim.getOriginPath()

      new Thread(new Runnable {
        def run(): Unit = {
          try {
            logger.log(Level.INFO, s"Starting AvlRunner with path: $avlPath")
            val (elevationAngle, azimuthAngle) = window.viewer3D.getViewAngles
            // Adjust azimuth by -90 degrees to match AVL's coordinate system
            val runner = new AvlRunner(avlPath, avl, originPath, azimuthAngle - 90, elevationAngle)
            logger.log(Level.INFO, "AvlRunner finished, getting calculation...")
            val calculation = runner.getCalculation()
            val geometryPlotPath = runner.getGeometryPlotPath()
            val trefftzPlotPath = runner.getTrefftzPlotPath()
            logger.log(Level.INFO, s"Got calculation: $calculation")

            window.display.asyncExec(new Runnable {
              def run(): Unit = {
                logger.log(Level.INFO, "AVL analysis completed successfully")
                // Store calculation in AVL object for tree display
                avl.setLastCalculation(calculation)
                // Refresh tree to show the new results node
                window.refreshTree
                // Open plot window with generated images
                val plotWindow = new swt.AvlPlotWindow(window.display)
                plotWindow.open(geometryPlotPath, trefftzPlotPath)
                avlResultsWindow.open(calculation)
              }
            })
          } catch {
            case ex: Throwable =>
              ex.printStackTrace()
              window.display.asyncExec(new Runnable {
                def run(): Unit = {
                  logger.log(Level.SEVERE, s"Error running AVL: ${ex.getMessage}", ex)
                }
              })
          }
        }
      }).start()
    }

    private def existsAvlExecutable: Boolean = {
      Option(this.configuration.getProperty("avl.path")).isDefined
    }

    private def exportAsCRRCsim(file: File): Unit = {
      crrcsim.calculate(this.configuration.getProperty("avl.path"), this.crrcsim.getOriginPath())

      val avl = this.crrcsim.getAvl()
      val lengthUnit = avl.getLengthUnit()
      val centerOfMass = crrcsim.getCenterOfMass()
      val m = JAXBContext.newInstance(classOf[CRRCSim]).createMarshaller()

      m.setAdapter(new XRelativeToCG(lengthUnit, centerOfMass.getX()))
      m.setAdapter(new YRelativeToCG(lengthUnit, centerOfMass.getY()))
      m.setAdapter(new ZRelativeToCG(lengthUnit, centerOfMass.getZ()))
      m.setAdapter(new XRelativeToCGInverted(lengthUnit, centerOfMass.getX()))
      m.setAdapter(new YRelativeToCGInverted(lengthUnit, centerOfMass.getY()))
      m.setAdapter(new ZRelativeToCGInverted(lengthUnit, centerOfMass.getZ()))
      m.setAdapter(new MetersConversor(new MultiUnit(lengthUnit, avl.getMassUnit(), avl.getTimeUnit())))
      m.setAdapter(new MetersConversorInverted(new MultiUnit(lengthUnit, avl.getMassUnit(), avl.getTimeUnit())))
      m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

      val fos = new FileOutputStream(file)
      m.marshal(crrcsim, fos)
      fos.close()
    }

    private def setAvlExecutable = {
      window.showOpenDialog(
        this.configuration.getProperty("avl.path", "~/"),
        "AVL executable",
        "*") match {
          case Some(file) =>
            this.configuration.setProperty("avl.path", file.getAbsolutePath())
            try {
                this.configuration.storeToXML(new FileOutputStream(CONFIGURATION_PATH), "Configuration file")
                logger.log(Level.INFO, s"AVL executable set to: ${file.getAbsolutePath()}")
            } catch {
              case ex: Exception =>
                logger.log(Level.FINE, "Unable to save configuation", ex)
            }
          case None =>
      }
    }

    private def clearAvlConfiguration = {
      this.configuration.remove("avl.path")
      try {
        this.configuration.storeToXML(new FileOutputStream(CONFIGURATION_PATH), "Configuration file")
        logger.log(Level.INFO, "AVL configuration cleared. Application will download AVL on next export.")

        // Optionally trigger re-download immediately
        logger.log(Level.INFO, "Re-checking AVL availability...")
        AvlManager.ensureAvlAvailable(configuration)
      } catch {
        case ex: Exception =>
          logger.log(Level.SEVERE, "Unable to clear AVL configuration", ex)
      }
    }

    private def showSaveDialog(path: String, description: String, extensions: String): Option[File] = {
      window.showSaveDialog(
        configuration.getProperty("crrcsim.save", "~/"),
        description,
        extensions
      )
    }

    private def extractProperties(data: Any) = {
      val objClass = data.getClass
      val regularFields = for{
        field <- objClass.getDeclaredFields
        if (field.isAnnotationPresent(classOf[annotations.AvlEditorField]))
      } yield {
        val annotation = field.getAnnotation(classOf[AvlEditorField])
        val options = annotation.options()
        if (options.nonEmpty) {
          new TableFieldOptions(
            data,
            field,
            annotation.text(),
            annotation.help(),
            options
          )
        } else {
          new TableFieldWritable(
            data,
            field,
            annotation.text(),
            annotation.help()
          )
        }
      }
      val fileFields = for{
        field <- objClass.getDeclaredFields
        if (field.isAnnotationPresent(classOf[annotations.AvlEditorFileField]))
      } yield {
        logger.info(s"Found file field: ${field.getName}")
        new TableFieldFile(
          data,
          field,
          field.getAnnotation(classOf[AvlEditorFileField]).text(),
          field.getAnnotation(classOf[AvlEditorFileField]).help(),
          field.getAnnotation(classOf[AvlEditorFileField]).extensions(),
          field.getAnnotation(classOf[AvlEditorFileField]).extensionDescription()
        )
      }
      val readOnlyFields = for{
        method <- objClass.getMethods
        if (method.isAnnotationPresent(classOf[annotations.AvlEditorReadOnly]))
      } yield new TableFieldReadOnly(
        data,
        method,
        method.getAnnotation(classOf[AvlEditorReadOnly]).text(),
        method.getAnnotation(classOf[AvlEditorReadOnly]).help()
      )
      regularFields ++ fileFields ++ readOnlyFields
    }

    private def loadPropertiesForTreeItem(data: Any) = {
      window.properties.setItemCount(extractProperties(data).length)
      window.properties.clearAll
    }

    private def propertiesSourceHandler(index: Integer): TableField = {
      extractProperties(window.treeNodeSelected.get)(index)
    }

    private def treeSourceHandler(parentData: Option[Any], index: Integer): (String, Any, Integer) = {
      val node = parentData match {
        case Some(data) => getChilds(data)(index)
        case None => (crrcsim.toString, crrcsim)
      }

      val childs = getChilds(node._2)

      (node._1, node._2, childs.length)
    }

    private def handleTreeEvent(data: Any): Unit = {
      val objClass = data.getClass
      val parentClass = window.treeNodeSelectedParent.map(_.getClass.getName).getOrElse("No Parent")
      // Load properties BEFORE button layout to ensure itemCount is correct
      loadPropertiesForTreeItem(data)
      if (objClass.isAnnotationPresent(classOf[annotations.AvlEditor])) {
        val crrcsimAnnotations = objClass.getAnnotation(classOf[annotations.AvlEditor]).asInstanceOf[annotations.AvlEditor]
        window.buttonsEnableOnly(crrcsimAnnotations.buttons.toList)
      } else {
        window.disableAllButtons
      }

      // Load 3D model if Graphics node is selected
      data match {
        case graphics: com.abajar.avleditor.crrcsim.Graphics =>
          val modelPath = graphics.getModel
          logger.info(s"Graphics selected, model path: $modelPath")
          // Set scale and axis mapping for dimension display
          window.viewer3D.setScale(graphics.getScale)
          window.viewer3D.setAxisMapping(graphics.getAvlXAxis, graphics.getAvlYAxis, graphics.getAvlZAxis)
          window.viewer3D.setShowReferenceLine(graphics.getShowReferenceLine)
          if (modelPath != null && modelPath.nonEmpty) {
            val file = new File(modelPath)
            logger.info(s"File exists: ${file.exists}, absolute: ${file.getAbsolutePath}")
            if (file.exists) {
              val loaded = window.viewer3D.loadModel(modelPath)
              logger.info(s"Model loaded: $loaded")
            } else {
              window.viewer3D.clearModel()
            }
          } else {
            window.viewer3D.clearModel()
          }
          window.viewer3D.clearSelectedSection()
          window.viewer3D.clearSelectedControl()
          window.viewer3D.clearSelectedBody()
        case section: com.abajar.avleditor.avl.geometry.Section =>
          // Find the parent surface and section index
          selectSectionIn3D(section)
          window.viewer3D.clearSelectedControl()
          window.viewer3D.clearSelectedBody()
        case control: com.abajar.avleditor.avl.geometry.Control =>
          // Find the parent surface, section and control index
          selectControlIn3D(control)
          window.viewer3D.clearSelectedSection()
          window.viewer3D.clearSelectedBody()
        case body: com.abajar.avleditor.avl.geometry.Body =>
          // Find the body index and select it
          selectBodyIn3D(body)
          window.viewer3D.clearSelectedSection()
          window.viewer3D.clearSelectedControl()
          window.viewer3D.clearSelectedProfilePoint()
        case profilePoint: com.abajar.avleditor.avl.geometry.BodyProfilePoint =>
          // Find the body and point index, select it in 3D
          selectProfilePointIn3D(profilePoint)
          window.viewer3D.clearSelectedSection()
          window.viewer3D.clearSelectedControl()
          window.viewer3D.clearSelectedBody()
        case _ =>
          // Clear selections for other nodes
          window.viewer3D.clearSelectedSection()
          window.viewer3D.clearSelectedControl()
          window.viewer3D.clearSelectedBody()
          window.viewer3D.clearSelectedProfilePoint()
      }
    }

    private def selectSectionIn3D(section: com.abajar.avleditor.avl.geometry.Section): Unit = {
      val avl = crrcsim.getAvl()
      if (avl == null || avl.getGeometry() == null) return

      val surfaces = avl.getGeometry().getSurfaces()
      var surfaceIdx = 0
      var found = false
      while (surfaceIdx < surfaces.size() && !found) {
        val surface = surfaces.get(surfaceIdx)
        val sections = surface.getSections()
        var sectionIdx = 0
        while (sectionIdx < sections.size() && !found) {
          if (sections.get(sectionIdx) eq section) {
            found = true
            val dX = surface.getdX()
            val dY = surface.getdY()
            val dZ = surface.getdZ()
            window.viewer3D.setSelectedSection(
              surfaceIdx, sectionIdx,
              section.getXle() + dX,
              section.getYle() + dY,
              section.getZle() + dZ,
              section.getChord()
            )
          }
          sectionIdx += 1
        }
        surfaceIdx += 1
      }
    }

    private def selectControlIn3D(control: com.abajar.avleditor.avl.geometry.Control): Unit = {
      val avl = crrcsim.getAvl()
      if (avl == null || avl.getGeometry() == null) return

      val surfaces = avl.getGeometry().getSurfaces()
      var surfaceIdx = 0
      var found = false
      while (surfaceIdx < surfaces.size() && !found) {
        val surface = surfaces.get(surfaceIdx)
        val sections = surface.getSections()
        var sectionIdx = 0
        while (sectionIdx < sections.size() && !found) {
          val section = sections.get(sectionIdx)
          val controls = section.getControls()
          var controlIdx = 0
          while (controlIdx < controls.size() && !found) {
            if (controls.get(controlIdx) eq control) {
              found = true
              window.viewer3D.setSelectedControl(
                surfaceIdx, sectionIdx, controlIdx,
                control.getXhinge()
              )
            }
            controlIdx += 1
          }
          sectionIdx += 1
        }
        surfaceIdx += 1
      }
    }

    private def syncControlType(control: com.abajar.avleditor.avl.geometry.Control): Unit = {
      val avl = crrcsim.getAvl()
      if (avl == null || avl.getGeometry() == null) return

      val controlName = control.getName()
      val controlType = control.getType()

      val surfaces = avl.getGeometry().getSurfaces()
      for (i <- 0 until surfaces.size()) {
        val surface = surfaces.get(i)
        val sections = surface.getSections()
        for (j <- 0 until sections.size()) {
          val section = sections.get(j)
          val controls = section.getControls()
          for (k <- 0 until controls.size()) {
            val otherControl = controls.get(k)
            if ((otherControl ne control) && otherControl.getName() == controlName) {
              otherControl.setType(controlType)
            }
          }
        }
      }
    }

    private def updateControlFromViewer(surfaceIdx: Int, sectionIdx: Int, controlIdx: Int, xhinge: Float): Unit = {
      val avl = crrcsim.getAvl()
      if (avl == null || avl.getGeometry() == null) return

      val surfaces = avl.getGeometry().getSurfaces()
      if (surfaceIdx < surfaces.size()) {
        val surface = surfaces.get(surfaceIdx)
        val sections = surface.getSections()
        if (sectionIdx < sections.size()) {
          val section = sections.get(sectionIdx)
          val controls = section.getControls()
          if (controlIdx < controls.size()) {
            val control = controls.get(controlIdx)
            control.setXhinge(xhinge)
            // Refresh properties table and AVL surfaces
            window.properties.clearAll()
            loadAvlSurfaces()
          }
        }
      }
    }

    private def selectBodyIn3D(body: com.abajar.avleditor.avl.geometry.Body): Unit = {
      import scala.collection.JavaConverters._
      val avl = crrcsim.getAvl()
      if (avl == null || avl.getGeometry() == null) return

      val bodies = avl.getGeometry().getBodies()
      var bodyIdx = 0
      var found = false
      while (bodyIdx < bodies.size() && !found) {
        if (bodies.get(bodyIdx) eq body) {
          found = true
          window.viewer3D.setSelectedBody(
            bodyIdx,
            body.getdX(),
            body.getdY(),
            body.getdZ()
          )
        }
        bodyIdx += 1
      }
    }

    private def updateBodyFromViewer(bodyIdx: Int, dX: Float, dY: Float, dZ: Float): Unit = {
      import scala.collection.JavaConverters._
      val avl = crrcsim.getAvl()
      if (avl == null || avl.getGeometry() == null) return

      val bodies = avl.getGeometry().getBodies()
      if (bodyIdx < bodies.size()) {
        val body = bodies.get(bodyIdx)
        body.setdX(dX)
        body.setdY(dY)
        body.setdZ(dZ)
        // Refresh properties table and AVL bodies
        window.treeNodeSelected.foreach {
          case b: com.abajar.avleditor.avl.geometry.Body if b eq body =>
            loadPropertiesForTreeItem(body)
          case _ =>
        }
        loadAvlBodies()
      }
    }

    private def selectProfilePointIn3D(profilePoint: com.abajar.avleditor.avl.geometry.BodyProfilePoint): Unit = {
      import scala.collection.JavaConverters._
      val avl = crrcsim.getAvl()
      if (avl == null || avl.getGeometry() == null) return

      val bodies = avl.getGeometry().getBodies()
      var bodyIdx = 0
      var found = false
      while (bodyIdx < bodies.size() && !found) {
        val body = bodies.get(bodyIdx)
        val points = body.getProfilePoints()
        var pointIdx = 0
        while (pointIdx < points.size() && !found) {
          if (points.get(pointIdx) eq profilePoint) {
            found = true
            window.viewer3D.setSelectedProfilePoint(
              bodyIdx,
              pointIdx,
              profilePoint.getX(),
              profilePoint.getRadius()
            )
          }
          pointIdx += 1
        }
        bodyIdx += 1
      }
    }

    private def updateProfilePointFromViewer(bodyIdx: Int, pointIdx: Int, x: Float, radius: Float): Unit = {
      import scala.collection.JavaConverters._
      val avl = crrcsim.getAvl()
      if (avl == null || avl.getGeometry() == null) return

      val bodies = avl.getGeometry().getBodies()
      if (bodyIdx < bodies.size()) {
        val body = bodies.get(bodyIdx)
        val points = body.getProfilePoints()
        if (pointIdx < points.size()) {
          val point = points.get(pointIdx)
          point.setX(x)
          point.setRadius(radius)
          // Refresh properties table and AVL bodies
          window.treeNodeSelected.foreach {
            case p: com.abajar.avleditor.avl.geometry.BodyProfilePoint if p eq point =>
              loadPropertiesForTreeItem(point)
            case _ =>
          }
          loadAvlBodies()
        }
      }
    }

    private def handleClickProperties(data: Any): Unit = data match {
      case tableField: TableField => window.help.setText(tableField.help)
    }

    private def getChilds(node: Any): scala.collection.immutable.List[(String, Any)] = node match {
      case null => List()
      case childs: java.util.List[_] =>
        childs.asScala.toList.map(child => (child.toString, child.asInstanceOf[Any]))
      case node =>
        node.getClass.getMethods.foldLeft(List[(String, Any)]())(
          (nodes, method)=>
            if (method.isAnnotationPresent(classOf[AvlEditorNode])){
              nodes :+ getNameNodePair(method, node)
            } else {
              nodes
            }
        )
    }

    private def getNameNodePair(method: Method, parentNode: Any) = {
      val name = method.getAnnotation(classOf[AvlEditorNode]).name
      val node = method.invoke(parentNode)
      (if (name == "Node") node.toString else name , node)
    }
}

