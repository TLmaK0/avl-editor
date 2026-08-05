/*
 * The materials on offer: twenty-odd to start from, editable, and a model that keeps its own figures
 * whatever the list says later.
 * Run with:  sbt "test:runMain com.abajar.avleditor.material.MaterialLibraryCheck"
 */
package com.abajar.avleditor.material

import com.abajar.avleditor.avl.geometry.{Section, Surface}
import com.abajar.avleditor.avl.mass.MaterialElement
import com.abajar.avleditor.crrcsim.{CRRCSimFactory, CRRCSimRepository}
import com.abajar.avleditor.view.annotations.AvlEditorField
import java.io.File
import java.nio.file.Files
import scala.collection.JavaConverters._

object MaterialLibraryCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Float, b: Double, tol: Double = 1e-3): Boolean = math.abs(a - b) < tol

  private def wing(): Surface = {
    val surface = new Surface
    surface.getSections.clear()
    Seq(0.0f, 1.0f).foreach { y =>
      val s = new Section
      s.setYle(y); s.setChord(0.4f)
      surface.getSections.add(s)
    }
    surface.initSectionParents()
    surface
  }

  def main(args: Array[String]): Unit = {
    val library = MaterialLibrary.defaults()
    val materials = library.getMaterials.asScala

    println("what the editor ships with")
    println(s"  ${materials.size} materials, ${materials.count(_.isSkin)} of them skins")
    check("twenty or more to choose from", materials.size >= 20)
    check("no two share a name", materials.map(_.getName).distinct.size == materials.size)
    check("every one states either a density or an areal weight",
      materials.forall(m => m.getDensity > 0f || m.getArealWeight > 0f || m.getName == "None"))
    check("the densities are plausible, from foam to steel",
      materials.filter(_.getDensity > 0f).forall(m => m.getDensity >= 0.01f && m.getDensity <= 8f))
    check("balsa is there, at the usual 0.16", near(library.find("Balsa, medium").getDensity, 0.16))
    check("and steel at 7.85", near(library.find("Steel").getDensity, 7.85))

    println("a skin states the weight of its thickness")
    val carbon = library.find("Carbon skin 0.20 mm")
    // 0.2 mm of a 1.55 g/cm3 laminate over a square metre: 0.2 mm x 1 m2 = 200 cm3 -> 310 g.
    println(f"  ${carbon.getName}: ${carbon.getArealWeight}%.0f g/m2")
    check("0.20 mm of carbon is 310 g/m2", near(carbon.getArealWeight, 310.0, 0.5))
    check("twice the thickness is twice the weight",
      near(library.find("Carbon skin 0.40 mm").getArealWeight, 2 * carbon.getArealWeight, 0.5))
    check("a skin has no density of its own to confuse it with", near(carbon.getDensity, 0.0))
    check("skins and solids are offered separately",
      library.solidNames.asScala.forall(n => !n.startsWith("Carbon skin")) &&
        library.skinNames.asScala.contains("Carbon skin 0.20 mm"))
    check("and 'None' is always an option for a skin", library.skinNames.asScala.contains("None"))

    println("editing the list")
    val edited = MaterialLibrary.defaults()
    edited.getMaterials.add(Material.solid("Depron 6 mm", 0.033f, "mine"))
    check("a material can be added", edited.find("Depron 6 mm") != null)
    edited.find("Balsa, medium").setDensity(0.18f)
    check("and an existing one changed", near(edited.find("Balsa, medium").getDensity, 0.18))
    edited.getMaterials.remove(edited.find("Steel"))
    check("and one removed", edited.find("Steel") == null)
    check("a name the list does not have answers nothing", edited.find("Adamantium") == null)

    println("saved and read back")
    val file = Files.createTempFile("avleditor-materials", ".yaml").toFile
    edited.save(file)
    val reloaded = MaterialLibrary.load(file)
    check("the list survives the round trip", reloaded.getMaterials.size == edited.getMaterials.size)
    check("with the edits in it",
      near(reloaded.find("Balsa, medium").getDensity, 0.18) && reloaded.find("Steel") == null &&
        reloaded.find("Depron 6 mm") != null)
    check("a file that is not there is created from the defaults", {
      val fresh = new File(file.getParentFile, "avleditor-materials-fresh.yaml")
      fresh.delete()
      val seeded = MaterialLibrary.load(fresh)
      val there = fresh.exists() && seeded.getMaterials.size >= 20
      fresh.delete()
      there
    })
    check("a file that cannot be read falls back to the defaults, rather than to nothing", {
      val broken = new File(file.getParentFile, "avleditor-materials-broken.yaml")
      Files.write(broken.toPath, "this is not a material library".getBytes)
      val fallback = MaterialLibrary.load(broken)
      broken.delete()
      fallback.getMaterials.size >= 20
    })
    file.delete()

    println("what a model keeps")
    Materials.use(MaterialLibrary.defaults())
    val surface = wing()
    surface.setMaterialName("Carbon fibre laminate")
    surface.setFillPercent(8f)
    val chosenDensity = surface.getMaterialDensity
    check("choosing a material copies its density onto the element", near(chosenDensity, 1.55))

    // The library changes, or the model travels to a machine whose library differs.
    val different = MaterialLibrary.defaults()
    different.find("Carbon fibre laminate").setDensity(9.99f)
    Materials.use(different)
    check("the model still weighs what it weighed", near(surface.getMaterialDensity, 1.55))

    val without = new MaterialLibrary
    Materials.use(without)
    check("even with an empty list", near(surface.getMaterialDensity, 1.55) &&
      surface.getMaterialName == "Carbon fibre laminate")
    check("and the dropdown then offers nothing rather than crashing",
      surface.materialOptions().length == 0 && surface.skinOptions().length == 1)
    Materials.use(MaterialLibrary.defaults())

    println("the properties table")
    val materialField = classOf[MaterialElement].getDeclaredField("materialName")
      .getAnnotation(classOf[AvlEditorField])
    check("the material row asks the element for its choices",
      materialField.optionsFrom() == "materialOptions")
    check("the choices are the library's solid materials",
      wing().materialOptions().toSeq == library.solidNames.asScala.toSeq)
    check("and the skin row offers the skins",
      wing().skinOptions().toSeq == library.skinNames.asScala.toSeq)
    val rows = com.abajar.avleditor.view.PropertyRows.rowLabels(classOf[Surface])
    println("  surface rows: " + rows.mkString(", "))
    check("a surface's rows include what it is made of, inherited and all",
      Seq("Material", "Density (g/cm3)", "Fill (%)", "Skin", "Skin weight (g/m2)").forall(rows.contains))
    check("its own geometry reads first", rows.indexOf("surface name") < rows.indexOf("Material"))
    check("a body offers the same", {
      val bodyRows = com.abajar.avleditor.view.PropertyRows.rowLabels(classOf[com.abajar.avleditor.avl.geometry.Body])
      Seq("Material", "Fill (%)", "Skin").forall(bodyRows.contains)
    })
    check("a section does not: it is not made of anything, it states a shape",
      !com.abajar.avleditor.view.PropertyRows.rowLabels(classOf[Section]).contains("Material"))
    check("the row that spells out the weight says where it comes from", {
      val summary = wing().getMaterialWeightSummary
      println(s"  $summary")
      summary.contains("kg") && summary.contains("g/cm3") && summary.contains("g/m2")
    })

    println("a saved model")
    val model = new CRRCSimFactory().create()
    val modelSurface = model.getAvl.getGeometry.getSurfaces.get(0)
    modelSurface.setMaterialName("EPP foam")
    modelSurface.setFillPercent(95f)
    modelSurface.setSkinName("Glass skin 0.20 mm")
    val modelFile = Files.createTempFile("avleditor-material-model", ".avle").toFile
    new CRRCSimRepository().storeToFile(modelFile, model)
    val reloadedModel = new CRRCSimRepository().restoreFromFile(modelFile)
    val reloadedSurface = reloadedModel.getAvl.getGeometry.getSurfaces.get(0)
    check("carries its material, fill and skin",
      reloadedSurface.getMaterialName == "EPP foam" && near(reloadedSurface.getFillPercent, 95.0) &&
        near(reloadedSurface.getMaterialDensity, 0.030) &&
        near(reloadedSurface.getSkinArealWeight, 380.0))
    modelFile.delete()

    println(if (ok) "MATERIAL_LIBRARY_OK" else "MATERIAL_LIBRARY_FAIL")
    if (!ok) sys.exit(1)
  }
}
