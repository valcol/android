/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio;

import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.ClipartAsset;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.expressions.Expression;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

/**
 * Panel which wraps a {@link ImageAsset}, allowing the user to browse for an image file to use as
 * an asset.
 */
final class ImageAssetPanel extends JPanel implements AssetPanel, Disposable {
  private final ImageAsset myImageAsset = new ImageAsset();
  private final BindingsManager myBindings = new BindingsManager();
  private final List<ActionListener> myListeners = Lists.newArrayListWithExpectedSize(1);

  private JPanel myRootPanel;
  private TextFieldWithBrowseButton myImagePathTextField;
  private JBLabel myPathLabel;

  public ImageAssetPanel() {
    super(new BorderLayout());
    add(myRootPanel);

    myImagePathTextField.addBrowseFolderListener(null, null, null, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());

    final TextProperty imagePathText = new TextProperty(myImagePathTextField.getTextField());
    myBindings.bind(imagePathText, new Expression<String>(myImageAsset.imagePath()) {
      @NotNull
      @Override
      public String get() {
        return myImageAsset.imagePath().get().getAbsolutePath();
      }
    });

    myBindings.bind(myImageAsset.imagePath(), new Expression<File>(imagePathText) {
      @NotNull
      @Override
      public File get() {
        return new File(imagePathText.get());
      }
    });

    InvalidationListener onImageChanged = new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        ActionEvent e = new ActionEvent(ImageAssetPanel.this, ActionEvent.ACTION_PERFORMED, null);
        for (ActionListener listener : myListeners) {
          listener.actionPerformed(e);
        }
      }
    };
    myImageAsset.imagePath().addListener(onImageChanged);
  }

  @NotNull
  @Override
  public BaseAsset getAsset() {
    return myImageAsset;
  }

  @Override
  public void addActionListener(@NotNull ActionListener l) {
    myListeners.add(l);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
  }
}