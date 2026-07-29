/*
 * Manual verification for AeroDerivation using a real NACA 0012 XFOIL polar
 * (Re=1e6, alpha 0..16). Run with:  sbt "test:runMain com.abajar.avleditor.xfoil.AeroDerivationCheck"
 */
package com.abajar.avleditor.xfoil

object AeroDerivationCheck {

  private val naca0012Polar: Seq[XfoilPolarPoint] = Seq(
    XfoilPolarPoint(0.0f,  0.0000f, 0.00540f, 0.00046f, -0.0000f),
    XfoilPolarPoint(2.0f,  0.2142f, 0.00580f, 0.00064f,  0.0030f),
    XfoilPolarPoint(4.0f,  0.4278f, 0.00728f, 0.00118f,  0.0060f),
    XfoilPolarPoint(6.0f,  0.6948f, 0.00973f, 0.00224f, -0.0043f),
    XfoilPolarPoint(8.0f,  0.9099f, 0.01211f, 0.00356f, -0.0039f),
    XfoilPolarPoint(10.0f, 1.0809f, 0.01498f, 0.00533f,  0.0053f),
    XfoilPolarPoint(12.0f, 1.2454f, 0.01936f, 0.00825f,  0.0133f),
    XfoilPolarPoint(14.0f, 1.3501f, 0.02611f, 0.01279f,  0.0267f),
    XfoilPolarPoint(16.0f, 1.3877f, 0.04171f, 0.02442f,  0.0302f)
  )

  def main(args: Array[String]): Unit = {
    val d = AeroDerivation.deriveFromPolar(naca0012Polar)
    println(s"derived: $d")

    var ok = true
    def check(name: String, cond: Boolean): Unit = {
      println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
    }

    // clMax3D = 0.9 * clMax2D(1.3877) = 1.2489
    check("clMax ≈ 1.249", d.clMax.exists(v => math.abs(v - 1.2489f) < 0.01f))
    // symmetric airfoil, alpha>=0 sweep -> clMin ~ 0
    check("clMin ≈ 0", d.clMin.exists(v => math.abs(v) < 0.01f))
    // min drag at alpha 0 -> CL at min CD ~ 0
    check("clCD0 ≈ 0", d.clCD0.exists(v => math.abs(v) < 0.05f))
    // parabolic-polar curvature: physically sane, order ~0.01
    check("cdCLsq in (0.004, 0.02)", d.cdCLsq.exists(v => v > 0.004f && v < 0.02f))
    check("uexpCD None (single Reynolds)", d.uexpCD.isEmpty)

    // Reynolds-exponent path with a synthetic higher-Re polar (lower drag).
    val highRe = naca0012Polar.map(p => p.copy(cd = p.cd * 0.85f))
    val d2 = AeroDerivation.deriveFromPolar(naca0012Polar, Some((highRe, 1.0e6, 3.0e6)))
    // cd2/cd1 = 0.85 over Re ratio 3 -> n = ln(0.85)/ln(3) ≈ -0.148
    check("uexpCD ≈ -0.148", d2.uexpCD.exists(v => math.abs(v + 0.148f) < 0.01f))

    println(if (ok) "AERO_DERIVATION_OK" else "AERO_DERIVATION_FAIL")
    if (!ok) sys.exit(1)
  }
}
