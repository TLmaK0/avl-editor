/*
 * What a part is made of says what it weighs: volume x density x fill, plus the area a skin covers.
 * Run with:  sbt "test:runMain com.abajar.avleditor.material.MaterialWeightCheck"
 */
package com.abajar.avleditor.material

import com.abajar.avleditor.avl.AVLGeometry
import com.abajar.avleditor.avl.geometry.{Body, BodyProfilePoint, Section, Surface}
import com.abajar.avleditor.avl.mass.MaterialElement
import scala.collection.JavaConverters._

object MaterialWeightCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Float, b: Double, tol: Double = 1e-3): Boolean = math.abs(a - b) < tol

  /** A rectangular wing: half-span 1 m, chord 0.4 m, 12% thick — 0.4 m² of planform per side. */
  private def wing(symmetric: Boolean): Surface = {
    val surface = new Surface
    surface.setName("wing")
    surface.setSymmetric(symmetric)
    surface.getSections.clear()
    Seq(0.0f, 1.0f).foreach { y =>
      val s = new Section
      s.setXle(0f); s.setYle(y); s.setZle(0f); s.setChord(0.4f)
      surface.getSections.add(s)
    }
    surface.initSectionParents()
    surface
  }

  private def podBody(): Body = {
    val body = new Body
    body.setName("pod")
    body.setLength(0.5f)
    body.setdX(1.0f); body.setdY(-0.55f)
    body.getProfilePoints.clear()
    body.getProfilePoints.add(new BodyProfilePoint(0.0f, 0.05f))
    body.getProfilePoints.add(new BodyProfilePoint(1.0f, 0.05f))
    body
  }

  def main(args: Array[String]): Unit = {
    println("the two conversions everything rests on")
    // A cubic metre of water is a tonne, and a square metre of a 1000 g/m2 skin is a kilogram.
    val cube = new Body
    cube.setLength(1.0f)
    cube.getProfilePoints.clear()
    // A cylinder of 1 m length and radius r has volume pi r^2; pick r so the volume is 1 m3.
    val radius = math.sqrt(1.0 / math.Pi).toFloat
    cube.getProfilePoints.add(new BodyProfilePoint(0.0f, radius))
    cube.getProfilePoints.add(new BodyProfilePoint(1.0f, radius))
    cube.setMaterialDensity(1.0f)
    cube.setFillPercent(100f)
    cube.setSkinArealWeight(0f)
    println(f"  1 m3 at 1 g/cm3: ${cube.materialWeight()}%.2f kg")
    check("a cubic metre at 1 g/cm3 weighs 1000 kg", near(cube.materialWeight(), 1000.0, 1.0))

    val skinOnly = wing(symmetric = false)
    skinOnly.setMaterialDensity(0f)
    skinOnly.setSkinArealWeight(1000f)
    println(f"  wetted area ${skinOnly.wettedArea()}%.4f m2 at 1000 g/m2: ${skinOnly.materialWeight()}%.4f kg")
    check("a square metre of skin at 1000 g/m2 weighs a kilogram",
      near(skinOnly.materialWeight(), skinOnly.wettedArea()))

    println("a wing's own figures")
    val panel = wing(symmetric = false)
    // 0.68 x 0.12 x 0.4 x 0.4 x 1 = 0.013056 m3 of airfoil box.
    println(f"  volume ${panel.definedSideVolume().getVolume}%.6f m3, wetted ${panel.wettedArea()}%.4f m2")
    check("the volume is the airfoil box", near(panel.definedSideVolume().getVolume, 0.013056, 1e-5))
    // Both faces of 0.4 m2, plus a little for the airfoil's curvature: 2 x 0.4 x (1 + 0.25 x 0.12).
    check("the wetted area is both faces of the planform", near(panel.wettedArea(), 0.824, 1e-3))

    println("balsa at 15%, the default for a surface")
    val balsa = wing(symmetric = false)
    check("that is what a new surface starts with",
      balsa.getMaterialName == "Balsa, medium" && near(balsa.getMaterialDensity, 0.16) &&
        near(balsa.getFillPercent, MaterialElement.DEFAULT_SURFACE_FILL_PERCENT))
    val expected = 0.013056 * 1000 * 0.16 * 0.15
    println(f"  weight ${balsa.materialWeight()}%.4f kg")
    check("weight is volume x density x fill", near(balsa.materialWeight(), expected, 1e-4))
    check("0% fill weighs nothing", { balsa.setFillPercent(0f); near(balsa.materialWeight(), 0.0) })
    check("100% weighs the solid",
      { balsa.setFillPercent(100f); near(balsa.materialWeight(), 0.013056 * 1000 * 0.16, 1e-3) })
    check("the fill cannot be pushed past 100",
      { balsa.setFillPercent(250f); near(balsa.getFillPercent, 100.0) })
    check("nor below zero", { balsa.setFillPercent(-5f); near(balsa.getFillPercent, 0.0) })

    println("a foam core in a carbon skin")
    val composite = wing(symmetric = false)
    composite.setMaterialName("XPS foam / Depron")
    composite.setFillPercent(100f)
    composite.setSkinName("Carbon skin 0.20 mm")
    check("choosing a material writes its density", near(composite.getMaterialDensity, 0.035))
    check("choosing a skin writes its areal weight", near(composite.getSkinArealWeight, 310.0))
    val core = 0.013056 * 1000 * 0.035
    val skin = 0.824 * 310 / 1000
    println(f"  core ${core}%.4f kg + skin ${skin}%.4f kg = ${composite.materialWeight()}%.4f kg")
    check("the two add up", near(composite.materialWeight(), core + skin, 1e-3))
    check("'None' clears the skin",
      { composite.setSkinName("None"); near(composite.getSkinArealWeight, 0.0) })

    println("a material the library does not have")
    val exotic = wing(symmetric = false)
    exotic.setMaterialDensity(0.42f)
    exotic.setMaterialName("Unobtainium")
    check("keeps the density the model states", near(exotic.getMaterialDensity, 0.42))
    check("and keeps its name", exotic.getMaterialName == "Unobtainium")

    println("a mirrored element")
    val mirrored = wing(symmetric = true)
    mirrored.setFillPercent(100f)
    val oneSide = mirrored.materialWeight()
    check("weighs the side it defines, its mirror weighing the other",
      near(oneSide, 0.013056 * 1000 * 0.16, 1e-3) && near(mirrored.massVolume(), 0.013056, 1e-5))
    val mass = mirrored.createMass()
    check("a mass created on it starts at that weight", near(mass.getMass, oneSide))
    check("and the pair weighs the whole wing",
      near(mirrored.getEffectiveMasses.asScala.map(_.getMass).sum, 2 * oneSide))

    println("an element whose side balances on the plane of symmetry")
    val nearlyCentred = podBody()
    nearlyCentred.setdY(1.0e-6f)
    nearlyCentred.setFillPercent(100f)
    val bothSides = nearlyCentred.materialWeight()
    check("one mass weighs the whole element, since there will be no mirror",
      near(bothSides, 2f * nearlyCentred.definedSideVolume().getVolume * 1000f * 0.16f, 1e-3))

    println("masses from materials over a whole aircraft")
    val geometry = new AVLGeometry
    geometry.getSurfaces.clear(); geometry.getBodies.clear(); geometry.getMasses.clear()
    geometry.getSurfaces.add(wing(symmetric = true))
    geometry.getBodies.add(podBody())
    val stated = geometry.addMassAt(0.1f, 0f, 0f)
    stated.setName("receiver"); stated.setMass(0.05f)

    check("they are generated", geometry.massesFromMaterials())
    val stored = geometry.getMassesRecursive.asScala
    stored.foreach(m => println(f"  ${m.getName}%-28s ${m.getMass}%.4f kg at y=${m.getY}%+.4f"))
    check("one per element, plus the mass stated by hand", stored.size == 3)
    check("the hand-stated mass is untouched",
      stored.exists(m => m.getName == "receiver" && near(m.getMass, 0.05)))
    check("each element's mass is what its material says", {
      val wingMass = stored.find(_.getName.startsWith("wing")).get
      near(wingMass.getMass, geometry.getSurfaces.get(0).materialWeight())
    })
    val total = geometry.getEffectiveMassesRecursive.asScala.map(_.getMass).sum
    println(f"  generated total ${total}%.4f kg")
    check("the total is a result, not a redistribution of what was there", total > 0.05f)
    check("running it again gives the same answer", {
      geometry.massesFromMaterials()
      near(geometry.getEffectiveMassesRecursive.asScala.map(_.getMass).sum, total)
    })

    println(if (ok) "MATERIAL_WEIGHT_OK" else "MATERIAL_WEIGHT_FAIL")
    if (!ok) sys.exit(1)
  }
}
