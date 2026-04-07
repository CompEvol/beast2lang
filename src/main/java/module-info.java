open module beast.language {
    requires beast.base;
    requires beast.pkgmgmt;
    requires beast.labs;
    requires feast;
    requires info.picocli;
    requires org.json;
    requires org.antlr.antlr4.runtime;
    requires java.logging;

    exports org.beast2.modelLanguage;
    exports org.beast2.modelLanguage.beast;
    exports org.beast2.modelLanguage.builder;
    exports org.beast2.modelLanguage.builder.handlers;
    exports org.beast2.modelLanguage.converter;
    exports org.beast2.modelLanguage.converter.pipeline;
    exports org.beast2.modelLanguage.data;
    exports org.beast2.modelLanguage.model;
    exports org.beast2.modelLanguage.operators;
    exports org.beast2.modelLanguage.parser;
    exports org.beast2.modelLanguage.phylospec;
    exports org.beast2.modelLanguage.schema;
    exports org.beast2.modelLanguage.schema.builder;
    exports org.beast2.modelLanguage.schema.core;
    exports org.beast2.modelLanguage.schema.scanner;
    exports org.beast2.modelLanguage.schema.validation;
}
