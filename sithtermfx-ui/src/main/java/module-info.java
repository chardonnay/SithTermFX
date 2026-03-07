
module com.sithtermfx.ui {
    requires com.sithtermfx.core;
    requires kotlin.stdlib;
    requires pty4j;
    requires purejavacomm;
    requires org.jetbrains.annotations;
    requires org.slf4j;
    requires java.desktop;
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;

    exports com.sithtermfx.ui;
    exports com.sithtermfx.ui.settings;
    exports com.sithtermfx.ui.split;
}
