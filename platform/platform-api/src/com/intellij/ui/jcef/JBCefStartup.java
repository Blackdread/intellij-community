// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.application.options.RegistryManager;

/**
 * Forces JCEF early startup in order to support co-existence with JavaFX (see IDEA-236310).
 */
final class JBCefStartup {
  @SuppressWarnings("unused")
  private JBCefClient STARTUP_CLIENT; // auto-disposed along with JBCefApp on IDE shutdown

  // os=mac
  JBCefStartup() {
    if (RegistryManager.getInstance().is("ide.browser.jcef.enabled") &&
        RegistryManager.getInstance().is("ide.browser.jcef.preinit"))
    {
      STARTUP_CLIENT = JBCefApp.getInstance().createClient();
    }
  }
}
