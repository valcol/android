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
package com.android.tools.idea.res;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.stubs.android.AndroidLibraryStub;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.android.tools.idea.model.AndroidModel;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static com.android.tools.idea.res.ModuleResourceRepositoryTest.assertHasExactResourceTypes;
import static com.android.tools.idea.res.ModuleResourceRepositoryTest.getFirstItem;

public class ProjectResourceRepositoryTest extends AndroidTestCase {
  private static final String LAYOUT = "resourceRepository/layout.xml";
  private static final String VALUES = "resourceRepository/values.xml";
  private static final String VALUES_OVERLAY1 = "resourceRepository/valuesOverlay1.xml";
  private static final String VALUES_OVERLAY2 = "resourceRepository/valuesOverlay2.xml";
  private static final String VALUES_OVERLAY2_NO = "resourceRepository/valuesOverlay2No.xml";

  private static final Logger logger = Logger.getInstance(ProjectResourceRepositoryTest.class);

  public void testStable() {
    assertSame(ProjectResourceRepository.getProjectResources(myFacet, true), ProjectResourceRepository.getProjectResources(myFacet, true));
    assertSame(ProjectResourceRepository.getProjectResources(myFacet, true), ProjectResourceRepository.getProjectResources(myModule, true));
  }

  // Ensure that we invalidate the id cache when the file is rescanned but ids don't change
  // (this was broken)
  public void testInvalidateIds() {
    // Like testOverlayUpdates1, but rather than testing changes to layout resources (file-based resource)
    // perform document edits in value-documents

    VirtualFile layoutFile = myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");

    VirtualFile res1 = myFixture.copyFileToProject(VALUES, "res/values/values.xml").getParent().getParent();
    VirtualFile res2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml").getParent().getParent();
    VirtualFile res3 = myFixture.copyFileToProject(VALUES_OVERLAY2, "res3/values/nameDoesNotMatter.xml").getParent().getParent();
    myFixture.copyFileToProject(VALUES_OVERLAY2_NO, "res3/values-no/values.xml");

    assertNotSame(res1, res2);
    assertNotSame(res1, res3);
    assertNotSame(res2, res3);

    // Just need an empty repository to make it a real module -set-; otherwise with a single
    // module we just get a module repository, not a module set repository
    LocalResourceRepository other = new LocalResourceRepository("unit test") {
      @NonNull
      @Override
      protected Map<ResourceType, ListMultimap<String, ResourceItem>> getMap() {
        return Collections.emptyMap();
      }

      @Nullable
      @Override
      protected ListMultimap<String, ResourceItem> getMap(ResourceType type, boolean create) {
        return ArrayListMultimap.create();
      }

      @NotNull
      @Override
      protected Set<VirtualFile> computeResourceDirs() {
        return ImmutableSet.of();
      }
    };

    ModuleResourceRepository module = ModuleResourceRepository.createForTest(myFacet, Arrays.asList(res1, res2, res3));
    final ProjectResourceRepository resources = ProjectResourceRepository.createForTest(myFacet, Arrays.asList(module, other));

    PsiFile layoutPsiFile = PsiManager.getInstance(getProject()).findFile(layoutFile);
    assertNotNull(layoutPsiFile);
    assertTrue(resources.hasResourceItem(ResourceType.ID, "btn_title_refresh"));
    final ResourceItem item = getFirstItem(resources, ResourceType.ID, "btn_title_refresh");

    final long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(layoutPsiFile);
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String string = "<ImageView style=\"@style/TitleBarSeparator\" />";
      int offset = document.getText().indexOf(string);
      document.deleteString(offset, offset + string.length());
      documentManager.commitDocument(document);
    });

    assertTrue(resources.isScanPending(layoutPsiFile));
    ApplicationManager.getApplication().invokeLater(() -> {
      assertTrue(generation < resources.getModificationCount());
      // Should still be defined:
      assertTrue(resources.hasResourceItem(ResourceType.ID, "btn_title_refresh"));
      ResourceItem newItem = getFirstItem(resources, ResourceType.ID, "btn_title_refresh");
      assertNotNull(newItem.getSource());
      // However, should be a different item
      assertNotSame(item, newItem);
    });
    UIUtil.dispatchAllInvocationEvents();
  }

  // Regression test for https://code.google.com/p/android/issues/detail?id=57090
  public void testParents() {
    myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");
    List<AndroidFacet> libraries = AndroidUtils.getAllAndroidDependencies(myModule, true);
    assertEquals(2, libraries.size());
    ModuleRootModificationUtil.addDependency(libraries.get(0).getModule(), libraries.get(1).getModule());

    addArchiveLibraries();

    ProjectResourceRepository repository = ProjectResourceRepository.create(myFacet);
    assertEquals(3, repository.getChildCount());
    Collection<String> items = repository.getItemsOfType(ResourceType.STRING);
    assertTrue(items.isEmpty());

    for (AndroidFacet facet : libraries) {
      LocalResourceRepository moduleRepository = facet.getModuleResources(true);
      assertNotNull(moduleRepository);
      LocalResourceRepository moduleSetRepository = facet.getProjectResources(true);
      assertNotNull(moduleSetRepository);
      LocalResourceRepository librarySetRepository = facet.getAppResources(true);
      assertNotNull(librarySetRepository);
    }
    myFacet.getModuleResources(true);
    myFacet.getProjectResources(true);
    myFacet.getAppResources(true);
  }

  public void testGetResourceDirsAndUpdateRoots() {
    myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");
    addArchiveLibraries();
    List<VirtualFile> flavorDirs = Lists.newArrayList(myFacet.getAllResourceDirectories());
    // For debugging: check that the IO File collection is consistent with the VirtualFile one.
    Collection<File> ioFileFlavorDirs =
      IdeaSourceProvider.getAllSourceProviders(myFacet)
        .stream()
        .flatMap(provider -> provider.getResDirectories().stream())
        .collect(Collectors.toList());
    final ProjectResourceRepository repository = ProjectResourceRepository.create(myFacet);
    List<? extends LocalResourceRepository> originalChildren = repository.getChildren();
    // Should have a bunch repository directories from the various flavors.
    Set<VirtualFile> resourceDirs = repository.getResourceDirs();
    String beforeState =
      new DebugState("Before Removal", myFacet.getAndroidModel(), flavorDirs, ioFileFlavorDirs, resourceDirs, originalChildren).toString();
    logger.warn("Test logging: " + beforeState);
    if (resourceDirs.isEmpty()) {
      logger.warn("Had empty resourceDirs: " + beforeState);
      // Check if the IO files actually exist.
      for (File ioDir : ioFileFlavorDirs) {
        if (!ioDir.exists()) {
          logger.warn("Dir doesn't exist: " + ioDir.getPath());
        }
      }
      // See if a VFS refresh and root update will help.
      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        ioFileFlavorDirs.forEach(file -> {
          VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, true);
          if (virtualFile == null) {
            logger.warn("Still can't find the virtual file for " + file);
          }
        });
        repository.updateRoots();
      });
      // Load up the dirs again, before trying the asserts a second time.
      flavorDirs = Lists.newArrayList(myFacet.getAllResourceDirectories());
      originalChildren = repository.getChildren();
      resourceDirs = repository.getResourceDirs();
      String refreshedState =
        new DebugState("After hacky refresh", myFacet.getAndroidModel(), flavorDirs, ioFileFlavorDirs, resourceDirs, originalChildren)
          .toString();
      logger.warn("State after hacky refresh: " + refreshedState);
      // If it's still empty, just disable the test. We'll look over the logs.
      if (resourceDirs.isEmpty() || flavorDirs.isEmpty()) {
        logger.warn("Still no resource directories! Test disabled.");
        return;
      }
    }
    assertFalse(beforeState, resourceDirs.isEmpty());
    assertFalse(beforeState, flavorDirs.isEmpty());
    assertSameElements(beforeState, resourceDirs, flavorDirs);

    // Now delete a directory, notify the folder manager and updateRoots.
    final VirtualFile firstFlavor = flavorDirs.remove(0);
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        try {
          firstFlavor.delete(this);
        }
        catch (IOException e) {
          assertFalse("delete failed " + e, false);
        }
        myFacet.getResourceFolderManager().invalidate();
        repository.updateRoots();
      }
    });
    // The child repositories should be the same since the module structure didn't change.
    List<? extends LocalResourceRepository> newChildren = repository.getChildren();
    Set<VirtualFile> newResourceDirs = repository.getResourceDirs();
    String afterState =
      new DebugState("After Removal", myFacet.getAndroidModel(), flavorDirs, ioFileFlavorDirs, newResourceDirs, newChildren).toString();
    assertTrue(afterState, newChildren.equals(originalChildren));
    // However, the resourceDirs should now be different, missing the first flavor directory.
    assertSameElements(afterState, newResourceDirs, flavorDirs);
  }

  // For debugging failures to testGetResourceDirsAndUpdateRoots (temporary).
  // See: https://code.google.com/p/android/issues/detail?id=227339
  private static class DebugState {
    private final String label;
    private final AndroidModel androidModel;
    private final Collection<VirtualFile> flavorResDirs;
    private final Collection<File> ioFileFlavorResDirs;
    private final Collection<VirtualFile> resDirs;
    private final Collection<? extends LocalResourceRepository> repoChildren;

    DebugState(String label,
               AndroidModel androidModel,
               Collection<VirtualFile> flavorResDirs,
               Collection<File> ioFileFlavorResDirs,
               Collection<VirtualFile> resDirs,
               Collection<? extends LocalResourceRepository> repoChildren) {
      this.label = label;
      this.androidModel = androidModel;
      this.flavorResDirs = flavorResDirs;
      this.ioFileFlavorResDirs = ioFileFlavorResDirs;
      this.resDirs = resDirs;
      this.repoChildren = repoChildren;
    }

    @Override
    public String toString() {
      return String.format("%s: {\n\tmodel: %s\n\tflavorResDirs: %s\n\tioFileFlavorResDirs: %s\n\tresDirs: %s\n\trepoChildren: %s\n}",
                           label,
                           androidModel.toString(),
                           String.format("|%d| %s",
                                         flavorResDirs.size(),
                                         String.join(", ", flavorResDirs.stream().map(VirtualFile::getPath).collect(Collectors.toList()))),
                           String.format("|%d| %s",
                                         ioFileFlavorResDirs.size(),
                                         String.join(", ", ioFileFlavorResDirs.stream().map(File::getPath).collect(Collectors.toList()))),
                           String.format("|%d| %s",
                                         resDirs.size(),
                                         String.join(", ", resDirs.stream().map(VirtualFile::getPath).collect(Collectors.toList()))),
                           String.format("|%d| %s",
                                         repoChildren.size(),
                                         String.join(", ", repoChildren.stream().map(repo -> describeResourceRepository(repo, 1))
                                           .collect(Collectors.toList())))
      );
    }

    private static String describeResourceRepository(LocalResourceRepository repository, int tabLevel) {
      String tabs = "";
      for (int i = 0; i < tabLevel; ++i) {
        tabs += "\t";
      }
      if (repository instanceof MultiResourceRepository) {
        MultiResourceRepository multiRepo = (MultiResourceRepository)repository;
        return String.format("%s%s w/ %d children {\n%s\n%s}",
                             tabs,
                             multiRepo.toString(),
                             multiRepo.getChildCount(),
                             String.join("\n",
                                         multiRepo.getChildren().stream().map(child -> describeResourceRepository(child, tabLevel + 1))
                                           .collect(
                                             Collectors.toList())),
                             tabs);
      }
      else {
        return String.format("%s%s", tabs, repository.toString());
      }
    }
  }

  public void testHasResourcesOfType() {
    // Test hasResourcesOfType merging (which may be optimized to be lighter-weight than map merging).
    VirtualFile res1 = myFixture.copyFileToProject(LAYOUT, "res/layout/layout.xml").getParent().getParent();
    VirtualFile res2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml").getParent().getParent();
    VirtualFile values3 = myFixture.copyFileToProject(VALUES, "res3/values/many_more_values.xml");
    VirtualFile res3 = values3.getParent().getParent();

    assertNotSame(res1, res2);
    assertNotSame(res1, res3);
    assertNotSame(res2, res3);
    // Test having some overlap between the modules.
    LocalResourceRepository module1 = ModuleResourceRepository.createForTest(myFacet, Arrays.asList(res1, res2));
    LocalResourceRepository module2 = ModuleResourceRepository.createForTest(myFacet, Arrays.asList(res2, res3));
    final ProjectResourceRepository resources = ProjectResourceRepository.createForTest(myFacet, Arrays.asList(module1, module2));

    // Create a repo with res1, res2, res3 and check types.
    // After that, delete a file in res3 and check types again.
    EnumSet<ResourceType> typesWithoutRes3 = EnumSet.of(ResourceType.ARRAY, ResourceType.ID, ResourceType.LAYOUT,
                                                        ResourceType.STRING, ResourceType.STYLE);
    EnumSet<ResourceType> allTypes = EnumSet.copyOf(typesWithoutRes3);
    allTypes.addAll(Arrays.asList(ResourceType.ATTR, ResourceType.INTEGER, ResourceType.DECLARE_STYLEABLE, ResourceType.PLURALS));


    assertHasExactResourceTypes(resources, allTypes);
    // Now delete the values file and check again.
    final PsiFile psiValues3 = PsiManager.getInstance(getProject()).findFile(values3);
    assertNotNull(psiValues3);
    WriteCommandAction.runWriteCommandAction(null, psiValues3::delete);
    assertHasExactResourceTypes(resources, typesWithoutRes3);
  }

  private void addArchiveLibraries() {
    // Add in some Android projects too
    myFacet.getProperties().ALLOW_USER_CONFIGURATION = false; // make it a Gradle project
    AndroidProjectStub androidProject = TestProjects.createFlavorsProject();
    VariantStub variant = androidProject.getFirstVariant();
    assertNotNull(variant);
    File rootDir = androidProject.getRootDir();
    AndroidModuleModel androidModel = new AndroidModuleModel(androidProject.getName(), rootDir, androidProject, variant.getName());
    myFacet.setAndroidModel(androidModel);

    File bundle = new File(rootDir, "bundle.aar");
    File libJar = new File(rootDir, "bundle_aar" + File.separatorChar + "library.jar");
    AndroidLibraryStub library = new AndroidLibraryStub(bundle, libJar);
    variant.getMainArtifact().getDependencies().addLibrary(library);
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    final String testName = getTestName(true);
    if (testName.equals("parents")) { // for unit test testDependencies
      addModuleWithAndroidFacet(projectBuilder, modules, "plib1", PROJECT_TYPE_LIBRARY);
      addModuleWithAndroidFacet(projectBuilder, modules, "plib2", PROJECT_TYPE_LIBRARY);
    } else if (testName.equals("dependencies")) { // for unit test testDependencies
      addModuleWithAndroidFacet(projectBuilder, modules, "sharedlib", PROJECT_TYPE_LIBRARY);
      addModuleWithAndroidFacet(projectBuilder, modules, "lib1", PROJECT_TYPE_LIBRARY);
      addModuleWithAndroidFacet(projectBuilder, modules, "lib2", PROJECT_TYPE_LIBRARY);
      addModuleWithAndroidFacet(projectBuilder, modules, "app", PROJECT_TYPE_APP);
    }
  }

  // Regression test for https://code.google.com/p/android/issues/detail?id=65140
  public void testDependencies() throws Exception {
    myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");

    Module lib1 = null;
    Module lib2 = null;
    Module sharedLib = null;
    Module app = null;

    for (Module module : ModuleManager.getInstance(getProject()).getModules()) {
      if (module != myModule) {
        VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
        assertEquals(1, contentRoots.length);

        String name = contentRoots[0].getName();
        switch (name) {
          case "lib1":
            lib1 = module;
            break;
          case "lib2":
            lib2 = module;
            break;
          case "sharedlib":
            sharedLib = module;
            break;
          case "app":
            app = module;
            break;
          default:
            fail(name);
            break;
        }
      }
    }

    assertNotNull(lib1);
    assertNotNull(lib2);
    assertNotNull(sharedLib);
    assertNotNull(app);

    renameModule(lib1, "lib1");
    renameModule(lib2, "lib2");
    renameModule(sharedLib, "sharedLib");
    renameModule(app, "app");

    AndroidFacet lib1Facet = AndroidFacet.getInstance(lib1);
    AndroidFacet lib2Facet = AndroidFacet.getInstance(lib2);
    AndroidFacet sharedLibFacet = AndroidFacet.getInstance(sharedLib);
    AndroidFacet appFacet = AndroidFacet.getInstance(app);

    assertNotNull(lib1Facet);
    assertNotNull(lib2Facet);
    assertNotNull(sharedLibFacet);
    assertNotNull(appFacet);

    // Set up project dependencies
    addModuleDependency(lib1, sharedLib);
    addModuleDependency(lib2, sharedLib);
    addModuleDependency(app, lib1);
    addModuleDependency(app, lib2);

    assertTrue(ModuleRootManager.getInstance(app).isDependsOn(lib1));
    assertTrue(ModuleRootManager.getInstance(lib1).isDependsOn(sharedLib));
    assertFalse(ModuleRootManager.getInstance(sharedLib).isDependsOn(lib1));
    assertFalse(ModuleRootManager.getInstance(lib2).isDependsOn(lib1));
    // Note that these are currently direct dependencies only, so app.isDependsOn(sharedLib) is false

    // Test AndroidUtils#getallAndroidDependencies
    List<AndroidFacet> appDependsOn = AndroidUtils.getAllAndroidDependencies(app, true);
    assertTrue(appDependsOn.contains(lib1Facet));
    assertTrue(appDependsOn.contains(lib2Facet));
    assertTrue(appDependsOn.contains(sharedLibFacet));
    assertFalse(appDependsOn.contains(appFacet));

    List<AndroidFacet> lib1DependsOn = AndroidUtils.getAllAndroidDependencies(lib1, true);
    assertTrue(lib1DependsOn.contains(sharedLibFacet));
    assertFalse(lib1DependsOn.contains(appFacet));
    assertFalse(lib1DependsOn.contains(lib1Facet));
    assertFalse(lib1DependsOn.contains(lib2Facet));

    // Set up resources so we can check which values are inherited through module dependencies
    VirtualFile v1 = myFixture.copyFileToProject(VALUES, "additionalModules/sharedlib/res/values/sharedvalues.xml");
    VirtualFile v2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "additionalModules/lib2/res/values/lib2values.xml");
    assertNotNull(v1);
    assertNotNull(v2);

    PsiManager manager = PsiManager.getInstance(getProject());
    PsiFile sharedLibValues = manager.findFile(v1);
    PsiFile lib2Values = manager.findFile(v2);
    assertNotNull(sharedLibValues);
    assertNotNull(lib2Values);

    final AppResourceRepository lib1Resources = lib1Facet.getAppResources(true);
    final AppResourceRepository lib2Resources = lib2Facet.getAppResources(true);
    assertNotNull(lib1Resources);
    assertNotNull(lib2Resources);
    assertNotSame(lib1Resources, lib2Resources);

    assertFalse(lib1Resources.isScanPending(sharedLibValues));
    assertFalse(lib1Resources.isScanPending(lib2Values));
    assertFalse(lib2Resources.isScanPending(sharedLibValues));
    assertFalse(lib2Resources.isScanPending(lib2Values));

    assertTrue(lib1Resources.hasResourceItem(ResourceType.PLURALS, "my_plural"));
    assertTrue(lib1Resources.hasResourceItem(ResourceType.STRING, "ellipsis"));
    assertTrue(lib1Resources.hasResourceItem(ResourceType.ARRAY, "security_questions"));
    List<ResourceItem> items = lib1Resources.getResourceItem(ResourceType.STRING, "ellipsis");
    assertNotNull(items);
    ResourceValue firstValue = items.get(0).getResourceValue(false);
    assertNotNull(firstValue);
    assertEquals("Here it is: \u2026!", firstValue.getValue());

    assertTrue(lib2Resources.hasResourceItem(ResourceType.ARRAY, "security_questions"));
    assertTrue(lib2Resources.hasResourceItem(ResourceType.PLURALS, "my_plural"));
    assertTrue(lib2Resources.hasResourceItem(ResourceType.STRING, "ellipsis"));

    // ONLY defined in lib2: should not be visible from lib1
    assertTrue(lib2Resources.hasResourceItem(ResourceType.STRING, "unique_string"));
    items = lib2Resources.getResourceItem(ResourceType.STRING, "unique_string");
    assertNotNull(items);
    firstValue = items.get(0).getResourceValue(false);
    assertNotNull(firstValue);
    assertEquals("Unique", firstValue.getValue());

    assertFalse(lib1Resources.hasResourceItem(ResourceType.STRING, "unique_string"));
  }

  private static void addModuleDependency(Module from, Module to) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(from).getModifiableModel();
    model.addModuleOrderEntry(to);
    ApplicationManager.getApplication().runWriteAction(model::commit);
  }

  private static void renameModule(Module from, String name) throws ModuleWithNameAlreadyExists {
    final ModifiableModuleModel model = ModuleManager.getInstance(from.getProject()).getModifiableModel();
    model.renameModule(from, name);
    ApplicationManager.getApplication().runWriteAction(model::commit);
  }

  // Note that the project resource repository is also tested in the app resource repository test, which of course merges
  // project resources with libraries
}
