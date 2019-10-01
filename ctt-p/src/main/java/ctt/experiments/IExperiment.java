package ctt.experiments;

import com.google.common.collect.Table;
import ctt.Configuration;
import ctt.Main;
import ctt.coverage.CoverageAnalyser.CounterType;
import ctt.coverage.CoverageAnalyser.CoverageStat;
import ctt.types.TestCollection;

import java.util.Map;

public interface IExperiment {

  void run(Configuration config, TestCollection testCollection,
           Table<String, String, Map<CounterType, CoverageStat>> coverageData);

  void printSummary();
}
