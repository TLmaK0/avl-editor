/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.swt

import org.eclipse.swt.widgets.{Composite, Label, Button}
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.{GridLayout, GridData, FillLayout}
import org.eclipse.swt.events._
import org.eclipse.swt.awt.SWT_AWT

import java.awt.Frame
import com.jogamp.opengl._
import com.jogamp.opengl.awt.GLJPanel
import com.jogamp.opengl.fixedfunc.{GLLightingFunc, GLMatrixFunc}
import com.jogamp.opengl.util.FPSAnimator
import com.jogamp.opengl.util.awt.TextRenderer
import java.awt.Font

import com.abajar.avleditor.ac3d.{AC3DLoader, AC3DModel}

class Viewer3DGL(parent: Composite, style: Int) extends Composite(parent, style) {
  private val gridLayout = new GridLayout(1, false)
  gridLayout.marginWidth = 0
  gridLayout.marginHeight = 0
  gridLayout.verticalSpacing = 2
  setLayout(gridLayout)

  private var model: Option[AC3DModel] = None
  @volatile private var vertices: Array[Float] = Array()
  @volatile private var colors: Array[Float] = Array()

  // Camera parameters
  @volatile private var rotationX: Float = 20.0f
  @volatile private var rotationY: Float = 45.0f
  @volatile private var zoom: Float = 1.0f
  @volatile private var panX: Float = 0.0f
  @volatile private var panY: Float = 0.0f

  // Mouse tracking
  private var lastMouseX: Int = 0
  private var lastMouseY: Int = 0
  private var isDragging: Boolean = false
  private var isPanning: Boolean = false

  // Model bounds
  @volatile private var centerX: Float = 0
  @volatile private var centerY: Float = 0
  @volatile private var centerZ: Float = 0
  @volatile private var modelScale: Float = 1.0f

  // Model dimensions
  @volatile private var modelMinX: Float = 0
  @volatile private var modelMaxX: Float = 0
  @volatile private var modelMinY: Float = 0
  @volatile private var modelMaxY: Float = 0
  @volatile private var modelMinZ: Float = 0
  @volatile private var modelMaxZ: Float = 0

  // Display scale
  @volatile private var displayScale: Float = 1.0f
  @volatile private var showDimensions: Boolean = true
  @volatile private var wireframeMode: Boolean = true
  @volatile private var showAvlSurfaces: Boolean = true

  // AVL surface data: Array of (sections, symmetric), where sections is Array of (x, y, z, chord, ainc, naca, controls)
  // controls is Array of (name, xhinge, gain, sgnDup, type)
  type ControlData = (String, Float, Float, Float, Int)  // (name, xhinge, gain, sgnDup, type)
  type SectionData = (Float, Float, Float, Float, Float, String, Array[ControlData])  // (xle, yle, zle, chord, ainc, naca, controls)
  @volatile private var avlSurfaces: Array[(Array[SectionData], Boolean)] = Array()

  // AVL body data: Array of (name, profile, dX, dY, dZ, length, ydupl)
  // profile is Array of (x, radius) points defining the body shape
  type BodyProfilePoint = (Float, Float)  // (x, radius)
  type BodyData = (String, Array[(Float, Float)], Float, Float, Float, Float, Option[Float])  // (name, profile[(x,radius)], dX, dY, dZ, length, ydupl)
  @volatile private var avlBodies: Array[BodyData] = Array()

  // Collision points (wheels) in AVL coordinates, and which one the tree has selected
  @volatile private var collisionPoints: Array[(Float, Float, Float)] = Array()
  @volatile private var selectedCollisionPoint: Option[Int] = None
  @volatile private var isDraggingCollisionPoint: Boolean = false
  private var collisionPointUpdateCallback: Option[(Int, Float, Float, Float) => Unit] = None

  // Masses in AVL coordinates with their weight and whether they are the mirrored half of one,
  // (x, y, z, kg, virtual), and which one the tree has selected. The selected one is moved one axis
  // at a time, so each axis drags separately.
  @volatile private var massPoints: Array[(Float, Float, Float, Float, Boolean)] = Array()
  // For each mass, the index of the mass mirroring it and the plane they mirror about, or -1: the
  // two halves of a mirrored element move together while one of them is dragged, rather than the
  // twin jumping into place when the mouse is released.
  @volatile private var massMirrors: Array[(Int, Float)] = Array()

  /**
   * The shapes the propulsion components have — the battery's box, the motor's cylinder, the propeller's swept
   * disc — each tied to the mass point it is centred on. Live during a resize drag: the array is the copy the
   * view draws, and the model is written once the mouse is released, as a mass drag does.
   */
  @volatile private var componentShapes: Array[com.abajar.avleditor.mass.ComponentShape] = Array()
  private var shapeSizeUpdateCallback: Option[(Int, Float, Float, Float) => Unit] = None
  private var isDraggingShapeSizeX = false
  private var isDraggingShapeSizeY = false
  private var isDraggingShapeSizeZ = false
  @volatile private var selectedMassPoint: Option[Int] = None
  @volatile private var isDraggingMassAxisX: Boolean = false
  @volatile private var isDraggingMassAxisY: Boolean = false
  @volatile private var isDraggingMassAxisZ: Boolean = false
  private var massPointUpdateCallback: Option[(Int, Float, Float, Float) => Unit] = None

  // Selected section for editing (surfaceIndex, sectionIndex, x, y, z, chord)
  @volatile private var selectedSection: Option[(Int, Int, Float, Float, Float, Float)] = None
  @volatile private var isDraggingSection: Boolean = false
  @volatile private var isDraggingChord: Boolean = false
  private var sectionUpdateCallback: Option[(Int, Int, Float, Float, Float, Float) => Unit] = None

  // Selected control for editing (surfaceIndex, sectionIndex, controlIdx, xhinge)
  @volatile private var selectedControl: Option[(Int, Int, Int, Float)] = None
  @volatile private var isDraggingControl: Boolean = false
  private var controlUpdateCallback: Option[(Int, Int, Int, Float) => Unit] = None

  // Selected body for editing (bodyIndex, dX, dY, dZ)
  @volatile private var selectedBody: Option[(Int, Float, Float, Float)] = None
  @volatile private var isDraggingBody: Boolean = false  // Center handle with snap
  @volatile private var isDraggingBodyAxisX: Boolean = false
  @volatile private var isDraggingBodyAxisY: Boolean = false
  @volatile private var isDraggingBodyAxisZ: Boolean = false
  private var bodyUpdateCallback: Option[(Int, Float, Float, Float) => Unit] = None

  // Selected profile point for editing (bodyIndex, pointIndex, x, radius)
  @volatile private var selectedProfilePoint: Option[(Int, Int, Float, Float)] = None
  @volatile private var isDraggingProfilePointX: Boolean = false      // Dragging X position handle
  @volatile private var isDraggingProfilePointRadius: Boolean = false  // Dragging radius handle
  @volatile private var radiusHandleAngle: Float = 0f  // Angle in Y-Z plane (0 = +Z direction)
  private var profilePointUpdateCallback: Option[(Int, Int, Float, Float) => Unit] = None

  // Axis mapping configuration
  @volatile private var avlXAxis: String = "X"   // Model axis for AVL X (forward)
  @volatile private var avlYAxis: String = "-Z"  // Model axis for AVL Y (spanwise)
  @volatile private var avlZAxis: String = "Y"   // Model axis for AVL Z (up)
  @volatile private var showReferenceLine: Boolean = true

  // Create AWT Frame embedded in SWT
  private val awtComposite = new Composite(this, SWT.EMBEDDED)
  awtComposite.setLayout(new FillLayout())
  awtComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
  private val awtFrame = SWT_AWT.new_Frame(awtComposite)

  // Bottom bar with checkboxes and info label
  private val bottomBar = new Composite(this, SWT.NONE)
  private val bottomLayout = new GridLayout(3, false)
  bottomLayout.marginWidth = 0
  bottomLayout.marginHeight = 0
  bottomBar.setLayout(bottomLayout)
  bottomBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))

  // Wireframe checkbox
  private val wireframeCheckbox = new Button(bottomBar, SWT.CHECK)
  wireframeCheckbox.setText("Wireframe")
  wireframeCheckbox.setSelection(true)
  wireframeCheckbox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false))
  wireframeCheckbox.addSelectionListener(new SelectionAdapter {
    override def widgetSelected(e: SelectionEvent): Unit = {
      wireframeMode = wireframeCheckbox.getSelection
    }
  })

  // AVL surfaces checkbox
  private val avlSurfacesCheckbox = new Button(bottomBar, SWT.CHECK)
  avlSurfacesCheckbox.setText("AVL")
  avlSurfacesCheckbox.setSelection(true)
  avlSurfacesCheckbox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false))
  avlSurfacesCheckbox.addSelectionListener(new SelectionAdapter {
    override def widgetSelected(e: SelectionEvent): Unit = {
      showAvlSurfaces = avlSurfacesCheckbox.getSelection
    }
  })

  // Info label for dimensions and controls
  private val infoLabel = new Label(bottomBar, SWT.NONE)
  infoLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))
  infoLabel.setText("Right-drag: rotate | Middle-drag: pan | Wheel: zoom | Double-click: fit")

  // OpenGL setup using AWT GLCanvas
  private val glProfile = GLProfile.getDefault
  private val glCapabilities = new GLCapabilities(glProfile)
  glCapabilities.setDoubleBuffered(true)
  glCapabilities.setHardwareAccelerated(true)

  // Use GLJPanel instead of GLCanvas for better compatibility with SWT_AWT
  private val glCanvas = new GLJPanel(glCapabilities)
  awtFrame.setLayout(new java.awt.BorderLayout())
  awtFrame.add(glCanvas, java.awt.BorderLayout.CENTER)
  awtFrame.setVisible(true)
  glCanvas.setVisible(true)

  private var glInitialized = false
  private var textRenderer: TextRenderer = _

  glCanvas.addGLEventListener(new GLEventListener {
    override def init(drawable: GLAutoDrawable): Unit = {
      val gl = drawable.getGL.getGL2
      gl.glClearColor(0.2f, 0.2f, 0.2f, 1.0f)
      gl.glEnable(GL.GL_DEPTH_TEST)
      gl.glEnable(GLLightingFunc.GL_LIGHTING)
      gl.glEnable(GLLightingFunc.GL_LIGHT0)
      gl.glEnable(GLLightingFunc.GL_COLOR_MATERIAL)
      gl.glColorMaterial(GL.GL_FRONT_AND_BACK, GLLightingFunc.GL_AMBIENT_AND_DIFFUSE)
      gl.glShadeModel(GLLightingFunc.GL_SMOOTH)

      val lightPos = Array(1.0f, 1.0f, 1.0f, 0.0f)
      val lightAmbient = Array(0.3f, 0.3f, 0.3f, 1.0f)
      val lightDiffuse = Array(0.8f, 0.8f, 0.8f, 1.0f)
      gl.glLightfv(GLLightingFunc.GL_LIGHT0, GLLightingFunc.GL_POSITION, lightPos, 0)
      gl.glLightfv(GLLightingFunc.GL_LIGHT0, GLLightingFunc.GL_AMBIENT, lightAmbient, 0)
      gl.glLightfv(GLLightingFunc.GL_LIGHT0, GLLightingFunc.GL_DIFFUSE, lightDiffuse, 0)

      textRenderer = new TextRenderer(new Font("SansSerif", Font.BOLD, 14))
      glInitialized = true
    }

    override def dispose(drawable: GLAutoDrawable): Unit = {
      if (textRenderer != null) textRenderer.dispose()
    }

    override def display(drawable: GLAutoDrawable): Unit = {
      render(drawable)
    }

    override def reshape(drawable: GLAutoDrawable, x: Int, y: Int, width: Int, height: Int): Unit = {
      val gl = drawable.getGL.getGL2
      gl.glViewport(0, 0, width, height)
    }
  })

  // Mouse handling
  glCanvas.addMouseListener(new java.awt.event.MouseAdapter {
    override def mousePressed(e: java.awt.event.MouseEvent): Unit = {
      lastMouseX = e.getX
      lastMouseY = e.getY
      if (e.getButton == java.awt.event.MouseEvent.BUTTON1) {
        // Left click to drag section, control, body, profile point or collision point when
        // one is selected
        if (selectedMassPoint.isDefined) {
          getClosestMassHandle(e.getX, e.getY) match {
            case "y"  => isDraggingMassAxisY = true
            case "z"  => isDraggingMassAxisZ = true
            case "s0" => isDraggingShapeSizeX = true
            case "s1" => isDraggingShapeSizeY = true
            case "s2" => isDraggingShapeSizeZ = true
            case _    => isDraggingMassAxisX = true
          }
        } else if (selectedCollisionPoint.isDefined) {
          isDraggingCollisionPoint = true
        } else if (selectedProfilePoint.isDefined) {
          // Check which handle is closer (X position or radius)
          val clickedHandle = getClosestProfilePointHandle(e.getX, e.getY)
          if (clickedHandle == "radius") {
            isDraggingProfilePointRadius = true
          } else {
            isDraggingProfilePointX = true
          }
        } else if (selectedControl.isDefined) {
          isDraggingControl = true
        } else if (selectedBody.isDefined) {
          val clickedHandle = getClosestBodyAxisHandle(e.getX, e.getY)
          clickedHandle match {
            case "x" => isDraggingBodyAxisX = true
            case "y" => isDraggingBodyAxisY = true
            case "z" => isDraggingBodyAxisZ = true
            case _ => isDraggingBody = true  // center with snap
          }
        } else if (selectedSection.isDefined) {
          // Check which handle is closer
          val clickedHandle = getClosestHandle(e.getX, e.getY)
          if (clickedHandle == "trailing") {
            isDraggingChord = true
          } else {
            isDraggingSection = true
          }
        }
      } else if (e.getButton == java.awt.event.MouseEvent.BUTTON3) {
        // Right click to rotate
        isDragging = true
      } else if (e.getButton == java.awt.event.MouseEvent.BUTTON2) {
        // Middle click to pan
        isPanning = true
      }
    }

    override def mouseReleased(e: java.awt.event.MouseEvent): Unit = {
      if (isDraggingShapeSizeX || isDraggingShapeSizeY || isDraggingShapeSizeZ) {
        isDraggingShapeSizeX = false
        isDraggingShapeSizeY = false
        isDraggingShapeSizeZ = false
        selectedShapeIndex.foreach { shapeIndex =>
          val shape = componentShapes(shapeIndex)
          shapeSizeUpdateCallback.foreach(_(shape.pointIndex, shape.sizeX, shape.sizeY, shape.sizeZ))
        }
      } else if (isDraggingMassAxisX || isDraggingMassAxisY || isDraggingMassAxisZ) {
        isDraggingMassAxisX = false
        isDraggingMassAxisY = false
        isDraggingMassAxisZ = false
        selectedMassPoint.foreach { index =>
          if (index < massPoints.length) {
            val (x, y, z, _, _) = massPoints(index)
            massPointUpdateCallback.foreach(_(index, x, y, z))
          }
        }
      } else if (isDraggingCollisionPoint) {
        isDraggingCollisionPoint = false
        selectedCollisionPoint.foreach { index =>
          if (index < collisionPoints.length) {
            val (x, y, z) = collisionPoints(index)
            collisionPointUpdateCallback.foreach(_(index, x, y, z))
          }
        }
      } else if (isDraggingProfilePointX || isDraggingProfilePointRadius) {
        isDraggingProfilePointX = false
        isDraggingProfilePointRadius = false
        // Notify callback with updated x and radius
        selectedProfilePoint.foreach { case (bodyIdx, pointIdx, x, radius) =>
          profilePointUpdateCallback.foreach(_(bodyIdx, pointIdx, x, radius))
        }
      } else if (isDraggingControl) {
        isDraggingControl = false
        // Notify callback with updated xhinge
        selectedControl.foreach { case (surfIdx, secIdx, ctrlIdx, xhinge) =>
          controlUpdateCallback.foreach(_(surfIdx, secIdx, ctrlIdx, xhinge))
        }
      } else if (isDraggingBody || isDraggingBodyAxisX || isDraggingBodyAxisY || isDraggingBodyAxisZ) {
        isDraggingBody = false
        isDraggingBodyAxisX = false
        isDraggingBodyAxisY = false
        isDraggingBodyAxisZ = false
        // Notify callback with updated position
        selectedBody.foreach { case (bodyIdx, dX, dY, dZ) =>
          bodyUpdateCallback.foreach(_(bodyIdx, dX, dY, dZ))
        }
      } else if (isDraggingSection || isDraggingChord) {
        isDraggingSection = false
        isDraggingChord = false
        // Notify callback with updated position and chord
        selectedSection.foreach { case (surfIdx, secIdx, x, y, z, chord) =>
          sectionUpdateCallback.foreach(_(surfIdx, secIdx, x, y, z, chord))
        }
      }
      isDragging = false
      isPanning = false
    }

    override def mouseClicked(e: java.awt.event.MouseEvent): Unit = {
      if (e.getClickCount == 2) zoomToFit()
    }
  })

  glCanvas.addMouseMotionListener(new java.awt.event.MouseMotionAdapter {
    override def mouseDragged(e: java.awt.event.MouseEvent): Unit = {
      if (isDraggingShapeSizeX || isDraggingShapeSizeY || isDraggingShapeSizeZ) {
        // A face drag resizes about the centre: the mass sits at the centre of the volume, so pushing one
        // face out pushes its opposite out too and the weight does not move.
        val axis = if (isDraggingShapeSizeX) 0 else if (isDraggingShapeSizeY) 1 else 2
        selectedShapeIndex.foreach { shapeIndex =>
          val shape = componentShapes(shapeIndex)
          if (shape.resizable(axis) && shape.pointIndex < massPoints.length) {
            val (x, y, z, _, _) = massPoints(shape.pointIndex)
            val faceDelta = massAxisDelta(x, y, z, axis, e.getX - lastMouseX, e.getY - lastMouseY)
            val current = axis match {
              case 0 => shape.sizeX
              case 1 => shape.sizeY
              case _ => shape.sizeZ
            }
            val resized = com.abajar.avleditor.mass.ComponentShapes
              .resizedExtent(current, faceDelta, shape.minSize)
            // A cylinder has one diameter, so either handle across its axis moves both sizes together.
            val roundAcross = shape.kind == com.abajar.avleditor.mass.ComponentShapes.Cylinder
            val updated = componentShapes.clone()
            updated(shapeIndex) = axis match {
              case 0 => shape.copy(sizeX = resized)
              case 1 => if (roundAcross) shape.copy(sizeY = resized, sizeZ = resized)
                        else shape.copy(sizeY = resized)
              case _ => if (roundAcross) shape.copy(sizeY = resized, sizeZ = resized)
                        else shape.copy(sizeZ = resized)
            }
            componentShapes = updated
          }
        }
        lastMouseX = e.getX
        lastMouseY = e.getY
      } else if (isDraggingMassAxisX || isDraggingMassAxisY || isDraggingMassAxisZ) {
        // One axis at a time, and by however far the mouse travelled along that axis as it appears
        // on screen. A fixed "horizontal is x" rule moves the mass the wrong way as soon as the
        // view is rotated, and every view here is rotated. No vertex snapping: a battery sits
        // inside the fuselage, not on its skin.
        val axis = if (isDraggingMassAxisX) 0 else if (isDraggingMassAxisY) 1 else 2
        selectedMassPoint.foreach { index =>
          if (index < massPoints.length) {
            val (x, y, z, mass, virtual) = massPoints(index)
            val delta = massAxisDelta(x, y, z, axis, e.getX - lastMouseX, e.getY - lastMouseY)
            val moved = axis match {
              case 0 => (x + delta, y, z, mass, virtual)
              case 1 => (x, y + delta, z, mass, virtual)
              case _ => (x, y, z + delta, mass, virtual)
            }
            val updated = massPoints.clone()
            updated(index) = moved
            // A mirrored element carries its masses in pairs, so the twin follows as it is dragged.
            if (index < massMirrors.length) {
              val (twinIndex, planeY) = massMirrors(index)
              if (twinIndex >= 0 && twinIndex < updated.length) {
                val (nx, ny, nz, _, _) = moved
                val (_, _, _, twinMass, twinVirtual) = updated(twinIndex)
                updated(twinIndex) = (nx, 2f * planeY - ny, nz, twinMass, twinVirtual)
              }
            }
            massPoints = updated
          }
        }
        lastMouseX = e.getX
        lastMouseY = e.getY
      } else if (isDraggingCollisionPoint) {
        // Horizontal mouse moves along the fuselage (AVL x), vertical moves in height (AVL z),
        // which is what placing a wheel under the aircraft needs. Snapping to a mesh vertex
        // sets all three coordinates at once, for putting the contact exactly on the model.
        val sensitivity = 0.1f / zoom / displayScale
        val deltaScreenX = (e.getX - lastMouseX) * sensitivity
        val deltaScreenY = (e.getY - lastMouseY) * sensitivity
        selectedCollisionPoint.foreach { index =>
          if (index < collisionPoints.length) {
            val (x, y, z) = collisionPoints(index)
            var (newX, newY, newZ) = (x + deltaScreenX, y, z - deltaScreenY)
            findNearestVertexByScreenPos(e.getX, e.getY).foreach { case (vx, vy, vz) =>
              val (avlX, avlY, avlZ) = modelToAvl(vx / displayScale, vy / displayScale, vz / displayScale)
              newX = avlX; newY = avlY; newZ = avlZ
            }
            val updated = collisionPoints.clone()
            updated(index) = (newX, newY, newZ)
            collisionPoints = updated
          }
        }
        lastMouseX = e.getX
        lastMouseY = e.getY
      } else if (isDraggingProfilePointX) {
        // Dragging X position handle - try to snap to vertices
        selectedProfilePoint.foreach { case (bodyIdx, pointIdx, x, radius) =>
          if (bodyIdx < avlBodies.length) {
            val (_, _, dX, dY, dZ, length, _) = avlBodies(bodyIdx)
            // Try to find a nearby vertex to snap to
            findNearestVertexByScreenPos(e.getX, e.getY) match {
              case Some((vx, vy, vz)) =>
                // Convert vertex to AVL coordinates
                val (avlX, avlY, avlZ) = modelToAvl(vx / displayScale, vy / displayScale, vz / displayScale)
                // Calculate new X as relative position along body (normalized 0-1)
                // X is normalized, so divide by length
                val newX = scala.math.max(0.0f, scala.math.min(1.0f, (avlX - dX) / length))
                selectedProfilePoint = Some((bodyIdx, pointIdx, newX, radius))
              case None =>
                // No snap - use direct mouse movement
                // Sensitivity is scaled by length since x is normalized (0-1)
                val sensitivity = 0.1f / zoom / displayScale / length
                val deltaScreenX = (e.getX - lastMouseX) * sensitivity
                val newX = scala.math.max(0.0f, scala.math.min(1.0f, x + deltaScreenX))
                selectedProfilePoint = Some((bodyIdx, pointIdx, newX, radius))
            }
          }
        }
        lastMouseX = e.getX
        lastMouseY = e.getY
      } else if (isDraggingProfilePointRadius) {
        // Calculate radius and angle based on mouse position, with snap to vertices
        selectedProfilePoint.foreach { case (bodyIdx, pointIdx, x, _) =>
          if (bodyIdx < avlBodies.length) {
            val (_, _, dX, dY, dZ, length, _) = avlBodies(bodyIdx)
            // Profile X is normalized (0-1), multiply by length
            val actualX = x * length
            val (mx, my, mz) = avlToModel(dX + actualX, dY, dZ)

            // Try to snap to nearby vertex
            findNearestVertexByScreenPos(e.getX, e.getY) match {
              case Some((vx, vy, vz)) =>
                // Snap: calculate radius as distance from body center to vertex
                val (avlX, avlY, avlZ) = modelToAvl(vx / displayScale, vy / displayScale, vz / displayScale)
                val newRadius = scala.math.sqrt((avlY - dY) * (avlY - dY) + (avlZ - dZ) * (avlZ - dZ)).toFloat
                // Calculate angle to snapped vertex in Y-Z plane
                radiusHandleAngle = scala.math.atan2(avlY - dY, avlZ - dZ).toFloat
                selectedProfilePoint = Some((bodyIdx, pointIdx, x, scala.math.max(0.001f, newRadius)))
              case None =>
                // No snap: use distance from mouse to center
                projectModelToScreen(mx, my, mz) match {
                  case Some((centerScreenX, centerScreenY)) =>
                    val dx = e.getX - centerScreenX
                    val dy = centerScreenY - e.getY  // Invert Y for screen coords
                    val distFromCenter = scala.math.sqrt(dx * dx + dy * dy).toFloat
                    // Convert screen pixels to AVL units
                    val newRadius = scala.math.max(0.001f, distFromCenter * 0.005f / zoom)
                    // Calculate angle from center to mouse
                    radiusHandleAngle = scala.math.atan2(dx, dy).toFloat
                    selectedProfilePoint = Some((bodyIdx, pointIdx, x, newRadius))
                  case None => // Keep current
                }
            }
          }
        }
        lastMouseX = e.getX
        lastMouseY = e.getY
      } else if (isDraggingControl) {
        // Change xhinge based on horizontal mouse movement
        val sensitivity = 0.01f / zoom  // Smaller sensitivity for xhinge (0-1 range)
        val deltaScreenX = (e.getX - lastMouseX) * sensitivity
        selectedControl.foreach { case (surfIdx, secIdx, ctrlIdx, xhinge) =>
          // Clamp xhinge between 0 and 1
          val newXhinge = scala.math.max(0.0f, scala.math.min(1.0f, xhinge + deltaScreenX))
          selectedControl = Some((surfIdx, secIdx, ctrlIdx, newXhinge))
        }
        lastMouseX = e.getX
        lastMouseY = e.getY
      } else if (isDraggingBodyAxisX) {
        // Move body along X axis only
        val sensitivity = 0.1f / zoom / displayScale
        val delta = (e.getX - lastMouseX) * sensitivity
        selectedBody.foreach { case (bodyIdx, dX, dY, dZ) =>
          selectedBody = Some((bodyIdx, dX + delta, dY, dZ))
        }
        lastMouseX = e.getX
        lastMouseY = e.getY
      } else if (isDraggingBodyAxisY) {
        // Move body along Y axis only
        val sensitivity = 0.1f / zoom / displayScale
        val delta = -(e.getY - lastMouseY) * sensitivity  // Inverted for Y
        selectedBody.foreach { case (bodyIdx, dX, dY, dZ) =>
          selectedBody = Some((bodyIdx, dX, dY + delta, dZ))
        }
        lastMouseX = e.getX
        lastMouseY = e.getY
      } else if (isDraggingBodyAxisZ) {
        // Move body along Z axis only
        val sensitivity = 0.1f / zoom / displayScale
        val delta = -(e.getY - lastMouseY) * sensitivity  // Vertical mouse = Z
        selectedBody.foreach { case (bodyIdx, dX, dY, dZ) =>
          selectedBody = Some((bodyIdx, dX, dY, dZ + delta))
        }
        lastMouseX = e.getX
        lastMouseY = e.getY
      } else if (isDraggingBody) {
        // Move body with snap to vertices (center handle)
        val sensitivity = 0.1f / zoom / displayScale
        val deltaScreenX = (e.getX - lastMouseX) * sensitivity
        val deltaScreenY = (e.getY - lastMouseY) * sensitivity
        selectedBody.foreach { case (bodyIdx, dX, dY, dZ) =>
          var newDX = dX + deltaScreenX
          var newDY = dY - deltaScreenY
          var newDZ = dZ
          // Snap to nearest vertex
          findNearestVertexByScreenPos(e.getX, e.getY).foreach { case (vx, vy, vz) =>
            val (avlX, avlY, avlZ) = modelToAvl(vx / displayScale, vy / displayScale, vz / displayScale)
            newDX = avlX
            newDY = avlY
            newDZ = avlZ
          }
          selectedBody = Some((bodyIdx, newDX, newDY, newDZ))
        }
        lastMouseX = e.getX
        lastMouseY = e.getY
      } else if (isDraggingSection) {
        // Move section based on mouse movement
        val sensitivity = 0.1f / zoom / displayScale
        val deltaScreenX = (e.getX - lastMouseX) * sensitivity
        val deltaScreenY = (e.getY - lastMouseY) * sensitivity
        selectedSection.foreach { case (surfIdx, secIdx, xle, yle, zle, chord) =>
          // Correct axis mapping with inverted signs
          var newXle = xle + deltaScreenX  // Mouse left = AVL X+
          var newYle = yle - deltaScreenY  // Mouse down = AVL Y+
          var newZle = zle
          // Snap to nearest vertex based on screen projection
          findNearestVertexByScreenPos(e.getX, e.getY).foreach { case (vx, vy, vz) =>
            val (avlX, avlY, avlZ) = modelToAvl(vx / displayScale, vy / displayScale, vz / displayScale)
            newXle = avlX
            newYle = avlY
            newZle = avlZ
          }
          selectedSection = Some((surfIdx, secIdx, newXle, newYle, newZle, chord))
        }
        lastMouseX = e.getX
        lastMouseY = e.getY
      } else if (isDraggingChord) {
        // Change chord based on mouse movement (vertical = chord change)
        val sensitivity = 0.1f / zoom / displayScale
        val deltaScreenY = (e.getY - lastMouseY) * sensitivity
        selectedSection.foreach { case (surfIdx, secIdx, xle, yle, zle, chord) =>
          val newChord = scala.math.max(0.01f, chord - deltaScreenY)  // Mouse up = larger chord
          selectedSection = Some((surfIdx, secIdx, xle, yle, zle, newChord))
        }
        lastMouseX = e.getX
        lastMouseY = e.getY
      } else if (isDragging) {
        rotationY += (e.getX - lastMouseX) * 0.5f
        rotationX += (e.getY - lastMouseY) * 0.5f
        lastMouseX = e.getX
        lastMouseY = e.getY
      } else if (isPanning) {
        panX += (e.getX - lastMouseX) * 0.5f
        panY -= (e.getY - lastMouseY) * 0.5f
        lastMouseX = e.getX
        lastMouseY = e.getY
      }
    }
  })

  glCanvas.addMouseWheelListener(new java.awt.event.MouseWheelListener {
    override def mouseWheelMoved(e: java.awt.event.MouseWheelEvent): Unit = {
      val rotation = -e.getWheelRotation
      zoom *= (1.0f + rotation * 0.1f)
      if (zoom < 0.1f) zoom = 0.1f
      if (zoom > 10.0f) zoom = 10.0f
    }
  })

  // Handle resize to update AWT frame size
  awtComposite.addControlListener(new ControlAdapter {
    override def controlResized(e: ControlEvent): Unit = {
      val size = awtComposite.getSize
      java.awt.EventQueue.invokeLater(new Runnable {
        override def run(): Unit = {
          awtFrame.setSize(size.x, size.y)
          awtFrame.validate()
          glCanvas.setSize(size.x, size.y)
        }
      })
    }
  })

  // Start animator at 30 FPS after a short delay to ensure proper initialization
  private val animator = new FPSAnimator(glCanvas, 30, true)

  // Delay start to ensure AWT frame is properly realized
  new Thread(new Runnable {
    override def run(): Unit = {
      Thread.sleep(500)
      java.awt.EventQueue.invokeLater(new Runnable {
        override def run(): Unit = {
          awtFrame.validate()
          awtFrame.repaint()
          glCanvas.repaint()
          animator.start()
        }
      })
    }
  }).start()

  // Clean up on dispose
  addDisposeListener(new DisposeListener {
    override def widgetDisposed(e: DisposeEvent): Unit = {
      animator.stop()
      awtFrame.dispose()
    }
  })

  def loadModel(filePath: String): Boolean = {
    AC3DLoader.load(filePath) match {
      case Some(m) =>
        model = Some(m)
        val (verts, cols, _) = AC3DLoader.getTriangles(m)
        vertices = verts
        colors = cols
        calculateBounds()
        zoomToFit()
        true
      case None =>
        model = None
        vertices = Array()
        colors = Array()
        false
    }
  }

  def zoomToFit(): Unit = {
    if (vertices.isEmpty) return
    panX = 0.0f
    panY = 0.0f
    rotationX = 20.0f
    rotationY = 45.0f

    // Calculate projected bounding box after rotation
    val radX = scala.math.toRadians(rotationX)
    val radY = scala.math.toRadians(rotationY)
    val cosX = scala.math.cos(radX).toFloat
    val sinX = scala.math.sin(radX).toFloat
    val cosY = scala.math.cos(radY).toFloat
    val sinY = scala.math.sin(radY).toFloat

    // Get the 8 corners of the bounding box
    val corners = Array(
      (modelMinX, modelMinY, modelMinZ),
      (modelMaxX, modelMinY, modelMinZ),
      (modelMinX, modelMaxY, modelMinZ),
      (modelMaxX, modelMaxY, modelMinZ),
      (modelMinX, modelMinY, modelMaxZ),
      (modelMaxX, modelMinY, modelMaxZ),
      (modelMinX, modelMaxY, modelMaxZ),
      (modelMaxX, modelMaxY, modelMaxZ)
    )

    var projMinX = Float.MaxValue
    var projMaxX = Float.MinValue
    var projMinY = Float.MaxValue
    var projMaxY = Float.MinValue

    // Project each corner after rotation
    for ((x, y, z) <- corners) {
      // Center the point
      val cx = (x - centerX) * modelScale
      val cy = (y - centerY) * modelScale
      val cz = (z - centerZ) * modelScale

      // Rotate around Y axis first
      val x1 = cx * cosY + cz * sinY
      val z1 = -cx * sinY + cz * cosY

      // Then rotate around X axis
      val y2 = cy * cosX - z1 * sinX

      // Project to screen (orthographic, so just use x1 and y2)
      projMinX = scala.math.min(projMinX, x1)
      projMaxX = scala.math.max(projMaxX, x1)
      projMinY = scala.math.min(projMinY, y2)
      projMaxY = scala.math.max(projMaxY, y2)
    }

    // Calculate required zoom to fit projected size in view
    // View shows 300/zoom units total (from -150/zoom to +150/zoom)
    val projWidth = projMaxX - projMinX
    val projHeight = projMaxY - projMinY
    val maxProjSize = scala.math.max(projWidth, projHeight)

    if (maxProjSize > 0) {
      zoom = 290.0f / maxProjSize
    } else {
      zoom = 1.0f
    }
  }

  def clearModel(): Unit = {
    model = None
    vertices = Array()
    colors = Array()
  }

  def getViewAngles: (Float, Float) = (rotationX, rotationY)

  def setAvlSurfaces(surfaces: Array[(Array[SectionData], Boolean)]): Unit = {
    avlSurfaces = surfaces
  }

  def setAvlBodies(bodies: Array[BodyData]): Unit = {
    avlBodies = bodies
  }

  /** Collision points (wheels) in AVL coordinates, drawn as markers so they can be placed
    * visually instead of by typing coordinates into the properties table. */
  def setCollisionPoints(points: Array[(Float, Float, Float)]): Unit = {
    collisionPoints = points
  }

  def setSelectedCollisionPoint(index: Int): Unit = {
    selectedCollisionPoint = Some(index)
  }

  def clearSelectedCollisionPoint(): Unit = {
    selectedCollisionPoint = None
  }

  /** Called on mouse release with the dragged point's index and its new AVL coordinates. */
  def setCollisionPointUpdateCallback(callback: (Int, Float, Float, Float) => Unit): Unit = {
    collisionPointUpdateCallback = Some(callback)
  }

  /** Every mass the model has a position for, as (x, y, z, kg) in AVL coordinates. They decide the
    * centre of gravity, so they are drawn where they are and the selected one can be moved.
    *
    * `mirrors` pairs each mass with the index of its twin and the plane they mirror about, or
    * (-1, 0) when it has none. */
  def setMassPoints(points: Array[(Float, Float, Float, Float, Boolean)],
                    mirrors: Array[(Int, Float)] = Array()): Unit = {
    massPoints = points
    massMirrors = mirrors
  }

  def setSelectedMassPoint(index: Int): Unit = {
    selectedMassPoint = Some(index)
  }

  def clearSelectedMassPoint(): Unit = {
    selectedMassPoint = None
  }

  /** Called on mouse release with the dragged mass's index and its new AVL coordinates. */
  /**
   * The shapes to draw around the mass points. Sizes are in the model's length unit, like every other
   * coordinate here, and each shape names the mass point it is centred on.
   */
  def setComponentShapes(shapes: Array[com.abajar.avleditor.mass.ComponentShape]): Unit = {
    componentShapes = shapes
    glCanvas.repaint()
  }

  def setShapeSizeUpdateCallback(callback: (Int, Float, Float, Float) => Unit): Unit = {
    shapeSizeUpdateCallback = Some(callback)
  }

  def setMassPointUpdateCallback(callback: (Int, Float, Float, Float) => Unit): Unit = {
    massPointUpdateCallback = Some(callback)
  }

  def setScale(scale: Float): Unit = {
    displayScale = scale
    if (vertices.nonEmpty) updateInfoLabel()
  }

  def setShowDimensions(show: Boolean): Unit = {
    showDimensions = show
  }

  def setAxisMapping(xAxis: String, yAxis: String, zAxis: String): Unit = {
    avlXAxis = xAxis
    avlYAxis = yAxis
    avlZAxis = zAxis
  }

  def setShowReferenceLine(show: Boolean): Unit = {
    showReferenceLine = show
  }

  // Transform AVL coordinates to model coordinates based on axis mapping
  // avlXAxis says which MODEL axis AVL-X maps to (e.g., "Y" means AVL-X -> Model-Y)
  private def avlToModel(avlX: Float, avlY: Float, avlZ: Float): (Float, Float, Float) = {
    var modelX = 0f
    var modelY = 0f
    var modelZ = 0f

    def assignToModel(avlValue: Float, modelAxis: String): Unit = {
      val normalized = modelAxis.trim.toUpperCase
      val sign = if (normalized.startsWith("-")) -1f else 1f
      val axis = normalized.replace("-", "")
      axis match {
        case "X" => modelX = avlValue * sign
        case "Y" => modelY = avlValue * sign
        case "Z" => modelZ = avlValue * sign
        case _ =>
      }
    }

    assignToModel(avlX, avlXAxis)  // AVL X goes to model axis specified by avlXAxis
    assignToModel(avlY, avlYAxis)  // AVL Y goes to model axis specified by avlYAxis
    assignToModel(avlZ, avlZAxis)  // AVL Z goes to model axis specified by avlZAxis

    (modelX, modelY, modelZ)
  }

  // Transform model coordinates to AVL coordinates (inverse of avlToModel)
  private def modelToAvl(modelX: Float, modelY: Float, modelZ: Float): (Float, Float, Float) = {
    var avlX = 0f
    var avlY = 0f
    var avlZ = 0f

    def extractFromModel(modelAxis: String): Float = {
      val normalized = modelAxis.trim.toUpperCase
      val sign = if (normalized.startsWith("-")) -1f else 1f
      val axis = normalized.replace("-", "")
      axis match {
        case "X" => modelX * sign
        case "Y" => modelY * sign
        case "Z" => modelZ * sign
        case _ => 0f
      }
    }

    avlX = extractFromModel(avlXAxis)
    avlY = extractFromModel(avlYAxis)
    avlZ = extractFromModel(avlZAxis)

    (avlX, avlY, avlZ)
  }

  def setSelectedSection(surfaceIdx: Int, sectionIdx: Int, x: Float, y: Float, z: Float, chord: Float): Unit = {
    selectedSection = Some((surfaceIdx, sectionIdx, x, y, z, chord))
  }

  def clearSelectedSection(): Unit = {
    selectedSection = None
  }

  def setSectionUpdateCallback(callback: (Int, Int, Float, Float, Float, Float) => Unit): Unit = {
    sectionUpdateCallback = Some(callback)
  }

  def setSelectedControl(surfaceIdx: Int, sectionIdx: Int, controlIdx: Int, xhinge: Float): Unit = {
    selectedControl = Some((surfaceIdx, sectionIdx, controlIdx, xhinge))
  }

  def clearSelectedControl(): Unit = {
    selectedControl = None
  }

  def setControlUpdateCallback(callback: (Int, Int, Int, Float) => Unit): Unit = {
    controlUpdateCallback = Some(callback)
  }

  def setSelectedBody(bodyIdx: Int, dX: Float, dY: Float, dZ: Float): Unit = {
    selectedBody = Some((bodyIdx, dX, dY, dZ))
  }

  def clearSelectedBody(): Unit = {
    selectedBody = None
  }

  def setBodyUpdateCallback(callback: (Int, Float, Float, Float) => Unit): Unit = {
    bodyUpdateCallback = Some(callback)
  }

  def setSelectedProfilePoint(bodyIdx: Int, pointIdx: Int, x: Float, radius: Float): Unit = {
    selectedProfilePoint = Some((bodyIdx, pointIdx, x, radius))
  }

  def clearSelectedProfilePoint(): Unit = {
    selectedProfilePoint = None
  }

  def setProfilePointUpdateCallback(callback: (Int, Int, Float, Float) => Unit): Unit = {
    profilePointUpdateCallback = Some(callback)
  }

  private def getClosestHandle(mouseX: Int, mouseY: Int): String = {
    selectedSection.map { case (_, _, xle, yle, zle, chord) =>
      val leadingPos = projectToScreen(xle, yle, zle)
      val trailingPos = projectToScreen(xle + chord, yle, zle)

      (leadingPos, trailingPos) match {
        case (Some((lx, ly)), Some((tx, ty))) =>
          val distLeading = (mouseX - lx) * (mouseX - lx) + (mouseY - ly) * (mouseY - ly)
          val distTrailing = (mouseX - tx) * (mouseX - tx) + (mouseY - ty) * (mouseY - ty)
          // If closer to trailing edge, use trailing for chord
          // Otherwise use leading for position
          if (distTrailing < distLeading) "trailing"
          else "leading"
        case _ => "leading"  // Default to leading edge if projection fails
      }
    }.getOrElse("leading")
  }

  private def getClosestProfilePointHandle(mouseX: Int, mouseY: Int): String = {
    selectedProfilePoint.map { case (bodyIdx, pointIdx, x, radius) =>
      if (bodyIdx < avlBodies.length) {
        val (_, _, dX, dY, dZ, length, _) = avlBodies(bodyIdx)
        // Calculate handle positions exactly as they are drawn
        // Profile X is normalized (0-1), multiply by length to get actual position
        val actualX = x * length
        val (mx, my, mz) = avlToModel(dX + actualX, dY, dZ)
        // Use the same angle calculation as drawing
        val yOffset = radius * scala.math.sin(radiusHandleAngle).toFloat
        val zOffset = radius * scala.math.cos(radiusHandleAngle).toFloat
        val (rxEnd, ryEnd, rzEnd) = avlToModel(dX + actualX, dY + yOffset, dZ + zOffset)

        // Project to screen
        val xHandlePos = projectModelToScreen(mx, my, mz)
        val radiusHandlePos = projectModelToScreen(rxEnd, ryEnd, rzEnd)

        (xHandlePos, radiusHandlePos) match {
          case (Some((xhX, xhY)), Some((rhX, rhY))) =>
            val distX = (mouseX - xhX) * (mouseX - xhX) + (mouseY - xhY) * (mouseY - xhY)
            val distRadius = (mouseX - rhX) * (mouseX - rhX) + (mouseY - rhY) * (mouseY - rhY)
            // Select the closer handle
            if (distRadius < distX) "radius" else "x"
          case (Some(_), None) => "x"  // Only X handle visible
          case (None, Some(_)) => "radius"  // Only radius handle visible
          case _ => "x"
        }
      } else "x"
    }.getOrElse("x")
  }

  // Calculate handle offset based on body size
  private def getBodyAxisHandleOffset(length: Float, profile: Array[(Float, Float)]): Float = {
    val maxRadius = if (profile.nonEmpty) profile.map(_._2).max else 0.03f
    // Offset is half the length or twice the radius, whichever is larger
    scala.math.max(length / 2, maxRadius * 2)
  }

  private def getClosestBodyAxisHandle(mouseX: Int, mouseY: Int): String = {
    selectedBody.map { case (bodyIdx, dX, dY, dZ) =>
      if (bodyIdx < avlBodies.length) {
        val (_, profile, _, _, _, length, ydupl) = avlBodies(bodyIdx)
        if (profile.nonEmpty) {
          // Profile X is normalized (0-1), so center is always at length/2
          val bodyCenterX = length / 2
          val handleOffset = getBodyAxisHandleOffset(length, profile)

          // Calculate distances to mouse
          def distSq(screen: Option[(Int, Int)]): Float = screen match {
            case Some((sx, sy)) =>
              val dx = mouseX - sx
              val dy = mouseY - sy
              (dx * dx + dy * dy).toFloat
            case None => Float.MaxValue
          }

          // Calculate distances for original body
          def getDistances(bodyDY: Float): (Float, Float, Float, Float) = {
            val centerAvl = (dX + bodyCenterX, bodyDY, dZ)
            val xHandleAvl = (dX + bodyCenterX + handleOffset, bodyDY, dZ)
            val yHandleAvl = (dX + bodyCenterX, bodyDY + handleOffset, dZ)
            val zHandleAvl = (dX + bodyCenterX, bodyDY, dZ + handleOffset)

            val centerScreen = projectToScreen(centerAvl._1, centerAvl._2, centerAvl._3)
            val xScreen = projectToScreen(xHandleAvl._1, xHandleAvl._2, xHandleAvl._3)
            val yScreen = projectToScreen(yHandleAvl._1, yHandleAvl._2, yHandleAvl._3)
            val zScreen = projectToScreen(zHandleAvl._1, zHandleAvl._2, zHandleAvl._3)

            (distSq(centerScreen), distSq(xScreen), distSq(yScreen), distSq(zScreen))
          }

          val (distCenter, distX, distY, distZ) = getDistances(dY)

          // Also consider mirrored body handles if YDUPLICATE is set
          val (mirrorDistCenter, mirrorDistX, mirrorDistY, mirrorDistZ) = ydupl match {
            case Some(yDup) =>
              val mirroredDY = 2 * yDup - dY
              getDistances(mirroredDY)
            case None =>
              (Float.MaxValue, Float.MaxValue, Float.MaxValue, Float.MaxValue)
          }

          // Find minimum distance across both original and mirrored handles
          val allDistances = Seq(
            (distCenter, "center"), (distX, "x"), (distY, "y"), (distZ, "z"),
            (mirrorDistCenter, "center"), (mirrorDistX, "x"), (mirrorDistY, "y"), (mirrorDistZ, "z")
          )
          allDistances.minBy(_._1)._2
        } else "center"
      } else "center"
    }.getOrElse("center")
  }

  /**
   * The aircraft's largest dimension in AVL units, taken from the AVL geometry rather than from the
   * mesh: the geometry is what the masses are positioned against, and a model may have no mesh at
   * all. Used to size the mass handles, so they are grabbable on a 20 cm model and not vast on a
   * 20 m one.
   */
  private def avlExtent(): Float = {
    var largest = 0f
    avlSurfaces.foreach { case (sections, symmetric) =>
      sections.foreach { case (xle, yle, zle, chord, _, _, _) =>
        largest = scala.math.max(largest, xle + chord)
        largest = scala.math.max(largest, scala.math.abs(yle) * (if (symmetric) 2f else 1f))
      }
    }
    avlBodies.foreach { case (_, _, dX, _, _, length, _) =>
      largest = scala.math.max(largest, dX + length)
    }
    if (largest > 0f) largest else 1.0f
  }

  private def massHandleOffset(): Float = avlExtent() * 0.12f

  /** The three handle positions of a mass marker, in AVL coordinates. */
  private def massAxisHandles(x: Float, y: Float, z: Float): Seq[(Float, Float, Float)] = {
    val offset = massHandleOffset()
    Seq((x + offset, y, z), (x, y + offset, z), (x, y, z + offset))
  }

  /** The shape drawn around the selected mass point, if that component states one. */
  private def selectedShapeIndex: Option[Int] =
    selectedMassPoint.flatMap { point =>
      componentShapes.indexWhere(_.pointIndex == point) match {
        case -1 => None
        case index => Some(index)
      }
    }

  /**
   * Where the size handles sit: at the centre of the three faces the sizes grow towards.
   *
   * Only for a shape the model lets the user size. The propeller's disc has none: its diameter decides the
   * exported thrust, and dragging that by eye would be editing the propulsion rather than placing a part.
   */
  private def shapeSizeHandles(shapeIndex: Int): Seq[(Int, (Float, Float, Float))] = {
    val shape = componentShapes(shapeIndex)
    if (shape.pointIndex >= massPoints.length) return Nil
    val (x, y, z, _, _) = massPoints(shape.pointIndex)
    // One per axis the shape allows, and no more: a handle for a size that cannot change would be a lie.
    Seq(
      (0, (x + shape.sizeX / 2f, y, z)),
      (1, (x, y + shape.sizeY / 2f, z)),
      (2, (x, y, z + shape.sizeZ / 2f))
    ).filter { case (axis, _) => shape.resizable(axis) }
  }

  private def getClosestMassAxisHandle(mouseX: Int, mouseY: Int): String = {
    selectedMassPoint.filter(_ < massPoints.length).map { index =>
      val (x, y, z, _, _) = massPoints(index)
      val distances = massAxisHandles(x, y, z).zip(Seq("x", "y", "z")).map { case ((hx, hy, hz), axis) =>
        val distance = projectToScreen(hx, hy, hz) match {
          case Some((sx, sy)) =>
            val dx = mouseX - sx
            val dy = mouseY - sy
            (dx * dx + dy * dy).toFloat
          case None => Float.MaxValue
        }
        (distance, axis)
      }
      distances.minBy(_._1)._2
    }.getOrElse("x")
  }

  /**
   * Which handle of the selected mass the pointer is nearest: one of the three position arrows ("x", "y",
   * "z") or, when the component states a shape it can be sized by, one of the three size handles ("sx",
   * "sy", "sz"). Whichever is closest on screen wins, so the two sets can share the same selection without
   * a mode to switch between them.
   */
  private def getClosestMassHandle(mouseX: Int, mouseY: Int): String = {
    def screenDistance(handle: (Float, Float, Float)): Float =
      projectToScreen(handle._1, handle._2, handle._3) match {
        case Some((sx, sy)) =>
          val dx = mouseX - sx
          val dy = mouseY - sy
          (dx * dx + dy * dy).toFloat
        case None => Float.MaxValue
      }

    selectedMassPoint.filter(_ < massPoints.length).map { index =>
      val (x, y, z, _, _) = massPoints(index)
      val position = massAxisHandles(x, y, z).zip(Seq("x", "y", "z"))
      val sizes = selectedShapeIndex.toSeq.flatMap(shapeSizeHandles)
        .map { case (axis, handle) => (handle, "s" + axis) }
      (position ++ sizes).map { case (handle, name) => (screenDistance(handle), name) }.minBy(_._1)._2
    }.getOrElse("x")
  }

  /**
   * How far a mass moves along one axis for a mouse movement, by projecting the mouse movement onto
   * that axis's direction on screen. The handle offset is the yardstick: it is one known length
   * along the axis, so its projected length converts pixels back into AVL units.
   */
  private def massAxisDelta(x: Float, y: Float, z: Float, axis: Int, mouseDx: Int, mouseDy: Int): Float = {
    val offset = massHandleOffset()
    val handle = massAxisHandles(x, y, z)(axis)
    (projectToScreen(x, y, z), projectToScreen(handle._1, handle._2, handle._3)) match {
      case (Some((originX, originY)), Some((handleX, handleY))) =>
        Viewer3DGL.axisDragDelta(handleX - originX, handleY - originY, mouseDx, mouseDy, offset)
      case _ => 0f
    }
  }

  // Find nearest vertex based on screen position (2D projection)
  private def findNearestVertexByScreenPos(mouseX: Int, mouseY: Int): Option[(Float, Float, Float)] = {
    val verts = vertices
    if (verts.isEmpty) return None

    var minDistSq = Float.MaxValue
    var nearestX = 0f
    var nearestY = 0f
    var nearestZ = 0f

    var i = 0
    while (i < verts.length - 2) {
      val vx = verts(i)
      val vy = verts(i + 1)
      val vz = verts(i + 2)

      // Project vertex to screen (model coordinates)
      projectModelToScreen(vx / displayScale, vy / displayScale, vz / displayScale).foreach { case (sx, sy) =>
        val dx = mouseX - sx
        val dy = mouseY - sy
        val distSq = dx * dx + dy * dy
        if (distSq < minDistSq) {
          minDistSq = distSq
          nearestX = vx
          nearestY = vy
          nearestZ = vz
        }
      }
      i += 3
    }

    // Snap threshold in pixels (15 pixels)
    val snapThresholdPx = 15
    if (minDistSq < snapThresholdPx * snapThresholdPx) {
      Some((nearestX, nearestY, nearestZ))
    } else {
      None
    }
  }

  // Project AVL coordinates to screen coordinates
  private def projectToScreen(avlX: Float, avlY: Float, avlZ: Float): Option[(Int, Int)] = {
    val (mx, my, mz) = avlToModel(avlX, avlY, avlZ)
    projectModelToScreen(mx, my, mz)
  }

  // Project model coordinates to screen coordinates
  private def projectModelToScreen(modelX: Float, modelY: Float, modelZ: Float): Option[(Int, Int)] = {
    val canvasWidth = glCanvas.getWidth
    val canvasHeight = glCanvas.getHeight
    if (canvasWidth == 0 || canvasHeight == 0) return None

    val worldX = (modelX * displayScale - centerX) * modelScale
    val worldY = (modelY * displayScale - centerY) * modelScale
    val worldZ = (modelZ * displayScale - centerZ) * modelScale

    // OpenGL applies transforms in reverse order, so rotateY then rotateX
    val radX = scala.math.toRadians(rotationX)
    val radY = scala.math.toRadians(rotationY)
    val cosX = scala.math.cos(radX).toFloat
    val sinX = scala.math.sin(radX).toFloat
    val cosY = scala.math.cos(radY).toFloat
    val sinY = scala.math.sin(radY).toFloat

    // First rotate around Y axis (to match OpenGL's reverse order)
    val rx1 = worldX * cosY + worldZ * sinY
    val rz1 = -worldX * sinY + worldZ * cosY
    // Then rotate around X axis
    val ry2 = worldY * cosX - rz1 * sinX
    val rz2 = worldY * sinX + rz1 * cosX

    val viewX = rx1
    val viewY = ry2

    // Apply pan
    val viewXPanned = viewX + panX
    val viewYPanned = viewY + panY

    // Ortho projection: size = 150 / zoom
    val size = 150.0f / zoom
    val aspect = canvasWidth.toFloat / canvasHeight.toFloat

    // Map to screen coordinates
    val screenX = (canvasWidth / 2.0f + viewXPanned / (size * aspect) * canvasWidth / 2.0f).toInt
    val screenY = (canvasHeight / 2.0f - viewYPanned / size * canvasHeight / 2.0f).toInt

    Some((screenX, screenY))
  }

  private def isClickOnHandle(mouseX: Int, mouseY: Int): Boolean = {
    selectedSection.exists { case (_, _, xle, yle, zle, _) =>
      // Project the 3D point to 2D screen coordinates
      val canvasWidth = glCanvas.getWidth
      val canvasHeight = glCanvas.getHeight
      if (canvasWidth == 0 || canvasHeight == 0) return false

      // Apply the same transformations as in render
      val worldX = (yle * displayScale - centerX) * modelScale
      val worldY = (xle * displayScale - centerY) * modelScale
      val worldZ = (zle * displayScale - centerZ) * modelScale

      // Apply rotation
      val radX = scala.math.toRadians(rotationX)
      val radY = scala.math.toRadians(rotationY)
      val cosX = scala.math.cos(radX)
      val sinX = scala.math.sin(radX)
      val cosY = scala.math.cos(radY)
      val sinY = scala.math.sin(radY)

      // Rotate around Y axis first, then X
      val rx1 = worldX * cosY - worldZ * sinY
      val rz1 = worldX * sinY + worldZ * cosY
      val ry1 = worldY * cosX - rz1 * sinX

      // Apply zoom and pan
      val screenX = (rx1 * zoom * 20 + panX + canvasWidth / 2).toInt
      val screenY = (canvasHeight / 2 - ry1 * zoom * 20 + panY).toInt

      // Check if click is within 15 pixels of handle
      val dx = mouseX - screenX
      val dy = mouseY - screenY
      dx * dx + dy * dy < 225  // 15^2
    }
  }

  private def calculateBounds(): Unit = {
    if (vertices.isEmpty) return

    var minX = Float.MaxValue
    var minY = Float.MaxValue
    var minZ = Float.MaxValue
    var maxX = Float.MinValue
    var maxY = Float.MinValue
    var maxZ = Float.MinValue

    var i = 0
    while (i < vertices.length) {
      val x = vertices(i)
      val y = vertices(i + 1)
      val z = vertices(i + 2)
      minX = scala.math.min(minX, x)
      minY = scala.math.min(minY, y)
      minZ = scala.math.min(minZ, z)
      maxX = scala.math.max(maxX, x)
      maxY = scala.math.max(maxY, y)
      maxZ = scala.math.max(maxZ, z)
      i += 3
    }

    centerX = (minX + maxX) / 2
    centerY = (minY + maxY) / 2
    centerZ = (minZ + maxZ) / 2

    modelMinX = minX
    modelMaxX = maxX
    modelMinY = minY
    modelMaxY = maxY
    modelMinZ = minZ
    modelMaxZ = maxZ

    val sizeX = maxX - minX
    val sizeY = maxY - minY
    val sizeZ = maxZ - minZ
    val maxSize = scala.math.max(scala.math.max(sizeX, sizeY), sizeZ)
    modelScale = if (maxSize > 0) 100.0f / maxSize else 1.0f

    // Update info label with dimensions
    updateInfoLabel()
  }

  private def updateInfoLabel(): Unit = {
    if (isDisposed) return
    val wingspan = (modelMaxZ - modelMinZ) / displayScale
    val length = (modelMaxY - modelMinY) / displayScale
    getDisplay.asyncExec(new Runnable {
      override def run(): Unit = {
        if (!isDisposed && !infoLabel.isDisposed) {
          infoLabel.setText(f"Wingspan: $wingspan%.2f | Length: $length%.2f")
        }
      }
    })
  }

  private def render(drawable: GLAutoDrawable): Unit = {
    val verts = vertices  // Local copy for thread safety
    val cols = colors
    val gl = drawable.getGL.getGL2
    val width = drawable.getSurfaceWidth
    val height = drawable.getSurfaceHeight

    // Set up projection
    gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION)
    gl.glLoadIdentity()
    val aspect = width.toFloat / scala.math.max(height, 1).toFloat
    val size = 150.0f / zoom
    gl.glOrtho(-size * aspect, size * aspect, -size, size, -1000, 1000)

    // Set up modelview
    gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW)
    gl.glLoadIdentity()

    gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT)

    // Camera transformations
    gl.glTranslatef(panX, panY, 0)
    gl.glRotatef(rotationX, 1, 0, 0)
    gl.glRotatef(rotationY, 0, 1, 0)

    if (verts.nonEmpty) {
      gl.glPushMatrix()
      gl.glScalef(modelScale, modelScale, modelScale)
      gl.glTranslatef(-centerX, -centerY, -centerZ)

      // Set polygon mode (wireframe or solid)
      // GL_LINE = 0x1B01, GL_FILL = 0x1B02
      if (wireframeMode) {
        gl.glPolygonMode(GL.GL_FRONT_AND_BACK, 0x1B01)
        gl.glDisable(GLLightingFunc.GL_LIGHTING)
      }

      // Draw triangles
      gl.glBegin(GL.GL_TRIANGLES)
      var i = 0
      while (i < verts.length - 8) {
        val colorIdx = i / 3 * 4
        if (colorIdx + 2 < cols.length) {
          gl.glColor3f(cols(colorIdx), cols(colorIdx + 1), cols(colorIdx + 2))
        } else {
          gl.glColor3f(0.7f, 0.7f, 0.7f)
        }

        // Calculate normal
        val v0x = verts(i); val v0y = verts(i + 1); val v0z = verts(i + 2)
        val v1x = verts(i + 3); val v1y = verts(i + 4); val v1z = verts(i + 5)
        val v2x = verts(i + 6); val v2y = verts(i + 7); val v2z = verts(i + 8)

        val ux = v1x - v0x; val uy = v1y - v0y; val uz = v1z - v0z
        val vx = v2x - v0x; val vy = v2y - v0y; val vz = v2z - v0z

        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx

        val len = scala.math.sqrt(nx * nx + ny * ny + nz * nz).toFloat
        if (len > 0) { nx /= len; ny /= len; nz /= len }
        gl.glNormal3f(nx, ny, nz)

        gl.glVertex3f(verts(i), verts(i + 1), verts(i + 2))
        i += 3
      }
      gl.glEnd()

      // Restore polygon mode and lighting
      if (wireframeMode) {
        gl.glPolygonMode(GL.GL_FRONT_AND_BACK, 0x1B02)  // GL_FILL
        gl.glEnable(GLLightingFunc.GL_LIGHTING)
      }

      gl.glPopMatrix()

      if (showDimensions) drawDimensionLines(gl, drawable)
      if (showReferenceLine) drawReferenceLine(gl)
      if (showAvlSurfaces && avlSurfaces.nonEmpty) drawAvlSurfaces(gl)
      if (showAvlSurfaces && avlBodies.nonEmpty) drawAvlBodies(gl)
      if (selectedSection.isDefined) drawSelectedSectionHandle(gl)
      if (selectedControl.isDefined) drawSelectedControlHandle(gl)
      if (selectedBody.isDefined) drawSelectedBodyHandle(gl)
      if (selectedProfilePoint.isDefined) drawSelectedProfilePointHandle(gl)
      if (collisionPoints.nonEmpty) drawCollisionPoints(gl)
      if (massPoints.nonEmpty) drawMassPoints(gl)
      if (componentShapes.nonEmpty) drawComponentShapes(gl)
      if (selectedMassPoint.isDefined) drawSelectedMassHandles(gl)
      if (selectedMassPoint.isDefined) drawShapeSizeHandles(gl)
    }

    gl.glFlush()
  }

  private def drawAvlSurfaces(gl: GL2): Unit = {
    gl.glDisable(GLLightingFunc.GL_LIGHTING)
    gl.glDisable(GL.GL_DEPTH_TEST)
    gl.glLineWidth(2.0f)

    gl.glPushMatrix()
    gl.glScalef(modelScale, modelScale, modelScale)
    gl.glTranslatef(-centerX, -centerY, -centerZ)

    for ((surface, symmetric) <- avlSurfaces) {
      if (surface.length >= 2) {
        // Draw original surface
        drawSingleSurface(gl, surface)

        // Draw symmetric surface (mirror across Y=0 plane) if symmetric is enabled
        if (symmetric) {
          val mirroredSurface = surface.map { case (xle, yle, zle, chord, ainc, naca, controls) =>
            (xle, -yle, zle, chord, ainc, naca, controls)
          }
          drawSingleSurface(gl, mirroredSurface)
        }
      }
    }

    gl.glPopMatrix()
    gl.glLineWidth(1.0f)
    gl.glEnable(GL.GL_DEPTH_TEST)
    gl.glEnable(GLLightingFunc.GL_LIGHTING)
  }

  private def drawSingleSurface(gl: GL2, surface: Array[SectionData]): Unit = {
    import scala.math.{toRadians, cos, sin, sqrt, atan2}

    gl.glColor3f(0.0f, 1.0f, 0.0f)

    // Calculate span direction from first to last section
    val (_, y0, z0, _, _, _, _) = surface.head
    val (_, y1, z1, _, _, _, _) = surface.last
    val spanY = y1 - y0
    val spanZ = z1 - z0

    // Determine if surface is more vertical or horizontal based on absolute span components
    // Use absolute values to avoid flipping the profile based on span direction
    val absSpanY = scala.math.abs(spanY)
    val absSpanZ = scala.math.abs(spanZ)

    // Angle in Y-Z plane: 0 = horizontal (thickness in Z), π/2 = vertical (thickness in Y)
    val spanAngle = atan2(absSpanZ, absSpanY).toFloat

    // Generate all NACA profiles for the surface
    val numProfilePoints = 7
    val profiles = surface.map { case (xle, yle, zle, chord, ainc, naca, controls) =>
      val profile = generateNaca4Digit(naca, numProfilePoints)
      val aincRad = toRadians(ainc)
      val cosA = cos(aincRad).toFloat
      val sinA = sin(aincRad).toFloat

      // Transform profile points to 3D coordinates
      // Profile thickness direction is perpendicular to span in Y-Z plane
      val cosSpan = cos(spanAngle).toFloat
      val sinSpan = sin(spanAngle).toFloat

      profile.map { case (px, pz) =>
        val dx = px * chord
        val dz = pz * chord
        // Apply incidence rotation around span axis
        val rotX = dx * cosA - dz * sinA
        val thickness = dx * sinA + dz * cosA
        // Rotate thickness direction to be perpendicular to span
        // For horizontal span (Y): thickness goes in Z
        // For vertical span (Z): thickness goes in -Y
        val rotY = -thickness * sinSpan
        val rotZ = thickness * cosSpan
        avlToModel(xle + rotX, yle + rotY, zle + rotZ)
      }
    }

    // Draw NACA airfoil profile for each section
    for (profile <- profiles) {
      gl.glBegin(GL.GL_LINE_LOOP)
      for ((mx, my, mz) <- profile) {
        gl.glVertex3f(mx * displayScale, my * displayScale, mz * displayScale)
      }
      gl.glEnd()
    }

    // Draw spanwise lines connecting corresponding points between adjacent sections
    if (profiles.length >= 2) {
      for (i <- 0 until profiles.length - 1) {
        val profile1 = profiles(i)
        val profile2 = profiles(i + 1)
        val minLen = scala.math.min(profile1.length, profile2.length)

        for (j <- 0 until minLen) {
          val (mx1, my1, mz1) = profile1(j)
          val (mx2, my2, mz2) = profile2(j)
          gl.glBegin(GL.GL_LINES)
          gl.glVertex3f(mx1 * displayScale, my1 * displayScale, mz1 * displayScale)
          gl.glVertex3f(mx2 * displayScale, my2 * displayScale, mz2 * displayScale)
          gl.glEnd()
        }
      }
    }

    // Draw control surface hinge lines and points
    gl.glLineWidth(4.0f)

    // Helper to set color based on control type
    def setControlColor(ctrlType: Int): Unit = {
      ctrlType match {
        case 0 => gl.glColor3f(0.0f, 0.7f, 1.0f)  // Aileron - cyan/bright blue
        case 1 => gl.glColor3f(1.0f, 0.0f, 1.0f)  // Elevator - magenta
        case 2 => gl.glColor3f(1.0f, 0.2f, 0.2f)  // Rudder - bright red
        case _ => gl.glColor3f(1.0f, 0.6f, 0.0f)  // Unknown - orange
      }
    }

    // First pass: draw hinge points and hinge-to-trailing-edge lines for ALL controls
    gl.glPointSize(10.0f)
    for (section <- surface) {
      val (xle, yle, zle, chord, _, _, controls) = section
      for (ctrl <- controls) {
        val (_, xhinge, _, _, ctrlType) = ctrl
        setControlColor(ctrlType)

        val hingeX = xle + chord * xhinge
        val trailingX = xle + chord  // Trailing edge position
        val (mxH, myH, mzH) = avlToModel(hingeX, yle, zle)
        val (mxT, myT, mzT) = avlToModel(trailingX, yle, zle)

        // Draw hinge point
        gl.glBegin(GL.GL_POINTS)
        gl.glVertex3f(mxH * displayScale, myH * displayScale, mzH * displayScale)
        gl.glEnd()

        // Draw line from hinge to trailing edge
        gl.glBegin(GL.GL_LINES)
        gl.glVertex3f(mxH * displayScale, myH * displayScale, mzH * displayScale)
        gl.glVertex3f(mxT * displayScale, myT * displayScale, mzT * displayScale)
        gl.glEnd()
      }
    }

    // Second pass: draw control surface quadrilaterals between adjacent sections
    for (i <- 0 until surface.length - 1) {
      val (xle1, yle1, zle1, chord1, _, _, controls1) = surface(i)
      val (xle2, yle2, zle2, chord2, _, _, controls2) = surface(i + 1)

      for (ctrl1 <- controls1) {
        val (name1, xhinge1, _, _, ctrlType1) = ctrl1
        controls2.find(_._1 == name1).foreach { ctrl2 =>
          val (_, xhinge2, _, _, _) = ctrl2
          setControlColor(ctrlType1)

          // Calculate hinge and trailing edge positions for both sections
          val hingeX1 = xle1 + chord1 * xhinge1
          val hingeX2 = xle2 + chord2 * xhinge2
          val trailX1 = xle1 + chord1
          val trailX2 = xle2 + chord2

          val (mxH1, myH1, mzH1) = avlToModel(hingeX1, yle1, zle1)
          val (mxH2, myH2, mzH2) = avlToModel(hingeX2, yle2, zle2)
          val (mxT1, myT1, mzT1) = avlToModel(trailX1, yle1, zle1)
          val (mxT2, myT2, mzT2) = avlToModel(trailX2, yle2, zle2)

          // Draw hinge line (connecting hinges between sections)
          gl.glBegin(GL.GL_LINES)
          gl.glVertex3f(mxH1 * displayScale, myH1 * displayScale, mzH1 * displayScale)
          gl.glVertex3f(mxH2 * displayScale, myH2 * displayScale, mzH2 * displayScale)
          gl.glEnd()

          // Draw trailing edge line (connecting trailing edges between sections)
          gl.glBegin(GL.GL_LINES)
          gl.glVertex3f(mxT1 * displayScale, myT1 * displayScale, mzT1 * displayScale)
          gl.glVertex3f(mxT2 * displayScale, myT2 * displayScale, mzT2 * displayScale)
          gl.glEnd()
        }
      }
    }
    gl.glPointSize(1.0f)
    gl.glLineWidth(2.0f)
  }

  private def drawAvlBodies(gl: GL2): Unit = {
    if (avlBodies.isEmpty) return

    gl.glDisable(GLLightingFunc.GL_LIGHTING)
    gl.glDisable(GL.GL_DEPTH_TEST)
    gl.glLineWidth(2.0f)

    gl.glPushMatrix()
    gl.glScalef(modelScale, modelScale, modelScale)
    gl.glTranslatef(-centerX, -centerY, -centerZ)

    val numSegments = 16  // Number of segments around the revolution

    for (idx <- avlBodies.indices) {
      val (_, profile, origDX, origDY, origDZ, length, ydupl) = avlBodies(idx)
      if (profile.length >= 2) {
        // Use updated position if this body is selected and being dragged
        val (dX, dY, dZ) = selectedBody match {
          case Some((bodyIdx, sdX, sdY, sdZ)) if bodyIdx == idx =>
            (sdX, sdY, sdZ)
          case _ =>
            (origDX, origDY, origDZ)
        }

        // Draw original body
        drawSingleBody(gl, profile, dX, dY, dZ, length, numSegments)

        // Draw mirrored body if YDUPLICATE is set
        ydupl.foreach { yDup =>
          val mirroredDY = 2 * yDup - dY
          drawSingleBody(gl, profile, dX, mirroredDY, dZ, length, numSegments)
        }
      }
    }

    gl.glPopMatrix()
    gl.glLineWidth(1.0f)
    gl.glEnable(GL.GL_DEPTH_TEST)
    gl.glEnable(GLLightingFunc.GL_LIGHTING)
  }

  private def drawSingleBody(gl: GL2, profile: Array[BodyProfilePoint], dX: Float, dY: Float, dZ: Float, length: Float, numSegments: Int): Unit = {
    import scala.math.{Pi, cos, sin}

    gl.glColor3f(0.8f, 0.5f, 0.2f)  // Orange/brown color for bodies

    // Generate circles at each profile point
    // Profile X is normalized (0-1), multiply by length to get actual size
    // Radius is absolute (not normalized), don't multiply by length
    val circles = profile.map { case (x, radius) =>
      (0 until numSegments).map { i =>
        val angle = 2 * Pi * i / numSegments
        val cy = radius * cos(angle).toFloat
        val cz = radius * sin(angle).toFloat
        // Transform: body X is along AVL X, Y and Z form the circular cross-section
        avlToModel(dX + x * length, dY + cy, dZ + cz)
      }.toArray
    }

    // Draw longitudinal lines (along the body length)
    for (segIdx <- 0 until numSegments) {
      gl.glBegin(GL.GL_LINE_STRIP)
      for (circle <- circles) {
        val (mx, my, mz) = circle(segIdx)
        gl.glVertex3f(mx * displayScale, my * displayScale, mz * displayScale)
      }
      gl.glEnd()
    }

    // Draw circular cross-sections at each profile point
    for (circle <- circles) {
      gl.glBegin(GL.GL_LINE_LOOP)
      for ((mx, my, mz) <- circle) {
        gl.glVertex3f(mx * displayScale, my * displayScale, mz * displayScale)
      }
      gl.glEnd()
    }
  }

  // Generate NACA 4-digit airfoil coordinates
  // Returns array of (x, z) points normalized to chord = 1
  private def generateNaca4Digit(naca: String, numPoints: Int = 30): Array[(Float, Float)] = {
    import scala.math.{Pi, cos, sin, sqrt, atan}

    if (naca == null || naca.isEmpty || naca.length != 4 || !naca.forall(_.isDigit)) {
      // Return simple line if invalid NACA code
      return Array((0f, 0f), (1f, 0f))
    }

    try {
      val m = naca.charAt(0).asDigit / 100.0  // Max camber
      val p = naca.charAt(1).asDigit / 10.0   // Position of max camber
      val t = naca.substring(2).toInt / 100.0 // Thickness

      val points = new scala.collection.mutable.ArrayBuffer[(Float, Float)]()

      // Generate points from trailing edge, along upper surface, to leading edge, along lower surface, back to trailing edge
      for (i <- 0 until numPoints) {
        val beta = Pi * i / (numPoints - 1)
        val x = (1 - cos(beta)) / 2.0  // Cosine spacing for better LE resolution

        // Thickness distribution (NACA formula)
        val yt = 5 * t * (0.2969 * sqrt(x) - 0.1260 * x - 0.3516 * x * x + 0.2843 * x * x * x - 0.1015 * x * x * x * x)

        // Camber line
        val yc = if (p == 0 || m == 0) 0.0
                 else if (x < p) m / (p * p) * (2 * p * x - x * x)
                 else m / ((1 - p) * (1 - p)) * (1 - 2 * p + 2 * p * x - x * x)

        // Camber line slope
        val dyc = if (p == 0 || m == 0) 0.0
                  else if (x < p) 2 * m / (p * p) * (p - x)
                  else 2 * m / ((1 - p) * (1 - p)) * (p - x)

        val theta = atan(dyc)

        // Upper surface
        val xu = x - yt * sin(theta)
        val zu = yc + yt * cos(theta)
        points += ((xu.toFloat, zu.toFloat))
      }

      // Lower surface (reverse direction)
      for (i <- (numPoints - 2) to 1 by -1) {
        val beta = Pi * i / (numPoints - 1)
        val x = (1 - cos(beta)) / 2.0

        val yt = 5 * t * (0.2969 * sqrt(x) - 0.1260 * x - 0.3516 * x * x + 0.2843 * x * x * x - 0.1015 * x * x * x * x)

        val yc = if (p == 0 || m == 0) 0.0
                 else if (x < p) m / (p * p) * (2 * p * x - x * x)
                 else m / ((1 - p) * (1 - p)) * (1 - 2 * p + 2 * p * x - x * x)

        val dyc = if (p == 0 || m == 0) 0.0
                  else if (x < p) 2 * m / (p * p) * (p - x)
                  else 2 * m / ((1 - p) * (1 - p)) * (p - x)

        val theta = atan(dyc)

        // Lower surface
        val xl = x + yt * sin(theta)
        val zl = yc - yt * cos(theta)
        points += ((xl.toFloat, zl.toFloat))
      }

      points.toArray
    } catch {
      case _: Exception => Array((0f, 0f), (1f, 0f))
    }
  }

  private def drawNacaProfile(gl: GL2, xle: Float, yle: Float, zle: Float,
                               chord: Float, ainc: Float, naca: String): Unit = {
    import scala.math.{toRadians, cos, sin}

    val profile = generateNaca4Digit(naca)
    if (profile.length < 3) return

    // Convert incidence angle to radians
    val aincRad = toRadians(ainc)
    val cosA = cos(aincRad).toFloat
    val sinA = sin(aincRad).toFloat

    gl.glBegin(GL.GL_LINE_LOOP)
    for ((px, pz) <- profile) {
      // Scale by chord and apply incidence rotation around leading edge
      // Rotation is around Y axis (spanwise), affecting X and Z
      val dx = px * chord
      val dz = pz * chord
      val rotX = dx * cosA - dz * sinA
      val rotZ = dx * sinA + dz * cosA

      // Transform to AVL coordinates and then to model coordinates
      val (mx, my, mz) = avlToModel(xle + rotX, yle, zle + rotZ)
      gl.glVertex3f(mx * displayScale, my * displayScale, mz * displayScale)
    }
    gl.glEnd()
  }

  private def drawSelectedSectionHandle(gl: GL2): Unit = {
    selectedSection.foreach { case (_, _, xle, yle, zle, chord) =>
      gl.glDisable(GLLightingFunc.GL_LIGHTING)
      gl.glDisable(GL.GL_DEPTH_TEST)

      gl.glPushMatrix()
      gl.glScalef(modelScale, modelScale, modelScale)
      gl.glTranslatef(-centerX, -centerY, -centerZ)

      val (mx1, my1, mz1) = avlToModel(xle, yle, zle)
      val (mx2, my2, mz2) = avlToModel(xle + chord, yle, zle)

      // Draw leading edge handle - yellow/orange color
      gl.glColor3f(1.0f, 0.7f, 0.0f)
      gl.glPointSize(12.0f)
      gl.glBegin(GL.GL_POINTS)
      gl.glVertex3f(mx1 * displayScale, my1 * displayScale, mz1 * displayScale)
      gl.glEnd()

      // Draw trailing edge handle - lighter color
      gl.glColor3f(1.0f, 0.9f, 0.5f)
      gl.glPointSize(8.0f)
      gl.glBegin(GL.GL_POINTS)
      gl.glVertex3f(mx2 * displayScale, my2 * displayScale, mz2 * displayScale)
      gl.glEnd()

      // Draw chord line highlighted
      gl.glColor3f(1.0f, 0.7f, 0.0f)
      gl.glLineWidth(3.0f)
      gl.glBegin(GL.GL_LINES)
      gl.glVertex3f(mx1 * displayScale, my1 * displayScale, mz1 * displayScale)
      gl.glVertex3f(mx2 * displayScale, my2 * displayScale, mz2 * displayScale)
      gl.glEnd()

      gl.glPopMatrix()
      gl.glPointSize(1.0f)
      gl.glLineWidth(1.0f)
      gl.glEnable(GL.GL_DEPTH_TEST)
      gl.glEnable(GLLightingFunc.GL_LIGHTING)
    }
  }

  private def drawSelectedControlHandle(gl: GL2): Unit = {
    selectedControl.foreach { case (surfIdx, secIdx, ctrlIdx, xhinge) =>
      // Get section data from avlSurfaces
      if (surfIdx < avlSurfaces.length) {
        val (sections, _) = avlSurfaces(surfIdx)
        if (secIdx < sections.length) {
          val (xle, yle, zle, chord, _, _, controls) = sections(secIdx)
          if (ctrlIdx < controls.length) {
            gl.glDisable(GLLightingFunc.GL_LIGHTING)
            gl.glDisable(GL.GL_DEPTH_TEST)

            gl.glPushMatrix()
            gl.glScalef(modelScale, modelScale, modelScale)
            gl.glTranslatef(-centerX, -centerY, -centerZ)

            // Calculate hinge position using the current xhinge from selection
            val hingeX = xle + chord * xhinge
            val trailingX = xle + chord
            val (mxH, myH, mzH) = avlToModel(hingeX, yle, zle)
            val (mxT, myT, mzT) = avlToModel(trailingX, yle, zle)

            // Draw hinge handle - bright magenta
            gl.glColor3f(1.0f, 0.0f, 1.0f)
            gl.glPointSize(14.0f)
            gl.glBegin(GL.GL_POINTS)
            gl.glVertex3f(mxH * displayScale, myH * displayScale, mzH * displayScale)
            gl.glEnd()

            // Draw line from hinge to trailing edge - highlighted
            gl.glLineWidth(4.0f)
            gl.glBegin(GL.GL_LINES)
            gl.glVertex3f(mxH * displayScale, myH * displayScale, mzH * displayScale)
            gl.glVertex3f(mxT * displayScale, myT * displayScale, mzT * displayScale)
            gl.glEnd()

            gl.glPopMatrix()
            gl.glPointSize(1.0f)
            gl.glLineWidth(1.0f)
            gl.glEnable(GL.GL_DEPTH_TEST)
            gl.glEnable(GLLightingFunc.GL_LIGHTING)
          }
        }
      }
    }
  }

  private def drawSelectedBodyHandle(gl: GL2): Unit = {
    selectedBody.foreach { case (bodyIdx, dX, dY, dZ) =>
      // Get body profile from avlBodies
      if (bodyIdx < avlBodies.length) {
        val (_, profile, origDX, origDY, origDZ, length, ydupl) = avlBodies(bodyIdx)
        if (profile.nonEmpty) {
          gl.glDisable(GLLightingFunc.GL_LIGHTING)
          gl.glDisable(GL.GL_DEPTH_TEST)

          gl.glPushMatrix()
          gl.glScalef(modelScale, modelScale, modelScale)
          gl.glTranslatef(-centerX, -centerY, -centerZ)

          // Calculate center of body for the handle
          // Profile X is normalized (0-1), so center is always at length/2
          val bodyCenterX = length / 2
          val handleOffset = getBodyAxisHandleOffset(length, profile)

          // Draw handle for original body
          drawBodyAxisHandles(gl, dX, dY, dZ, bodyCenterX, handleOffset)

          // Draw handle for mirrored body if YDUPLICATE is set
          ydupl.foreach { yDup =>
            val mirroredDY = 2 * yDup - dY
            drawBodyAxisHandles(gl, dX, mirroredDY, dZ, bodyCenterX, handleOffset)
          }

          gl.glPopMatrix()
          gl.glPointSize(1.0f)
          gl.glLineWidth(1.0f)
          gl.glEnable(GL.GL_DEPTH_TEST)
          gl.glEnable(GLLightingFunc.GL_LIGHTING)
        }
      }
    }
  }

  private def drawBodyAxisHandles(gl: GL2, dX: Float, dY: Float, dZ: Float, bodyCenterX: Float, handleOffset: Float): Unit = {
    val (mx, my, mz) = avlToModel(dX + bodyCenterX, dY, dZ)

    // Draw center point - yellow (with snap)
    if (isDraggingBody) {
      gl.glColor3f(1.0f, 1.0f, 0.5f)  // Brighter when dragging
      gl.glPointSize(16.0f)
    } else {
      gl.glColor3f(1.0f, 1.0f, 0.0f)  // Yellow
      gl.glPointSize(12.0f)
    }
    gl.glBegin(GL.GL_POINTS)
    gl.glVertex3f(mx * displayScale, my * displayScale, mz * displayScale)
    gl.glEnd()

    // Calculate axis handle positions
    val (hxX, hxY, hxZ) = avlToModel(dX + bodyCenterX + handleOffset, dY, dZ)
    val (hyX, hyY, hyZ) = avlToModel(dX + bodyCenterX, dY + handleOffset, dZ)
    val (hzX, hzY, hzZ) = avlToModel(dX + bodyCenterX, dY, dZ + handleOffset)

    gl.glLineWidth(3.0f)

    // X axis handle - Red
    if (isDraggingBodyAxisX) {
      gl.glColor3f(1.0f, 0.5f, 0.5f)  // Brighter when dragging
      gl.glPointSize(16.0f)
    } else {
      gl.glColor3f(1.0f, 0.3f, 0.3f)
      gl.glPointSize(12.0f)
    }
    gl.glBegin(GL.GL_POINTS)
    gl.glVertex3f(hxX * displayScale, hxY * displayScale, hxZ * displayScale)
    gl.glEnd()
    gl.glBegin(GL.GL_LINES)
    gl.glVertex3f(mx * displayScale, my * displayScale, mz * displayScale)
    gl.glVertex3f(hxX * displayScale, hxY * displayScale, hxZ * displayScale)
    gl.glEnd()

    // Y axis handle - Green
    if (isDraggingBodyAxisY) {
      gl.glColor3f(0.5f, 1.0f, 0.5f)  // Brighter when dragging
      gl.glPointSize(16.0f)
    } else {
      gl.glColor3f(0.3f, 1.0f, 0.3f)
      gl.glPointSize(12.0f)
    }
    gl.glBegin(GL.GL_POINTS)
    gl.glVertex3f(hyX * displayScale, hyY * displayScale, hyZ * displayScale)
    gl.glEnd()
    gl.glBegin(GL.GL_LINES)
    gl.glVertex3f(mx * displayScale, my * displayScale, mz * displayScale)
    gl.glVertex3f(hyX * displayScale, hyY * displayScale, hyZ * displayScale)
    gl.glEnd()

    // Z axis handle - Blue
    if (isDraggingBodyAxisZ) {
      gl.glColor3f(0.5f, 0.7f, 1.0f)  // Brighter when dragging
      gl.glPointSize(16.0f)
    } else {
      gl.glColor3f(0.3f, 0.5f, 1.0f)
      gl.glPointSize(12.0f)
    }
    gl.glBegin(GL.GL_POINTS)
    gl.glVertex3f(hzX * displayScale, hzY * displayScale, hzZ * displayScale)
    gl.glEnd()
    gl.glBegin(GL.GL_LINES)
    gl.glVertex3f(mx * displayScale, my * displayScale, mz * displayScale)
    gl.glVertex3f(hzX * displayScale, hzY * displayScale, hzZ * displayScale)
    gl.glEnd()
  }

  /**
   * Collision points as markers. Point size is in pixels, like every other handle in this
   * viewer, so the markers stay legible at any zoom and no assumption is made about the
   * model's length unit. Depth testing is off, as for the other overlays, so a point inside
   * the fuselage is still visible.
   */
  private def drawCollisionPoints(gl: GL2): Unit = {
    gl.glDisable(GLLightingFunc.GL_LIGHTING)
    gl.glDisable(GL.GL_DEPTH_TEST)

    gl.glPushMatrix()
    gl.glScalef(modelScale, modelScale, modelScale)
    gl.glTranslatef(-centerX, -centerY, -centerZ)

    collisionPoints.zipWithIndex.foreach { case ((x, y, z), index) =>
      val selected = selectedCollisionPoint.exists(_ == index)
      val (mx, my, mz) = avlToModel(x, y, z)

      if (selected) {
        gl.glColor3f(1.0f, 0.4f, 0.0f) // orange, matching the other selected handles
        gl.glPointSize(16.0f)
      } else {
        gl.glColor3f(1.0f, 0.9f, 0.2f) // yellow
        gl.glPointSize(11.0f)
      }
      gl.glBegin(GL.GL_POINTS)
      gl.glVertex3f(mx * displayScale, my * displayScale, mz * displayScale)
      gl.glEnd()
    }

    gl.glPopMatrix()
    gl.glEnable(GL.GL_DEPTH_TEST)
    if (!wireframeMode) gl.glEnable(GLLightingFunc.GL_LIGHTING)
  }

  /**
   * The masses as markers, sized by weight. The size is the square root of the weight relative to
   * the heaviest one, so it reads as an area rather than as a diameter and a 2 kg battery does not
   * dwarf a 200 g motor entirely. A mass of zero still gets the smallest marker: its position is
   * real even when the weight is stated elsewhere.
   *
   * Sizes are in pixels, and depth testing is off, as for every other overlay in this viewer: a
   * mass inside the fuselage is exactly the case that has to be visible.
   */
  private def drawMassPoints(gl: GL2): Unit = {
    gl.glDisable(GLLightingFunc.GL_LIGHTING)
    gl.glDisable(GL.GL_DEPTH_TEST)

    gl.glPushMatrix()
    gl.glScalef(modelScale, modelScale, modelScale)
    gl.glTranslatef(-centerX, -centerY, -centerZ)

    val points = massPoints
    val heaviest = points.foldLeft(0f)((acc, p) => scala.math.max(acc, p._4))
    points.zipWithIndex.foreach { case ((x, y, z, mass, virtual), index) =>
      val (mx, my, mz) = avlToModel(x, y, z)
      val fraction = if (heaviest > 0f) scala.math.sqrt(scala.math.max(mass, 0f) / heaviest).toFloat else 0f
      val size = 7.0f + 9.0f * fraction

      if (selectedMassPoint.exists(_ == index)) {
        gl.glColor3f(1.0f, 0.85f, 1.0f) // the same violet, brightened
        gl.glPointSize(size + 5.0f)
      } else if (virtual) {
        // The mirrored half: dimmer, because the model does not store it. It is where the mass ends
        // up in AVL and in JSBSim all the same.
        gl.glColor3f(0.5f, 0.2f, 0.65f)
        gl.glPointSize(size)
      } else {
        gl.glColor3f(0.85f, 0.35f, 1.0f) // violet, so masses are not mistaken for collision points
        gl.glPointSize(size)
      }
      gl.glBegin(GL.GL_POINTS)
      gl.glVertex3f(mx * displayScale, my * displayScale, mz * displayScale)
      gl.glEnd()
    }

    gl.glPopMatrix()
    gl.glPointSize(1.0f)
    gl.glEnable(GL.GL_DEPTH_TEST)
    if (!wireframeMode) gl.glEnable(GLLightingFunc.GL_LIGHTING)
  }

  /**
   * One handle per axis on the selected mass, coloured as the body handles are (X red, Y green,
   * Z blue). There is no centre handle: without a surface to snap to, a free drag would have to
   * guess which plane the mass moves in, and guessing is what the axes avoid.
   */
  /**
   * The propulsion components' shapes: the battery's box, the motor's cylinder, the propeller's swept disc.
   *
   * Drawn as outlines rather than solids, so the aircraft around them stays visible — the question these
   * answer is whether the part fits where it is being put, which needs both the part and its surroundings.
   * Every point is computed in AVL coordinates and mapped through `avlToModel`, so the axis mapping the user
   * chose applies to a battery exactly as it does to a wing.
   */
  private def drawComponentShapes(gl: GL2): Unit = {
    val shapes = componentShapes
    val points = massPoints
    if (shapes.isEmpty || points.isEmpty) return

    gl.glDisable(GLLightingFunc.GL_LIGHTING)
    gl.glPushMatrix()
    gl.glScalef(modelScale, modelScale, modelScale)
    gl.glTranslatef(-centerX, -centerY, -centerZ)

    def vertex(x: Float, y: Float, z: Float): Unit = {
      val (mx, my, mz) = avlToModel(x, y, z)
      gl.glVertex3f(mx * displayScale, my * displayScale, mz * displayScale)
    }

    def line(a: (Float, Float, Float), b: (Float, Float, Float)): Unit = {
      gl.glBegin(GL.GL_LINES)
      vertex(a._1, a._2, a._3)
      vertex(b._1, b._2, b._3)
      gl.glEnd()
    }

    /** A circle about the thrust axis, at a station along it. */
    def circle(x: Float, y: Float, z: Float, radius: Float): Unit = {
      val segments = 24
      gl.glBegin(GL.GL_LINE_LOOP)
      for (i <- 0 until segments) {
        val angle = 2.0 * scala.math.Pi * i / segments
        vertex(x, y + (radius * scala.math.cos(angle)).toFloat, z + (radius * scala.math.sin(angle)).toFloat)
      }
      gl.glEnd()
    }

    shapes.foreach { shape =>
      if (shape.pointIndex < points.length) {
        val (x, y, z, _, _) = points(shape.pointIndex)
        val selected = selectedMassPoint.exists(_ == shape.pointIndex)
        if (selected) {
          gl.glColor3f(1.0f, 0.85f, 1.0f)
          gl.glLineWidth(2.5f)
        } else {
          gl.glColor3f(0.6f, 0.3f, 0.8f) // the masses' violet, dimmer: the shape belongs to the mass
          gl.glLineWidth(1.5f)
        }

        shape.kind match {
          case com.abajar.avleditor.mass.ComponentShapes.Box =>
            val (hx, hy, hz) = (shape.sizeX / 2f, shape.sizeY / 2f, shape.sizeZ / 2f)
            val corners = for (sx <- Seq(-1f, 1f); sy <- Seq(-1f, 1f); sz <- Seq(-1f, 1f))
              yield (x + sx * hx, y + sy * hy, z + sz * hz)
            // Every pair of corners differing in exactly one coordinate is an edge: 12 of them.
            for (i <- corners.indices; j <- (i + 1) until corners.length) {
              val (ax, ay, az) = corners(i)
              val (bx, by, bz) = corners(j)
              val differing = Seq(ax != bx, ay != by, az != bz).count(identity)
              if (differing == 1) line(corners(i), corners(j))
            }

          case com.abajar.avleditor.mass.ComponentShapes.Cylinder =>
            val halfLength = shape.sizeX / 2f
            val radius = shape.sizeY / 2f
            circle(x - halfLength, y, z, radius)
            circle(x + halfLength, y, z, radius)
            // Four lines along it, so it reads as a can rather than two rings.
            Seq((radius, 0f), (-radius, 0f), (0f, radius), (0f, -radius)).foreach { case (dy, dz) =>
              line((x - halfLength, y + dy, z + dz), (x + halfLength, y + dy, z + dz))
            }

          case com.abajar.avleditor.mass.ComponentShapes.Disc =>
            val radius = shape.sizeY / 2f
            circle(x, y, z, radius)
            // One spoke per stated blade: the count is the model's, not a decoration.
            for (blade <- 0 until scala.math.max(2, shape.blades)) {
              val angle = 2.0 * scala.math.Pi * blade / scala.math.max(2, shape.blades)
              line((x, y, z),
                (x, y + (radius * scala.math.cos(angle)).toFloat, z + (radius * scala.math.sin(angle)).toFloat))
            }

          case _ => ()
        }
      }
    }

    gl.glPopMatrix()
    gl.glLineWidth(1.0f)
    if (!wireframeMode) gl.glEnable(GLLightingFunc.GL_LIGHTING)
  }

  /** The three size handles of the selected shape: small cubes at the faces, so they are not mistaken for
    * the position arrows they sit alongside. */
  private def drawShapeSizeHandles(gl: GL2): Unit = {
    selectedShapeIndex.foreach { shapeIndex =>
      val handles = shapeSizeHandles(shapeIndex)
      if (handles.nonEmpty) {
        gl.glDisable(GLLightingFunc.GL_LIGHTING)
        gl.glDisable(GL.GL_DEPTH_TEST)
        gl.glPushMatrix()
        gl.glScalef(modelScale, modelScale, modelScale)
        gl.glTranslatef(-centerX, -centerY, -centerZ)

        val dragging = Array(isDraggingShapeSizeX, isDraggingShapeSizeY, isDraggingShapeSizeZ)
        handles.foreach { case (axis, (hx, hy, hz)) =>
          val isActive = dragging(axis)
          val (mx, my, mz) = avlToModel(hx, hy, hz)
          if (isActive) {
            gl.glColor3f(1.0f, 1.0f, 0.6f)
            gl.glPointSize(14.0f)
          } else {
            gl.glColor3f(1.0f, 0.9f, 0.2f) // amber: sizing, against the coloured arrows for moving
            gl.glPointSize(10.0f)
          }
          gl.glBegin(GL.GL_POINTS)
          gl.glVertex3f(mx * displayScale, my * displayScale, mz * displayScale)
          gl.glEnd()
        }

        gl.glPopMatrix()
        gl.glPointSize(1.0f)
        gl.glEnable(GL.GL_DEPTH_TEST)
        if (!wireframeMode) gl.glEnable(GLLightingFunc.GL_LIGHTING)
      }
    }
  }

  private def drawSelectedMassHandles(gl: GL2): Unit = {
    selectedMassPoint.filter(_ < massPoints.length).foreach { index =>
      val (x, y, z, _, _) = massPoints(index)

      gl.glDisable(GLLightingFunc.GL_LIGHTING)
      gl.glDisable(GL.GL_DEPTH_TEST)

      gl.glPushMatrix()
      gl.glScalef(modelScale, modelScale, modelScale)
      gl.glTranslatef(-centerX, -centerY, -centerZ)

      val (cx, cy, cz) = avlToModel(x, y, z)
      val handles = massAxisHandles(x, y, z)
      val colours = Seq((1.0f, 0.3f, 0.3f), (0.3f, 1.0f, 0.3f), (0.3f, 0.5f, 1.0f))
      val dragging = Seq(isDraggingMassAxisX, isDraggingMassAxisY, isDraggingMassAxisZ)

      gl.glLineWidth(3.0f)
      handles.zip(colours).zip(dragging).foreach { case (((hx, hy, hz), (r, g, b)), isActive) =>
        val (mx, my, mz) = avlToModel(hx, hy, hz)
        if (isActive) {
          gl.glColor3f(scala.math.min(1.0f, r + 0.4f), scala.math.min(1.0f, g + 0.4f), scala.math.min(1.0f, b + 0.4f))
          gl.glPointSize(16.0f)
        } else {
          gl.glColor3f(r, g, b)
          gl.glPointSize(12.0f)
        }
        gl.glBegin(GL.GL_POINTS)
        gl.glVertex3f(mx * displayScale, my * displayScale, mz * displayScale)
        gl.glEnd()
        gl.glBegin(GL.GL_LINES)
        gl.glVertex3f(cx * displayScale, cy * displayScale, cz * displayScale)
        gl.glVertex3f(mx * displayScale, my * displayScale, mz * displayScale)
        gl.glEnd()
      }

      gl.glPopMatrix()
      gl.glPointSize(1.0f)
      gl.glLineWidth(1.0f)
      gl.glEnable(GL.GL_DEPTH_TEST)
      if (!wireframeMode) gl.glEnable(GLLightingFunc.GL_LIGHTING)
    }
  }

  private def drawSelectedProfilePointHandle(gl: GL2): Unit = {
    selectedProfilePoint.foreach { case (bodyIdx, pointIdx, x, radius) =>
      // Get body data from avlBodies
      if (bodyIdx < avlBodies.length) {
        val (_, profile, dX, dY, dZ, length, _) = avlBodies(bodyIdx)
        if (pointIdx < profile.length) {
          gl.glDisable(GLLightingFunc.GL_LIGHTING)
          gl.glDisable(GL.GL_DEPTH_TEST)

          gl.glPushMatrix()
          gl.glScalef(modelScale, modelScale, modelScale)
          gl.glTranslatef(-centerX, -centerY, -centerZ)

          // Profile point position: center of the circle at this profile point
          // Profile X is normalized (0-1), multiply by length to get actual position
          val actualX = x * length
          val (mx, my, mz) = avlToModel(dX + actualX, dY, dZ)

          // Calculate radius handle position using angle in Y-Z plane
          // Y offset = radius * sin(angle), Z offset = radius * cos(angle)
          val yOffset = radius * scala.math.sin(radiusHandleAngle).toFloat
          val zOffset = radius * scala.math.cos(radiusHandleAngle).toFloat
          val (rxEnd, ryEnd, rzEnd) = avlToModel(dX + actualX, dY + yOffset, dZ + zOffset)

          // Draw connecting line from center to radius handle
          gl.glColor3f(0.5f, 0.5f, 0.5f)  // Gray line
          gl.glLineWidth(2.0f)
          gl.glBegin(GL.GL_LINES)
          gl.glVertex3f(mx * displayScale, my * displayScale, mz * displayScale)
          gl.glVertex3f(rxEnd * displayScale, ryEnd * displayScale, rzEnd * displayScale)
          gl.glEnd()

          // Draw X position handle - cyan, larger when dragging
          if (isDraggingProfilePointX) {
            gl.glColor3f(0.0f, 1.0f, 1.0f)  // Bright cyan when dragging
            gl.glPointSize(16.0f)
          } else {
            gl.glColor3f(0.0f, 0.8f, 0.8f)  // Darker cyan when not dragging
            gl.glPointSize(12.0f)
          }
          gl.glBegin(GL.GL_POINTS)
          gl.glVertex3f(mx * displayScale, my * displayScale, mz * displayScale)
          gl.glEnd()

          // Draw radius handle - orange, larger when dragging
          if (isDraggingProfilePointRadius) {
            gl.glColor3f(1.0f, 0.6f, 0.0f)  // Bright orange when dragging
            gl.glPointSize(16.0f)
          } else {
            gl.glColor3f(1.0f, 0.4f, 0.0f)  // Darker orange when not dragging
            gl.glPointSize(12.0f)
          }
          gl.glBegin(GL.GL_POINTS)
          gl.glVertex3f(rxEnd * displayScale, ryEnd * displayScale, rzEnd * displayScale)
          gl.glEnd()

          gl.glPopMatrix()
          gl.glPointSize(1.0f)
          gl.glLineWidth(1.0f)
          gl.glEnable(GL.GL_DEPTH_TEST)
          gl.glEnable(GLLightingFunc.GL_LIGHTING)
        }
      }
    }
  }

  private def drawReferenceLine(gl: GL2): Unit = {
    if (!showReferenceLine) return

    gl.glDisable(GLLightingFunc.GL_LIGHTING)
    gl.glDisable(GL.GL_DEPTH_TEST)

    gl.glPushMatrix()
    gl.glScalef(modelScale, modelScale, modelScale)
    gl.glTranslatef(-centerX, -centerY, -centerZ)

    // Calculate line extent based on model bounds
    val extent = scala.math.max(
      scala.math.max(modelMaxX - modelMinX, modelMaxY - modelMinY),
      modelMaxZ - modelMinZ
    ) * 2

    // Small offset for label position
    val labelOffset = extent * 0.12f

    gl.glLineWidth(2.0f)

    // Draw AVL axes using avlToModel transform (solid green lines)
    // This shows where AVL axes end up after mapping
    gl.glColor3f(0.0f, 1.0f, 0.0f)

    // AVL X axis (forward direction)
    val (axXn1, ayXn1, azXn1) = avlToModel(-extent, 0, 0)
    val (axXp1, ayXp1, azXp1) = avlToModel(extent, 0, 0)
    gl.glBegin(GL.GL_LINES)
    gl.glVertex3f(axXn1, ayXn1, azXn1)
    gl.glVertex3f(axXp1, ayXp1, azXp1)
    gl.glEnd()

    // AVL Y axis (spanwise/right direction)
    val (axYn1, ayYn1, azYn1) = avlToModel(0, -extent, 0)
    val (axYp1, ayYp1, azYp1) = avlToModel(0, extent, 0)
    gl.glBegin(GL.GL_LINES)
    gl.glVertex3f(axYn1, ayYn1, azYn1)
    gl.glVertex3f(axYp1, ayYp1, azYp1)
    gl.glEnd()

    // AVL Z axis (up direction)
    val (axZn1, ayZn1, azZn1) = avlToModel(0, 0, -extent)
    val (axZp1, ayZp1, azZp1) = avlToModel(0, 0, extent)
    gl.glBegin(GL.GL_LINES)
    gl.glVertex3f(axZn1, ayZn1, azZn1)
    gl.glVertex3f(axZp1, ayZp1, azZp1)
    gl.glEnd()

    // Draw Model 3D axes - dashed colored lines (original model coordinates)
    gl.glEnable(GL2.GL_LINE_STIPPLE)
    gl.glLineStipple(2, 0x00FF.toShort)

    // Model x axis - Red dashed
    gl.glColor3f(0.9f, 0.3f, 0.3f)
    gl.glBegin(GL.GL_LINES)
    gl.glVertex3f(-extent, 0, 0)
    gl.glVertex3f(extent, 0, 0)
    gl.glEnd()

    // Model y axis - Blue dashed
    gl.glColor3f(0.3f, 0.3f, 0.9f)
    gl.glBegin(GL.GL_LINES)
    gl.glVertex3f(0, -extent, 0)
    gl.glVertex3f(0, extent, 0)
    gl.glEnd()

    // Model z axis - Cyan dashed
    gl.glColor3f(0.3f, 0.9f, 0.9f)
    gl.glBegin(GL.GL_LINES)
    gl.glVertex3f(0, 0, -extent)
    gl.glVertex3f(0, 0, extent)
    gl.glEnd()

    gl.glDisable(GL2.GL_LINE_STIPPLE)

    // Draw axis labels
    if (textRenderer != null) {
      val textScale = extent * 0.005f / zoom

      textRenderer.begin3DRendering()

      // AVL axis labels (green, uppercase) - at transformed positions
      textRenderer.setColor(0.0f, 1.0f, 0.0f, 1.0f)
      val (xlX, ylX, zlX) = avlToModel(labelOffset, 0, 0)
      val (xlY, ylY, zlY) = avlToModel(0, labelOffset, 0)
      val (xlZ, ylZ, zlZ) = avlToModel(0, 0, labelOffset)
      textRenderer.draw3D("X", xlX, ylX, zlX, textScale)
      textRenderer.draw3D("Y", xlY, ylY, zlY, textScale)
      textRenderer.draw3D("Z", xlZ, ylZ, zlZ, textScale)

      // Model axis labels (colored, lowercase)
      textRenderer.setColor(0.9f, 0.4f, 0.4f, 1.0f)
      textRenderer.draw3D("x", labelOffset * 0.8f, 0, 0, textScale)
      textRenderer.setColor(0.4f, 0.4f, 0.9f, 1.0f)
      textRenderer.draw3D("y", 0, labelOffset * 0.8f, 0, textScale)
      textRenderer.setColor(0.3f, 0.9f, 0.9f, 1.0f)
      textRenderer.draw3D("z", 0, 0, labelOffset * 0.8f, textScale)

      textRenderer.end3DRendering()
    }

    gl.glPopMatrix()
    gl.glLineWidth(1.0f)
    gl.glEnable(GL.GL_DEPTH_TEST)
    gl.glEnable(GLLightingFunc.GL_LIGHTING)
  }

  private def drawDimensionLines(gl: GL2, drawable: GLAutoDrawable): Unit = {
    gl.glDisable(GLLightingFunc.GL_LIGHTING)
    gl.glDisable(GL.GL_DEPTH_TEST)  // Draw on top of everything
    gl.glColor3f(0.0f, 1.0f, 1.0f)  // Cyan color
    gl.glLineWidth(3.0f)
    gl.glEnable(GL2.GL_LINE_STIPPLE)
    gl.glLineStipple(2, 0x00FF.toShort)

    gl.glPushMatrix()
    gl.glScalef(modelScale, modelScale, modelScale)
    gl.glTranslatef(-centerX, -centerY, -centerZ)

    val offsetY = modelMinY - (modelMaxY - modelMinY) * 0.15f
    val offsetZ = modelMinZ - (modelMaxZ - modelMinZ) * 0.1f
    val offsetX = modelMaxX + (modelMaxX - modelMinX) * 0.15f
    val capSize = (modelMaxX - modelMinX) * 0.02f

    // Wingspan line
    gl.glBegin(GL.GL_LINES)
    gl.glVertex3f(modelMinX, offsetY, offsetZ)
    gl.glVertex3f(modelMaxX, offsetY, offsetZ)
    gl.glEnd()

    gl.glBegin(GL.GL_LINES)
    gl.glVertex3f(modelMinX, offsetY - capSize, offsetZ)
    gl.glVertex3f(modelMinX, offsetY + capSize, offsetZ)
    gl.glVertex3f(modelMaxX, offsetY - capSize, offsetZ)
    gl.glVertex3f(modelMaxX, offsetY + capSize, offsetZ)
    gl.glEnd()

    // Length line
    gl.glBegin(GL.GL_LINES)
    gl.glVertex3f(offsetX, modelMinY, offsetZ)
    gl.glVertex3f(offsetX, modelMaxY, offsetZ)
    gl.glEnd()

    gl.glBegin(GL.GL_LINES)
    gl.glVertex3f(offsetX - capSize, modelMinY, offsetZ)
    gl.glVertex3f(offsetX + capSize, modelMinY, offsetZ)
    gl.glVertex3f(offsetX - capSize, modelMaxY, offsetZ)
    gl.glVertex3f(offsetX + capSize, modelMaxY, offsetZ)
    gl.glEnd()

    gl.glDisable(GL2.GL_LINE_STIPPLE)
    gl.glLineWidth(1.0f)

    // Draw dimension text labels in 3D space (inside the transformed space)
    if (textRenderer != null) {
      val wingspanVal = (modelMaxX - modelMinX) / displayScale
      val lengthVal = (modelMaxY - modelMinY) / displayScale

      // Text scale - constant visual size regardless of zoom
      val textScale = (modelMaxX - modelMinX) * 0.006f / zoom

      textRenderer.begin3DRendering()
      textRenderer.setColor(0.0f, 1.0f, 1.0f, 1.0f)  // Cyan

      // Wingspan text - centered below the wingspan line
      val wingspanText = f"$wingspanVal%.2f m"
      val wingspanTextX = (modelMinX + modelMaxX) / 2
      val wingspanTextY = offsetY - capSize * 4
      textRenderer.draw3D(wingspanText, wingspanTextX, wingspanTextY, offsetZ, textScale)

      // Length text - beside the length line
      val lengthText = f"$lengthVal%.2f m"
      val lengthTextX = offsetX + capSize * 4
      val lengthTextY = (modelMinY + modelMaxY) / 2
      textRenderer.draw3D(lengthText, lengthTextX, lengthTextY, offsetZ, textScale)

      textRenderer.end3DRendering()
    }

    gl.glPopMatrix()

    gl.glEnable(GL.GL_DEPTH_TEST)
    gl.glEnable(GLLightingFunc.GL_LIGHTING)
  }
}

object Viewer3DGL {

  /** Below this, an axis is pointing so nearly into the screen that its direction on screen is
    * noise: a pixel of mouse movement would send the mass a long way. */
  val MinAxisPixels: Float = 6.0f

  /**
   * How far to move along an axis for a mouse movement, given where that axis points on screen.
   *
   * `axisScreenDx/Dy` is the screen vector of one known step along the axis — the handle offset —
   * and `unit` is that step in model units, so the projection of the mouse movement onto it converts
   * pixels back into model units. Dragging away from the handle's direction moves the mass forward
   * along the axis and towards it moves it back, which is what the handle looks like it should do
   * from any viewpoint.
   *
   * An axis seen almost end-on returns 0: it cannot be dragged meaningfully, and refusing is better
   * than a jump. Rotating the view brings it back.
   */
  def axisDragDelta(axisScreenDx: Float, axisScreenDy: Float,
                    mouseDx: Float, mouseDy: Float, unit: Float): Float = {
    val lengthSq = axisScreenDx * axisScreenDx + axisScreenDy * axisScreenDy
    if (lengthSq < MinAxisPixels * MinAxisPixels) 0f
    else (mouseDx * axisScreenDx + mouseDy * axisScreenDy) / lengthSq * unit
  }
}
