package ctt.types;

import java.util.ArrayList;
import java.util.List;

/**
 * [Used for JSON (de)/serialization] A list of the hit spectra in a test.
 */
public class TestCollection {

  public List<HitSpectrum> tests = new ArrayList<>();
}
