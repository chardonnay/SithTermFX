
module com.sithtermfx.core {
    requires org.jetbrains.annotations;
    requires kotlin.stdlib;
    requires org.slf4j;
    requires java.desktop;

    exports com.sithtermfx.core;
    exports com.sithtermfx.core.compatibility;
    exports com.sithtermfx.core.emulator;
    exports com.sithtermfx.core.emulator.charset;
    exports com.sithtermfx.core.emulator.mouse;
    exports com.sithtermfx.core.emulator.tn3270;
    exports com.sithtermfx.core.emulator.tn5250;
    exports com.sithtermfx.core.emulator.wyse;
    exports com.sithtermfx.core.emulator.tvi;
    exports com.sithtermfx.core.emulator.hp;
    exports com.sithtermfx.core.emulator.petscii;
    exports com.sithtermfx.core.input;
    exports com.sithtermfx.core.model;
    exports com.sithtermfx.core.model.hyperlinks;
    exports com.sithtermfx.core.typeahead;
    exports com.sithtermfx.core.util;
}
