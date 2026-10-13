package org.autojs.plugin.disguised.a11y.api;

import org.autojs.plugin.disguised.a11y.api.PluginInfo;
import org.autojs.plugin.disguised.a11y.api.NodeSnapshot;

interface IDisguisedA11yPlugin {

    PluginInfo getInfo();

    @nullable NodeSnapshot captureActiveWindow();

    boolean isServiceConnected();

}
