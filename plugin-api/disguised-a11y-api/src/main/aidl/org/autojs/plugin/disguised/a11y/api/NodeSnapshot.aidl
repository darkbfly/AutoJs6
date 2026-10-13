package org.autojs.plugin.disguised.a11y.api;

parcelable NodeSnapshot {

    @nullable String packageName;
    @nullable String id;
    @nullable String fullId;
    @nullable String desc;
    @nullable String text;
    android.graphics.Rect boundsInParent;
    android.graphics.Rect boundsInScreen;
    @nullable String className;

    boolean clickable;
    boolean longClickable;
    boolean scrollable;
    boolean checked;
    boolean enabled;
    boolean editable;
    boolean focusable;
    boolean checkable;
    boolean selected;
    boolean dismissable;
    boolean visibleToUser;
    boolean contextClickable;
    boolean focused;
    boolean accessibilityFocused;

    int indexInParent;
    int childCount;
    int depth;
    int rowCount;
    int columnCount;
    int row;
    int column;
    int rowSpan;
    int columnSpan;
    int drawingOrder;

    @nullable String[] actionNames;
    @nullable NodeSnapshot[] children;

}
