/*
 * A model states its figures in units of its own choosing, and there is one place that knows which: the AVL
 * node holds the three names and hands out a ModelUnits, and everything that converts asks it. Pinned because
 * the weight derived from materials used to be worked out in kilograms and written straight into a field the
 * rest of the editor read as grams, so a model stated in grams weighed a thousandth of what it should.
 * Run with:  sbt "test:runMain com.abajar.avleditor.ModelUnitsCheck"
 */
package com.abajar.avleditor

import com.abajar.avleditor.avl.geometry.{Section, Surface}
import com.abajar.avleditor.crrcsim.CRRCSimFactory

object ModelUnitsCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Double, b: Double, tol: Double = 1e-6): Boolean = math.abs(a - b) < tol

  /** The same wing in a model that states its masses in `massUnit`. */
  private def wing(massUnit: String, lengthUnit: String = "m") = {
    val crrcsim = new CRRCSimFactory().create()
    crrcsim.getAvl.setMassUnit(massUnit)
    crrcsim.getAvl.setLengthUnit(lengthUnit)
    val geometry = crrcsim.getAvl.getGeometry
    val surface = geometry.createSurface()
    Seq(0f, 0.5f).foreach { y =>
      val section = surface.createSection().asInstanceOf[Section]
      section.setXle(0f); section.setYle(y); section.setZle(0f); section.setChord(0.2f)
    }
    geometry.initParents()
    (crrcsim, surface)
  }

  def main(args: Array[String]): Unit = {
    println("the units come from the model, not from whoever holds a figure")
    val metric = new ModelUnits("m", "kg", "s")
    check("a kilogram in a model stated in kilograms is a kilogram", near(metric.toKilograms(1f), 1.0))
    val grams = new ModelUnits("m", "g", "s")
    check("a thousand grams are a kilogram", near(grams.toKilograms(1000f), 1.0))
    // A float 0.001 is not exactly a thousandth, so this is a conversion check and not a bit comparison.
    check("and a kilogram is a thousand of them", near(grams.fromKilograms(1f), 1000.0, 1e-3))
    val ounces = new ModelUnits("in", "oz", "s")
    check("an ounce is 28.35 g", near(ounces.toKilograms(1f), 0.0283495231, 1e-9))
    check("both ways round", near(ounces.fromKilograms(ounces.toKilograms(3.7f)), 3.7, 1e-5))
    check("an inch is 25.4 mm", near(ounces.toMetres(1f), 0.0254, 1e-9))
    check("one millimetre in inches", near(ounces.millimetre(), 0.03937008, 1e-7))
    check("and in centimetres", near(new ModelUnits("cm", "kg", "s").millimetre(), 0.1, 1e-6))
    check("a model that states nothing is metres, kilograms and seconds",
      ModelUnits.DEFAULTS.toString == "m/kg/s")

    println("a minute is sixty seconds")
    // It was 36 in the factor table, and the AVL mass file hid it by writing "60 s" by hand instead.
    check("in the factors", near(new ModelUnits("m", "kg", "m").secondsPerTimeUnit(), 60.0))
    check("and an hour is 3600", near(new ModelUnits("m", "kg", "h").secondsPerTimeUnit(), 3600.0))

    println("the AVL mass file's unit lines come from those same factors")
    check("metres", metric.avlLengthUnit() == "1 m")
    check("kilograms", metric.avlMassUnit() == "1 kg")
    check("seconds", metric.avlTimeUnit() == "1 s")
    check("centimetres", new ModelUnits("cm", "g", "h").avlLengthUnit() == "0.01 m")
    check("grams", new ModelUnits("cm", "g", "h").avlMassUnit() == "0.001 kg")
    check("hours", new ModelUnits("cm", "g", "h").avlTimeUnit() == "3600 s")

    println("the weight from materials is stated in the model's unit")
    val (inKg, _) = wing("kg")
    inKg.getAvl.getGeometry.massesFromMaterials()
    inKg.calculate()
    val kgTotal = inKg.getConfig.getMass_inertia.getMass
    println(f"  stated in kg: the model weighs $kgTotal%.5f kg")

    val (inGrams, _) = wing("g")
    inGrams.getAvl.getGeometry.massesFromMaterials()
    inGrams.calculate()
    val gramTotal = inGrams.getConfig.getMass_inertia.getMass
    println(f"  stated in g : the model weighs $gramTotal%.5f kg")
    // The same wing made of the same balsa weighs the same however the model chooses to write it down.
    check("the same wing weighs the same either way", near(gramTotal, kgTotal, 1e-5))
    check("and it is not a thousandth of it", gramTotal > kgTotal / 2)

    val (inOunces, _) = wing("oz")
    inOunces.getAvl.getGeometry.massesFromMaterials()
    inOunces.calculate()
    check("nor a different figure in ounces",
      near(inOunces.getConfig.getMass_inertia.getMass, kgTotal, 1e-5))

    println("a mass created by hand starts at the same weight, in the same unit")
    val (byHand, surface) = wing("g")
    val mass = surface.createMass()
    println(f"  the '+ Mass' button wrote ${mass.getMass}%.3f (grams)")
    check("stated in grams, so it is around a thousand times the kilogram figure",
      mass.getMass > 1f)
    check("and it agrees with the material's own summary",
      near(mass.getMass, byHand.getAvl.units().fromKilograms(surface.materialWeight()), 1e-4))

    println("an element on its own falls back to the defaults, not to nothing")
    // What a check does when it exercises one surface. A model always has an AVL node with the three units.
    val bare = new Surface
    check("its units are the defaults", bare.units().toString == "m/kg/s")

    println("nobody keeps a copy of the units")
    val live = new CRRCSimFactory().create()
    check("changing the unit changes what the model answers",
      live.getAvl.units().massUnit() == "kg" && {
        live.getAvl.setMassUnit("g")
        live.getAvl.units().massUnit() == "g"
      })
    check("and the geometry follows it without being told again",
      live.getAvl.getGeometry.units().massUnit() == "g")

    println(if (ok) "MODEL_UNITS_OK" else "MODEL_UNITS_FAIL")
    if (!ok) sys.exit(1)
  }
}
