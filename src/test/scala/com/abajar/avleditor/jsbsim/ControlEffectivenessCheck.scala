/*
 * AVL states control derivatives per unit of its control variable, and that variable is not an angle: the
 * .avl CONTROL line's gain says how many degrees one unit means. JSBSim drives the aerodynamics from the
 * deflection in radians, so the two have to be converted between — and they were not. Every exported model
 * had surfaces 180/(pi*gain) times weaker than the model stated: 2.9 times at the editor's default gain of
 * 20, and 57 times at a gain of 1.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.ControlEffectivenessCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.avl.runcase.{AvlCalculation, Configuration, StabilityDerivatives}

object ControlEffectivenessCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Double, b: Double, tol: Double): Boolean = math.abs(a - b) < tol

  /** The eurofighter's own numbers: an all-moving canard as the elevator, at a gain of 30. */
  private val ElevatorIndex = 3
  private val Cmd = 0.100025f
  private val CLd = 0.111766f
  private val Gain = 30f

  private def calculation(gain: Float): AvlCalculation = {
    val calc = new AvlCalculation(ElevatorIndex, 2, 0)
    val cfg = new Configuration
    cfg.setSref(0.3909f); cfg.setBref(1.0692f); cfg.setCref(0.3655f)
    cfg.setE(0.19f); cfg.setCLtot(0.0428f); cfg.setCmtot(0f); cfg.setCDvis(0.02f)
    calc.setConfiguration(cfg)
    val std = new StabilityDerivatives
    std.initControls(4)
    std.getCLd()(ElevatorIndex) = CLd
    std.getCmd()(ElevatorIndex) = Cmd
    std.getCld()(0) = 0.03f
    std.getCnd()(2) = 0.036f
    calc.setStabilityDerivatives(std)
    calc.setControlNames(Array("flapaileron", "aileron2", "rudder1", "canard"))
    calc.setControlGains(Array(20f, 20f, 20f, gain))
    calc.setTrimControlValues(Array(0f, 0f, 0f, -0.82874f))
    calc
  }

  def main(args: Array[String]): Unit = {
    val sref = 0.3909
    val bref = 1.0692

    println("the conversion itself")
    val aero = JsbsimExporter.buildAero(calculation(Gain), sref, bref)
    val expected = Cmd * 180.0 / (math.Pi * Gain)
    println(f"  AVL: Cmd = $Cmd%.6f per unit of the canard variable, at a gain of $Gain%.0f deg/unit")
    println(f"  exported: cmde = ${aero.cmde}%.6f per radian  (expected $expected%.6f)")
    check("the pitch control derivative is per radian of deflection", near(aero.cmde, expected, 1e-6))
    check("and so is the lift one",
      near(aero.clde, CLd * 180.0 / (math.Pi * Gain), 1e-6))
    check("it is bigger than the raw number, not smaller", aero.cmde > Cmd)

    println("the identity that says both describe the same surface")
    // AVL trimmed the aircraft with the canard variable at -0.82874, which its gain makes -24.9 degrees.
    // JSBSim will multiply the exported derivative by that same rotation expressed in radians. If the two
    // products differ, the exported aircraft is not the one AVL trimmed.
    val trimVariable = -0.82874
    val avlDeltaCm = Cmd * trimVariable
    val jsbsimDeltaCm = aero.cmde * math.toRadians(trimVariable * Gain)
    println(f"  AVL:     Cmd x d      = $avlDeltaCm%.6f")
    println(f"  JSBSim:  cmde x rad   = $jsbsimDeltaCm%.6f")
    check("the same deflection produces the same pitching moment in both",
      near(avlDeltaCm, jsbsimDeltaCm, 1e-9))

    println("what the old export did instead")
    val understated = Cmd / aero.cmde
    println(f"  it wrote $Cmd%.6f where $expected%.6f belongs: the canard was ${understated}%.2f times weaker")
    check("the error was a factor of 1.9 for this aircraft", near(1.0 / understated, 1.9099, 1e-3))

    println("and at the editor's default gain of 20")
    val default = JsbsimExporter.buildAero(calculation(20f), sref, bref)
    println(f"  a factor of ${default.cmde / Cmd}%.2f")
    check("a gain of 20 understated the controls 2.9 times", near(default.cmde / Cmd, 2.8648, 1e-3))
    val unity = JsbsimExporter.buildAero(calculation(1f), sref, bref)
    check("and a gain of 1 understated them 57 times", near(unity.cmde / Cmd, 57.2958, 1e-3))

    println("the other axes are converted too, each by its own gain")
    check("the ailerons", near(default.clda, 0.03 * 180.0 / (math.Pi * 20f), 1e-6))
    check("and the rudder", near(default.cndr, 0.036 * 180.0 / (math.Pi * 20f), 1e-6))

    println("a surface AVL never deflects")
    val zeroGain = JsbsimExporter.buildAero(calculation(0f), sref, bref)
    // A gain of zero means one unit of the variable rotates the surface not at all, so it contributes
    // nothing in AVL either. Passing the unconverted number through would state a control that works.
    check("contributes nothing, rather than its unconverted number", zeroGain.cmde == 0.0)

    println("what is not a control derivative is left alone")
    check("the rate derivatives are already per radian and stay untouched",
      aero.cmq == 0.0 && aero.cma == 0.0)

    println(if (ok) "CONTROL_EFFECTIVENESS_OK" else "CONTROL_EFFECTIVENESS_FAIL")
    if (!ok) sys.exit(1)
  }
}
