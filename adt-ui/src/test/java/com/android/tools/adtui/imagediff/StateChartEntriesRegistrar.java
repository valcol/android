/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.adtui.imagediff;

import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.RangedSeries;

import java.awt.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

class StateChartEntriesRegistrar extends ImageDiffEntriesRegistrar {

  public StateChartEntriesRegistrar() {
    registerSimpleStateChart();
    registerMultipleSeriesStateChart();
    registerArcHeightWidthStateChart();
    registerTextStateChart();
  }

  private void registerSimpleStateChart() {
    register(new StateChartImageDiffEntry("simple_state_chart_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a simple state chart with only one series
        addSeries();
      }
    });
  }

  private void registerMultipleSeriesStateChart() {
    register(new StateChartImageDiffEntry("multiple_series_state_chart_baseline.png") {
      @Override
      protected void generateComponent() {
        // Create a state chart with multiple series
        addSeries();
        addSeries();
      }
    });
  }

  private void registerTextStateChart() {
    // The threshold for image similarity is overridden by a higher value than the default one in this test,
    // because it's mostly composed of text and the font differ slightly depending on the OS.
    float thresholdSimilarityOverride = 1.5f;

    register(new StateChartImageDiffEntry("text_state_chart_baseline.png", thresholdSimilarityOverride) {
      @Override
      protected void generateComponent() {
        // The component generated by this test is mostly composed by texts, so it's better to use a TrueType Font.
        // TODO: this might hide some issues. We need to use the same font used in studio, to reflect what is seen by the user.
        myStateChart.setFont(ImageDiffUtil.getDefaultFont());

        // Set the render mode of the state chart to text
        myStateChart.setRenderMode(StateChart.RenderMode.TEXT);
        // Add a considerable amount of series to the state chart,
        // because the text of a single state chart doesn't occupy a lot of the image
        for (int i = 0; i < 15; i++) {
          addSeries();
        }
      }
    });
  }

  private void registerArcHeightWidthStateChart() {
    register(new StateChartImageDiffEntry("arc_height_width_state_chart_baseline.png") {
      @Override
      protected void generateComponent() {
        // Modify arc height and width of state chart
        myStateChart.setArcHeight(0.75f);
        myStateChart.setArcWidth(0.75f);
        addSeries();
      }
    });
  }

  private static abstract class StateChartImageDiffEntry extends AnimatedComponentImageDiffEntry {

    private enum TestState {
      NONE,
      STATE1,
      STATE2
    }

    /**
     * Arbitrary values to be added in the state charts.
     */
    private static final TestState[] MY_VALUES = {TestState.NONE, TestState.STATE1, TestState.STATE2, TestState.NONE, TestState.STATE1,
      TestState.STATE2};

    /**
     * Arbitrary flags to determine if a new state should be added to a state chart at some iteration.
     */
    private static final boolean[] NEW_STATE_CONTROL = {true, false, false, false, true, false, false};

    /**
     * Stores the index of the flag that will determine whether a value is going to be inserted in a state chart.
     */
    private int myNewStateControlArrayIndex;

    /**
     * Stores the index of the next value to be inserted in a state chart.
     */
    private int myValuesArrayIndex;

    StateChart<TestState> myStateChart;

    private List<DefaultDataSeries<TestState>> myData;

    StateChartImageDiffEntry(String baselineFilename, float similarityThreshold) {
      super(baselineFilename, similarityThreshold);
    }

    StateChartImageDiffEntry(String baselineFilename) {
      super(baselineFilename);
    }

    @Override
    protected void setUp() {
      myData = new ArrayList<>();
      myStateChart = new StateChart<>(getTestStateColor());
      myContentPane.add(myStateChart, BorderLayout.CENTER);
      myComponents.add(myStateChart);
      myNewStateControlArrayIndex = 0;
      myValuesArrayIndex = 0;
    }

    @Override
    protected void generateTestData() {
      for (int i = 0; i < TOTAL_VALUES; i++) {
        for (DefaultDataSeries<TestState> series : myData) {
          if (NEW_STATE_CONTROL[myNewStateControlArrayIndex++]) {
            int valueIndex = myValuesArrayIndex++ % MY_VALUES.length;
            // Don't add repeated states
            if (series.size() == 0 || series.getY(series.size() - 1) != MY_VALUES[valueIndex]) {
              series.add(myCurrentTimeUs, MY_VALUES[valueIndex]);
            }
          }
          myNewStateControlArrayIndex %= NEW_STATE_CONTROL.length;
        }
        myCurrentTimeUs += TIME_DELTA_US;
      }
    }

    private static EnumMap<TestState, Color> getTestStateColor() {
      EnumMap<TestState, Color> colors = new EnumMap<>(TestState.class);
      colors.put(TestState.NONE, new Color(0, 0, 0, 0));
      colors.put(TestState.STATE1, Color.RED);
      colors.put(TestState.STATE2, Color.BLUE);
      return colors;
    }

    /**
     * Add a series to the state chart.
     */
    protected void addSeries() {
      DefaultDataSeries<TestState> series = new DefaultDataSeries<>();
      RangedSeries<TestState> rangedSeries = new RangedSeries<>(myXRange, series);
      myData.add(series);
      myStateChart.addSeries(rangedSeries);
    }
  }
}