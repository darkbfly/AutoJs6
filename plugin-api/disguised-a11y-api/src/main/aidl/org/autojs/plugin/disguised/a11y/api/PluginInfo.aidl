package org.autojs.plugin.disguised.a11y.api;

parcelable PluginInfo {

    String name;
    @nullable String description;

    String author;
    @nullable String[] collaborators;

    String versionName;
    long versionCode;
    @nullable String versionDate;

    /** @sample "disguised-a11y" */
    @nullable String id;
    /** @sample "disguised-a11y" */
    @nullable String engine;
    /** @sample "select-to-speak" */
    @nullable String variant;

    @nullable android.os.Bundle capabilities;

}
