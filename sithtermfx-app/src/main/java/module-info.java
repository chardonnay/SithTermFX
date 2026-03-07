
module com.sithtermfx.app {
    requires com.sithtermfx.core;
    requires com.sithtermfx.ui;
    requires kotlin.stdlib;
    requires pty4j;
    requires purejavacomm;
    requires org.jetbrains.annotations;
    requires org.slf4j;
    requires java.desktop;
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires org.apache.logging.log4j.core;

    exports com.sithtermfx.app;
    exports com.sithtermfx.app.example;
}
